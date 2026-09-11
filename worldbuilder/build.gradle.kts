plugins {
    java
    application
}

dependencies {
    // The game, not the engine. A stage is Duke Dungeon's own kind of level: it is
    // built out of that game's generator, checked by that game's rules and filled
    // with the creatures its data files describe. The Studio is the engine's editor
    // and is left alone — it makes RTS games, which have none of this in them.
    implementation(project(":dungeon"))
}

application {
    mainClass.set("uz.duke.worldbuilder.WorldBuilderMain")
}

// Regenerates the stage the game ships with, into the dungeon's own resources so
// that it travels inside the installer:
//
//   ./gradlew :worldbuilder:writeExampleStage
//
// Here rather than saved by hand out of the builder because a file nobody can
// rebuild is a file nobody dares change.
tasks.register<JavaExec>("writeExampleStage") {
    group = "application"
    description = "Regenerate the example stage the game ships with"
    mainClass.set("uz.duke.worldbuilder.ExampleStage")
    classpath = sourceSets["main"].runtimeClasspath
    // From the repository root, so the path in the Java is the path you would type.
    // A JavaExec otherwise starts in its own module's directory, and the file lands
    // in worldbuilder/dungeon/src/... where nothing will ever read it.
    workingDir = rootProject.projectDir
}
