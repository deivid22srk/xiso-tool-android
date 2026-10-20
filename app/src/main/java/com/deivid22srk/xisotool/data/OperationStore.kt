package com.deivid22srk.xisotool.data

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Estado compartilhado entre o serviço em primeiro plano e a interface.
 * Fica no processo do app, então sobrevive à recriação da Activity.
 */
object OperationStore {

    private val _state = MutableStateFlow<OperationState>(OperationState.Idle)
    val state: StateFlow<OperationState> = _state.asStateFlow()

    fun start(
        type: OperationType,
        source: String,
        destination: String,
    ) {
        _state.value = OperationState.Running(
            type = type,
            source = source,
            destination = destination,
            startedAtElapsed = SystemClock.elapsedRealtime(),
        )
    }

    fun progress(
        processed: Long,
        total: Long,
        currentFile: String,
    ) {
        _state.update { current ->
            if (current is OperationState.Running) {
                current.copy(
                    processed = processed.coerceAtLeast(0L),
                    total = total.coerceAtLeast(0L),
                    currentFile = currentFile,
                )
            } else {
                current
            }
        }
    }

    fun markCancelling() {
        _state.update { current ->
            if (current is OperationState.Running) current.copy(cancelling = true) else current
        }
    }

    fun success(
        type: OperationType,
        destination: String,
        elapsedMillis: Long,
    ) {
        _state.value = OperationState.Success(type, destination, elapsedMillis)
    }

    fun failure(type: OperationType, message: String) {
        _state.value = OperationState.Failure(type, message)
    }

    fun cancelled(type: OperationType) {
        _state.value = OperationState.Cancelled(type)
    }

    fun reset() {
        _state.value = OperationState.Idle
    }
}
