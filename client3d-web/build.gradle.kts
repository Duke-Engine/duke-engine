plugins {
    `java-library`
}

// The client's platform in a browser: canvas fonts, the browser's picture decoder, localStorage, the canvas
// window. In the build with -Pweb only.
dependencies {
    api(project(":client3d"))
    api(project(":render-web"))
    api(project(":core-web"))
    implementation(project(":render-plugins")) // the glTF loaders, registered by hand where no config is read
}

// The desktop platform must not reach a browser build: one platform on the class path, and no AWT.
configurations.all {
    exclude(module = "client3d-desktop")
}
