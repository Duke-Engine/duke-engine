plugins {
    java
    // Publishing to Maven Central's Portal: it builds the sources and javadoc jars, signs everything and
    // uploads the bundle, none of which plain `maven-publish` can do on its own.
    id("com.vanniktech.maven.publish") version "0.30.0" apply false
}

allprojects {
    // The namespace verified at central.sonatype.com, which is the domain backwards. A Maven groupId may hold
    // a hyphen and a Java package may not, so the packages stay `uz.dukeengine.*` — the two need not match.
    group = "uz.duke-engine"
    version = "0.8.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    // The BOM is a `java-platform`, which is a different kind of thing: it has no sources, no tests and
    // no toolchain, and applying the java plugin to it fails outright.
    // client3d-android is built by the Android plugin, which refuses a project the java plugin is applied to.
    if (name == "bom" || name == "client3d-android") {
        return@subprojects
    }
    apply(plugin = "java")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.add("-Xlint:all")
        // The shape processor reads every type and claims no annotation, which javac would warn of in each module.
        options.compilerArgs.add("-Xlint:-processing")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    // A module with maps ships `maps/maps.list`, one line a file under `maps/` (`crypt/crypt.map`): a platform that packs
    // its resources into an app cannot scan a folder, so the maps are found by this list. Written from the folder,
    // so a new map needs no edit.
    for (set in listOf("main", "test")) {
        val maps = file("src/$set/resources/maps")
        if (maps.isDirectory) {
            val out = layout.buildDirectory.dir("generated/maplist/$set")
            val list = tasks.register("generate${set.replaceFirstChar(Char::uppercase)}MapList") {
                inputs.dir(maps)
                outputs.dir(out)
                doLast {
                    val lines = maps.walkTopDown().filter { it.isFile }
                        .map { it.relativeTo(maps).invariantSeparatorsPath }.sorted().toList()
                    val target = out.get().file("maps/maps.list").asFile
                    target.parentFile.mkdirs()
                    target.writeText(lines.joinToString("\n", postfix = "\n"))
                }
            }
            the<SourceSetContainer>()[set].resources.srcDir(files(out).builtBy(list))
        }
    }

    // Every module writes the shapes of its records, so the Binder can read data where reflection cannot. A
    // module's tests share its packages, so theirs are written under another class name.
    if (name != "processor") {
        dependencies {
            "annotationProcessor"(project(":processor"))
            "testAnnotationProcessor"(project(":processor"))
        }
        tasks.named<JavaCompile>("compileTestJava") {
            options.compilerArgs.add("-Aduke.shapes.class=DukeShapesTest")
        }
    }

    // The logic modules' tests run twice: on the written shapes, and on reflection alone, so the two are held
    // to binding every data test the same.
    if (name in setOf("core", "combat", "rts", "rpg")) {
        val test = the<SourceSetContainer>()["test"]
        val reflectionTest = tasks.register<Test>("reflectionTest") {
            description = "Runs the tests with the Binder reading types by reflection only."
            group = "verification"
            testClassesDirs = test.output.classesDirs
            classpath = test.runtimeClasspath
            systemProperty("duke.shapes", "reflection")
        }
        tasks.named("check") { dependsOn(reflectionTest) }
    }

    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:5.11.3"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }
}

// ---------------------------------------------------------------------------
// What is published, and what is not.
//
// The engine is: a game built on it depends on these and on nothing else here. `skirmish` is a
// game rather than a library — it is here to be played and to prove the engine carries more than one kind
// of game, and nobody should be able to depend on it by accident.
//
//   ./gradlew publishToMavenLocal            # to try it against a game on this machine
//   ./gradlew publishAndReleaseToMavenCentral   # needs the credentials and the signing key below
//
// Neither the credentials nor the key are in this repository. Put them in ~/.gradle/gradle.properties:
//
//   mavenCentralUsername=<the token name from central.sonatype.com>
//   mavenCentralPassword=<the token>
//   signingInMemoryKey=<the ascii-armoured secret key, newlines as \n>
//   signingInMemoryKeyPassword=<its passphrase>
// ---------------------------------------------------------------------------

