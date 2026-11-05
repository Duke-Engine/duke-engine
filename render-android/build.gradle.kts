import java.util.Properties

plugins {
    `java-library`
}

repositories {
    google()
}

// A plain jar compiled against the SDK's android.jar, as upstream built it. It is in the build only with -Pandroid.
val sdk = providers.environmentVariable("ANDROID_HOME").orNull ?: Properties().run {
    rootProject.file("local.properties").inputStream().use { load(it) }
    getProperty("sdk.dir")
}

dependencies {
    api(project(":render-core"))
    compileOnly(files("$sdk/platforms/android-36/android.jar"))
    compileOnly("androidx.annotation:annotation:1.3.0")
    compileOnly("androidx.lifecycle:lifecycle-common:2.4.0")
}
