//! Núcleo nativo do XISO Tool.
//!
//! Este crate é compilado como `cdylib` para Android e expõe três funções JNI:
//! `extractIso`, `createIso` e `cancel`. O processamento é feito em streaming,
//! com progresso real e cancelamento cooperativo.

use std::ffi::CString;
use std::fs::{self, File};
use std::io::{self, BufWriter, Read, Seek, SeekFrom, Write};
use std::panic::{catch_unwind, AssertUnwindSafe};
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::{Arc, Mutex};
use std::time::Instant;

use jni::objects::{GlobalRef, JObject, JString, JValue};
use jni::sys::jstring;
use jni::{JNIEnv, JavaVM};
use xdvdfs::blockdev::{BlockDeviceRead, BlockDeviceWrite};
use xdvdfs::layout::{VolumeDescriptor, SECTOR_SIZE};
use xdvdfs::write::fs::StdFilesystem;
use xdvdfs::write::img::{create_xdvdfs_image, ProgressInfo};

/// Bases conhecidas de imagens Xbox/XGD, além da XISO "crua" (offset 0).
/// A detecção é feita lendo o volume descriptor no setor 32 de cada base.
const XISO_BASES: &[u64] = &[
    0,
    405_798_912, // XGD1
    265_879_552, // XGD2
    34_078_720,  // XGD3
    198_144,     // 0x30600, usado por algumas ferramentas antigas
];

/// Buffer de cópia de 1 MiB: mantém o uso de memória baixo mesmo com ISOs de 7 GB.
const COPY_BUFFER: usize = 1024 * 1024;

/// Intervalo mínimo entre callbacks JNI de progresso (cerca de 8 por segundo).
const PROGRESS_INTERVAL_MS: u64 = 120;

static CANCELLED: AtomicBool = AtomicBool::new(false);

type OpResult<T> = Result<T, String>;

fn cancelled() -> bool {
    CANCELLED.load(Ordering::SeqCst)
}

fn check_cancel() -> OpResult<()> {
    if cancelled() {
        Err("Operação cancelada.".to_string())
    } else {
        Ok(())
    }
}

fn run_catching<F, T>(operation: F) -> OpResult<T>
where
    F: FnOnce() -> OpResult<T>,
{
    match catch_unwind(AssertUnwindSafe(operation)) {
        Ok(result) => result,
        Err(_) => Err("Erro interno inesperado ao processar a imagem.".to_string()),
    }
}

fn jstring_to_string(env: &mut JNIEnv<'_>, value: &JString<'_>) -> OpResult<String> {
    env.get_string(value)
        .map(Into::into)
        .map_err(|error| format!("Não foi possível ler o caminho informado: {error}"))
}

fn string_to_jstring(env: &mut JNIEnv<'_>, message: &str) -> jstring {
    match env.new_string(message) {
        Ok(value) => value.into_raw(),
        Err(_) => match env.new_string("Erro interno ao preparar a mensagem de retorno.") {
            Ok(value) => value.into_raw(),
            Err(_) => std::ptr::null_mut(),
        },
    }
}

/// Envia progresso para o callback Kotlin de forma segura e limitada.
///
/// O callback é mantido como referência global. O thread nativo já está ligado à
/// JVM quando a operação começa; `attach_current_thread` apenas reutiliza a
/// ligação existente (e cobre o caso de uma chamada vinda de outro thread).
struct Reporter {
    vm: JavaVM,
    callback: Option<GlobalRef>,
    epoch: Instant,
    total: AtomicU64,
    processed: AtomicU64,
    last_emit_ms: AtomicU64,
    current: Mutex<String>,
}

impl Reporter {
    fn new(env: &mut JNIEnv<'_>, callback: JObject<'_>) -> OpResult<Arc<Self>> {
        let vm = env
            .get_java_vm()
            .map_err(|error| format!("Não foi possível obter a JVM: {error}"))?;

        let callback = if callback.as_raw().is_null() {
            None
        } else {
            Some(
                env.new_global_ref(callback)
                    .map_err(|error| format!("Callback de progresso inválido: {error}"))?,
            )
        };

        Ok(Arc::new(Self {
            vm,
            callback,
            epoch: Instant::now(),
            total: AtomicU64::new(0),
            processed: AtomicU64::new(0),
            last_emit_ms: AtomicU64::new(0),
            current: Mutex::new(String::new()),
        }))
    }

