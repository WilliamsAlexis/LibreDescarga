plugins {
    alias(libs.plugins.libredescarga.android.library)
    alias(libs.plugins.libredescarga.hilt)
}

android {
    namespace = "com.libredescarga.core.downloader"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.config)
    implementation(projects.core.domain)
    implementation(projects.core.database)
    implementation(projects.core.engine)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.kotlinx.coroutines.android)
}
