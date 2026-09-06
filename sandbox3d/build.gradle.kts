plugins {
    java
    application
}

dependencies {
    implementation(project(":client3d"))
    runtimeOnly("org.jmonkeyengine:jme3-testdata:3.7.0-stable") // demo models (Oto)
}

application {
    mainClass.set("uz.duke.sandbox3d.Main")
}
