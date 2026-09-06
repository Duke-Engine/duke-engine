package uz.duke.studio.export;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;
import uz.duke.studio.model.GameFactory;
import uz.duke.studio.model.StudioProject;

/**
 * Exports a project as a standalone, cross-platform Gradle game: generated
 * {@code Main.java} calling the public engine API, the engine jars in
 * {@code libs/}, and a build script that runs on any OS with
 * {@code gradlew run} (or packages with {@code gradlew distZip}).
 */
public final class GameExporter {

    private GameExporter() {
    }

    /** Back-compat entry without a project assets folder. */
    public static String export(StudioProject project, Path targetDir, Path engineRoot) throws IOException {
        return export(project, targetDir, engineRoot, null);
    }

    /** Write the game project into {@code targetDir}. Returns a human summary. */
    public static String export(StudioProject project, Path targetDir, Path engineRoot, Path assetsRoot)
            throws IOException {
        Files.createDirectories(targetDir.resolve("src/main/java/game"));
        Files.createDirectories(targetDir.resolve("libs"));

        Files.writeString(targetDir.resolve("settings.gradle.kts"),
                "rootProject.name = \"" + sanitize(project.title) + "\"\n");
        Files.writeString(targetDir.resolve("build.gradle.kts"), buildScript(sanitize(project.title)));
        Files.writeString(targetDir.resolve("README.md"), readme(project.title));
        Files.writeString(targetDir.resolve("src/main/java/game/Main.java"), generateMain(project));

        // scripts ship as plain sources — the exported Gradle build compiles them
        if (!project.scripts.isEmpty()) {
            var scriptsDir = targetDir.resolve("src/main/java/game/scripts");
            Files.createDirectories(scriptsDir);
            for (var script : project.scripts) {
                Files.writeString(scriptsDir.resolve(script.name + ".java"), script.source);
            }
        }

        // assets go on the classpath, so the shipped game loads them natively
        int assetFiles = 0;
        if (assetsRoot != null && Files.isDirectory(assetsRoot)) {
            assetFiles = copyTree(assetsRoot, targetDir.resolve("src/main/resources"));
        }

        int jars = copyEngineJars(engineRoot, targetDir.resolve("libs"));
        boolean wrapper = copyWrapper(engineRoot, targetDir);

        var notes = new StringBuilder("Exported to " + targetDir + "\n");
        if (assetFiles > 0) {
            notes.append(assetFiles).append(" asset files bundled into src/main/resources/\n");
        }
        notes.append(jars > 0
                ? jars + " engine jars copied to libs/\n"
                : "WARNING: engine jars not found — run gradlew build in duke-engine first\n");
        notes.append(wrapper
                ? "Run:  gradlew run\nShip: gradlew packageApp  (native .exe/.app for THIS OS)\n"
                        + "      gradlew fatJar      (single runnable jar)\n"
                : "Gradle wrapper not copied — run with an installed Gradle: gradle run\n");
        return notes.toString();
    }

    private static String buildScript(String gameName) {
        return """
                plugins {
                    java
                    application
                }

                repositories {
                    mavenCentral()
                }

                val jmeVersion = "3.7.0-stable"

                dependencies {
                    implementation(fileTree("libs") { include("*.jar") })
                    implementation("org.jmonkeyengine:jme3-core:$jmeVersion")
                    implementation("org.jmonkeyengine:jme3-desktop:$jmeVersion")
                    implementation("org.jmonkeyengine:jme3-lwjgl3:$jmeVersion")
                    implementation("org.jmonkeyengine:jme3-plugins:$jmeVersion")
                    implementation("org.jmonkeyengine:jme3-jogg:$jmeVersion")
                    implementation("org.jmonkeyengine:jme3-testdata:$jmeVersion")
                }

                java {
                    toolchain {
                        languageVersion.set(JavaLanguageVersion.of(25))
                    }
                }

                application {
                    mainClass.set("game.Main")
                }

                // one self-contained jar with the game, engine and all libraries
                tasks.register<Jar>("fatJar") {
                    group = "distribution"
                    description = "Build a single runnable jar (java -jar game-all.jar)"
                    archiveFileName.set("game-all.jar")
                    destinationDirectory.set(layout.buildDirectory.dir("fat"))
                    manifest { attributes("Main-Class" to "game.Main") }
                    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "module-info.class")
                    from(sourceSets.main.get().output)
                    from(configurations.runtimeClasspath.get()
                            .filter { it.name.endsWith(".jar") }
                            .map { zipTree(it) })
                }

                // native package for THIS operating system (.exe app on Windows,
                // binary on Linux, .app on macOS). Uses the toolchain's own
                // jpackage so the bundled runtime always matches the classes.
                tasks.register<Exec>("packageApp") {
                    group = "distribution"
                    description = "Build a native app-image for the current OS"
                    dependsOn("fatJar")
                    val jdkHome = javaToolchains.launcherFor(java.toolchain)
                            .get().metadata.installationPath.asFile
                    val isWindows = System.getProperty("os.name").lowercase().contains("win")
                    doFirst { delete("build/package") }
                    executable = File(jdkHome, "bin/jpackage" + (if (isWindows) ".exe" else "")).absolutePath
                    args(
                        "--type", "app-image",
                        "--name", "%s",
                        "--input", "build/fat",
                        "--main-jar", "game-all.jar",
                        "--dest", "build/package"
                    )
                }
                """.formatted(gameName);
    }

