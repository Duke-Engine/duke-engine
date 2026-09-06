plugins {
    java
    application
}

dependencies {
    implementation(project(":game"))
}

application {
    mainClass.set("uz.duke.sandbox.Main")
}
