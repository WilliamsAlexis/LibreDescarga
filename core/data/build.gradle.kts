plugins {
    alias(libs.plugins.libredescarga.android.library)
    alias(libs.plugins.libredescarga.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.libredescarga.core.data"
}

dependencies {
    api(projects.core.domain)
    implementation(projects.core.datastore)
    implementation(projects.core.database)
    implementation(projects.core.engine)
    implementation(projects.core.downloader)
    implementation(projects.core.media)
    implementation(projects.core.network)
    implementation(projects.core.security)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(projects.core.testing)
}
