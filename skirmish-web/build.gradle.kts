plugins {
    id("org.teavm") version "0.16.0"
}

// The skirmish in a browser. `./gradlew -Pweb :skirmish-web:site` writes the page to
// build/site, to be served by any static server.
configurations.all {
    exclude(module = "client3d-desktop") // one platform, and no AWT
}

dependencies {
    implementation(project(":client3d-web"))
    implementation(project(":skirmish"))
}

teavm {
    js {
        mainClass.set("uz.dukeengine.skirmish.web.SkirmishWeb")
        obfuscated.set(false)
        outOfProcess.set(!project.hasProperty("teavmInProcess")) // the whole client is more than the Gradle daemon has room for
        processMemory.set(4096)
    }
}

// The compiler plug-ins swap JDK classes, and TeaVM takes the first swap it finds for a class: theirs before its own.
tasks.named("generateJavaScript") {
    val classpath = property("classpath") as ConfigurableFileCollection
    val given = classpath.from.toList()
    classpath.setFrom(listOf(configurations.runtimeClasspath.get().filter { it.name.startsWith("render-web-teavm") }) + given)
}

tasks.register<Sync>("site") {
    description = "Writes the skirmish page with its script, the renderer's and the client's assets and the game's models."
    from("src/main/webapp")
    from(tasks.named("generateJavaScript"))
    from(project(":render-core").file("src/main/resources")) {
        include("Common/**", "Interface/**", "com/jme3/system/version.properties")
        into("assets")
    }
    for (module in listOf(":render-effects", ":client3d")) {
        from(project(module).file("src/main/resources")) {
            include("Common/**", "MatDefs/**")
            into("assets")
        }
    }
    from(project(":skirmish").file("src/main/resources")) {
        include("models/**")
        into("assets")
    }
    into(layout.buildDirectory.dir("site"))
}
