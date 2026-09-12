plugins {
    java
    application
}

dependencies {
    // The 3D client, for an angled camera over a world of solid shapes. No models
    // are bound, so every creature renders as a coloured primitive — which is the
    // point at this stage: the shapes are the game, not a stand-in for art.
    implementation(project(":client3d"))
}

application {
    mainClass.set("uz.duke.dungeon.Main")
}

// ---------------------------------------------------------------------------
// Cuts the icon sheets in dungeon/art/icons into one file per icon:
//
//   ./gradlew :dungeon:cutIcons
//
// Here rather than done by hand for the reason the example stage is: twenty-two
// crops nobody can repeat are twenty-two crops nobody dares change. The sheets
// themselves are kept beside the tool, outside resources, so they travel with
// the repository without travelling inside the game.
// ---------------------------------------------------------------------------
// ---------------------------------------------------------------------------
// Turns the .fbx animations in dungeon/art/anim into .glb libraries the game can
// read:
//
//   ./gradlew :dungeon:convertAnimations
//   ./gradlew :dungeon:convertAnimations -PblenderPath="D:/apps/blender.exe"
//
// jME reads glTF and nothing else, and nothing else in this repository reads
// FBX — so the conversion is Blender's, run headless. Kept as a task for the
// reason the icon cut is: a step nobody can repeat is a step nobody dares
// change. See dungeon/art/anim/fbx_to_glb.py for what it does on the way.
//
// Blender is not a build dependency. This task is run by hand when an animation
// arrives, and the build does not depend on it: what ships is the .glb it wrote,
// which is committed.
// ---------------------------------------------------------------------------
private val animations = listOf(
    // source (under art/anim), output (under resources), clip name
    Triple("magic_area_attack.fbx", "magic.glb", "Magic_Area_Attack"),
)

tasks.register("convertAnimations") {
    group = "application"
    description = "Convert the .fbx animations into .glb libraries, using Blender"
    doLast {
        // Where it actually is, checked rather than hoped for: a name on the PATH
        // that is not there fails inside the process launcher, which reports
        // "a problem occurred starting process" and not one word about Blender.
        val onThePath = System.getenv("PATH").orEmpty()
            .split(File.pathSeparator)
            .flatMap { listOf(File(it, "blender"), File(it, "blender.exe")) }
            .firstOrNull { it.canExecute() }?.absolutePath
        val blender = (findProperty("blenderPath") as String?)
            ?: onThePath
            ?: listOf(
                "C:/Program Files/Blender Foundation/Blender 5.2/blender.exe",
                "C:/Program Files/Blender Foundation/Blender 4.2/blender.exe",
                "/Applications/Blender.app/Contents/MacOS/Blender",
                "/usr/bin/blender",
            ).firstOrNull { File(it).exists() }
            ?: throw GradleException(
                "Blender was not found on the PATH or where it is usually installed. " +
                    "Install it, or point at it: " +
                    "./gradlew :dungeon:convertAnimations -PblenderPath=\"...\""
            )
        animations.forEach { (from, into, clip) ->
            providers.exec {
                workingDir = rootProject.projectDir
                commandLine(
                    blender, "-b", "--factory-startup",
                    "-P", "dungeon/art/anim/fbx_to_glb.py", "--",
                    "dungeon/art/anim/$from",
                    "dungeon/src/main/resources/animations/characters/$into",
                    clip,
                )
            }.standardOutput.asText.get()
                .lines().filter { it.startsWith("CONVERTED") }
                .forEach { logger.lifecycle(it) }
        }
    }
}

tasks.register<JavaExec>("cutIcons") {
    group = "application"
    description = "Cut the icon sheets into the game's own icon folders"
    mainClass.set("uz.duke.dungeon.tools.IconSheets")
    classpath = sourceSets["main"].runtimeClasspath
    // From the repository root, so the paths in the Java are the paths you would
    // type -- the same reason :worldbuilder:writeExampleStage sets this.
    workingDir = rootProject.projectDir
}

// ---------------------------------------------------------------------------
// The installer, for the machine you are sitting at.
//
//   ./gradlew :dungeon:packageInstaller
//   ./gradlew :dungeon:packageInstaller -PinstallerVersion=1.2.0
//
// The same thing the release workflow builds, by the same route and with the
// same flags, so what comes out of a laptop and what comes out of CI are the
// same installer. Which is the whole reason it lives here rather than in a note
// somewhere: the two drift the moment they are written down twice.
//
// jpackage builds only for the operating system it is standing on, so this makes
// an .msi on Windows, a .dmg on macOS and a .deb on Linux — never the other two.
// Each needs the platform's own tools: WiX 3 for the MSI, fakeroot for the .deb.
// Missing them, jpackage says so plainly and this stops.
// ---------------------------------------------------------------------------

private val osName = System.getProperty("os.name").lowercase()
private val onWindows = osName.contains("win")
private val onMac = osName.contains("mac")

tasks.register<Exec>("packageInstaller") {
    group = "distribution"
    description = "Native installer for this OS, carrying its own Java runtime"
    dependsOn("installDist")

    // The toolchain's jpackage rather than whatever is on the PATH. A jpackage
    // from an older JDK bundles an older runtime, which then refuses the classes
    // it was handed — and on Windows the windowed exe dies with exit 1 and not a
    // word of explanation.
    val jdkHome = javaToolchains.launcherFor(java.toolchain).get()
            .metadata.installationPath.asFile
    executable = File(jdkHome, "bin/jpackage" + if (onWindows) ".exe" else "").absolutePath

    val type = if (onWindows) "msi" else if (onMac) "dmg" else "deb"
    val lib = layout.buildDirectory.dir("install/dungeon/lib").get().asFile
    val out = layout.buildDirectory.dir("installer").get().asFile
    // Not the project's own 0.1.0-SNAPSHOT: jpackage takes one to three integers
    // and nothing else, and macOS additionally refuses a leading zero — the
    // version goes into the bundle's own metadata, where it is counted fields
    // rather than a name. Override it for a real build; this is only a default
    // that both of them will accept.
    val appVersion = (findProperty("installerVersion") as String?) ?: "1.0.0"

    doFirst { delete(out) }

    argumentProviders.add(CommandLineArgumentProvider {
        buildList {
            addAll(listOf(
                "--type", type,
                "--name", "Duke Dungeon",
                "--app-version", appVersion,
                "--vendor", "abdurasul29052002",
                "--description", "A dungeon crawler on the duke-engine",
                "--input", lib.absolutePath,
                "--main-jar", tasks.jar.get().archiveFileName.get(),
                "--main-class", "uz.duke.dungeon.Main",
                // Spelled out rather than left to the default: LWJGL reaches for
                // sun.misc.Unsafe, which lives in jdk.unsupported, and a runtime
                // built without it fails at the first frame rather than here.
                "--add-modules", "java.se,jdk.unsupported",
                "--java-options", "-Xmx2g",
                "--dest", out.absolutePath,
            ))
            when (type) {
                // Per-user, so nobody has to be an administrator to play a game.
                "msi" -> addAll(listOf("--win-dir-chooser", "--win-menu",
                        "--win-shortcut", "--win-per-user-install"))
                // GLFW wants the process's first thread on macOS, and there is no
                // command line to add that to afterwards.
                "dmg" -> addAll(listOf("--java-options", "-XstartOnFirstThread"))
                // dpkg will not have a capital or a space in a package name.
                "deb" -> addAll(listOf("--linux-package-name", "duke-dungeon",
                        "--linux-shortcut", "--linux-menu-group", "Game"))
            }
        }
    })

    doLast { logger.lifecycle("installer written to $out") }
}
