plugins {
    id("org.teavm") version "0.16.0"
}

// The browser renderer's demo: a lit cube on a page. `./gradlew -Pweb :render-web-demo:site` writes the page to
// build/site, to be served by any static server.
dependencies {
    implementation(project(":render-web"))
}

teavm {
    js {
        mainClass.set("com.jme3.web.demo.CubeDemo")
        obfuscated.set(false)
    }
}

// The compiler plug-ins swap JDK classes, and TeaVM takes the first swap it finds for a class: theirs before its own.
tasks.named("generateJavaScript") {
    val classpath = property("classpath") as ConfigurableFileCollection
    val given = classpath.from.toList()
    classpath.setFrom(listOf(configurations.runtimeClasspath.get().filter { it.name.startsWith("render-web-teavm") }) + given)
}

tasks.register<Sync>("site") {
    description = "Writes the demo page with its script and the renderer's assets."
    from("src/main/webapp")
    from(tasks.named("generateJavaScript"))
    from(project(":render-core").file("src/main/resources")) {
        include("Common/**", "com/jme3/system/version.properties")
        into("assets")
    }
    into(layout.buildDirectory.dir("site"))
}
