@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.deivid22srk.xisotool.ui.screens

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deivid22srk.xisotool.data.OperationState
import com.deivid22srk.xisotool.data.OperationType
import com.deivid22srk.xisotool.ui.components.StatTile
import com.deivid22srk.xisotool.ui.components.XisoBackground
import com.deivid22srk.xisotool.ui.components.XisoSmallTopBar
import com.deivid22srk.xisotool.util.Formatters
import kotlin.math.roundToInt

@Composable
fun ProgressScreen(
    state: OperationState.Running,
    onCancel: () -> Unit,
    onBack: () -> Unit,
) {
    val total = state.total
    val fraction = if (total > 0L) {
        (state.processed.toDouble() / total.toDouble()).coerceIn(0.0, 1.0).toFloat()
    } else {
        0f
    }
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 180f),
        label = "progress",
    )

    val elapsedMillis = (SystemClock.elapsedRealtime() - state.startedAtElapsed).coerceAtLeast(1000L)
    val speed = if (state.processed > 0L) state.processed.toDouble() * 1000.0 / elapsedMillis else 0.0
    val remaining = if (speed > 0.0 && total > state.processed) {
        ((total - state.processed) / speed).toLong()
    } else {
        0L
    }

    val title = if (state.type == OperationType.EXTRACT) "Extraindo ISO" else "Criando ISO"

    XisoBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                XisoSmallTopBar(
                    title = title,
                    onBack = onBack,
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
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (total > 0L) {
                        CircularWavyProgressIndicator(
                            progress = { animated },
                            modifier = Modifier.size(232.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    } else {
                        CircularWavyProgressIndicator(
                            modifier = Modifier.size(232.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (total > 0L) "${(animated * 100f).roundToInt()}%" else "…",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            text = when {
                                state.cancelling -> "Cancelando…"
                                total > 0L -> "concluído"
                                else -> "preparando"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Text(
                    text = state.currentFile.ifBlank { "Preparando os arquivos…" },
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (total > 0L) {
                    LinearWavyProgressIndicator(
                        progress = { animated },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                } else {
                    LinearWavyProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StatTile(
                        label = "Processado",
                        value = Formatters.bytes(state.processed),
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "Total",
                        value = if (total > 0L) Formatters.bytes(total) else "Calculando",
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StatTile(
                        label = "Velocidade",
                        value = Formatters.speed(speed),
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "Restante",
                        value = Formatters.duration(remaining),
                        modifier = Modifier.weight(1f),
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LoadingIndicator(modifier = Modifier.size(30.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Você pode bloquear a tela ou trocar de app. A operação continua em segundo plano.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }

                Button(
                    onClick = onCancel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp),
                    enabled = !state.cancelling,
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Icon(Icons.Filled.Cancel, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (state.cancelling) "Cancelando…" else "Cancelar operação",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(Modifier.size(16.dp))
            }
        }
    }
}
