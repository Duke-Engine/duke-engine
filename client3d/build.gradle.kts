plugins {
    java
    `java-library`
}

val jmeVersion = "3.7.0-stable"

dependencies {
    api(project(":game"))
    api("org.jmonkeyengine:jme3-core:$jmeVersion")
    implementation("org.jmonkeyengine:jme3-desktop:$jmeVersion")
    implementation("org.jmonkeyengine:jme3-lwjgl3:$jmeVersion")
    implementation("org.jmonkeyengine:jme3-plugins:$jmeVersion") // glTF + Ogre model loaders
    implementation("org.jmonkeyengine:jme3-jogg:$jmeVersion")    // .ogg sound loading
}