    fn set_total(&self, total: u64) {
        self.total.store(total, Ordering::Relaxed);
        self.emit(true);
    }

    fn set_processed(&self, processed: u64) {
        self.processed.store(processed, Ordering::Relaxed);
        self.emit(false);
    }

    fn add_processed(&self, amount: u64) {
        let processed = self
            .processed
            .fetch_add(amount, Ordering::Relaxed)
            .saturating_add(amount);
        let total = self.total.load(Ordering::Relaxed);
        if total > 0 && processed > total {
            self.processed.store(total, Ordering::Relaxed);
        }
        self.emit(false);
    }

    fn set_current(&self, current: &str) {
        self.set_current_internal(current, false);
    }

    fn set_current_now(&self, current: &str) {
        self.set_current_internal(current, true);
    }

    fn set_current_internal(&self, current: &str, force: bool) {
        match self.current.lock() {
            Ok(mut value) => *value = current.to_string(),
            Err(poisoned) => *poisoned.into_inner() = current.to_string(),
        }
        self.emit(force);
    }

    fn complete(&self) {
        let total = self.total.load(Ordering::Relaxed);
        self.processed.store(total, Ordering::Relaxed);
        self.set_current_internal("Concluído", true);
    }

    fn emit(&self, force: bool) {
        let Some(callback) = &self.callback else {
            return;
        };

        let now_ms = self.epoch.elapsed().as_millis() as u64;
        if !force {
            let last = self.last_emit_ms.load(Ordering::Relaxed);
            if now_ms.saturating_sub(last) < PROGRESS_INTERVAL_MS {
                return;
            }
        }
        self.last_emit_ms.store(now_ms, Ordering::Relaxed);

        let processed = self.processed.load(Ordering::Relaxed) as i64;
        let total = self.total.load(Ordering::Relaxed) as i64;
        let current = match self.current.lock() {
            Ok(value) => value.clone(),
            Err(poisoned) => poisoned.into_inner().clone(),
        };

        if let Ok(mut env) = self.vm.attach_current_thread() {
            if let Ok(value) = env.new_string(current) {
                let value = JObject::from(value);
                {
                    let arguments = [
                        JValue::Long(processed),
                        JValue::Long(total),
                        JValue::Object(&value),
                    ];
                    let _ = env.call_method(
                        callback.as_obj(),
                        "onProgress",
                        "(JJLjava/lang/String;)V",
                        &arguments,
                    );
                }
                // Sem isto, uma operação longa acumularia referências locais.
                let _ = env.delete_local_ref(value);
            }
            if env.exception_check().unwrap_or(false) {
                let _ = env.exception_clear();
            }
        }
    }
}

fn validate_component(name: &str) -> OpResult<()> {
    if name.is_empty()
        || name == "."
        || name == ".."
        || name.contains('/')
        || name.contains('\\')
        || name.contains('\0')
    {
        return Err(format!("Nome de arquivo inválido na imagem: \"{name}\"."));
    }
    Ok(())
}

fn temp_path(target: &Path, suffix: &str) -> OpResult<PathBuf> {
    let name = target
        .file_name()
        .and_then(|value| value.to_str())
        .ok_or_else(|| "O caminho de destino não tem um nome de arquivo válido.".to_string())?;
    Ok(target.with_file_name(format!(".{name}.{suffix}")))
}

fn remove_any(path: &Path) {
    if path.is_dir() {
        let _ = fs::remove_dir_all(path);
    } else {
        let _ = fs::remove_file(path);
    }
}

fn human_bytes(bytes: u64) -> String {
    const MIB: f64 = 1024.0 * 1024.0;
    const GIB: f64 = 1024.0 * 1024.0 * 1024.0;
    let value = bytes as f64;
    if value >= GIB {
        format!("{:.1} GB", value / GIB).replace('.', ",")
    } else {
        format!("{:.0} MB", value / MIB)
    }
}

#[cfg(unix)]
fn available_space(path: &Path) -> Option<u64> {
    use std::os::unix::ffi::OsStrExt;

    let path = CString::new(path.as_os_str().as_bytes()).ok()?;
    let mut stats = std::mem::MaybeUninit::<libc::statvfs>::uninit();
    let result = unsafe { libc::statvfs(path.as_ptr(), stats.as_mut_ptr()) };
    if result != 0 {
        return None;
    }
    let stats = unsafe { stats.assume_init() };
    Some((stats.f_bavail as u64).saturating_mul(stats.f_frsize as u64))
}

