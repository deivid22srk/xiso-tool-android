@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.deivid22srk.xisotool.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deivid22srk.xisotool.data.OperationState
import com.deivid22srk.xisotool.data.OperationType
import com.deivid22srk.xisotool.ui.components.ExpressiveIcon
import com.deivid22srk.xisotool.ui.components.PrimaryActionButton
import com.deivid22srk.xisotool.ui.components.XisoBackground
import com.deivid22srk.xisotool.ui.components.XisoSmallTopBar
import com.deivid22srk.xisotool.util.Formatters
import java.io.File

@Composable
fun ResultScreen(
    state: OperationState,
    onHome: () -> Unit,
    onRetry: () -> Unit,
    onOpenFolder: (String) -> Unit,
    onShareIso: (String) -> Unit,
) {
    val success = state as? OperationState.Success
    val failure = state as? OperationState.Failure
    val cancelled = state is OperationState.Cancelled
    val type = when (state) {
        is OperationState.Success -> state.type
        is OperationState.Failure -> state.type
        is OperationState.Cancelled -> state.type
        is OperationState.Running -> state.type
        OperationState.Idle -> OperationType.EXTRACT
    }

    val title = when {
        success != null && success.type == OperationType.EXTRACT -> "ISO extraída!"
        success != null -> "ISO criada!"
        failure != null -> "Não foi possível concluir"
        cancelled -> "Operação cancelada"
        else -> "Resultado"
    }
    val subtitle = when {
        success != null && success.type == OperationType.EXTRACT ->
            "Os arquivos do jogo estão prontos na pasta abaixo, incluindo o default.xbe."

        success != null -> "Sua imagem XISO está pronta para usar no xemu ou em um Xbox modificado."
        failure != null -> "Nada foi mantido pela metade. Confira a mensagem e tente novamente."
        cancelled -> "A pasta temporária foi removida. Nenhum arquivo foi alterado."
        else -> ""
    }

    val accent = when {
        success != null -> MaterialTheme.colorScheme.primaryContainer
        cancelled -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.errorContainer
    }
    val onAccent = when {
        success != null -> MaterialTheme.colorScheme.onPrimaryContainer
        cancelled -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onErrorContainer
    }
    val icon = when {
        success != null -> Icons.Filled.CheckCircle
        cancelled -> Icons.Filled.Cancel
        else -> Icons.Filled.Error
    }

    XisoBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                XisoSmallTopBar(
                    title = "Resultado",
                    onBack = onHome,
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                ExpressiveIcon(
                    icon = icon,
                    size = 96.dp,
                    containerColor = accent,
                    contentColor = onAccent,
                )

                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                when {
                    success != null -> {
                        PathCard(
                            title = if (success.type == OperationType.EXTRACT) {
                                "Pasta do jogo"
                            } else {
                                "Arquivo ISO"
                            },
                            path = success.destination,
                        )
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(24.dp),
                            ) {
                                Column {
                                    Text(
                                        text = "Operação",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = if (success.type == OperationType.EXTRACT) "Extração" else "Criação",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Tempo",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = Formatters.duration((success.elapsedMillis / 1000L).coerceAtLeast(1L)),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }

                    failure != null -> {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.errorContainer,
                        ) {
                            Text(
                                text = failure.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(18.dp),
                            )
                        }
                    }

                    else -> Unit
                }

                when {
                    success != null && success.type == OperationType.EXTRACT -> {
                        PrimaryActionButton(
                            text = "Abrir pasta",
                            icon = Icons.Filled.FolderOpen,
                            onClick = { onOpenFolder(success.destination) },
                        )
                        OutlinedButton(
                            onClick = onHome,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                        ) {
                            Text("Voltar ao início")
                        }
                    }

                    success != null -> {
                        PrimaryActionButton(
                            text = "Compartilhar ISO",
                            icon = Icons.Filled.Share,
                            onClick = { onShareIso(success.destination) },
                        )
                        OutlinedButton(
                            onClick = {
                                onOpenFolder(File(success.destination).parent ?: success.destination)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                        ) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Abrir pasta")
                        }
                    }

                    failure != null -> {
                        PrimaryActionButton(
                            text = "Tentar novamente",
                            onClick = onRetry,
                        )
                        OutlinedButton(
                            onClick = onHome,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                        ) {
                            Text("Voltar ao início")
                        }
                    }

                    cancelled -> {
                        PrimaryActionButton(
                            text = "Voltar ao início",
                            onClick = onHome,
                        )
                        OutlinedButton(
                            onClick = onRetry,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                        ) {
                            Text("Tentar novamente")
                        }
                    }
                }

                Text(
                    text = if (type == OperationType.EXTRACT) {
                        "A pasta contém a estrutura original da imagem, incluindo subpastas e default.xbe."
                    } else {
                        "A ISO criada preserva a estrutura de arquivos da pasta de origem."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.size(8.dp))
            }
        }
    }
}

@Composable
private fun PathCard(title: String, path: String) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = path,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (copied) {
                    Text(
                        text = "Caminho copiado",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            IconButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Caminho", path))
                    copied = true
                },
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Copiar caminho")
            }
        }
    }
}
