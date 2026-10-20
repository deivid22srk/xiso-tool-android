# XISO Tool

Aplicativo Android para **extrair imagens XISO do Xbox clássico para pastas** e
**criar imagens XISO válidas a partir de uma pasta de jogo**, com interface em
português, progresso real e cancelamento seguro.

O processamento acontece inteiramente no aparelho. O núcleo de arquivos é escrito
em Rust com [`xdvdfs`](https://crates.io/crates/xdvdfs) e é acessado pela interface
Kotlin/Jetpack Compose através de JNI.

> Este repositório não contém jogos, BIOS, arquivos extraídos ou qualquer conteúdo
> proprietário. Use apenas cópias de jogos que você possui legalmente.

## Download

Baixe o APK mais recente em
[github.com/deivid22srk/xiso-tool-android/releases](https://github.com/deivid22srk/xiso-tool-android/releases).
O APK é universal e inclui as ABIs `arm64-v8a`, `armeabi-v7a`, `x86` e `x86_64`.

---

## Funcionalidades

- **Extrair ISO → pasta**
  - Detecta imagens XISO do Xbox clássico em offsets conhecidos (XISO crua, XGD1,
    XGD2, XGD3 e imagens antigas em `0x30600`).
  - Copia os arquivos em streaming, com buffer de 1 MiB e pouco uso de memória.
  - Mostra porcentagem, bytes processados, velocidade, tempo restante e o arquivo
    atual.
  - Grava em uma pasta temporária oculta e só renomeia quando termina.
  - Remove a pasta temporária em caso de erro ou cancelamento.

- **Criar ISO a partir de pasta**
  - Valida a presença de `default.xbe` na raiz antes de começar.
  - Usa `StdFilesystem` + `create_xdvdfs_image` do `xdvdfs`.
  - Grava em `.{nome}.part` e só troca para o nome final ao concluir.
  - Verifica espaço livre e rejeita destinos dentro da própria pasta do jogo.
  - Cancela de forma cooperativa, sem deixar ISO parcial.

- **Experiência Material 3 Expressive**
  - `MaterialExpressiveTheme` e `MotionScheme.expressive()`.
  - Cores dinâmicas no Android 12+, com paleta Xbox de fallback.
  - `LargeFlexibleTopAppBar`, `CircularWavyProgressIndicator`,
    `LinearWavyProgressIndicator`, `LoadingIndicator`, `MaterialShapes` e
    transições com mola.
  - Telas: Início, Extrair, Criar ISO, Progresso e Resultado.

- **Serviço em primeiro plano**
  - Tipo `dataSync`, notificação persistente com porcentagem e ação **Cancelar**.
  - `PARTIAL_WAKE_LOCK` para continuar com a tela bloqueada.
  - Permissão de notificações no Android 13+.
  - Tratamento de timeout do serviço no Android 14/15.

---

## Requisitos

- Android 8.0 (API 26) ou superior.
- Acesso a todos os arquivos (`MANAGE_EXTERNAL_STORAGE`) no Android 11+; nas
  versões anteriores, permissões normais de leitura/gravação.
- Uma ISO ou pasta de jogo do Xbox clássico que você possui.

### Por que o app pede acesso a todos os arquivos?

O app trabalha com arquivos grandes em qualquer pasta escolhida pelo usuário
(por exemplo, `Download`, cartão SD ou armazenamento interno). O Storage Access
Framework não oferece gravação direta e eficiente em pastas arbitrárias para esse
caso, então o app usa acesso direto e explica a permissão antes de abrir as
configurações do Android.

A permissão pode ser revogada a qualquer momento nas configurações do sistema.
O app não envia arquivos para servidores e não inclui anúncios ou rastreamento.

> Para publicar na Google Play, o uso de `MANAGE_EXTERNAL_STORAGE` exige uma
> declaração de política e aprovação. Este projeto é distribuído como APK direto
> pelo GitHub Releases/Actions.

---

## Como usar

### Extrair uma ISO

1. Abra **Extrair ISO**.
2. Selecione o arquivo `.iso`, `.xiso` ou `.img`.
3. Escolha a pasta de destino e confirme o nome da pasta que será criada.
4. Toque em **Extrair ISO**.
5. Acompanhe pela tela de progresso ou pela notificação. É possível cancelar.

### Criar uma ISO

1. Abra **Criar ISO**.
2. Selecione a pasta que contém `default.xbe` na raiz e as subpastas do jogo.
3. Escolha onde salvar e o nome do arquivo `.iso`.
4. Toque em **Criar ISO**.
5. Ao final, use **Compartilhar ISO** ou **Abrir pasta**.

---

## Arquitetura

```
app/                         Android (Kotlin + Jetpack Compose)
├── MainActivity.kt          Edge-to-edge, permissões, composição raiz
├── service/                 Foreground service, notificação, wake lock
├── native/XisoNative.kt     Ponte JNI e callback de progresso
├── data/                    OperationState e OperationStore (StateFlow)
├── ui/                      ViewModel, navegação, file browser, telas
└── src/main/jniLibs/        Bibliotecas Rust geradas pelo CI (não versionadas)

rust/
├── Cargo.toml               cdylib `xiso_core`
└── src/lib.rs               Extração/criação, JNI, progresso, cancelamento

.github/workflows/build.yml  Rust + NDK + Gradle + APK assinado
```

### Fluxo de uma operação

1. A tela valida os caminhos e inicia `XisoForegroundService`.
2. O serviço cria a notificação, adquire o wake lock e chama a função JNI.
3. O Rust processa em streaming e chama `onProgress(processed, total, arquivo)`
   na thread nativa (limitado a ~8 atualizações por segundo).
4. `OperationStore` publica o estado; a tela e a notificação observam.
5. Em sucesso, o arquivo/pasta temporário é renomeado. Em erro ou cancelamento,
   é removido.
6. O serviço publica o resultado, libera o wake lock e encerra.

### Contrato JNI

```kotlin
external fun extractIso(isoPath: String, outDir: String, callback: ProgressCallback): String?
external fun createIso(srcDir: String, outIso: String, callback: ProgressCallback): String?
external fun cancel()
```

As funções retornam `null` em caso de sucesso ou uma mensagem em português em
caso de erro.

---

## Build

### GitHub Actions (recomendado)

O workflow `.github/workflows/build.yml` roda em pushes para `main`, pull requests
e manualmente. Ele:

1. Instala JDK 17, Android SDK 36, build-tools 36 e NDK 27.
2. Instala os alvos Rust para `arm64-v8a`, `armeabi-v7a`, `x86` e `x86_64`.
3. Compila o crate Rust com `cargo-ndk` para `app/src/main/jniLibs`.
4. Compila o APK release com Gradle 8.14.3.
5. Assina com o keystore dos secrets ou com uma chave temporária de CI.
6. Publica o artefato `xiso-tool-apk` e, em tags `v*`, cria um GitHub Release.

Para usar sua própria chave, configure os secrets:

| Secret | Descrição |
| --- | --- |
| `KEYSTORE_BASE64` | Keystore `.jks`/`.keystore` codificado em base64 |
| `KEYSTORE_PASSWORD` | Senha do keystore |
| `KEY_ALIAS` | Alias da chave |
| `KEY_PASSWORD` | Senha da chave |

Sem esses secrets, o CI gera uma chave temporária — o APK instala, mas não deve
ser usado para atualizações de longo prazo.

### Build local

Pré-requisitos: JDK 17, Android SDK 36, NDK 27, Rust estável, `cargo-ndk` e
Gradle 8.14.3.

```bash
rustup target add \
  aarch64-linux-android \
  armv7-linux-androideabi \
  i686-linux-android \
  x86_64-linux-android

cargo install cargo-ndk --locked

cd rust
cargo ndk \
  -t arm64-v8a -t armeabi-v7a -t x86 -t x86_64 \
  --platform 26 \
  -o ../app/src/main/jniLibs \
  build --release

cd ..
gradle assembleDebug
```

O APK de debug fica em `app/build/outputs/apk/debug/app-debug.apk`.

Para gerar um release assinado localmente, defina `XISO_KEYSTORE_PATH`,
`XISO_KEYSTORE_PASSWORD`, `XISO_KEY_ALIAS` e `XISO_KEY_PASSWORD` e execute
`gradle assembleRelease`.

---

## Privacidade e segurança

- Nenhum arquivo é enviado pela rede. O app não declara permissão de internet.
- O `FileProvider` é usado apenas para compartilhar uma ISO ou abrir uma pasta
  quando o usuário toca no botão correspondente.
- Pastas e ISOs temporárias usam nomes ocultos (`.nome.part`) e são removidas em
  caso de falha/cancelamento.
- Caminhos de arquivos e mensagens de erro são exibidos localmente.

## Licença

MIT. Consulte [LICENSE](LICENSE).

## Aviso legal

Xbox é uma marca registrada da Microsoft. Este é um projeto independente, sem
afiliação com a Microsoft. Não baixe nem distribua jogos; use apenas cópias que
você possui.
