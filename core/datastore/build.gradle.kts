plugins {
    alias(libs.plugins.libredescarga.android.library)
    alias(libs.plugins.libredescarga.hilt)
}

android {
    namespace = "com.libredescarga.core.datastore"
}

dependencies {
    api(projects.core.model)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
}
