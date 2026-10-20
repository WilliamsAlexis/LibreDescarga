package com.libredescarga.buildlogic

import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Dependencias comunes de Compose para apps y librerías. */
internal fun Project.configureCompose() {
    dependencies {
        val bom = libs.lib("androidx-compose-bom")
        add("implementation", platform(bom))
        add("androidTestImplementation", platform(bom))
        add("implementation", libs.lib("androidx-compose-ui-tooling-preview"))
        add("debugImplementation", libs.lib("androidx-compose-ui-tooling"))
        add("androidTestImplementation", libs.lib("androidx-compose-ui-test-junit4"))
        add("debugImplementation", libs.lib("androidx-compose-ui-test-manifest"))
    }
}
