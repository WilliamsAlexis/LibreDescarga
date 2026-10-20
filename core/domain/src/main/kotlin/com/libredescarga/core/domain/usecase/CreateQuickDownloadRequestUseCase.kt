package com.libredescarga.core.domain.usecase

import com.libredescarga.core.domain.repository.UserPreferencesRepository
import com.libredescarga.core.model.AudioFormat
import com.libredescarga.core.model.DownloadRequest
import com.libredescarga.core.model.MediaEntry
import com.libredescarga.core.model.UserSettings
import com.libredescarga.core.model.VideoQuality
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Descarga rápida con un toque usando la calidad por defecto de Ajustes
 * (por ejemplo, "siempre 720p" o "siempre MP3 320"). Si el video está en
 * "Preguntar", se usa el audio; si ambos están en "Preguntar", devuelve null.
 */
class CreateQuickDownloadRequestUseCase @Inject constructor(
    private val preferences: UserPreferencesRepository,
) {
    suspend operator fun invoke(entry: MediaEntry): DownloadRequest? =
        create(entry, preferences.settings.first())

    companion object {
        fun create(entry: MediaEntry, settings: UserSettings): DownloadRequest? = when {
            settings.defaultVideoQuality != VideoQuality.ASK ->
                DownloadRequestFactory.video(entry, settings.defaultVideoQuality)

            settings.defaultAudioFormat != AudioFormat.ASK ->
                DownloadRequestFactory.audio(entry, settings.defaultAudioFormat)

            else -> null
        }
    }
}
