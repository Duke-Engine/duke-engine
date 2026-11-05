plugins {
    `java-library`
}

dependencies {
    api(project(":render-core"))
    testRuntimeOnly(project(":render-desktop"))
    testRuntimeOnly(project(":render-plugins"))
    testRuntimeOnly("org.jmonkeyengine:jme3-testdata:3.7.0-stable") { isTransitive = false }
}
