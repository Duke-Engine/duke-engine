plugins {
    id("com.android.library") version "9.2.1"
}

val jmeVersion = "3.7.0-stable"

repositories {
    google()
}

// Android's side of the client's platform interfaces: fonts and pictures from android.graphics, settings in the app's
// files, a dialog for text, the screen the surface fills; and the activity a game's Android launcher extends.
android {
    namespace = "uz.dukeengine.client3d.android"
    compileSdk = 36
    defaultConfig {
        // The engine calls Java 21 library methods (List.getFirst and others) that Android has from 35 only.
        minSdk = 35
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        // AGP knows no Java 25 (its Kotlin has no such JVM target); javac is raised to 25 below.
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    api(project(":client3d"))
    api("org.jmonkeyengine:jme3-android:$jmeVersion")
    implementation("org.jmonkeyengine:jme3-android-native:$jmeVersion")
    testImplementation("junit:junit:4.13.2")
}

// The engine's classes are Java 25 (class version 69), which javac reads only at 25; D8 and R8 take them as they are.
// The toolchain gives javac and the unit tests a JDK 25 whatever JDK Gradle itself runs on.
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
