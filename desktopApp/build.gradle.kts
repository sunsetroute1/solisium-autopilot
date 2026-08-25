import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.swing)
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}

compose.desktop {
    application {
        mainClass = "com.solisium.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "Solisium Autopilot"
            packageVersion = "0.1.0"
            description = "Read-only Throne and Liberty companion"
            windows {
                menu = true
                // Stable so upgrades replace rather than stack installs.
                upgradeUuid = "6f0a2c1e-4d3b-4a52-9c71-8e5b0f2a7d34"
            }
        }
    }
}

// The database lives next to the repo root, matching the CLI.
tasks.withType<JavaExec>().configureEach {
    workingDir = rootProject.projectDir
}
