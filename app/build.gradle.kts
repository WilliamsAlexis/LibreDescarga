import java.util.Properties

plugins {
    alias(libs.plugins.libredescarga.android.application)
    alias(libs.plugins.libredescarga.android.application.compose)
    alias(libs.plugins.libredescarga.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// ---------------------------------------------------------------------------
// Identidad de la app: el paquete se cambia SOLO aquÃ­.
// (El nombre visible se cambia en src/main/res/values/strings.xml â†’ app_name.)
// ---------------------------------------------------------------------------
val appPackage = "com.libredescarga.app"
val appVersionCode = 6
val appVersionName = "1.0.5"

// Datos de firma, fuera del control de versiones (ver keystore.properties.example).
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val hasReleaseKeystore = keystoreProperties.getProperty("storeFile") != null

android {
    namespace = "com.libredescarga.app"

    defaultConfig {
        applicationId = appPackage
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    // NewPipe Extractor usa APIs de Java (java.time, java.nio...) que Android anterior a 13 no trae.
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // youtubedl-android ejecuta Python y FFmpeg desde las librerÃ­as nativas
        // extraÃ­das: deben instalarse descomprimidas en el telÃ©fono.
        jniLibs {
            useLegacyPackaging = true
        }
    }

    // Un APK por arquitectura (mÃ¡s ligero) y uno universal para quien no sepa cuÃ¡l elegir.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            isUniversalApk = true
        }
    }
}

dependencies {
    implementation(projects.core.config)
    implementation(projects.core.model)
    implementation(projects.core.domain)
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(projects.core.engine)
    implementation(projects.core.downloader)
    implementation(projects.core.player)
    implementation(projects.core.vault)
    implementation(projects.core.network)
    implementation(projects.core.security)
    implementation(projects.core.ui)

    implementation(projects.feature.onboarding)
    implementation(projects.feature.analysis)
    implementation(projects.feature.browser)
    implementation(projects.feature.home)
    implementation(projects.feature.downloads)
    implementation(projects.feature.library)
    implementation(projects.feature.player)
    implementation(projects.feature.vault)
    implementation(projects.feature.settings)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.navigationSuite)
    implementation(libs.androidx.work.runtime)
    // Alinea la versiÃ³n que traen WorkManager y las librerÃ­as de pruebas.
    implementation(libs.androidx.concurrent.futures.ktx)
    implementation(libs.androidx.hilt.work)
    implementation(libs.kotlinx.coroutines.android)
    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)

    testImplementation(projects.core.testing)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
