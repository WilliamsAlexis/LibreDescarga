pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "LibreDescarga"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")

include(":core:config")
include(":core:model")
include(":core:domain")
include(":core:data")
include(":core:datastore")
include(":core:database")
include(":core:security")
include(":core:engine")
include(":core:downloader")
include(":core:media")
include(":core:player")
include(":core:vault")
include(":core:network")
include(":core:designsystem")
include(":core:ui")
include(":core:testing")

include(":feature:onboarding")
include(":feature:analysis")
include(":feature:home")
include(":feature:downloads")
include(":feature:library")
include(":feature:player")
include(":feature:browser")
include(":feature:vault")
include(":feature:settings")
