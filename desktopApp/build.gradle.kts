import org.gradle.jvm.tasks.Jar
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

val appVersion = "0.1.0"

compose.desktop {
    application {
        mainClass = "com.solisium.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "Solisium Autopilot"
            packageVersion = appVersion
            description = "Read-only Throne and Liberty companion"
            vendor = "Solisium"
            copyright = "Solisium"
            // A hand-picked module list saves perhaps 40 MB and risks a
            // NoClassDefFoundError that only shows up on a user's machine, in a code
            // path nobody exercised. Ship the whole runtime instead.
            includeAllModules = true
            windows {
                menu = true
                menuGroup = "Solisium"
                perUserInstall = true
                dirChooser = true
                shortcut = true
                // Stable so upgrades replace rather than stack installs.
                upgradeUuid = "6f0a2c1e-4d3b-4a52-9c71-8e5b0f2a7d34"
                val icon = rootProject.file("desktopApp/icon/solisium.ico")
                if (icon.isFile) iconFile.set(icon)
            }
            appResourcesRootDir.set(layout.projectDirectory.dir("appResources"))
        }
    }
}

// The database lives next to the repo root, matching the CLI.
tasks.withType<JavaExec>().configureEach {
    workingDir = rootProject.projectDir
}

// ---------------------------------------------------------------------------
// Shipping guard: nothing distributed may contain a key.
//
// The runtime rules already keep keys in %LOCALAPPDATA%\Solisium, outside both the
// source tree and the install directory. This exists because "by design" is not a
// guarantee: a stray aes.txt in the project, or a key pasted into a resource, would
// otherwise be packaged and handed out silently. Packaging depends on this check, so
// that mistake fails the build instead.
// ---------------------------------------------------------------------------

val forbiddenFileNames = listOf("secrets.properties", "aes.txt", "aes.key", ".env")
val forbiddenExtensions = listOf("key", "pem", "pfx", "p12")

// A 32-byte key and a SHA-256 hash are the same shape, so scanning for bare hex would
// flag every checksum in every manifest. Only hex under a key-like field name counts,
// which mirrors the runtime detection in AesKey.
val labelledKeyPattern =
    Regex("""(?<label>[A-Za-z0-9_.\-]{1,48})["']?\s*[:=]\s*["']?\s*(?:0[xX])?(?<key>[0-9a-fA-F]{64})(?![0-9a-fA-F])""")
val keyLabelAllow = listOf("aes", "key", "secret", "encryption", "crypt", "cipher")
val keyLabelDeny = listOf(
    "hash", "sha", "digest", "checksum", "crc", "guid", "uuid",
    "signature", "sign", "etag", "blake", "md5", "public", "pub",
)
val scannableExtensions = listOf(
    "txt", "json", "ini", "cfg", "conf", "properties", "yaml", "yml",
    "kt", "kts", "java", "md", "xml", "sq", "sqm", "bat", "ps1", "cmd", "sh",
)

fun labelMeansKey(label: String): Boolean {
    val normalized = label.lowercase().trim('"', '\'', ' ', ':', '=', ',', '{', '[', '-', '_', '.')
    if (normalized.isEmpty()) return false
    if (keyLabelDeny.any { normalized.contains(it) }) return false
    return keyLabelAllow.any { normalized.contains(it) }
}

/**
 * Test fixtures need key-shaped constants to test key handling at all. Rather than
 * exempting whole directories, a file declares its fixtures with this marker, so the
 * exemption is visible in review and a real key pasted anywhere else still fails.
 */
val fixtureMarker = "secret-scan-allow-fixture"

fun findSecrets(root: File, label: String): List<String> {
    if (!root.exists()) return emptyList()
    val problems = mutableListOf<String>()
    root.walkTopDown().forEach { file ->
        if (!file.isFile) return@forEach
        val name = file.name.lowercase()
        val extension = name.substringAfterLast('.', "")
        if (name in forbiddenFileNames || extension in forbiddenExtensions) {
            problems.add("$label: forbidden file ${file.relativeTo(root)}")
            return@forEach
        }
        if (extension !in scannableExtensions) return@forEach
        if (file.length() > 2L * 1024 * 1024) return@forEach
        val text = runCatching { file.readText() }.getOrNull() ?: return@forEach
        if (text.contains(fixtureMarker)) return@forEach
        for (match in labelledKeyPattern.findAll(text)) {
            val field = match.groups["label"]?.value?.trim() ?: continue
            if (!labelMeansKey(field)) continue
            // Report where and under what name, never the value itself.
            problems.add("$label: ${file.relativeTo(root)} holds a key-shaped value under \"$field\"")
        }
    }
    return problems
}

