plugins {
    alias(libs.plugins.libredescarga.android.feature)
}

android {
    namespace = "com.libredescarga.feature.player"
}

dependencies {
    implementation(projects.core.player)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
}
