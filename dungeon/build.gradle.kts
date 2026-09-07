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
