package com.libredescarga.core.player

import android.media.audiofx.Equalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class EqualizerBand(val index: Int, val centerHz: Int, val levelMb: Int)

data class EqualizerState(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val presets: List<String> = emptyList(),
    /** Preajuste elegido, o null si el usuario movió las bandas a mano. */
    val presetIndex: Int? = null,
    val bands: List<EqualizerBand> = emptyList(),
    val minLevelMb: Int = 0,
    val maxLevelMb: Int = 0,
)

/**
 * Ecualizador básico (android.media.audiofx) sobre la sesión de audio del reproductor.
 * Algunos teléfonos no lo admiten: en ese caso [EqualizerState.available] es false.
 */
@Singleton
class AudioEffectsController @Inject constructor() {

    private var equalizer: Equalizer? = null
    private var enabled = false
    private var presetIndex: Int? = null
    private val customLevels = mutableMapOf<Int, Int>()

    private val _state = MutableStateFlow(EqualizerState())
    val state: StateFlow<EqualizerState> = _state.asStateFlow()

    fun attach(audioSessionId: Int) {
        release()
        if (audioSessionId <= 0) return
        equalizer = runCatching { Equalizer(0, audioSessionId) }.getOrNull()
        applySettings()
        publish()
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        runCatching { equalizer?.enabled = value }
        publish()
    }

    fun usePreset(index: Int) {
        presetIndex = index
        customLevels.clear()
        runCatching { equalizer?.usePreset(index.toShort()) }
        publish()
    }

    fun setBandLevel(band: Int, levelMb: Int) {
        presetIndex = null
        customLevels[band] = levelMb
        runCatching { equalizer?.setBandLevel(band.toShort(), levelMb.toShort()) }
        publish()
    }

    fun release() {
        runCatching { equalizer?.release() }
        equalizer = null
        _state.value = _state.value.copy(available = false)
    }

    /** Al cambiar la sesión de audio se vuelven a aplicar los ajustes del usuario. */
    private fun applySettings() {
        val eq = equalizer ?: return
        runCatching {
            presetIndex?.let { eq.usePreset(it.toShort()) }
            customLevels.forEach { (band, level) -> eq.setBandLevel(band.toShort(), level.toShort()) }
            eq.enabled = enabled
        }
    }

    private fun publish() {
        val eq = equalizer
        _state.value = if (eq == null) {
            EqualizerState(available = false, enabled = enabled)
        } else {
            runCatching {
                val range = eq.bandLevelRange
                EqualizerState(
                    available = true,
                    enabled = enabled,
                    presets = (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) },
                    presetIndex = presetIndex,
                    bands = (0 until eq.numberOfBands).map { band ->
                        EqualizerBand(
                            index = band,
                            centerHz = eq.getCenterFreq(band.toShort()) / 1000,
                            levelMb = eq.getBandLevel(band.toShort()).toInt(),
                        )
                    },
                    minLevelMb = range[0].toInt(),
                    maxLevelMb = range[1].toInt(),
                )
            }.getOrElse { EqualizerState(available = false, enabled = enabled) }
        }
    }
}
