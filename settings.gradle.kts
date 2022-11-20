rootProject.name = "ksp-sample"
pluginManagement {
    plugins {
        id("com.google.devtools.ksp") version "1.6.20-1.0.5"
        kotlin("jvm") version "1.7.20"
    }
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

include("annotation")
include("processor")
