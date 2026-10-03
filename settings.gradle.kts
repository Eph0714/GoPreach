pluginManagement {
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
        // Native TomTom Maps SDK (Territory Assignment boundary preview) —
        // the "complete" flavor needs no credentials, see app/build.gradle.kts.
        maven { url = uri("https://repositories.tomtom.com/artifactory/maven") }
    }
}

rootProject.name = "GoPreach"
include(":app")