fn check_available_space(path: &Path, needed: u64, action: &str) -> OpResult<()> {
    let Some(available) = available_space(path) else {
        return Ok(());
    };

    let margin = 64 * 1024 * 1024;
    let required = needed
        .saturating_add(needed / 100)
        .saturating_add(margin);

    if available < required {
        return Err(format!(
            "Espaço insuficiente para {action}. Necessário: {}, disponível: {}. Libere espaço e tente novamente.",
            human_bytes(required),
            human_bytes(available)
        ));
    }
    Ok(())
}

struct BaseDevice {
    file: File,
    base: u64,
}

impl BlockDeviceRead<io::Error> for BaseDevice {
    fn read(&mut self, offset: u64, buffer: &mut [u8]) -> io::Result<()> {
        let absolute = self
            .base
            .checked_add(offset)
            .ok_or_else(|| io::Error::new(io::ErrorKind::InvalidInput, "offset inválido"))?;
        self.file.seek(SeekFrom::Start(absolute))?;
        self.file.read_exact(buffer)
    }
}

fn open_iso(iso: &Path) -> OpResult<(BaseDevice, VolumeDescriptor)> {
    let file = File::open(iso).map_err(|error| {
        format!(
            "Não foi possível abrir a ISO \"{}\": {error}",
            iso.display()
        )
    })?;
    let length = file
        .metadata()
        .map_err(|error| format!("Não foi possível ler o tamanho da ISO: {error}"))?
        .len();

    for &base in XISO_BASES {
        let descriptor_offset = base.saturating_add(32 * SECTOR_SIZE as u64);
        if descriptor_offset.saturating_add(SECTOR_SIZE as u64) > length {
            continue;
        }

        let mut device = BaseDevice {
            file: file
                .try_clone()
                .map_err(|error| format!("Não foi possível ler a ISO: {error}"))?,
            base,
        };
        let mut descriptor = [0u8; SECTOR_SIZE as usize];

        if device
            .read(32 * SECTOR_SIZE as u64, &mut descriptor)
            .is_err()
        {
            continue;
        }

        if let Ok(volume) = VolumeDescriptor::deserialize::<io::Error>(&descriptor) {
            if volume.is_valid() {
                let root_table = volume.root_table;
                if !root_table.is_empty() {
                    return Ok((device, volume));
                }
            }
        }
    }

    Err("ISO inválida ou não é uma imagem XISO do Xbox clássico.".to_string())
}

