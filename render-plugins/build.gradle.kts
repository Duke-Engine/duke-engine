plugins {
    `java-library`
}

sourceSets {
    main {
        java {
            srcDir("src/ogre/java")
            srcDir("src/gltf/java")
            srcDir("src/fbx/java")
            srcDir("src/xml/java")
        }
    }
}

dependencies {
    api(project(":render-core"))
    implementation(project(":render-plugins-json"))
    implementation(project(":render-plugins-json-gson"))
    testRuntimeOnly(project(":render-desktop"))
    testRuntimeOnly("org.jmonkeyengine:jme3-testdata:3.7.0-stable") { isTransitive = false }
}
