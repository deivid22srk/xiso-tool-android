@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.deivid22srk.xisotool.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deivid22srk.xisotool.ui.components.ExpressiveCard
import com.deivid22srk.xisotool.ui.components.InfoBanner
import com.deivid22srk.xisotool.ui.components.PathSelector
import com.deivid22srk.xisotool.ui.components.PrimaryActionButton
import com.deivid22srk.xisotool.ui.components.XisoBackground
import com.deivid22srk.xisotool.ui.components.XisoTopBar
import java.io.File

@Composable
fun CreateScreen(
    sourceDir: String?,
    sourceHasDefaultXbe: Boolean,
    destinationParent: String,
    fileName: String,
    onPickSource: () -> Unit,
    onPickDestination: () -> Unit,
    onFileNameChange: (String) -> Unit,
    onBack: () -> Unit,
    onStart: () -> String?,
) {
    var error by remember { mutableStateOf<String?>(null) }
    var starting by remember { mutableStateOf(false) }

    val trimmedName = fileName.trim()
    val finalName = if (trimmedName.isEmpty() || trimmedName.endsWith(".iso", ignoreCase = true)) {
        trimmedName
    } else {
        "$trimmedName.iso"
    }
    val destinationExists = finalName.isNotEmpty() && File(destinationParent, finalName).exists()
    val validName = finalName.isNotEmpty() &&
        !finalName.contains('/') &&
        !finalName.contains('\\')
    val canStart = sourceDir != null &&
        sourceHasDefaultXbe &&
        validName &&
        !destinationExists

    XisoBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                XisoTopBar(
                    title = "Criar ISO",
                    subtitle = "Pasta do jogo → XISO",
                    onBack = onBack,
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Escolha a pasta com os arquivos do jogo e defina onde a imagem XISO será criada.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                ExpressiveCard {
                    StepHeader(
                        number = "1",
                        title = "Pasta do jogo",
                        description = "Deve conter default.xbe na raiz, além das pastas do jogo.",
                    )
                    Spacer(Modifier.size(14.dp))
                    PathSelector(
                        label = "Pasta de origem",
                        value = sourceDir,
                        placeholder = "Nenhuma pasta selecionada",
                        icon = Icons.Filled.Folder,
                        onChoose = onPickSource,
                        chooseLabel = "Escolher",
                    )

                    if (sourceDir != null) {
                        Spacer(Modifier.size(12.dp))
                        DefaultXbeStatus(found = sourceHasDefaultXbe)
                    }
                }

                ExpressiveCard {
                    StepHeader(
                        number = "2",
                        title = "Destino da ISO",
                        description = "A imagem será gravada no local escolhido.",
                    )
                    Spacer(Modifier.size(14.dp))
                    PathSelector(
                        label = "Salvar em",
                        value = destinationParent,
                        placeholder = "Escolha uma pasta",
                        icon = Icons.Filled.Folder,
                        onChoose = onPickDestination,
                        chooseLabel = "Alterar",
                    )
                    Spacer(Modifier.size(14.dp))
                    OutlinedTextField(
                        value = fileName,
                        onValueChange = {
                            onFileNameChange(it)
                            error = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nome da ISO") },
                        singleLine = true,
                        isError = destinationExists,
                        supportingText = {
                            Text(
                                text = if (destinationExists) {
                                    "Já existe uma ISO com esse nome. Escolha outro."
                                } else if (validName) {
                                    "Arquivo: ${File(destinationParent, finalName).absolutePath}"
                                } else {
                                    "Digite um nome para o arquivo .iso."
                                },
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }

                InfoBanner(
                    text = "A ISO é gravada primeiro como arquivo temporário e só recebe o nome final quando tudo termina. " +
                        "Se você cancelar, o arquivo parcial é removido.",
                )

                if (error != null) {
                    Text(
                        text = error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                PrimaryActionButton(
                    text = if (starting) "Iniciando…" else "Criar ISO",
                    icon = Icons.Filled.FileUpload,
                    enabled = canStart && !starting,
                    onClick = {
                        starting = true
                        val message = onStart()
                        if (message != null) {
                            error = message
                            starting = false
                        }
                    },
                )

                Text(
                    text = "A ISO criada pode ser usada no xemu ou em um Xbox com modificação, desde que a pasta esteja completa.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DefaultXbeStatus(found: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (found) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (found) Icons.Filled.CheckCircle else Icons.Filled.Error,
                contentDescription = null,
                tint = if (found) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                },
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (found) {
                    "default.xbe encontrado na raiz."
                } else {
                    "default.xbe não encontrado. Selecione a pasta que contém os arquivos do jogo."
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (found) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                },
            )
        }
    }
}
