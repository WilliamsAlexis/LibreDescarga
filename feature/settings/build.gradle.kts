plugins {
    alias(libs.plugins.libredescarga.android.feature)
}

android {
    namespace = "com.libredescarga.feature.settings"
}

dependencies {
    implementation(projects.core.security)
}
