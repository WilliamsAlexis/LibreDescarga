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
}
