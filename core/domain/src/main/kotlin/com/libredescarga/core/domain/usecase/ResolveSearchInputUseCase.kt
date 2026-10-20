package com.libredescarga.core.domain.usecase

import com.libredescarga.core.security.UrlValidator
import javax.inject.Inject

sealed interface SearchInput {
    data object Empty : SearchInput
    data object Forbidden : SearchInput
    data class Link(val url: String) : SearchInput
    data class Keywords(val text: String) : SearchInput
}

/** La barra universal acepta un enlace (se analiza) o palabras clave (se buscan). */
class ResolveSearchInputUseCase @Inject constructor() {
    operator fun invoke(raw: String): SearchInput {
        val text = raw.trim()
        if (text.isEmpty()) return SearchInput.Empty
        return when (val result = UrlValidator.validate(text)) {
            is UrlValidator.Result.Valid -> SearchInput.Link(result.url)
            UrlValidator.Result.ForbiddenScheme -> SearchInput.Forbidden
            UrlValidator.Result.NotAUrl -> SearchInput.Keywords(text)
        }
    }
}