    private static String readme(String title) {
        return """
                # %s

                Made with Duke Studio (duke-engine).

                ## Run
                    gradlew run

                ## Ship
                    gradlew fatJar       -> build/fat/game-all.jar    (java -jar game-all.jar)
                    gradlew packageApp   -> build/package/            (native app for THIS OS)
                    gradlew distZip      -> build/distributions/      (portable zip with launch scripts)

                jpackage builds for the operating system it runs on: run `gradlew packageApp`
                on Windows for the .exe app, on Linux for the Linux binary, and on macOS for
                the .app bundle. A JDK 25 with jpackage on the PATH is required.
                """.formatted(title);
    }

    private static String generateMain(StudioProject project) {
        var code = new StringBuilder();
        code.append("""
                package game;

                import java.awt.Color;
                import uz.duke.client3d.Duke3D;
                import uz.duke.client3d.Visuals;
                import uz.duke.game.DukeGame;
                import uz.duke.game.script.ScriptModule;

                /** Generated by Duke Studio. */
                public final class Main {

                    private static final String UNITS_INI = \"""
                """);
        for (var line : GameFactory.toIni(project).split("\n")) {
            code.append("            ").append(escape(line)).append('\n');
        }
        code.append("            \"\"\";\n\n");
        code.append("    public static void main(String[] args) {\n");
        project.ensureIntegrity();
        var map = project.maps.get(0); // exported game ships the first map as its match
        code.append("        var dukeGame = DukeGame.create(\"").append(escape(project.title)).append("\")\n");
        code.append("                .subtitle(\"").append(escape(project.menuSubtitle)).append("\")\n");
        code.append("                .loadUnits(UNITS_INI)\n");
        code.append("                .map(").append(map.cellsWide).append(", ")
                .append(map.cellsHigh).append(");\n\n");

        if (!project.scripts.isEmpty()) {
            code.append("        dukeGame.customModules(mf -> {\n");
            for (var script : project.scripts) {
                code.append("            ScriptModule.registerScript(mf, \"")
                        .append(escape(script.name)).append("\", game.scripts.")
                        .append(script.name).append("::new);\n");
            }
            code.append("        });\n\n");
        }

        for (int i = 0; i < project.players.size(); i++) {
            var player = project.players.get(i);
            code.append("        var p").append(i).append(" = dukeGame.addPlayer(\"")
                    .append(escape(player.name)).append("\", Color.decode(\"")
                    .append(player.colorHex).append("\"));\n");
            code.append("        dukeGame.money(p").append(i).append(", ").append(player.money).append(");\n");
        }
        for (int a = 0; a < project.players.size(); a++) {
            for (int b = a + 1; b < project.players.size(); b++) {
                var relation = project.players.get(a).team == project.players.get(b).team
                        ? "allies" : "enemies";
                code.append("        dukeGame.").append(relation)
                        .append("(p").append(a).append(", p").append(b).append(");\n");
            }
        }
        code.append("        dukeGame.localPlayer(p").append(Math.max(0, project.localPlayer)).append(");\n\n");

        if (!map.blockedCells.isEmpty()) {
            code.append("        var grid = dukeGame.getTerrain();\n");
            for (var cell : map.blockedCells) {
                var parts = cell.split(",");
                if (parts.length == 2) {
                    code.append("        grid.setBlocked(").append(parts[0].trim()).append(", ")
                            .append(parts[1].trim()).append(", true);\n");
                }
            }
            code.append('\n');
        }

        // neutral map objects (resource piles, critters)
        for (var neutral : map.neutrals) {
            code.append("        dukeGame.spawnNeutral(\"").append(escape(neutral.unitName)).append("\", ")
                    .append(neutral.x).append("f, ").append(neutral.y).append("f);\n");
        }

        // each player's starting base, from their faction, at the map's start position
        for (int i = 0; i < project.players.size(); i++) {
            var faction = project.findFaction(project.players.get(i).faction);
            if (faction == null && !project.factions.isEmpty()) {
                faction = project.factions.get(0);
            }
            if (faction == null) {
                continue;
            }
            float[] start = i < map.startPositions.size()
                    ? map.startPositions.get(i)
                    : new float[] {60f + i * 120f, 60f};
            for (var startingUnit : faction.startingUnits) {
                code.append("        dukeGame.spawn(\"").append(escape(startingUnit.unit)).append("\", p")
                        .append(i).append(", ").append(start[0] + startingUnit.dx).append("f, ")
                        .append(start[1] + startingUnit.dy).append("f);\n");
            }
        }

        code.append("\n        var visuals = Visuals.create()");
        for (var unit : project.units) {
            if (unit.modelPath.isBlank() && unit.fireSound.isBlank() && unit.dieSound.isBlank()) {
                continue;
            }
            code.append("\n                .unit(\"").append(escape(unit.name)).append("\", u -> u");
            if (!unit.modelPath.isBlank()) {
                code.append(".model(\"").append(escape(unit.modelPath)).append("\")")
                        .append(".scale(").append(unit.modelScale).append("f)")
                        .append(".yOffset(").append(unit.modelYOffset).append("f)")
                        .append(".facing(").append(unit.modelFacing).append("f)");
                appendAnim(code, "idle", unit.idleAnim);
                appendAnim(code, "walk", unit.walkAnim);
                appendAnim(code, "attack", unit.attackAnim);
            }
            appendAnim(code, "fireSound", unit.fireSound);
            appendAnim(code, "dieSound", unit.dieSound);
            code.append(")");
        }
        code.append(";\n\n        Duke3D.launch(dukeGame, visuals);\n    }\n}\n");
        return code.toString();
    }

