import com.android.build.api.dsl.ApplicationExtension
import com.libredescarga.buildlogic.PROJECT_JAVA_VERSION
import com.libredescarga.buildlogic.configureKotlinAndroid
import com.libredescarga.buildlogic.intVersion
import com.libredescarga.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Configuración común para la app principal: versiones de Android, Java y pruebas. */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            // Activa el plugin oficial de Android para aplicaciones.
            pluginManager.apply("com.android.application")

            extensions.configure<ApplicationExtension> {
                // Versiones mínima, objetivo y de compilación de Android; vienen de libs.versions.toml.
                compileSdk = libs.intVersion("compileSdk")
                defaultConfig {
                    minSdk = libs.intVersion("minSdk")
                    targetSdk = libs.intVersion("targetSdk")
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                    // Permite usar iconos vectoriales también en teléfonos antiguos.
                    vectorDrawables.useSupportLibrary = true
                }
                compileOptions {
                    sourceCompatibility = PROJECT_JAVA_VERSION
                    targetCompatibility = PROJECT_JAVA_VERSION
                }
            }
            // Aplica la configuración común de Kotlin.
            configureKotlinAndroid()
        }
    }
}
