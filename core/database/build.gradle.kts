plugins {
    alias(libs.plugins.libredescarga.android.library)
    alias(libs.plugins.libredescarga.android.room)
    alias(libs.plugins.libredescarga.hilt)
}

android {
    namespace = "com.libredescarga.core.database"
}

dependencies {
    api(projects.core.model)
    implementation(libs.kotlinx.coroutines.android)
}
