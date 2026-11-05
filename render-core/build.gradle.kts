plugins {
    `java-library`
}

sourceSets {
    main {
        java {
            srcDir("src/plugins/java")
            srcDir("src/tools/java")
        }
    }
}

dependencies {
    testImplementation(project(":render-desktop"))
    testRuntimeOnly(project(":render-plugins"))
    // shortcut: the test pictures and models still come from jME's release, import them if a test needs a change.
    testRuntimeOnly("org.jmonkeyengine:jme3-testdata:3.7.0-stable") { isTransitive = false }
}
