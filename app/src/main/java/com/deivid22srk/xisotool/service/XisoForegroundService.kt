package com.deivid22srk.xisotool.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.deivid22srk.xisotool.MainActivity
import com.deivid22srk.xisotool.R
import com.deivid22srk.xisotool.XisoApplication
import com.deivid22srk.xisotool.data.OperationState
import com.deivid22srk.xisotool.data.OperationStore
import com.deivid22srk.xisotool.data.OperationType
import com.deivid22srk.xisotool.native.XisoNative
import com.deivid22srk.xisotool.util.Formatters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Executa a extração/criação fora da interface, com notificação determinada e
 * cancelamento. O estado real fica em [OperationStore] e é observado pela tela.
 */
class XisoForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentType: OperationType? = null

    @Volatile
    private var operationStarted = 0L

    @Volatile
    private var cancelRequested = false

    @Volatile
    private var timedOut = false

    private var lastNotificationElapsed = 0L
    private var lastNotifiedTotal = -1L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                requestCancel()
                return START_NOT_STICKY
            }

            ACTION_START -> {
                if (activeJob != null) return START_NOT_STICKY

                val type = intent.getStringExtra(EXTRA_TYPE)
                    ?.let { runCatching { OperationType.valueOf(it) }.getOrNull() }
                    ?: return START_NOT_STICKY
                val source = intent.getStringExtra(EXTRA_SOURCE) ?: return START_NOT_STICKY
                val destination = intent.getStringExtra(EXTRA_DESTINATION) ?: return START_NOT_STICKY

                startOperation(type, source, destination)
                return START_NOT_STICKY
            }
        }
        return START_NOT_STICKY
    }

    private fun startOperation(type: OperationType, source: String, destination: String) {
        currentType = type
        cancelRequested = false
        timedOut = false
        operationStarted = SystemClock.elapsedRealtime()
        lastNotificationElapsed = 0L
        lastNotifiedTotal = -1L

        OperationStore.start(type, source, destination)
        startForegroundCompat(type)
        acquireWakeLock()

        activeJob = scope.launch {
            execute(type, source, destination)
        }
    }

    private fun execute(type: OperationType, source: String, destination: String) {
        val callback = object : XisoNative.ProgressCallback {
            override fun onProgress(processed: Long, total: Long, currentFile: String) {
                OperationStore.progress(processed, total, currentFile)
                maybeUpdateNotification(type, processed, total, currentFile)
            }
        }

        try {
            if (!XisoNative.isAvailable()) {
                throw IllegalStateException(
                    "O núcleo nativo não pôde ser carregado: ${XisoNative.loadError ?: "erro desconhecido"}",
                )
            }

            val error = when (type) {
                OperationType.EXTRACT -> XisoNative.extractIso(source, destination, callback)
                OperationType.CREATE -> XisoNative.createIso(source, destination, callback)
            }

            when {
                error == null -> {
                    val elapsed = (SystemClock.elapsedRealtime() - operationStarted).coerceAtLeast(1L)
                    OperationStore.success(type, destination, elapsed)
                    postFinalNotification(type, success = true, message = destination)
                }

                timedOut -> {
                    // O timeout já publicou a falha e encerrou o serviço de forma controlada.
                }

                cancelRequested -> {
                    OperationStore.cancelled(type)
                    postFinalNotification(
                        type,
                        success = false,
                        message = "Nenhum arquivo foi alterado. A pasta temporária foi removida.",
                        cancelled = true,
                    )
                }

                else -> {
                    OperationStore.failure(type, error)
                    postFinalNotification(type, success = false, message = error)
                }
            }
        } catch (throwable: Throwable) {
            val message = throwable.message?.takeIf { it.isNotBlank() }
                ?: "Ocorreu um erro inesperado durante a operação."
            if (timedOut) {
                // Mantém a mensagem de timeout já publicada.
            } else if (cancelRequested) {
                OperationStore.cancelled(type)
                postFinalNotification(
                    type,
                    success = false,
                    message = "Nenhum arquivo foi alterado. A pasta temporária foi removida.",
                    cancelled = true,
                )
            } else {
                OperationStore.failure(type, message)
                postFinalNotification(type, success = false, message = message)
            }
        } finally {
            activeJob = null
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun requestCancel() {
        if (cancelRequested) return
        if (activeJob == null) {
            stopSelf()
            return
        }

        cancelRequested = true
        OperationStore.markCancelling()
        if (XisoNative.isAvailable()) {
            XisoNative.cancel()
        }

        val running = OperationStore.state.value as? OperationState.Running
        if (running != null) {
            showProgressNotification(
                currentType ?: running.type,
                running.processed,
                running.total,
                running.currentFile,
                cancelling = true,
            )
        }
    }

    private fun startForegroundCompat(type: OperationType) {
        val notification = buildProgressNotification(
            type = type,
            processed = 0L,
            total = 0L,
            currentFile = "",
            cancelling = false,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                PROGRESS_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(PROGRESS_NOTIFICATION_ID, notification)
        }
    }

    private fun maybeUpdateNotification(
        type: OperationType,
        processed: Long,
        total: Long,
        currentFile: String,
    ) {
        val now = SystemClock.elapsedRealtime()
        val totalChanged = total != lastNotifiedTotal
        if (!totalChanged && now - lastNotificationElapsed < NOTIFICATION_INTERVAL_MS) {
            return
        }
        lastNotificationElapsed = now
        lastNotifiedTotal = total
        showProgressNotification(type, processed, total, currentFile, cancelling = cancelRequested)
    }

    private fun showProgressNotification(
        type: OperationType,
        processed: Long,
        total: Long,
        currentFile: String,
        cancelling: Boolean,
    ) {
        val notification = buildProgressNotification(type, processed, total, currentFile, cancelling)
        runCatching {
            NotificationManagerCompat.from(this).notify(PROGRESS_NOTIFICATION_ID, notification)
        }
    }

    @SuppressLint("MissingPermission")
    private fun buildProgressNotification(
        type: OperationType,
        processed: Long,
        total: Long,
        currentFile: String,
        cancelling: Boolean,
    ): Notification {
        val percent = if (total > 0L) {
            ((processed.toDouble() / total.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)
        } else {
            0
        }

        val title = when (type) {
            OperationType.EXTRACT -> getString(R.string.notification_extracting)
            OperationType.CREATE -> getString(R.string.notification_creating)
        }

        val shortFile = currentFile.take(70).ifBlank { "…" }
        val text = when {
            cancelling -> getString(R.string.notification_cancelling)
            total <= 0L -> getString(R.string.notification_preparing)
            else -> "$percent% • $shortFile"
        }

        val subText = if (total > 0L && processed > 0L && !cancelling) {
            val elapsedSeconds = ((SystemClock.elapsedRealtime() - operationStarted) / 1000.0)
                .coerceAtLeast(0.5)
            val speed = processed.toDouble() / elapsedSeconds
            val remaining = if (speed > 0.0) {
                ((total - processed).coerceAtLeast(0L) / speed).toLong()
            } else {
                0L
            }
            "${Formatters.speed(speed)} • ${Formatters.duration(remaining)} restantes"
        } else {
            null
        }

        val builder = NotificationCompat.Builder(this, XisoApplication.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(contentPendingIntent())
            .setProgress(1000, percent * 10, total <= 0L)
            .addAction(0, getString(R.string.notification_cancel), cancelPendingIntent())

        if (subText != null) {
            builder.setSubText(subText)
        }

        return builder.build()
    }

    @SuppressLint("MissingPermission")
    private fun postFinalNotification(
        type: OperationType,
        success: Boolean,
        message: String,
        cancelled: Boolean = false,
    ) {
        val title = when {
            cancelled -> getString(R.string.notification_cancelled)
            success && type == OperationType.EXTRACT -> getString(R.string.notification_extract_success)
            success -> getString(R.string.notification_create_success)
            else -> getString(R.string.notification_failure)
        }

        val notification = NotificationCompat.Builder(this, XisoApplication.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentPendingIntent())
            .build()

        runCatching {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(FINAL_NOTIFICATION_ID, notification)
        }
    }

    private fun contentPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun cancelPendingIntent(): PendingIntent {
        val intent = Intent(this, XisoForegroundService::class.java).setAction(ACTION_CANCEL)
        return PendingIntent.getService(
            this,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "XisoTool:operation",
        ).apply {
            setReferenceCounted(false)
            acquire(MAX_WAKE_LOCK_MILLIS)
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            wakeLock?.takeIf { it.isHeld }?.release()
        }
        wakeLock = null
    }

    private fun handleTimeout() {
        if (timedOut) return
        if (activeJob == null) {
            stopSelf()
            return
        }

        timedOut = true
        cancelRequested = true
        OperationStore.markCancelling()
        if (XisoNative.isAvailable()) {
            XisoNative.cancel()
        }

        val type = currentType ?: OperationType.EXTRACT
        val message = "O tempo máximo do serviço em segundo plano foi atingido. A operação foi interrompida com segurança."
        OperationStore.failure(type, message)
        postFinalNotification(type, success = false, message = message)
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Android 14+ chama este método quando o tipo dataSync atinge o limite de
     * execução. Encerramos de forma controlada, sem deixar arquivo parcial.
     */
    override fun onTimeout(startId: Int) {
        handleTimeout()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        handleTimeout()
    }

    override fun onDestroy() {
        if (XisoNative.isAvailable()) {
            XisoNative.cancel()
        }
        activeJob?.cancel()
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_START = "com.deivid22srk.xisotool.action.START"
        private const val ACTION_CANCEL = "com.deivid22srk.xisotool.action.CANCEL"
        private const val EXTRA_TYPE = "extra_type"
        private const val EXTRA_SOURCE = "extra_source"
        private const val EXTRA_DESTINATION = "extra_destination"

        private const val PROGRESS_NOTIFICATION_ID = 1001
        private const val FINAL_NOTIFICATION_ID = 1002
        private const val NOTIFICATION_INTERVAL_MS = 450L
        private const val MAX_WAKE_LOCK_MILLIS = 12L * 60L * 60L * 1000L

        fun startExtract(context: Context, source: String, destination: String) {
            start(context, OperationType.EXTRACT, source, destination)
        }

        fun startCreate(context: Context, source: String, destination: String) {
            start(context, OperationType.CREATE, source, destination)
        }

        private fun start(
            context: Context,
            type: OperationType,
            source: String,
            destination: String,
        ) {
            val intent = Intent(context, XisoForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TYPE, type.name)
                putExtra(EXTRA_SOURCE, source)
                putExtra(EXTRA_DESTINATION, destination)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancel(context: Context) {
            val intent = Intent(context, XisoForegroundService::class.java).setAction(ACTION_CANCEL)
            runCatching { context.startService(intent) }
        }
    }
}