fn extract_iso(iso: &Path, out_dir: &Path, reporter: Arc<Reporter>) -> OpResult<()> {
    if !iso.is_file() {
        return Err("A ISO selecionada não existe ou não é um arquivo.".to_string());
    }
    if !out_dir.is_absolute() {
        return Err("O caminho da pasta de destino deve ser absoluto.".to_string());
    }
    if out_dir.exists() {
        return Err(
            "A pasta de destino já existe. Escolha outro nome para não sobrescrever arquivos."
                .to_string(),
        );
    }

    let parent = out_dir
        .parent()
        .ok_or_else(|| "Não foi possível determinar a pasta pai do destino.".to_string())?;
    fs::create_dir_all(parent)
        .map_err(|error| format!("Não foi possível criar a pasta de destino: {error}"))?;

    let (mut device, volume) = open_iso(iso)?;
    reporter.set_current_now("Lendo a estrutura da ISO…");

    let tree = volume
        .root_table
        .file_tree(&mut device)
        .map_err(|error| format!("Não foi possível ler a estrutura da ISO: {error}"))?;

    let total = tree
        .iter()
        .filter(|(_, dirent)| !dirent.node.dirent.is_directory())
        .map(|(_, dirent)| dirent.node.dirent.data.size as u64)
        .sum::<u64>();

    reporter.set_processed(0);
    reporter.set_total(total);
    check_available_space(parent, total, "extrair a ISO")?;

    let temporary = temp_path(out_dir, "part")?;
    remove_any(&temporary);
    fs::create_dir_all(&temporary)
        .map_err(|error| format!("Não foi possível criar a pasta temporária: {error}"))?;

    let result = (|| -> OpResult<()> {
        let mut processed = 0u64;

        for (parent_path, dirent) in &tree {
            check_cancel()?;

            let name = dirent
                .name_str::<io::Error>()
                .map_err(|_| "A ISO contém um nome de arquivo inválido.".to_string())?;
            if name.is_empty() {
                continue;
            }

            let mut relative = PathBuf::new();
            for component in parent_path
                .trim_start_matches('/')
                .split('/')
                .filter(|value| !value.is_empty())
            {
                validate_component(component)?;
                relative.push(component);
            }
            validate_component(name.as_ref())?;
            relative.push(name.as_ref());

            let destination = temporary.join(&relative);

            if dirent.node.dirent.is_directory() {
                fs::create_dir_all(&destination).map_err(|error| {
                    format!(
                        "Não foi possível criar a pasta \"{}\": {error}",
                        relative.display()
                    )
                })?;
                continue;
            }

            if let Some(parent) = destination.parent() {
                fs::create_dir_all(parent).map_err(|error| {
                    format!(
                        "Não foi possível criar a pasta \"{}\": {error}",
                        relative.display()
                    )
                })?;
            }

            let size = dirent.node.dirent.data.size as u64;
            let data_offset = dirent.node.dirent.data.sector as u64 * SECTOR_SIZE as u64;
            reporter.set_current(&relative.to_string_lossy());

            let file = File::create(&destination).map_err(|error| {
                format!(
                    "Não foi possível criar o arquivo \"{}\": {error}",
                    relative.display()
                )
            })?;
            let mut output = BufWriter::with_capacity(COPY_BUFFER, file);

            if size > 0 {
                let mut buffer = vec![0u8; COPY_BUFFER];
                let mut copied = 0u64;

                while copied < size {
                    check_cancel()?;
                    let chunk = std::cmp::min(buffer.len() as u64, size - copied) as usize;
                    device
                        .read(data_offset + copied, &mut buffer[..chunk])
                        .map_err(|error| {
                            format!(
                                "Falha ao ler o arquivo \"{}\" da ISO: {error}",
                                relative.display()
                            )
                        })?;
                    output.write_all(&buffer[..chunk]).map_err(|error| {
                        format!(
                            "Falha ao gravar o arquivo \"{}\": {error}",
                            relative.display()
                        )
                    })?;
                    copied += chunk as u64;
                    processed = processed.saturating_add(chunk as u64);
                    reporter.set_processed(processed);
                }
            }

            output.flush().map_err(|error| {
                format!(
                    "Falha ao finalizar o arquivo \"{}\": {error}",
                    relative.display()
                )
            })?;
        }

        Ok(())
    })();

    match result {
        Ok(()) => {
            fs::rename(&temporary, out_dir).map_err(|error| {
                format!(
                    "Não foi possível concluir a extração em \"{}\": {error}",
                    out_dir.display()
                )
            })?;
            reporter.complete();
            Ok(())
        }
        Err(error) => {
            remove_any(&temporary);
            Err(error)
        }
    }
}

fn has_default_xbe(directory: &Path) -> OpResult<bool> {
    let entries = fs::read_dir(directory)
        .map_err(|error| format!("Não foi possível ler a pasta do jogo: {error}"))?;

    for entry in entries {
        let entry = entry.map_err(|error| format!("Não foi possível ler a pasta do jogo: {error}"))?;
        let file_type = entry
            .file_type()
            .map_err(|error| format!("Não foi possível ler um item da pasta: {error}"))?;
        if file_type.is_file()
            && entry
                .file_name()
                .to_string_lossy()
                .eq_ignore_ascii_case("default.xbe")
        {
            return Ok(true);
        }
    }

    Ok(false)
}

