import com.libredescarga.buildlogic.lib
import com.libredescarga.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

/**
 * Configuración común de los módulos :feature:* (capa UI):
 * Compose + Hilt + rutas de navegación tipadas (kotlinx.serialization).
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("libredescarga.android.library")
            pluginManager.apply("libredescarga.android.library.compose")
            pluginManager.apply("libredescarga.hilt")
            pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

            dependencies {
                add("implementation", project(":core:config"))
                add("implementation", project(":core:model"))
                add("implementation", project(":core:domain"))
                add("implementation", project(":core:designsystem"))
                add("implementation", project(":core:ui"))

                add("implementation", libs.lib("androidx-activity-compose"))
                add("implementation", libs.lib("androidx-compose-material3"))
                add("implementation", libs.lib("androidx-compose-material-iconsExtended"))
                add("implementation", libs.lib("androidx-hilt-lifecycle-viewmodel-compose"))
                add("implementation", libs.lib("androidx-lifecycle-runtime-compose"))
                add("implementation", libs.lib("androidx-lifecycle-viewmodel-compose"))
                add("implementation", libs.lib("androidx-navigation-compose"))
                add("implementation", libs.lib("kotlinx-serialization-json"))

                add("testImplementation", project(":core:testing"))
                add("testImplementation", libs.lib("turbine"))
                add("androidTestImplementation", project(":core:testing"))
            }
        }
    }
}
