package com.libredescarga.core.model

/** Un APK descargable de una versión nueva. */
data class ApkAsset(
    /** Arquitectura (por ejemplo "arm64-v8a"), o null si es el APK universal. */
    val abi: String?,
    val url: String,
    /** SHA-256 en hexadecimal (64 caracteres). */
    val sha256: String,
)

data class AppUpdate(
    val versionCode: Long,
    val versionName: String,
    val notes: String,
    val minSdk: Int,
    val assets: List<ApkAsset>,
) {
    /** El APK que corresponde a este teléfono: primero la arquitectura más específica, y si no, el universal. */
    fun assetFor(supportedAbis: List<String>): ApkAsset? =
        supportedAbis.firstNotNullOfOrNull { abi -> assets.firstOrNull { it.abi == abi } }
            ?: assets.firstOrNull { it.abi == null }
}

sealed interface UpdateCheck {
    data object UpToDate : UpdateCheck
    data class Available(val update: AppUpdate) : UpdateCheck

    /** La versión nueva necesita un Android más reciente que el de este teléfono. */
    data class Unsupported(val update: AppUpdate) : UpdateCheck
}

/** Por qué falló buscar, descargar o instalar una actualización. */
enum class UpdateFailure { NETWORK, INVALID_MANIFEST, NO_COMPATIBLE_APK, HASH_MISMATCH, SIGNATURE_MISMATCH, STORAGE, UNKNOWN }

class UpdateException(val failure: UpdateFailure, message: String? = null, cause: Throwable? = null) :
    Exception(message ?: failure.name, cause)

/** Qué pasó al pedir instalar el APK ya verificado. */
enum class InstallOutcome {
    /** Android mostró su pantalla de instalación. */
    STARTED,

    /** Falta permitir "instalar apps desconocidas" para LibreDescarga; se abrió esa pantalla de Android. */
    NEEDS_PERMISSION,
}

enum class EngineUpdateOutcome { UPDATED, ALREADY_UP_TO_DATE }

/** Resultado de actualizar la lista de sitios bloqueados. */
sealed interface BlocklistRefresh {
    data class Updated(val version: Int, val domains: Int) : BlocklistRefresh
    data object AlreadyCurrent : BlocklistRefresh
}