val published = mapOf(
    "bom" to "Every module of the engine at one version, so a game writes the version once and cannot mix "
        + "a client from one release with a core from another.",
    "processor" to "The annotation processor a game adds beside the engine: it writes the shape of every record a "
        + "module declares, so the .duke binder reads data without reflection where a platform has none.",
    "core" to "The genre-neutral engine: subsystems, the fixed-rate deterministic loop, objects and modules, "
        + "the .duke data layer, pathfinding, fog of war and lock-step networking.",
    "combat" to "What every side that fights shares, on top of core: weapons and their bonuses, shots, statuses, "
        + "experience, auras, pursuit and the move, attack and stop orders — for an RTS and an RPG alike.",
    "rts" to "The RTS library on top of combat: commands, production, economy, construction and the RTS "
        + "vocabulary — the parts every RTS shares, with the rules left to the game.",
    "rpg" to "The RPG library on top of combat: a hero's skills, mana and buffs, levels and attributes, items "
        + "and loot, missions — the parts every RPG shares, with the rules left to the game.",
    "game" to "The Unity-style API: the DukeGame facade, players, orders and lock-step multiplayer over TCP.",
    "game-swing" to "A 2D Swing window over the DukeGame facade: select with the left button, order with the right.",
    "client3d" to "The 3D client on jMonkeyEngine: model loading, skeletal animation, positional sound, an RTS "
        + "camera, effects, menus and a minimap.",
    "client3d-desktop" to "The desktop side of the 3D client: the platform interfaces answered with AWT, Swing and GLFW, "
        + "so a game gets fonts, picture files, settings and a window by depending on it.",
    "rts-client-3d" to "The RTS's part of the 3D client: what its window sends that only an RTS has words for — a "
        + "building's rally point and its build menu.",
    "rpg-client-3d" to "The RPG's part of the 3D client: the hero's bar and portrait, the skill cards, the bag "
        + "and what is on a creature, and the hero's card on the status line, both ends of it.",
    "action" to "A game played from one hero's shoulder, the simulation half: a walk along a held direction, an "
        + "attack toward a point, a dodge's roll — orders of the game's own and a module, on the engine's seams.",
    "client3d-action" to "Controls for a game played from one hero's shoulder, plugged into the 3D client: walking by "
        + "held keys, a camera kept on him and turned by a held mouse, the left button striking, skills aimed ahead.",
    "client3d-touch" to "Touch controls for the 3D client, plugged in as a ControlScheme: tap to select and order, a "
        + "drag to pan, a long press and drag for a box, a pinch to zoom, two fingers twisting to turn the camera.",
    "kit" to "The starter set a game begins from: an effects library and the particle art it is drawn with. "
        + "Data only — no code.",
)

configure(subprojects.filter { it.name in published }) {
    apply(plugin = "com.vanniktech.maven.publish")

    configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
        // CENTRAL_PORTAL, not the default. The default is the old OSSRH at oss.sonatype.org, which works by
        // creating a staging repository against an account this project does not have — the failure reads
        // "Cannot get stagingProfiles for account (402)", which says nothing about the real cause. A namespace
        // verified at central.sonatype.com is a Central Portal namespace, and the Portal takes a bundle instead.
        publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
        // Only when a key is there. Central will not take an unsigned artifact, but publishing to the local
        // repository to try the engine against a game must not need one — and a key is never in a repository.
        if (providers.gradleProperty("signingInMemoryKey").isPresent) {
            signAllPublications()
        }
        coordinates(project.group.toString(), project.name, project.version.toString())
        pom {
            name.set("duke-engine ${project.name}")
            description.set(published.getValue(project.name))
            url.set("https://duke-engine.uz")
            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://github.com/Duke-Engine/duke-engine/blob/master/LICENSE")
                    distribution.set("repo")
                }
            }
            developers {
                developer {
                    id.set("abdurasul29052002")
                    name.set("Abdurasul Abduraimov")
                    url.set("https://duke-engine.uz")
                }
            }
            scm {
                url.set("https://github.com/Duke-Engine/duke-engine")
                connection.set("scm:git:https://github.com/Duke-Engine/duke-engine.git")
                developerConnection.set("scm:git:ssh://git@github.com/abdurasul29052002/duke-engine.git")
            }
        }
    }
}
