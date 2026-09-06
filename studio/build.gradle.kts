plugins {
    java
    application
}

dependencies {
    implementation(project(":client3d"))
    implementation("com.google.code.gson:gson:2.11.0")
}

application {
    mainClass.set("uz.duke.studio.StudioMain")
}

// writes examples/RohanVsMordor.duke at the repo root
tasks.register<JavaExec>("writeExamples") {
    group = "application"
    description = "Regenerate the example game projects"
    mainClass.set("uz.duke.studio.examples.RohanVsMordor")
    classpath = sourceSets["main"].runtimeClasspath
}

// ships the example game as a standalone project into dist/RohanVsMordor
tasks.register<JavaExec>("exportExample") {
    group = "application"
    description = "Export the example game as a standalone Gradle project"
    mainClass.set("uz.duke.studio.examples.ExportExample")
    classpath = sourceSets["main"].runtimeClasspath
}
