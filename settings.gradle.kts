pluginManagement {
    repositories {
        gradlePluginPortal()
        // The Android Gradle Plugin, for client3d-android alone.
        google {
            content {
                includeGroupByRegex("""com\.android.*|com\.google.*|androidx.*""")
            }
        }
    }
}

rootProject.name = "duke-engine"

include("render-core", "render-effects", "render-plugins", "render-plugins-json", "render-plugins-json-gson", "render-jogg",
    "render-desktop", "render-lwjgl3", "render-bench")
include("bom", "processor", "core", "combat", "rts", "rpg", "game", "game-swing", "client3d", "client3d-desktop", "rts-client-3d", "rpg-client-3d", "action", "client3d-action", "client3d-touch", "kit", "skirmish")

// The Android platform needs the Android SDK and its Gradle plugin: built only when asked, `./gradlew -Pandroid ...`,
// so the desktop build needs neither.
if (providers.gradleProperty("android").isPresent) {
    include("render-android", "client3d-android", "skirmish-android")
}
