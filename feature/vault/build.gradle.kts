plugins {
    alias(libs.plugins.libredescarga.android.feature)
}

android {
    namespace = "com.libredescarga.feature.vault"
}

dependencies {
    implementation(projects.core.security)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.appcompat)
}
