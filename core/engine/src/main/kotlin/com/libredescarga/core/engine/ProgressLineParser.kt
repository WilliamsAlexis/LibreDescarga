package com.libredescarga.core.engine

import kotlin.math.roundToLong

data class EngineProgress(
    val percent: Float,
    val etaSeconds: Long?,
    val speedBytesPerSecond: Long?,
    val totalBytes: Long?,
    val downloadedBytes: Long?,
)

/**
 * Extrae tamaño y velocidad de las líneas de progreso de yt-dlp, por ejemplo:
 * `[download]  45.3% of ~ 12.34MiB at  1.20MiB/s ETA 00:10 (frag 3/20)`.
 */
object ProgressLineParser {

    private val TOTAL = Regex("""of\s+~?\s*([\d.]+)\s*([KMGT]?i?B)\b""")
    private val SPEED = Regex("""at\s+~?\s*([\d.]+)\s*([KMGT]?i?B)/s""")

    fun parse(percent: Float, etaSeconds: Long, line: String): EngineProgress {
        val safePercent = if (percent.isNaN()) 0f else percent.coerceIn(0f, 100f)
        val total = TOTAL.find(line)?.let { toBytes(it.groupValues[1], it.groupValues[2]) }
        val speed = SPEED.find(line)?.let { toBytes(it.groupValues[1], it.groupValues[2]) }
        return EngineProgress(
            percent = safePercent,
            etaSeconds = etaSeconds.takeIf { it >= 0 },
            speedBytesPerSecond = speed,
            totalBytes = total,
            downloadedBytes = total?.let { (it * safePercent / 100.0).roundToLong() },
        )
    }

    fun toBytes(value: String, unit: String): Long? {
        val number = value.toDoubleOrNull() ?: return null
        val multiplier = when (unit.uppercase()) {
            "B" -> 1.0
            "KIB" -> 1024.0
            "MIB" -> 1024.0 * 1024
            "GIB" -> 1024.0 * 1024 * 1024
            "TIB" -> 1024.0 * 1024 * 1024 * 1024
            "KB" -> 1_000.0
            "MB" -> 1_000_000.0
            "GB" -> 1_000_000_000.0
            "TB" -> 1_000_000_000_000.0
            else -> return null
        }
        return (number * multiplier).roundToLong()
    }
}
