package com.deivid22srk.xisotool.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.deivid22srk.xisotool.data.OperationState
import com.deivid22srk.xisotool.ui.screens.CreateScreen
import com.deivid22srk.xisotool.ui.screens.ExtractScreen
import com.deivid22srk.xisotool.ui.screens.HomeScreen
import com.deivid22srk.xisotool.ui.screens.PermissionScreen
import com.deivid22srk.xisotool.ui.screens.ProgressScreen
import com.deivid22srk.xisotool.ui.screens.ResultScreen
import java.io.File

@Composable
fun XisoToolApp(
    storageGranted: Boolean,
    notificationsGranted: Boolean,
    onRequestStorage: () -> Unit,
    onRetryStorage: () -> Unit,
    onRequestNotifications: () -> Unit,
    viewModel: MainViewModel = viewModel(),
) {
    if (!storageGranted) {
        PermissionScreen(
            onRequestStorage = onRequestStorage,
            onRetry = onRetryStorage,
        )
        return
    }

    val context = LocalContext.current
    val operation by viewModel.operation.collectAsStateWithLifecycle()
    var picker by remember { mutableStateOf<PickerConfig?>(null) }
    var lastBrowseDirectory by rememberSaveable { mutableStateOf<String?>(null) }
    var showCancelDialog by remember { mutableStateOf(false) }

    fun chooseFile(
        title: String,
        extensions: Set<String>,
        onSelected: (File) -> Unit,
    ) {
        picker = PickerConfig(
            mode = PickerMode.FILE,
            title = title,
            initialDirectory = lastBrowseDirectory,
            fileExtensions = extensions,
        ) { file ->
            lastBrowseDirectory = file.parent
            picker = null
            onSelected(file)
        }
    }

    fun chooseDirectory(
        title: String,
        onSelected: (File) -> Unit,
    ) {
        picker = PickerConfig(
            mode = PickerMode.DIRECTORY,
            title = title,
            initialDirectory = lastBrowseDirectory,
        ) { directory ->
            lastBrowseDirectory = directory.absolutePath
            picker = null
            onSelected(directory)
        }
    }

    BackHandler(enabled = viewModel.screen == AppScreen.Progress) {
        showCancelDialog = true
    }
    BackHandler(
        enabled = viewModel.screen == AppScreen.ExtractSetup ||
            viewModel.screen == AppScreen.CreateSetup ||
            viewModel.screen == AppScreen.Result,
    ) {
        viewModel.handleBack()
    }

    AnimatedContent(
        targetState = viewModel.screen,
        transitionSpec = {
            (
                fadeIn(animationSpec = tween(220)) +
                    slideInHorizontally(animationSpec = tween(260)) { it / 12 }
                ) togetherWith (
                fadeOut(animationSpec = tween(160)) +
                    slideOutHorizontally(animationSpec = tween(200)) { -it / 14 }
                )
        },
        label = "screen",
    ) { screen ->
        when (screen) {
            AppScreen.Home -> HomeScreen(
                onExtract = viewModel::openExtract,
                onCreate = viewModel::openCreate,
                onAbout = viewModel::openAbout,
                notificationsGranted = notificationsGranted,
                onRequestNotifications = onRequestNotifications,
            )

            AppScreen.ExtractSetup -> ExtractScreen(
                isoPath = viewModel.extractSource,
                destinationParent = viewModel.extractParent,
                folderName = viewModel.extractFolderName,
                onPickIso = {
                    chooseFile(
                        title = "Escolher ISO",
                        extensions = setOf("iso", "xiso", "img"),
                    ) { file ->
                        viewModel.onExtractSourceSelected(file.absolutePath)
                    }
                },
                onPickDestination = {
                    chooseDirectory("Pasta de destino") { directory ->
                        viewModel.onExtractParentSelected(directory.absolutePath)
                    }
                },
                onFolderNameChange = viewModel::onExtractFolderNameChange,
                onBack = { viewModel.handleBack() },
                onStart = { viewModel.startExtract(context) },
            )

            AppScreen.CreateSetup -> CreateScreen(
                sourceDir = viewModel.createSource,
                sourceHasDefaultXbe = viewModel.createSourceHasDefaultXbe,
                destinationParent = viewModel.createParent,
                fileName = viewModel.createFileName,
                onPickSource = {
                    chooseDirectory("Pasta do jogo") { directory ->
                        viewModel.onCreateSourceSelected(directory.absolutePath)
                    }
                },
                onPickDestination = {
                    chooseDirectory("Pasta de destino") { directory ->
                        viewModel.onCreateParentSelected(directory.absolutePath)
                    }
                },
                onFileNameChange = viewModel::onCreateFileNameChange,
                onBack = { viewModel.handleBack() },
                onStart = { viewModel.startCreate(context) },
            )

            AppScreen.Progress -> {
                val running = operation as? OperationState.Running
                if (running != null) {
                    ProgressScreen(
                        state = running,
                        onCancel = { showCancelDialog = true },
                        onBack = { showCancelDialog = true },
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            AppScreen.Result -> {
                if (operation is OperationState.Success ||
                    operation is OperationState.Failure ||
                    operation is OperationState.Cancelled
                ) {
                    ResultScreen(
                        state = operation,
                        onHome = viewModel::finishResult,
                        onRetry = viewModel::retryLastOperation,
                        onOpenFolder = { path ->
                            openFolderOrBrowse(
                                context = context,
                                path = path,
                                onBrowse = { directory ->
                                    lastBrowseDirectory = directory.absolutePath
                                    picker = PickerConfig(
                                        mode = PickerMode.DIRECTORY,
                                        title = "Pasta do jogo",
                                        initialDirectory = directory.absolutePath,
                                    ) { selected ->
                                        lastBrowseDirectory = selected.absolutePath
                                        picker = null
                                    }
                                },
                            )
                        },
                        onShareIso = { path -> shareIso(context, path) },
                    )
                }
            }
        }
    }

    picker?.let { config ->
        FileBrowserDialog(
            config = config,
            onDismiss = { picker = null },
        )
    }

    if (viewModel.showAbout) {
        AboutDialog(onDismiss = viewModel::closeAbout)
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Cancelar operação?") },
            text = {
                Text(
                    "A pasta ou a ISO temporária será removida. O jogo original não é alterado. " +
                        "Essa ação pode levar alguns segundos.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelDialog = false
                        viewModel.requestCancel(context)
                    },
                ) {
                    Text("Cancelar operação")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text("Continuar")
                }
            },
        )
    }
}

private fun openFolderOrBrowse(
    context: Context,
    path: String,
    onBrowse: (File) -> Unit,
) {
    val directory = File(path)
    if (!directory.exists()) {
        Toast.makeText(context, "A pasta não existe mais.", Toast.LENGTH_LONG).show()
        return
    }

    val opened = runCatching {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            directory,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "resource/folder")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }.isSuccess

    if (!opened) {
        onBrowse(directory)
    }
}

private fun shareIso(context: Context, path: String) {
    val file = File(path)
    if (!file.exists() || !file.isFile) {
        Toast.makeText(context, "A ISO não existe mais.", Toast.LENGTH_LONG).show()
        return
    }

    runCatching {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartilhar ISO"))
    }.onFailure {
        Toast.makeText(context, "Não foi possível compartilhar a ISO.", Toast.LENGTH_LONG).show()
    }
}
