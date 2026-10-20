package com.libredescarga.core.model

enum class RepeatMode { OFF, ALL, ONE }

data class QueueEntry(
    val uri: String,
    val title: String,
    val artist: String?,
    val isVideo: Boolean,
)

/** Estado del reproductor compartido (música y video). */
data class PlaybackState(
    val isConnected: Boolean = false,
    val queue: List<QueueEntry> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val speed: Float = 1f,
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    /** Carátula incrustada en el audio actual (bytes de imagen), si existe. */
    val artwork: ByteArray? = null,
) {
    val current: QueueEntry? get() = queue.getOrNull(currentIndex)
    val hasMedia: Boolean get() = current != null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PlaybackState) return false
        return isConnected == other.isConnected && queue == other.queue && currentIndex == other.currentIndex &&
            isPlaying == other.isPlaying && isBuffering == other.isBuffering && positionMs == other.positionMs &&
            durationMs == other.durationMs && speed == other.speed && shuffle == other.shuffle &&
            repeatMode == other.repeatMode && artwork.contentEquals(other.artwork)
    }

    override fun hashCode(): Int {
        var result = queue.hashCode()
        result = 31 * result + currentIndex
        result = 31 * result + isPlaying.hashCode()
        result = 31 * result + positionMs.hashCode()
        result = 31 * result + (artwork?.contentHashCode() ?: 0)
        return result
    }
}

/** Velocidades del reproductor de video (0,25x a 3x). */
val PlaybackSpeeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)
