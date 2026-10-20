package com.libredescarga.feature.home

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.libredescarga.core.designsystem.icon.LibreIcons
import com.libredescarga.core.model.MediaEntry
import com.libredescarga.core.model.MediaError
import com.libredescarga.core.model.SearchProvider
import com.libredescarga.core.model.Site

data class HomeUiState(
    val query: String = "",
    val suggestions: List<String> = emptyList(),
    val provider: SearchProvider = SearchProvider.YOUTUBE,
    val sites: List<Site> = emptyList(),
    val search: SearchState = SearchState.Idle,
    val clipboardUrl: String? = null,
    val clipboardDetectionEnabled: Boolean = true,
    val analysisUrl: String? = null,
    val quickDownloadAvailable: Boolean = false,
    val message: HomeMessage? = null,
)

sealed interface SearchState {
    data object Idle : SearchState
    data class Loading(val query: String) : SearchState
    data class Results(val query: String, val items: List<MediaEntry>) : SearchState
    data class Failed(val query: String, val error: MediaError) : SearchState
}

sealed interface HomeMessage {
    data class Queued(val count: Int) : HomeMessage
    data object ForbiddenLink : HomeMessage
    data object InvalidSite : HomeMessage
    data object NoBrowser : HomeMessage
    data class Error(val error: MediaError) : HomeMessage
}

sealed interface HomeEvent {
    data class OpenSite(val url: String) : HomeEvent
}

/** Accesos rápidos a categorías: abren una búsqueda (sin contenido patrocinado). */
enum class HomeCategory(
    @param:StringRes val labelRes: Int,
    @param:StringRes val queryRes: Int,
    val icon: ImageVector,
) {
    MUSIC(R.string.home_category_music, R.string.home_category_music_query, LibreIcons.Music),
    MOVIES(R.string.home_category_movies, R.string.home_category_movies_query, LibreIcons.Movies),
    NEWS(R.string.home_category_news, R.string.home_category_news_query, LibreIcons.News),
    SPORTS(R.string.home_category_sports, R.string.home_category_sports_query, LibreIcons.Sports),
    GAMING(R.string.home_category_gaming, R.string.home_category_gaming_query, LibreIcons.Gaming),
}
