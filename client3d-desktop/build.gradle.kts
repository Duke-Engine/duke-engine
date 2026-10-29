plugins {
    java
    `java-library`
}

val jmeVersion = "3.7.0-stable"

// The desktop's side of the client's platform interfaces: fonts, picture files, settings, the window.
dependencies {
    api(project(":client3d"))
    implementation("org.jmonkeyengine:jme3-desktop:$jmeVersion")
    implementation("org.jmonkeyengine:jme3-lwjgl3:$jmeVersion")
}
