import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    // Phase 0: desktop JVM only (macOS `say` TTS). Android/iOS targets are added
    // in Phase 1. The pacing engine in commonMain is target-independent, so the
    // Phase 0 gate is fully reachable with just this target.
    jvm("desktop")

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
            }
        }
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
