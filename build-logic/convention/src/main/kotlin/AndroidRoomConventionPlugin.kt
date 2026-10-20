import androidx.room.gradle.RoomExtension
import com.libredescarga.buildlogic.lib
import com.libredescarga.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("androidx.room")
            pluginManager.apply("com.google.devtools.ksp")

            extensions.configure<RoomExtension> {
                // Los esquemas exportados permiten probar las migraciones.
                schemaDirectory("$projectDir/schemas")
            }

            dependencies {
                add("implementation", libs.lib("room-runtime"))
                add("implementation", libs.lib("room-ktx"))
                add("ksp", libs.lib("room-compiler"))
                add("androidTestImplementation", libs.lib("room-testing"))
            }
        }
    }
}
