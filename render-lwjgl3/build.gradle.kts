plugins {
    `java-library`
}

val lwjgl = "3.3.3"

dependencies {
    api(project(":render-core"))
    api(project(":render-desktop"))
    api("org.lwjglx:lwjgl3-awt:0.1.8")
    api("org.lwjgl:lwjgl-jawt:$lwjgl")
    api("org.lwjgl:lwjgl-opencl:$lwjgl")
    for (library in listOf("lwjgl", "lwjgl-glfw", "lwjgl-jemalloc", "lwjgl-openal", "lwjgl-opengl")) {
        api("org.lwjgl:$library:$lwjgl")
        for (platform in listOf("windows", "windows-x86", "linux", "linux-arm32", "linux-arm64", "macos", "macos-arm64")) {
            runtimeOnly("org.lwjgl:$library:$lwjgl:natives-$platform")
        }
    }
}
