import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm("desktop")

    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }

    // `expect class Speaker`/`PdfExtractor` are the sanctioned platform boundaries;
    // silence the "expect/actual classes are in Beta" advisory for them.
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.pdfbox)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.pdfbox.android)
            }
        }
    }
}

android {
    namespace = "studio.sparkcube.cadence.shared"
    compileSdk = 35
    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

compose.desktop {
    application {
        mainClass = "studio.sparkcube.cadence.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "Cadence"
            packageVersion = "1.0.0"
        }
    }
}