fn directory_size(directory: &Path) -> OpResult<u64> {
    let mut total = 0u64;
    let mut pending = vec![directory.to_path_buf()];

    while let Some(current) = pending.pop() {
        check_cancel()?;
        let entries = fs::read_dir(&current)
            .map_err(|error| format!("Não foi possível ler \"{}\": {error}", current.display()))?;

        for entry in entries {
            let entry = entry.map_err(|error| {
                format!(
                    "Não foi possível ler um item de \"{}\": {error}",
                    current.display()
                )
            })?;
            let file_type = entry.file_type().map_err(|error| {
                format!(
                    "Não foi possível identificar \"{}\": {error}",
                    entry.path().display()
                )
            })?;

            if file_type.is_symlink() {
                return Err(format!(
                    "A pasta contém um link simbólico não suportado: \"{}\".",
                    entry.path().display()
                ));
            }
            if file_type.is_dir() {
                pending.push(entry.path());
            } else if file_type.is_file() {
                let length = entry
                    .metadata()
                    .map_err(|error| {
                        format!(
                            "Não foi possível ler o tamanho de \"{}\": {error}",
                            entry.path().display()
                        )
                    })?
                    .len();
                total = total.saturating_add(length);
            } else {
                return Err(format!(
                    "A pasta contém um tipo de arquivo não suportado: \"{}\".",
                    entry.path().display()
                ));
            }
        }
    }

    Ok(total)
}

/// Imagem de saída que conta os bytes realmente gravados e interrompe a cópia
/// quando o usuário cancela. O `xdvdfs` copia os arquivos através de
/// `std::io::Write`, então essa é a forma mais direta de obter progresso por byte.
struct CountingImage {
    inner: BufWriter<File>,
    reporter: Arc<Reporter>,
}

impl CountingImage {
    fn new(inner: BufWriter<File>, reporter: Arc<Reporter>) -> Self {
        Self { inner, reporter }
    }

    fn finish(&mut self) -> io::Result<()> {
        self.inner.flush()?;
        self.inner.get_ref().sync_all()
    }
}

impl Write for CountingImage {
    fn write(&mut self, buffer: &[u8]) -> io::Result<usize> {
        if cancelled() {
            return Err(io::Error::new(
                io::ErrorKind::Interrupted,
                "operação cancelada",
            ));
        }
        let written = std::io::Write::write(&mut self.inner, buffer)?;
        if written > 0 {
            self.reporter.add_processed(written as u64);
        }
        Ok(written)
    }

    fn flush(&mut self) -> io::Result<()> {
        self.inner.flush()
    }
}

impl Seek for CountingImage {
    fn seek(&mut self, position: SeekFrom) -> io::Result<u64> {
        self.inner.seek(position)
    }
}

impl BlockDeviceWrite<io::Error> for CountingImage {
    fn write(&mut self, offset: u64, buffer: &[u8]) -> io::Result<()> {
        if cancelled() {
            return Err(io::Error::new(
                io::ErrorKind::Interrupted,
                "operação cancelada",
            ));
        }
        self.inner.seek(SeekFrom::Start(offset))?;
        self.inner.write_all(buffer)?;
        self.reporter.add_processed(buffer.len() as u64);
        Ok(())
    }

    fn len(&mut self) -> io::Result<u64> {
        self.inner.flush()?;
        Ok(self.inner.get_ref().metadata()?.len())
    }
}

fn map_create_error(error: xdvdfs::util::Error<io::Error>) -> String {
    if cancelled() {
        "Operação cancelada.".to_string()
    } else {
        format!("Não foi possível criar a ISO: {error}")
    }
}

