package com.libredescarga.feature.analysis.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.libredescarga.core.config.AppConfig
import com.libredescarga.core.designsystem.icon.LibreIcons
import com.libredescarga.core.model.AudioFormat
import com.libredescarga.core.model.MediaKind
import com.libredescarga.core.model.SubtitleTrack
import com.libredescarga.core.model.VideoOption
import com.libredescarga.core.ui.estimatedSizeLabel
import com.libredescarga.core.ui.label
import com.libredescarga.core.ui.qualityLabel
import com.libredescarga.core.ui.viewCountLabel
import com.libredescarga.feature.analysis.AnalysisProgress
import com.libredescarga.feature.analysis.AnalysisUiState
import com.libredescarga.feature.analysis.QuickDefault
import com.libredescarga.feature.analysis.R
import java.util.Locale

/**
 * Pantalla que se muestra cuando el enlace tiene UN solo video o audio.
 * Deja elegir la calidad o el formato y los subtítulos, y tiene el botón Descargar.
 */
@Composable
internal fun SingleMediaContent(
    state: AnalysisUiState.Single,
    analysisProgress: Float = 0f,
    submitError: String?,
    onTabSelected: (MediaKind) -> Unit,
    onVideoSelected: (VideoOption) -> Unit,
    onAudioSelected: (AudioFormat) -> Unit,
    onSubtitleSelected: (SubtitleTrack?) -> Unit,
    onEmbedSubtitleChange: (Boolean) -> Unit,
    onDownload: () -> Unit,
    onQuickDownload: () -> Unit,
) {
    val info = state.info
    // Pestañas disponibles: solo se muestran Video y/o Audio si ese enlace ofrece opciones de ese tipo.
    val tabs = buildList {
        if (info.videoOptions.isNotEmpty()) add(MediaKind.VIDEO)
        if (info.audioOptions.isNotEmpty()) add(MediaKind.AUDIO)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Parte que se desplaza: cabecera, descarga rápida, pestañas, opciones y ajustes avanzados.
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                // Cabecera con la miniatura, el título, el autor y las visitas.
                MediaHeader(
                    title = info.title,
                    subtitle = info.uploader,
                    details = info.viewCount?.let { viewCountLabel(it) },
                    thumbnailUrl = info.thumbnailUrl,
                    durationSeconds = info.durationSeconds,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }

            // El análisis completo sigue en marcha: ya se pueden descargar las opciones rápidas, y pronto llegan más calidades.
            if (state.loadingMoreQualities) {
                item {
                    Column(
                        modifier = Modifier.padding(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        LinearProgressIndicator(progress = { analysisProgress }, modifier = Modifier.fillMaxWidth())
                        Text(
                            text = stringResource(R.string.analysis_loading_more, AnalysisProgress.percent(analysisProgress)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Botón de descarga rápida con la calidad que el usuario eligió por defecto en Ajustes.
            state.quickDefault?.let { quick ->
                item {
                    FilledTonalButton(
                        onClick = onQuickDownload,
                        enabled = !state.isSubmitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    ) {
                        Icon(LibreIcons.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(
                                R.string.analysis_quick_download,
                                when (quick) {
                                    is QuickDefault.Video -> quick.quality.label()
                                    is QuickDefault.Audio -> quick.format.label()
                                },
                            ),
                        )
                    }
                }
            }

            // Solo se muestran las pestañas si hay más de un tipo (video y audio).
            if (tabs.size > 1) {
                item {
                    PrimaryTabRow(selectedTabIndex = tabs.indexOf(state.tab).coerceAtLeast(0)) {
                        tabs.forEach { kind ->
                            Tab(
                                selected = state.tab == kind,
                                onClick = { onTabSelected(kind) },
                                text = {
                                    Text(
                                        stringResource(
                                            if (kind == MediaKind.VIDEO) R.string.analysis_tab_video else R.string.analysis_tab_audio,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
            }

            // Contenido según la pestaña elegida.
            when (state.tab) {
                MediaKind.VIDEO -> {
                    // Una fila por cada calidad de video disponible.
                    items(info.videoOptions, key = { "v-${it.formatId}" }) { option ->
                        OptionRow(
                            label = option.qualityLabel(),
                            badge = option.container.name,
                            trailing = estimatedSizeLabel(option.estimatedBytes),
                            selected = option == state.selectedVideo,
                            onClick = { onVideoSelected(option) },
                        )
                    }
                    if (info.subtitles.isNotEmpty()) {
                        item {
                            SubtitleSection(
                                tracks = info.subtitles,
                                selected = state.subtitle,
                                embed = state.embedSubtitle,
                                onSelected = onSubtitleSelected,
                                onEmbedChange = onEmbedSubtitleChange,
                            )
                        }
                    }
                }

                MediaKind.AUDIO -> {
                    // Una fila por cada formato de audio disponible.
                    items(info.audioOptions, key = { "a-${it.format}" }) { option ->
                        OptionRow(
                            label = option.format.label(),
                            trailing = estimatedSizeLabel(option.estimatedBytes),
                            selected = option.format == state.selectedAudio,
                            onClick = { onAudioSelected(option.format) },
                        )
                    }
                    item {
                        Text(
                            text = stringResource(R.string.analysis_audio_metadata_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }

                MediaKind.IMAGE -> Unit // El análisis solo ofrece video y audio.
            }

        }

        // Parte fija de abajo: avisos y botón Descargar.
        HorizontalDivider()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            submitError?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            // Aviso en rojo si el teléfono casi no tiene espacio.
            if (state.lowStorage) {
                Text(
                    text = stringResource(R.string.analysis_low_storage, estimatedSizeLabel(state.availableBytes)),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            // Botón principal; se desactiva si todavía no se puede descargar.
            Button(
                onClick = onDownload,
                enabled = state.canDownload,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) {
                Icon(LibreIcons.Downloads, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                val size = state.selectedBytes
                Text(
                    if (size != null && size > 0) {
                        stringResource(R.string.analysis_download_with_size, estimatedSizeLabel(size))
                    } else {
                        stringResource(R.string.analysis_download)
                    },
                )
            }
        }
    }
}

/** Sección de subtítulos: elegir idioma (o ninguno) y decidir si se incrustan en el video. */
@Composable
private fun SubtitleSection(
    tracks: List<SubtitleTrack>,
    selected: SubtitleTrack?,
    embed: Boolean,
    onSelected: (SubtitleTrack?) -> Unit,
    onEmbedChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.analysis_subtitles),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        FlowRow(
            modifier = Modifier.padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selected == null,
                onClick = { onSelected(null) },
                label = { Text(stringResource(R.string.analysis_subtitles_none)) },
            )
            tracks.forEach { track ->
                FilterChip(
                    selected = track == selected,
                    onClick = { onSelected(track) },
                    label = { Text(track.displayName()) },
                )
            }
        }
        if (selected != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = embed, role = Role.Switch, onValueChange = onEmbedChange)
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.analysis_subtitles_embed), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = stringResource(
                            if (embed) R.string.analysis_subtitles_embed_on else R.string.analysis_subtitles_embed_off,
                            AppConfig.DOWNLOAD_FOLDER_NAME,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = embed, onCheckedChange = null)
            }
        }
    }
}

/** Nombre legible del subtítulo, por ejemplo "Español" o "Español (automático)". */
@Composable
private fun SubtitleTrack.displayName(): String {
    val code = languageCode.removeSuffix("-orig")
    val locale = LocalConfiguration.current.locales[0]
    val language = name ?: Locale.forLanguageTag(code).getDisplayLanguage(locale)
        .replaceFirstChar { it.titlecase(locale) }
        .ifBlank { code }
    return if (isAutomatic) stringResource(R.string.analysis_subtitles_automatic, language) else language
}
