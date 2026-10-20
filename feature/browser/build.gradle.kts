plugins {
    alias(libs.plugins.libredescarga.android.feature)
}

android {
    namespace = "com.libredescarga.feature.browser"
}

dependencies {
    implementation(projects.core.security)
    implementation(libs.androidx.webkit)
}
