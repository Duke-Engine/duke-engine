plugins {
    `java-platform`
}

// Every module of the engine, at one version. A game writes the version once:
//
//   implementation(platform("uz.duke-engine:bom:0.10.1"))
//   implementation("uz.duke-engine:client3d")
//   implementation("uz.duke-engine:kit")
//
// and cannot end up with a client3d from one release and a core from another — which is the one
// mistake a multi-module library makes easy and a BOM makes impossible.
dependencies {
    constraints {
        api(project(":render-core"))
        api(project(":render-effects"))
        api(project(":render-plugins"))
        api(project(":render-plugins-json"))
        api(project(":render-plugins-json-gson"))
        api(project(":render-jogg"))
        api(project(":render-desktop"))
        api(project(":render-lwjgl3"))
        api(project(":processor"))
        api(project(":core"))
        api(project(":combat"))
        api(project(":rts"))
        api(project(":rpg"))
        api(project(":game"))
        api(project(":game-swing"))
        api(project(":client3d"))
        api(project(":client3d-desktop"))
        api(project(":rts-client-3d"))
        api(project(":rpg-client-3d"))
        api(project(":action"))
        api(project(":client3d-action"))
        api(project(":client3d-touch"))
        api(project(":kit"))
        // Only a build with -Pandroid has it, and the release is one.
        findProject(":client3d-android")?.let { api(it) }
        findProject(":render-android")?.let { api(it) }
    }
}
