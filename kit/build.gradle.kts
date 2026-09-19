plugins {
    `java-library`
}

// The starter set a game begins from: data and the art it names, nothing to compile yet. Its files sit
// under kit/ on the classpath, so a game's own never share a path with them — a game replaces one of the
// kit's blocks by writing its own of the same Name, in its own file.
