package com.deivid22srk.xisotool.data

import android.os.SystemClock

enum class OperationType {
    EXTRACT,
    CREATE,
}

sealed interface OperationState {

    data object Idle : OperationState

    data class Running(
        val type: OperationType,
        val source: String,
        val destination: String,
        val processed: Long = 0L,
        val total: Long = 0L,
        val currentFile: String = "",
        val startedAtElapsed: Long = SystemClock.elapsedRealtime(),
        val startedAtEpoch: Long = System.currentTimeMillis(),
        val cancelling: Boolean = false,
    ) : OperationState

    data class Success(
        val type: OperationType,
        val destination: String,
        val elapsedMillis: Long,
        val finishedAtEpoch: Long = System.currentTimeMillis(),
    ) : OperationState

    data class Failure(
        val type: OperationType,
        val message: String,
        val finishedAtEpoch: Long = System.currentTimeMillis(),
    ) : OperationState

    data class Cancelled(
        val type: OperationType,
        val finishedAtEpoch: Long = System.currentTimeMillis(),
    ) : OperationState
}
