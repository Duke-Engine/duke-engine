plugins {
    java
    `java-library`
}

// Touch controls for the 3D client, plugged in as its ControlScheme: fingers read through jME's TouchInput (jme3-core),
// which every platform delivers or emulates. It names no platform and no kind of game.
dependencies {
    api(project(":client3d"))
}
