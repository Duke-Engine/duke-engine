plugins {
    java
    `java-library`
}

dependencies {
    // The runtime knows no kind of game: a game brings its kind — the RTS library, say — and the runtime runs it
    // through core's Flavour. The shared layer every kind fights with it may name.
    api(project(":combat"))
    // The runtime's own tests play matches, and an RTS is the kind they play.
    testImplementation(project(":rts"))
}
