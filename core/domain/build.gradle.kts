plugins {
    alias(libs.plugins.libredescarga.jvm.library)
}

dependencies {
    api(projects.core.model)
    api(projects.core.config)
    api(projects.core.security)
    api(libs.kotlinx.coroutines.core)
    api(libs.javax.inject)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(projects.core.testing)
}