fn create_iso(source: &Path, out_iso: &Path, reporter: Arc<Reporter>) -> OpResult<()> {
    if !source.is_dir() {
        return Err("A pasta do jogo não existe ou não é uma pasta.".to_string());
    }
    if !out_iso.is_absolute() {
        return Err("O caminho da ISO de saída deve ser absoluto.".to_string());
    }
    if !has_default_xbe(source)? {
        return Err(
            "A pasta selecionada não contém o arquivo default.xbe na raiz. Escolha a pasta que contém os arquivos do jogo."
                .to_string(),
        );
    }

    let source_canonical = fs::canonicalize(source)
        .map_err(|error| format!("Não foi possível acessar a pasta do jogo: {error}"))?;
    if out_iso.starts_with(&source_canonical) {
        return Err(
            "A ISO de saída não pode ficar dentro da pasta do jogo. Escolha outra pasta."
                .to_string(),
        );
    }

    let parent = out_iso
        .parent()
        .ok_or_else(|| "Não foi possível determinar a pasta da ISO de saída.".to_string())?;
    if parent.starts_with(&source_canonical) {
        return Err(
            "A ISO de saída não pode ficar dentro da pasta do jogo. Escolha outra pasta."
                .to_string(),
        );
    }

    fs::create_dir_all(parent)
        .map_err(|error| format!("Não foi possível criar a pasta da ISO: {error}"))?;
    if let Ok(parent_canonical) = fs::canonicalize(parent) {
        if parent_canonical.starts_with(&source_canonical) {
            return Err(
                "A ISO de saída não pode ficar dentro da pasta do jogo. Escolha outra pasta."
                    .to_string(),
            );
        }
    }
    if out_iso.is_dir() {
        return Err("O caminho escolhido para a ISO é uma pasta.".to_string());
    }
    if out_iso.exists() {
        return Err("Já existe um arquivo de ISO com esse nome. Escolha outro nome.".to_string());
    }

    reporter.set_current_now("Calculando o tamanho do jogo…");
    let total = directory_size(source)?;
    reporter.set_processed(0);
    reporter.set_total(total);
    check_available_space(parent, total, "criar a ISO")?;

    let temporary = temp_path(out_iso, "part")?;
    remove_any(&temporary);

    let file = File::create(&temporary)
        .map_err(|error| format!("Não foi possível criar a ISO temporária: {error}"))?;
    let writer = BufWriter::with_capacity(COPY_BUFFER, file);
    let mut image = CountingImage::new(writer, Arc::clone(&reporter));
    let mut filesystem = StdFilesystem::create(source);
    let source_prefix = source.to_string_lossy().to_string();

    let result = create_xdvdfs_image(&mut filesystem, &mut image, |info| match info {
        ProgressInfo::FileAdded(path, _) => {
            let path = path.trim_matches('"');
            let relative = path
                .strip_prefix(source_prefix.as_str())
                .unwrap_or(path)
                .trim_start_matches('/');
            reporter.set_current(relative);
        }
        ProgressInfo::FinishedPacking => {}
        _ => {}
    });

    let finish_result = image.finish();

    if let Err(error) = result {
        remove_any(&temporary);
        return Err(map_create_error(error));
    }
    if let Err(error) = finish_result {
        remove_any(&temporary);
        return Err(format!("Não foi possível finalizar a ISO: {error}"));
    }

    drop(image);

    if cancelled() {
        remove_any(&temporary);
        return Err("Operação cancelada.".to_string());
    }

    fs::rename(&temporary, out_iso).map_err(|error| {
        remove_any(&temporary);
        format!(
            "Não foi possível concluir a criação da ISO em \"{}\": {error}",
            out_iso.display()
        )
    })?;

    reporter.complete();
    Ok(())
}

// ---------------------------------------------------------------------------
// Ponte JNI
// ---------------------------------------------------------------------------

#[no_mangle]
pub extern "system" fn Java_com_deivid22srk_xisotool_native_XisoNative_extractIso(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    iso_path: JString<'_>,
    out_dir: JString<'_>,
    callback: JObject<'_>,
) -> jstring {
    let result = (|| -> OpResult<()> {
        let iso = jstring_to_string(&mut env, &iso_path)?;
        let destination = jstring_to_string(&mut env, &out_dir)?;
        let reporter = Reporter::new(&mut env, callback)?;
        CANCELLED.store(false, Ordering::SeqCst);
        run_catching(|| extract_iso(Path::new(&iso), Path::new(&destination), reporter))
    })();

    match result {
        Ok(()) => std::ptr::null_mut(),
        Err(message) => string_to_jstring(&mut env, &message),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_deivid22srk_xisotool_native_XisoNative_createIso(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    source_dir: JString<'_>,
    out_iso: JString<'_>,
    callback: JObject<'_>,
) -> jstring {
    let result = (|| -> OpResult<()> {
        let source = jstring_to_string(&mut env, &source_dir)?;
        let destination = jstring_to_string(&mut env, &out_iso)?;
        let reporter = Reporter::new(&mut env, callback)?;
        CANCELLED.store(false, Ordering::SeqCst);
        run_catching(|| create_iso(Path::new(&source), Path::new(&destination), reporter))
    })();

    match result {
        Ok(()) => std::ptr::null_mut(),
        Err(message) => string_to_jstring(&mut env, &message),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_deivid22srk_xisotool_native_XisoNative_cancel(
    _env: JNIEnv<'_>,
    _this: JObject<'_>,
) {
    CANCELLED.store(true, Ordering::SeqCst);
}
