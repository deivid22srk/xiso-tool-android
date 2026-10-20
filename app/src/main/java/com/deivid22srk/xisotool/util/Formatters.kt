package com.deivid22srk.xisotool.util

import java.util.Locale
import kotlin.math.roundToInt

object Formatters {

    private val ptBr = Locale.forLanguageTag("pt-BR")

    fun bytes(value: Long): String {
        if (value <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = value.toDouble()
        var unit = 0
        while (size >= 1024.0 && unit < units.lastIndex) {
            size /= 1024.0
            unit++
        }
        return if (unit == 0) {
            "${value} B"
        } else {
            String.format(ptBr, "%.1f %s", size, units[unit])
        }
    }

    fun speed(bytesPerSecond: Double): String {
        if (bytesPerSecond <= 0.0 || bytesPerSecond.isNaN() || bytesPerSecond.isInfinite()) {
            return "—"
        }
        return "${bytes(bytesPerSecond.toLong())}/s"
    }

    fun duration(seconds: Long): String {
        if (seconds <= 0L) return "—"
        return when {
            seconds < 60L -> "${seconds}s"
            seconds < 3600L -> "${seconds / 60}m ${seconds % 60}s"
            else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
        }
    }

    fun percent(processed: Long, total: Long): Int {
        if (total <= 0L) return 0
        return ((processed.toDouble() / total.toDouble()) * 100.0)
            .roundToInt()
            .coerceIn(0, 100)
    }
}
