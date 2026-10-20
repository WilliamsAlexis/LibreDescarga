plugins {
    alias(libs.plugins.libredescarga.android.library)
    alias(libs.plugins.libredescarga.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.libredescarga.core.network"
}

dependencies {
    api(projects.core.domain)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    // OkHttp solo se usa para consultar GitHub Releases (actualizaciones y lista negra).
    implementation(libs.okhttp)
}
