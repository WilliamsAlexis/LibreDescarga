plugins {
    alias(libs.plugins.libredescarga.android.library)
    alias(libs.plugins.libredescarga.hilt)
}

android {
    namespace = "com.libredescarga.core.media"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.security)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
