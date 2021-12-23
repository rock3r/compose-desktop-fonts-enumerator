plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeDesktop)
}

group = "dev.sebastiano"
version = "0.0.1-SNAPSHOT"

kotlin {
    target {
        compilations.all {
            kotlinOptions {
                jvmTarget = "11"
                freeCompilerArgs = listOf("-Xopt-in=kotlin.RequiresOptIn")
            }
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(compose.desktop.currentOs) {
        exclude(group = "org.jetbrains.compose.material")
    }
    implementation(libs.jna)
}
