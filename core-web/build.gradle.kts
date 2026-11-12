plugins {
    `java-library`
    id("org.teavm") version "0.16.0"
}

// The engine's core in a browser: what stands in for the parts that need the JVM (reflection over records), and
// a probe that binds the skirmish's units there. `./gradlew -Pweb :core-web:site` writes the probe page to
// build/site. In the build with -Pweb only.
dependencies {
    api(project(":core"))
    api("org.teavm:teavm-jso-apis:0.16.0")
    runtimeOnly(project(":render-web-teavm"))
    implementation(project(":rts"))
    implementation(project(":skirmish")) // its Unit record, for the probe
    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

teavm {
    js {
        mainClass.set("uz.dukeengine.web.probe.BinderProbe")
        obfuscated.set(false)
    }
}

// The compiler plug-ins swap classes, and TeaVM takes the first swap it finds for a class: theirs before its own.
tasks.named("generateJavaScript") {
    val classpath = property("classpath") as ConfigurableFileCollection
    val given = classpath.from.toList()
    classpath.setFrom(listOf(configurations.runtimeClasspath.get().filter { it.name.startsWith("render-web-teavm") }) + given)
}

tasks.register<Sync>("site") {
    description = "Writes the Binder probe page with its script and the skirmish's unit files."
    from("src/main/webapp")
    from(tasks.named("generateJavaScript"))
    from(project(":skirmish").file("src/main/resources/data/units")) {
        into("units")
    }
    into(layout.buildDirectory.dir("site"))
}
