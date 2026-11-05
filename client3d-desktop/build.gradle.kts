plugins {
    java
    `java-library`
}

// The desktop's side of the client's platform interfaces: fonts, picture files, settings, the window.
dependencies {
    api(project(":client3d"))
    implementation(project(":render-desktop"))
    implementation(project(":render-lwjgl3"))
}
