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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
fun ExtractScreen(
    isoPath: String?,
    destinationParent: String,
    folderName: String,
    onPickIso: () -> Unit,
    onPickDestination: () -> Unit,
    onFolderNameChange: (String) -> Unit,
    onBack: () -> Unit,
    onStart: () -> String?,
) {
    var error by remember { mutableStateOf<String?>(null) }
    var starting by remember { mutableStateOf(false) }

    val name = folderName.trim()
    val destinationExists = name.isNotEmpty() && File(destinationParent, name).exists()
    val canStart = isoPath != null &&
        File(isoPath).isFile &&
        name.isNotEmpty() &&
        !name.contains('/') &&
        !name.contains('\\') &&
        !destinationExists

    XisoBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                XisoTopBar(
                    title = "Extrair ISO",
                    subtitle = "ISO → pasta do jogo",
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
                    text = "Escolha a imagem do Xbox e o nome da pasta que será criada com os arquivos do jogo.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                ExpressiveCard {
                    StepHeader(
                        number = "1",
                        title = "Escolha a ISO",
                        description = "Formatos aceitos: .iso, .xiso e .img.",
                    )
                    Spacer(Modifier.size(14.dp))
                    PathSelector(
                        label = "Imagem de origem",
                        value = isoPath,
                        placeholder = "Nenhuma ISO selecionada",
                        icon = Icons.Filled.InsertDriveFile,
                        onChoose = onPickIso,
                        chooseLabel = "Escolher",
                    )
                }

                ExpressiveCard {
                    StepHeader(
                        number = "2",
                        title = "Pasta de destino",
                        description = "A pasta final será criada dentro do local escolhido.",
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
                        value = folderName,
                        onValueChange = {
                            onFolderNameChange(it)
                            error = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nome da pasta") },
                        singleLine = true,
                        isError = destinationExists,
                        supportingText = {
                            Text(
                                text = if (destinationExists) {
                                    "Já existe uma pasta com esse nome. Escolha outro."
                                } else if (name.isNotEmpty()) {
                                    "Destino: ${File(destinationParent, name).absolutePath}"
                                } else {
                                    "Digite um nome para a pasta do jogo."
                                },
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }

                InfoBanner(
                    text = "A extração acontece em uma pasta temporária. Se você cancelar ou ocorrer um erro, " +
                        "nada é deixado pela metade no destino.",
                )

                if (error != null) {
                    Text(
                        text = error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                PrimaryActionButton(
                    text = if (starting) "Iniciando…" else "Extrair ISO",
                    icon = Icons.Filled.FileDownload,
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
                    text = "O progresso aparece na tela e na notificação. Você pode bloquear a tela ou usar outro app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun StepHeader(
    number: String,
    title: String,
    description: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(34.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = number,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
