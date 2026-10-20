package com.deivid22srk.xisotool.ui

import android.content.Context
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deivid22srk.xisotool.data.OperationState
import com.deivid22srk.xisotool.data.OperationStore
import com.deivid22srk.xisotool.data.OperationType
import com.deivid22srk.xisotool.service.XisoForegroundService
import java.io.File
import kotlinx.coroutines.launch

sealed interface AppScreen {
    data object Home : AppScreen
    data object ExtractSetup : AppScreen
    data object CreateSetup : AppScreen
    data object Progress : AppScreen
    data object Result : AppScreen
}

class MainViewModel : ViewModel() {

    val operation = OperationStore.state

    var screen by mutableStateOf<AppScreen>(AppScreen.Home)
        private set
    var showAbout by mutableStateOf(false)
        private set

    var extractSource by mutableStateOf<String?>(null)
        private set
    var extractParent by mutableStateOf(defaultOutputParent())
        private set
    var extractFolderName by mutableStateOf("")
        private set

    var createSource by mutableStateOf<String?>(null)
        private set
    var createSourceHasDefaultExecutable by mutableStateOf(false)
        private set
    var createParent by mutableStateOf(defaultOutputParent())
        private set
    var createFileName by mutableStateOf("")
        private set

    init {
        if (OperationStore.state.value is OperationState.Running) {
            screen = AppScreen.Progress
        }

        viewModelScope.launch {
            OperationStore.state.collect { state ->
                when (state) {
                    is OperationState.Running -> screen = AppScreen.Progress
                    is OperationState.Success,
                    is OperationState.Failure,
                    is OperationState.Cancelled -> if (screen == AppScreen.Progress) {
                        screen = AppScreen.Result
                    }

                    OperationState.Idle -> Unit
                }
            }
        }
    }

    fun openExtract() {
        screen = AppScreen.ExtractSetup
    }

    fun openCreate() {
        screen = AppScreen.CreateSetup
    }

    fun openAbout() {
        showAbout = true
    }

    fun closeAbout() {
        showAbout = false
    }

    fun onExtractSourceSelected(path: String) {
        extractSource = path
        extractFolderName = File(path).nameWithoutExtension.ifBlank { "Jogo" }
    }

    fun onExtractParentSelected(path: String) {
        extractParent = path
    }

    fun onExtractFolderNameChange(value: String) {
        extractFolderName = value
    }

    fun onCreateSourceSelected(path: String) {
        createSource = path
        createSourceHasDefaultExecutable = hasDefaultExecutable(File(path))
        createFileName = "${File(path).name.ifBlank { "Jogo" }}.iso"
    }

    fun onCreateParentSelected(path: String) {
        createParent = path
    }

    fun onCreateFileNameChange(value: String) {
        createFileName = value
    }

    fun startExtract(context: Context): String? {
        val source = extractSource ?: return "Escolha uma ISO do Xbox clássico ou Xbox 360."
        if (!File(source).isFile) return "A ISO selecionada não existe ou não é um arquivo."
        if (!File(source).canRead()) return "Não foi possível ler a ISO. Verifique a permissão de acesso aos arquivos."

        val nameError = validateFolderName(extractFolderName)
        if (nameError != null) return nameError

        val destination = File(extractParent, extractFolderName.trim())
        if (destination.exists()) {
            return "A pasta de destino já existe. Escolha outro nome para não sobrescrever arquivos."
        }

        return try {
            XisoForegroundService.startExtract(context, source, destination.absolutePath)
            screen = AppScreen.Progress
            null
        } catch (throwable: Throwable) {
            "Não foi possível iniciar a operação: ${throwable.message ?: "erro desconhecido"}"
        }
    }

    fun startCreate(context: Context): String? {
        val source = createSource ?: return "Escolha a pasta do jogo."
        if (!File(source).isDirectory) return "A pasta do jogo não existe ou não é uma pasta."
        if (!createSourceHasDefaultExecutable) {
            return "A pasta selecionada não contém default.xbe (Xbox clássico) nem default.xex (Xbox 360) na raiz."
        }

        val name = normalizeIsoName(createFileName)
        val nameError = validateIsoName(name)
        if (nameError != null) return nameError

        val destination = File(createParent, name)
        if (destination.exists()) {
            return "Já existe uma ISO com esse nome. Escolha outro nome."
        }

        return try {
            XisoForegroundService.startCreate(context, source, destination.absolutePath)
            screen = AppScreen.Progress
            null
        } catch (throwable: Throwable) {
            "Não foi possível iniciar a operação: ${throwable.message ?: "erro desconhecido"}"
        }
    }

    fun requestCancel(context: Context) {
        XisoForegroundService.cancel(context)
    }

    fun finishResult() {
        OperationStore.reset()
        screen = AppScreen.Home
    }

    fun retryLastOperation() {
        val type = when (val current = OperationStore.state.value) {
            is OperationState.Failure -> current.type
            is OperationState.Cancelled -> current.type
            else -> null
        }

        when (type) {
            OperationType.EXTRACT -> screen = AppScreen.ExtractSetup
            OperationType.CREATE -> screen = AppScreen.CreateSetup
            null -> screen = AppScreen.Home
        }
    }

    /**
     * Retorna true quando a tela tratou o botão Voltar. Na tela de progresso o
     * botão é tratado pelo diálogo de confirmação de cancelamento.
     */
    fun handleBack(): Boolean {
        return when (screen) {
            AppScreen.Home, AppScreen.Progress -> false
            AppScreen.ExtractSetup, AppScreen.CreateSetup -> {
                screen = AppScreen.Home
                true
            }

            AppScreen.Result -> {
                finishResult()
                true
            }
        }
    }

    private fun hasDefaultExecutable(directory: File): Boolean {
        return runCatching {
            directory.listFiles()?.any { file ->
                file.isFile && (
                    file.name.equals("default.xbe", ignoreCase = true) ||
                        file.name.equals("default.xex", ignoreCase = true)
                )
            } == true
        }.getOrDefault(false)
    }

    private fun normalizeIsoName(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return ""
        return if (trimmed.endsWith(".iso", ignoreCase = true)) trimmed else "$trimmed.iso"
    }

    private fun validateFolderName(value: String): String? {
        val name = value.trim()
        return when {
            name.isEmpty() -> "Informe um nome para a pasta de destino."
            name == "." || name == ".." -> "Escolha um nome de pasta válido."
            name.contains('/') || name.contains('\\') -> "O nome da pasta não pode conter barras."
            name.length > 120 -> "O nome da pasta é muito longo."
            else -> null
        }
    }

    private fun validateIsoName(value: String): String? {
        val name = value.trim()
        return when {
            name.isEmpty() -> "Informe um nome para a ISO."
            name == "." || name == ".." -> "Escolha um nome de ISO válido."
            name.contains('/') || name.contains('\\') -> "O nome da ISO não pode conter barras."
            name.length > 120 -> "O nome da ISO é muito longo."
            !name.endsWith(".iso", ignoreCase = true) -> "O nome da ISO deve terminar em .iso."
            else -> null
        }
    }

    companion object {
        @Suppress("DEPRECATION")
        fun defaultOutputParent(): String {
            return File(Environment.getExternalStorageDirectory(), "XISO").absolutePath
        }
    }
}
