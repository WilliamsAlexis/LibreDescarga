plugins {
    alias(libs.plugins.libredescarga.android.library)
    alias(libs.plugins.libredescarga.android.library.compose)
}

android {
    namespace = "com.libredescarga.core.ui"
}

dependencies {
    api(projects.core.designsystem)
    api(projects.core.model)
    implementation(projects.core.security)
    api(libs.coil.compose)
    // Cargador de red de Coil (miniaturas). Se registra solo, sin configuración.
    implementation(libs.coil.network.okhttp)
}
