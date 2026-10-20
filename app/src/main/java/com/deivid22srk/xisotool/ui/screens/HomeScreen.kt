@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.deivid22srk.xisotool.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deivid22srk.xisotool.ui.components.ExpressiveIcon
import com.deivid22srk.xisotool.ui.components.InfoBanner
import com.deivid22srk.xisotool.ui.components.XisoBackground
import com.deivid22srk.xisotool.ui.components.XisoTopBar

@Composable
fun HomeScreen(
    onExtract: () -> Unit,
    onCreate: () -> Unit,
    onAbout: () -> Unit,
    notificationsGranted: Boolean,
    onRequestNotifications: () -> Unit,
) {
    XisoBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                XisoTopBar(
                    title = "XISO Tool",
                    subtitle = "Jogos do Xbox clássico",
                    actions = {
                        IconButton(onClick = onAbout) {
                            Icon(Icons.Filled.Settings, contentDescription = "Sobre o app")
                        }
                    },
                )
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { HeroCard() }

                if (!notificationsGranted) {
                    item { NotificationPermissionCard(onRequestNotifications) }
                }

                item {
                    Text(
                        text = "O que você quer fazer?",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }

                item {
                    OperationCard(
                        title = "Extrair ISO",
                        description = "Transforme uma ISO do Xbox em uma pasta com todos os arquivos do jogo.",
                        icon = Icons.Filled.FileDownload,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        onClick = onExtract,
                    )
                }

                item {
                    OperationCard(
                        title = "Criar ISO",
                        description = "Monte uma imagem XISO a partir da pasta do jogo, com default.xbe na raiz.",
                        icon = Icons.Filled.FileUpload,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        onClick = onCreate,
                    )
                }

                item {
                    InfoBanner(
                        text = "Tudo é processado no seu aparelho, sem enviar arquivos para a internet. " +
                            "Imagens grandes são lidas em streaming, com pouco uso de memória.",
                    )
                }

                item {
                    Text(
                        text = "Compatível com imagens XISO do Xbox clássico, xemu e consoles modificados. " +
                            "O app não inclui jogos: use apenas cópias que você possui.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ),
            ),
    ) {
        Icon(
            imageVector = Icons.Filled.Album,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.10f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 34.dp, y = 30.dp)
                .size(190.dp),
        )

        Column(modifier = Modifier.padding(24.dp)) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.10f),
            ) {
                Text(
                    text = "XBOX CLÁSSICO",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            Spacer(Modifier.size(18.dp))

            Text(
                text = "Seu jogo,\ndo disco para a pasta.",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                lineHeight = 42.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            Spacer(Modifier.size(12.dp))

            Text(
                text = "Extraia e crie imagens XISO com progresso real, cancelamento seguro e suporte a arquivos grandes.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
            )

            Spacer(Modifier.size(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroChip("XISO")
                HeroChip("Offline")
                HeroChip("Streaming")
            }
        }
    }
}

@Composable
private fun HeroChip(text: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.08f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun OperationCard(
    title: String,
    description: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExpressiveIcon(
                icon = icon,
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                contentColor = contentColor,
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.78f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = contentColor,
            )
        }
    }
}

@Composable
private fun NotificationPermissionCard(onRequest: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Acompanhe o progresso",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text = "Ative as notificações para ver a porcentagem e cancelar mesmo com a tela bloqueada.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
                )
            }
            TextButton(onClick = onRequest) {
                Text("Ativar")
            }
        }
    }
}
