plugins {
    java
    `java-library`
}

dependencies {
    // A desktop window over the runtime: the 2D Swing view a game can open without the 3D client.
    api(project(":game"))
}
