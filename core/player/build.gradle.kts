plugins {
    alias(libs.plugins.libredescarga.android.library)
    alias(libs.plugins.libredescarga.hilt)
}

android {
    namespace = "com.libredescarga.core.player"
}

dependencies {
    api(projects.core.domain)
    implementation(projects.core.vault)
    api(libs.media3.exoplayer)
    api(libs.media3.session)
    implementation(libs.androidx.concurrent.futures.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
