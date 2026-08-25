plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

dependencies {
    implementation(project(":core"))
}

application {
    mainClass.set("com.solisium.cli.MainKt")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

kotlin {
    jvmToolchain(17)
}
