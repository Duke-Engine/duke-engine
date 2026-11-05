plugins {
    `java-library`
}

dependencies {
    api(project(":render-plugins-json"))
    api("com.google.code.gson:gson:2.9.1")
}
