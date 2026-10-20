package com.deivid22srk.xisotool.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deivid22srk.xisotool.BuildConfig

private const val REPOSITORY_URL = "https://github.com/deivid22srk/xiso-tool-android"

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
        },
        title = {
            Text(
                text = "XISO Tool",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Versão ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Extraia e crie imagens XISO de jogos do Xbox clássico e Xbox 360, com progresso real, " +
                        "cancelamento seguro e processamento totalmente offline.",
                )
                Text(
                    text = "O núcleo de arquivos é escrito em Rust e usa o formato XDVDFS. " +
                        "A interface segue o Material 3 Expressive, com cores dinâmicas no Android 12+.",
                )
                Text(
                    text = "Este app não inclui jogos. Use apenas cópias que você possui legalmente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(REPOSITORY_URL)),
                        )
                    }
                },
            ) {
                Text("Ver no GitHub")
            }
        },
    )
}
