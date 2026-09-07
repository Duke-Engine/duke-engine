plugins {
    java
    application
}

dependencies {
    // The game talks to the engine's public API and nothing else. No jME: a
    // top-down dungeon of primitives has nothing a 3D renderer would add yet.
    implementation(project(":game"))
}

application {
    mainClass.set("uz.duke.dungeon.Main")
}
