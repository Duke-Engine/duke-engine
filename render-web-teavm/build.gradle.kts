// Plug-ins of the TeaVM compiler for the browser renderer: which JDK classes are swapped and what stays reflective.
// They run inside TeaVM's worker on the Gradle daemon's JDK, so they are compiled for 21. In the build with -Pweb only.
tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

dependencies {
    compileOnly("org.teavm:teavm-extension-spi:0.16.0")
    compileOnly("org.teavm:teavm-extension-apis:0.16.0")
}
