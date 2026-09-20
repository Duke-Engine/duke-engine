plugins {
    java
    application
}

dependencies {
    // The 3D client, for a camera over an open field.
    implementation(project(":client3d"))
    // The starter set: the effects every game may use, and the art they are drawn with.
    implementation(project(":kit"))
}

application {
    mainClass.set("uz.dukeengine.skirmish.Main")
}
