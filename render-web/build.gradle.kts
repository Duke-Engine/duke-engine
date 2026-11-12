plugins {
    `java-library`
}

// The renderer in a browser: GL on WebGL2, a canvas context and assets fetched from the page. Compiled to JavaScript by
// TeaVM, with the compiler plug-ins of render-web-teavm ahead of TeaVM's own classes. In the build with -Pweb only.
dependencies {
    api(project(":render-core"))
    api("org.teavm:teavm-jso-apis:0.16.0")
    runtimeOnly(project(":render-web-teavm"))
}
