plugins {
    java
    `java-library`
}

dependencies {
    api(project(":client3d"))
    api(project(":rpg"))
    // A kind of game for the tests' matches to run on; the part itself names none.
    testImplementation(project(":rts"))
    // The platform the tests run on.
    testRuntimeOnly(project(":client3d-desktop"))
}
