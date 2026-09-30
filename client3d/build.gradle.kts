plugins {
    java
    `java-library`
}

val jmeVersion = "3.7.0-stable"

dependencies {
    api(project(":game"))
    // The RTS library's panels are still drawn here — the build menu, the production and placement orders — until
    // the client is split into its RTS and RPG halves.
    api(project(":rts"))
    api("org.jmonkeyengine:jme3-core:$jmeVersion")
    implementation("org.jmonkeyengine:jme3-desktop:$jmeVersion")
    implementation("org.jmonkeyengine:jme3-lwjgl3:$jmeVersion")
    implementation("org.jmonkeyengine:jme3-plugins:$jmeVersion") // glTF + Ogre model loaders
    implementation("org.jmonkeyengine:jme3-plugins-json:$jmeVersion") // the loader's JSON, to read a glb's extras
    implementation("org.jmonkeyengine:jme3-jogg:$jmeVersion")    // .ogg sound loading
}
