plugins {
    application
}

dependencies {
    implementation(project(":render-effects"))
    runtimeOnly(project(":render-desktop"))
    runtimeOnly(project(":render-lwjgl3"))
    runtimeOnly(project(":render-plugins"))
    runtimeOnly(project(":render-plugins-json-gson"))
}

application {
    mainClass.set("uz.dukeengine.render.bench.RenderBench")
}
