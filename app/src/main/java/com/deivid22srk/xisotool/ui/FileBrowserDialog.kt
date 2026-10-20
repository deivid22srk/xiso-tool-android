package com.deivid22srk.xisotool.ui

import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.deivid22srk.xisotool.util.Formatters
import java.io.File

enum class PickerMode {
    FILE,
    DIRECTORY,
}

data class PickerConfig(
    val mode: PickerMode,
    val title: String,
    val initialDirectory: String?,
    val fileExtensions: Set<String> = emptySet(),
    val onSelected: (File) -> Unit,
)

private data class DirectoryListing(
    val files: List<File>,
    val error: String?,
)

@Composable
fun FileBrowserDialog(
    config: PickerConfig,
    onDismiss: () -> Unit,
) {
    var currentDirectory by remember(config) {
        mutableStateOf(resolveDirectory(config.initialDirectory))
    }
    var showAllFiles by remember(config) { mutableStateOf(false) }
    var showNewFolder by remember(config) { mutableStateOf(false) }
    var createError by remember(config) { mutableStateOf<String?>(null) }

    val listing = remember(currentDirectory, showAllFiles, config.mode, config.fileExtensions) {
        listDirectory(
            directory = currentDirectory,
            mode = config.mode,
            extensions = config.fileExtensions,
            showAllFiles = showAllFiles,
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                containerColor = MaterialTheme.colorScheme.surface,
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = config.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Filled.Close, contentDescription = "Fechar")
                            }
                        },
                        actions = {
                            if (config.mode == PickerMode.DIRECTORY) {
                                TextButton(onClick = { showNewFolder = true }) {
                                    Text("Nova pasta")
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                },
                bottomBar = {
                    if (config.mode == PickerMode.DIRECTORY) {
                        Surface(tonalElevation = 3.dp) {
                            Button(
                                onClick = { config.onSelected(currentDirectory) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                    .height(56.dp),
                                shape = MaterialTheme.shapes.large,
                            ) {
                                Text("Selecionar esta pasta")
                            }
                        }
                    }
                },
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Storage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Local atual",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = currentDirectory.absolutePath,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(
                                onClick = {
                                    currentDirectory = currentDirectory.parentFile ?: currentDirectory
                                },
                            ) {
                                Icon(
                                    Icons.Filled.KeyboardArrowUp,
                                    contentDescription = "Subir um nível",
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AssistChip(
                            onClick = { currentDirectory = externalStorageRoot() },
                            label = { Text("Armazenamento interno") },
                            leadingIcon = { Icon(Icons.Filled.Storage, contentDescription = null) },
                        )
                        AssistChip(
                            onClick = { currentDirectory = File("/storage") },
                            label = { Text("/storage") },
                        )
                    }

                    if (config.mode == PickerMode.FILE) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Mostrar todos os arquivos",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = showAllFiles,
                                onCheckedChange = { showAllFiles = it },
                            )
                        }
                    }

                    if (listing.error != null) {
                        Text(
                            text = listing.error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        items(listing.files, key = { it.absolutePath }) { file ->
                            FileRow(
                                file = file,
                                onClick = {
                                    if (file.isDirectory) {
                                        currentDirectory = file
                                    } else if (config.mode == PickerMode.FILE) {
                                        config.onSelected(file)
                                        onDismiss()
                                    }
                                },
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 68.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                        }

                        if (listing.files.isEmpty() && listing.error == null) {
                            item {
                                Text(
                                    text = if (config.mode == PickerMode.DIRECTORY) {
                                        "Esta pasta está vazia. Você pode selecioná-la ou criar uma nova pasta."
                                    } else {
                                        "Nenhum arquivo compatível nesta pasta."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(24.dp),
                                )
                            }
                        }
                    }

                    if (config.mode == PickerMode.FILE) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(
                                text = "Toque em um arquivo .iso, .xiso ou .img para selecionar.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNewFolder) {
        var folderName by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = {
                showNewFolder = false
                createError = null
            },
            title = { Text("Nova pasta") },
            text = {
                Column {
                    OutlinedTextField(
                        value = folderName,
                        onValueChange = {
                            folderName = it
                            createError = null
                        },
                        label = { Text("Nome da pasta") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (createError != null) {
                        Text(
                            text = createError.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = folderName.trim()
                        when {
                            name.isEmpty() -> createError = "Informe um nome."
                            name.contains('/') || name.contains('\\') -> createError = "O nome não pode conter barras."
                            File(currentDirectory, name).exists() -> createError = "Já existe uma pasta com esse nome."
                            else -> {
                                val created = File(currentDirectory, name)
                                if (created.mkdirs()) {
                                    currentDirectory = created
                                    showNewFolder = false
                                    createError = null
                                } else {
                                    createError = "Não foi possível criar a pasta. Verifique a permissão de acesso."
                                }
                            }
                        }
                    },
                ) {
                    Text("Criar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showNewFolder = false
                        createError = null
                    },
                ) {
                    Text("Cancelar")
                }
            },
        )
    }
}

@Composable
private fun FileRow(file: File, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = MaterialTheme.shapes.small,
            color = if (file.isDirectory) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (file.isDirectory) {
                        Icons.Filled.Folder
                    } else {
                        Icons.Filled.InsertDriveFile
                    },
                    contentDescription = null,
                    tint = if (file.isDirectory) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (file.isDirectory) "Pasta" else Formatters.bytes(file.length()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (file.isDirectory) {
            Icon(
                Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun listDirectory(
    directory: File,
    mode: PickerMode,
    extensions: Set<String>,
    showAllFiles: Boolean,
): DirectoryListing {
    if (!directory.exists()) {
        return DirectoryListing(emptyList(), "Esta pasta não existe mais.")
    }
    if (!directory.isDirectory) {
        return DirectoryListing(emptyList(), "O local selecionado não é uma pasta.")
    }

    val children = directory.listFiles()
        ?: return DirectoryListing(
            emptyList(),
            "Não foi possível ler esta pasta. Verifique a permissão de acesso aos arquivos.",
        )

    val normalizedExtensions = extensions.map { it.lowercase() }.toSet()
    val files = children
        .asSequence()
        .filter { file ->
            if (!showAllFiles && file.name.startsWith(".")) {
                false
            } else if (file.isDirectory) {
                true
            } else {
                mode == PickerMode.FILE && (
                    showAllFiles ||
                        normalizedExtensions.isEmpty() ||
                        file.extension.lowercase() in normalizedExtensions
                    )
            }
        }
        .sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
        .toList()

    return DirectoryListing(files, null)
}

private fun resolveDirectory(path: String?): File {
    if (path.isNullOrBlank()) return externalStorageRoot()
    val file = File(path)
    return when {
        file.isDirectory -> file
        file.isFile -> file.parentFile ?: externalStorageRoot()
        else -> externalStorageRoot()
    }
}

private fun externalStorageRoot(): File {
    val external = Environment.getExternalStorageDirectory()
    if (external.exists() && external.canRead()) return external

    val fallback = File("/storage/emulated/0")
    if (fallback.exists()) return fallback

    return File("/")
}
