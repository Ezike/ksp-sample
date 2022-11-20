plugins {
    kotlin("jvm") version "1.7.20"
    id("com.google.devtools.ksp") version "1.7.20-1.0.8"
    idea
}

buildscript {
    dependencies {
        classpath(kotlin("gradle-plugin", version = "1.7.20"))
    }
}

ksp {
    arg("ignoreGenericArgs", "false")
}

idea {
    module {
        // Not using += due to https://github.com/gradle/gradle/issues/8749
        sourceDirs = sourceDirs + file("build/generated/ksp/main/kotlin") // or tasks["kspKotlin"].destination
        testSourceDirs = testSourceDirs + file("build/generated/ksp/test/kotlin")
        generatedSourceDirs =
            generatedSourceDirs + file("build/generated/ksp/main/kotlin") + file("build/generated/ksp/test/kotlin")

    }
}

kotlin
dependencies {
    implementation(project(":annotation"))
    ksp(project(":processor"))
}

