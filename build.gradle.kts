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
    version = "0.2.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    // The BOM is a `java-platform`, which is a different kind of thing: it has no sources, no tests and
    // no toolchain, and applying the java plugin to it fails outright.
    if (name == "bom") {
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
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
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
    "core" to "The genre-neutral engine: subsystems, the fixed-rate deterministic loop, objects and modules, "
        + "the .duke data layer, pathfinding, fog of war and lock-step networking.",
    "rts" to "The RTS library on top of core: commands, combat, production, economy, veterancy and the RTS "
        + "vocabulary — the parts every RTS shares, with the rules left to the game.",
    "game" to "The Unity-style API: the DukeGame facade, players, orders, a 2D Swing client and lock-step "
        + "multiplayer over TCP.",
    "client3d" to "The 3D client on jMonkeyEngine: model loading, skeletal animation, positional sound, an RTS "
        + "camera, effects, menus and a minimap.",
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
