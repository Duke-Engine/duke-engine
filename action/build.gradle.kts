plugins {
    java
    `java-library`
}

// The simulation half of a game played from one hero's shoulder: walking along a held direction, an attack toward a
// point, a dodge's roll. Orders of the game's own (GameOrder) and a module, on the seams core, combat and rpg offer —
// a game that never names it never has any of it.
dependencies {
    api(project(":rpg"))
}

// The boundary test reads the main modules' sources, so a change to them runs it again.
tasks.test {
    for (module in listOf("core", "combat", "rts", "rpg", "game", "client3d", "rts-client-3d", "rpg-client-3d")) {
        inputs.dir(rootProject.file("$module/src/main/java")).withPropertyName("sources of $module")
            .withPathSensitivity(PathSensitivity.RELATIVE)
    }
}
