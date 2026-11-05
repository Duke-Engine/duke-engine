plugins {
    java
    `java-library`
}

dependencies {
    api(project(":game"))
    api(project(":render-core"))
    implementation(project(":render-effects")) // the filters of a physically based picture
    implementation(project(":render-plugins")) // glTF + Ogre model loaders
    implementation(project(":render-plugins-json")) // the loader's JSON, to read a glb's extras
    implementation(project(":render-jogg")) // .ogg sound loading
    // A kind of game for the tests' matches to run on; the client itself names none.
    testImplementation(project(":rts"))
    // The platform the tests run on; the client itself names none.
    testImplementation(project(":client3d-desktop"))
}