    private static void appendAnim(StringBuilder code, String method, String value) {
        if (value != null && !value.isBlank()) {
            code.append(".").append(method).append("(\"").append(escape(value)).append("\")");
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String sanitize(String s) {
        return s.replaceAll("[^A-Za-z0-9 _-]", "").trim().replace(' ', '-');
    }

    /** Recursively copy a directory tree; returns the number of files copied. */
    private static int copyTree(Path source, Path target) throws IOException {
        int[] count = {0};
        try (Stream<Path> walk = Files.walk(source)) {
            for (var path : walk.toList()) {
                var destination = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                    count[0]++;
                }
            }
        }
        return count[0];
    }

    private static int copyEngineJars(Path engineRoot, Path libs) throws IOException {
        int copied = 0;
        for (var module : new String[] {"core", "game", "client3d"}) {
            var dir = engineRoot.resolve(module).resolve("build/libs");
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> jars = Files.list(dir)) {
                for (var jar : jars.filter(p -> p.toString().endsWith(".jar")).toList()) {
                    Files.copy(jar, libs.resolve(jar.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                    copied++;
                }
            }
        }
        return copied;
    }

    private static boolean copyWrapper(Path engineRoot, Path target) throws IOException {
        var gradlew = engineRoot.resolve("gradlew.bat");
        if (!Files.exists(gradlew)) {
            return false;
        }
        Files.copy(engineRoot.resolve("gradlew"), target.resolve("gradlew"), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(gradlew, target.resolve("gradlew.bat"), StandardCopyOption.REPLACE_EXISTING);
        var wrapperDir = target.resolve("gradle/wrapper");
        Files.createDirectories(wrapperDir);
        Files.copy(engineRoot.resolve("gradle/wrapper/gradle-wrapper.jar"),
                wrapperDir.resolve("gradle-wrapper.jar"), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(engineRoot.resolve("gradle/wrapper/gradle-wrapper.properties"),
                wrapperDir.resolve("gradle-wrapper.properties"), StandardCopyOption.REPLACE_EXISTING);
        return true;
    }
}
