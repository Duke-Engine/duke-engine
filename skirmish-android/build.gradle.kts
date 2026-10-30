plugins {
    id("com.android.application") version "9.2.1"
}

repositories {
    google()
}

// The skirmish on an Android phone: the desktop launcher's game, on client3d-android, played by touch.
// `./gradlew -Pandroid :skirmish-android:assembleDebug`, then `adb install -r build/outputs/apk/debug/*.apk`.
android {
    namespace = "uz.dukeengine.skirmish.android"
    compileSdk = 36
    defaultConfig {
        applicationId = "uz.dukeengine.skirmish"
        minSdk = 35 // client3d-android's: the engine calls Java 21 library methods Android has from 35 only
        targetSdk = 35
        versionCode = 1
        versionName = version.toString()
    }
    buildTypes {
        debug {
            // A debuggable build has D8 compile every `assert` on, and the client trips jME's own: "Illegal mat param
            // update" from UnitScene.heightOf, a unit's body brought up to date under a scene that is not. The desktop
            // runs without -ea. shortcut: asserts off on the phone as on the desktop, until the client's mid-scene
            // updateGeometricState calls are fixed.
            isDebuggable = false
        }
    }
    compileOptions {
        // AGP knows no Java 25 (its Kotlin has no such JVM target); javac is raised to 25 below.
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":skirmish")) {
        exclude(group = "uz.duke-engine", module = "client3d-desktop") // its desktop platform; this one is Android's
    }
    implementation(project(":client3d-android"))
    implementation(project(":client3d-touch"))
}

// The engine's classes are Java 25 (class version 69), which javac reads only at 25: as client3d-android does.
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}
afterEvaluate {
    tasks.withType<JavaCompile>().configureEach {
        sourceCompatibility = "25"
        targetCompatibility = "25"
    }
}
