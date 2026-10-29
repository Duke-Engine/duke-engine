plugins {
    java
    `java-library`
}

dependencies {
    api(project(":client3d"))
    api(project(":rts"))
    // The platform the tests run on.
    testRuntimeOnly(project(":client3d-desktop"))
}
