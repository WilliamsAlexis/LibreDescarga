plugins {
    alias(libs.plugins.libredescarga.android.library)
    alias(libs.plugins.libredescarga.hilt)
}

android {
    namespace = "com.libredescarga.core.engine"
}

dependencies {
    api(projects.core.model)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // yt-dlp + Python + QuickJS (library) y FFmpeg (unir, convertir, recortar, metadatos).
    implementation(libs.youtubedl.library)
    implementation(libs.youtubedl.ffmpeg)

    // Vía rápida para YouTube y YouTube Music: extrae los formatos en Java, sin lanzar Python ni resolver el JavaScript
    // con QuickJS (~2–4 s en vez de ~17 s). yt-dlp sigue como respaldo si falla.
    implementation(libs.newpipe.extractor)
    implementation(libs.okhttp)
}
