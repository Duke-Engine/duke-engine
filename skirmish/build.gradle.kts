plugins {
    java
    application
}

dependencies {
    // The 3D client and the RTS's part of it, for a camera over an open field.
    implementation(project(":rts-client-3d"))
    // The starter set: the effects every game may use, and the art they are drawn with.
    implementation(project(":kit"))
}

application {
    mainClass.set("uz.dukeengine.skirmish.Main")
}