val verifyNoSecretsInSource by tasks.registering {
    group = "verification"
    description = "Fails if a key or credential file is present in the project sources."
    doLast {
        val roots = listOf("core/src", "cli/src", "desktopApp/src", "androidApp/src", "examples", "docs")
        val problems = roots.flatMap { findSecrets(rootProject.file(it), "source") }
        if (problems.isNotEmpty()) {
            throw GradleException("refusing to build: possible secrets in the project\n" + problems.joinToString("\n"))
        }
        logger.lifecycle("secret scan: project sources clean")
    }
}

val verifyNoSecretsInDistribution by tasks.registering {
    group = "verification"
    description = "Fails if the built application image contains a key or credential file."
    dependsOn("createDistributable")
    doLast {
        val image = layout.buildDirectory.dir("compose/binaries/main/app").get().asFile
        val problems = findSecrets(image, "distribution")
        if (problems.isNotEmpty()) {
            throw GradleException(
                "refusing to distribute: possible secrets in the app image\n" + problems.joinToString("\n"),
            )
        }
        logger.lifecycle("secret scan: application image clean")
    }
}

// The Compose plugin registers its packaging tasks late, so these have to be matched
// rather than looked up by name at configuration time.
tasks.matching { it.name == "createDistributable" }
    .configureEach { dependsOn(verifyNoSecretsInSource) }
tasks.matching { it.name == "packageMsi" || it.name == "packageDistributionForCurrentOS" }
    .configureEach { dependsOn(verifyNoSecretsInDistribution) }

val starterOutputDir = layout.projectDirectory.dir("appResources/windows/starter")

val buildStarterPack by tasks.registering(JavaExec::class) {
    group = "distribution"
    description = "Builds the bundled starter database and warehouse for first-run."
    val core = project(":core")
    dependsOn(core.tasks.named("jvmJar"))
    classpath(
        core.tasks.named<Jar>("jvmJar").flatMap { it.archiveFile },
        core.configurations.named("jvmRuntimeClasspath"),
    )
    mainClass.set("com.solisium.core.bootstrap.StarterPackBuilderMainKt")
    args(starterOutputDir.asFile.absolutePath)
    doFirst {
        starterOutputDir.asFile.mkdirs()
    }
}

tasks.matching { it.name == "createDistributable" }.configureEach { dependsOn(buildStarterPack) }
tasks.matching { it.name == "run" }.configureEach { dependsOn(buildStarterPack) }

/**
 * The MSI, zipped for handing over. Both this and the portable build below bundle a
 * Java runtime, so neither needs a JDK on the target machine.
 */
val packageInstallerZip by tasks.registering(Zip::class) {
    group = "distribution"
    description = "Zips the MSI installer."
    dependsOn("packageMsi")
    archiveFileName.set("Solisium-Autopilot-$appVersion-windows-x64-installer.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from(layout.buildDirectory.dir("compose/binaries/main/msi")) {
        include("*.msi")
    }
    from(rootProject.file("packaging")) {
        include("README-INSTALL.txt")
    }
}

/**
 * A portable alternative for anyone who would rather not run an installer: the same
 * app image plus a per-user script that copies it into place and makes shortcuts.
 * Installs and uninstalls without administrator rights.
 */
val packagePortableZip by tasks.registering(Zip::class) {
    group = "distribution"
    description = "Zips the self-contained application image with its per-user installer script."
    dependsOn(verifyNoSecretsInDistribution)
    archiveFileName.set("Solisium-Autopilot-$appVersion-windows-x64-portable.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    // This directory already contains a folder named after the app, so copying its
    // contents puts "Solisium Autopilot/" at the zip root, which is the layout the
    // installer script expects.
    from(layout.buildDirectory.dir("compose/binaries/main/app"))
    from(rootProject.file("packaging")) {
        include("install.cmd", "Install-Solisium.ps1", "README-INSTALL.txt")
    }
}

tasks.register("packageRelease") {
    group = "distribution"
    description = "Builds both Windows distributions and their zips."
    dependsOn(packageInstallerZip, packagePortableZip)
}
