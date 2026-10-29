plugins {
    `java-platform`
}

// Every module of the engine, at one version. A game writes the version once:
//
//   implementation(platform("uz.duke-engine:bom:0.8.0"))
//   implementation("uz.duke-engine:client3d")
//   implementation("uz.duke-engine:kit")
//
// and cannot end up with a client3d from one release and a core from another — which is the one
// mistake a multi-module library makes easy and a BOM makes impossible.
dependencies {
    constraints {
        api(project(":processor"))
        api(project(":core"))
        api(project(":combat"))
        api(project(":rts"))
        api(project(":rpg"))
        api(project(":game"))
        api(project(":game-swing"))
        api(project(":client3d"))
        api(project(":rts-client-3d"))
        api(project(":rpg-client-3d"))
        api(project(":action"))
        api(project(":client3d-action"))
        api(project(":kit"))
    }
}
