import com.android.build.api.dsl.LibraryExtension
import com.libredescarga.buildlogic.PROJECT_JAVA_VERSION
import com.libredescarga.buildlogic.configureKotlinAndroid
import com.libredescarga.buildlogic.intVersion
import com.libredescarga.buildlogic.lib
import com.libredescarga.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Configuración común de todos los módulos de tipo "biblioteca" (los que no son la app principal).
 * Así no hay que repetir las mismas líneas en cada módulo: versión de Android, Java y pruebas.
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            // Activa el plugin oficial de Android para bibliotecas.
            pluginManager.apply("com.android.library")

            extensions.configure<LibraryExtension> {
                // Versiones de Android que usa el módulo; se leen del archivo libs.versions.toml.
                compileSdk = libs.intVersion("compileSdk")
                defaultConfig {
                    minSdk = libs.intVersion("minSdk")
                    // Herramienta que ejecuta las pruebas dentro de un teléfono o emulador.
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
                // Versión de Java con la que se compila.
                compileOptions {
                    sourceCompatibility = PROJECT_JAVA_VERSION
                    targetCompatibility = PROJECT_JAVA_VERSION
                }
                testOptions {
                    // En las pruebas rápidas, las funciones de Android devuelven valores vacíos en vez de fallar.
                    unitTests.isReturnDefaultValues = true
                }
            }
            // Aplica la configuración común de Kotlin.
            configureKotlinAndroid()

            // Herramientas de prueba que llevan todos los módulos.
            dependencies {
                add("testImplementation", libs.lib("junit"))
                add("testImplementation", libs.lib("kotlinx-coroutines-test"))
                add("androidTestImplementation", libs.lib("androidx-test-ext-junit"))
                add("androidTestImplementation", libs.lib("androidx-test-runner"))
            }
        }
    }
}
