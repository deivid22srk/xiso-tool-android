@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.deivid22srk.xisotool.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.deivid22srk.xisotool.ui.components.ExpressiveCard
import com.deivid22srk.xisotool.ui.components.ExpressiveIcon
import com.deivid22srk.xisotool.ui.components.InfoBanner
import com.deivid22srk.xisotool.ui.components.PrimaryActionButton
import com.deivid22srk.xisotool.ui.components.XisoBackground

@Composable
fun PermissionScreen(
    onRequestStorage: () -> Unit,
    onRetry: () -> Unit,
) {
    XisoBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Spacer(Modifier.size(24.dp))

            ExpressiveIcon(
                icon = Icons.Filled.FolderOpen,
                size = 96.dp,
            )

            Text(
                text = "Precisamos do seu acesso aos arquivos",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )

            Text(
                text = "Para ler ISOs do Xbox clássico e Xbox 360 e gravar pastas ou imagens no armazenamento, o XISO Tool precisa " +
                    "de acesso direto aos seus arquivos. Nada é enviado para a internet.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            ExpressiveCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                PermissionReason("Ler ISOs e pastas de jogos em qualquer local do armazenamento.")
                Spacer(Modifier.size(12.dp))
                PermissionReason("Criar a pasta extraída ou a nova ISO no destino escolhido.")
                Spacer(Modifier.size(12.dp))
                PermissionReason("Funcionar com arquivos grandes, sem copiar o jogo para dentro do app.")
            }

            InfoBanner(
                text = "No Android 11 ou superior, escolha a opção \"Acesso a todos os arquivos\" na tela de " +
                    "configurações que será aberta. Em versões anteriores, o Android pedirá a permissão de armazenamento.",
            )

            PrimaryActionButton(
                text = "Abrir configurações",
                icon = Icons.Filled.FolderOpen,
                onClick = onRequestStorage,
            )

            TextButton(onClick = onRetry) {
                Text("Já concedi, verificar novamente")
            }

            Text(
                text = "Você pode revogar essa permissão a qualquer momento nas configurações do Android.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PermissionReason(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
