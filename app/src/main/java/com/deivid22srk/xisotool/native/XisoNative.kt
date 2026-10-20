package com.deivid22srk.xisotool.native

import androidx.annotation.Keep

/**
 * Ponte para o núcleo Rust (crate `xiso-core`).
 *
 * As funções nativas retornam `null` em caso de sucesso ou uma mensagem de erro
 * em português. O callback é chamado na própria thread da operação, que já está
 * ligada à JVM.
 */
@Keep
object XisoNative {

    val loadError: String? = runCatching {
        System.loadLibrary("xiso_core")
    }.exceptionOrNull()?.let { error ->
        error.message ?: error.javaClass.simpleName
    }

    fun isAvailable(): Boolean = loadError == null

    external fun extractIso(
        isoPath: String,
        outDir: String,
        callback: ProgressCallback,
    ): String?

    external fun createIso(
        srcDir: String,
        outIso: String,
        callback: ProgressCallback,
    ): String?

    external fun cancel()

    @Keep
    fun interface ProgressCallback {
        fun onProgress(processed: Long, total: Long, currentFile: String)
    }
}
