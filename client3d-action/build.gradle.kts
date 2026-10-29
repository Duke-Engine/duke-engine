plugins {
    java
    `java-library`
}

// Controls for a game played from one hero's shoulder — walking by held keys, a camera kept on him and turned by a
// held mouse, the left button striking, skills aimed with no pointer, the nearest thing ordered on a key — plugged into
// the client as its ControlScheme. A game that never names it never has any of it.
dependencies {
    api(project(":client3d"))
    // The orders its mouse sends, and the module that carries them out.
    api(project(":action"))
    // A kind of game for the tests' matches to run on; the module itself names none.
    testImplementation(project(":rts"))
    // The platform the tests run on.
    testRuntimeOnly(project(":client3d-desktop"))
}
