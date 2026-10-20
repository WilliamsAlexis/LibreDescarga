plugins {
    alias(libs.plugins.libredescarga.android.library)
    alias(libs.plugins.libredescarga.android.library.compose)
}

android {
    namespace = "com.libredescarga.core.designsystem"
}

dependencies {
    api(projects.core.model)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.iconsExtended)
    api(libs.androidx.compose.ui)
}
