package uz.duke.studio.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Copies an asset file into the project's assets folder and returns the
 * engine-relative path to reference it by ({@code Models/tank.glb}).
 *
 * <p>Formats like {@code .gltf} and Ogre {@code .mesh.xml} reference sibling
 * files (geometry buffers, materials, textures), so the import also brings
 * along same-named siblings and the loose support files jME will ask for.
 * Self-contained formats ({@code .glb}, {@code .j3o}) copy as a single file —
 * recommend those to users.
 */
public final class AssetImporter {

    private AssetImporter() {
    }

    /** Copy {@code file} into {@code assetsRoot/category/} with its support files. */
    public static String importAsset(Path assetsRoot, Path file, String category) throws IOException {
        var targetDir = assetsRoot.resolve(category);
        Files.createDirectories(targetDir);
        Files.copy(file, targetDir.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);

        if (needsSiblings(file.getFileName().toString())) {
            copySupportFiles(file, targetDir);
        }
        return category + "/" + file.getFileName();
    }

    private static boolean needsSiblings(String fileName) {
        var lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".gltf") || lower.endsWith(".mesh.xml") || lower.endsWith(".obj");
    }

    /** Bring along same-basename siblings plus loose buffers/materials/textures. */
    private static void copySupportFiles(Path file, Path targetDir) throws IOException {
        var dir = file.getParent();
        if (dir == null) {
            return;
        }
        var base = baseName(file.getFileName().toString());
        try (Stream<Path> siblings = Files.list(dir)) {
            for (var sibling : siblings.filter(Files::isRegularFile).toList()) {
                var name = sibling.getFileName().toString();
                if (name.equals(file.getFileName().toString())) {
                    continue; // already copied
                }
                if (baseName(name).equalsIgnoreCase(base) || isSupportFile(name)) {
                    Files.copy(sibling, targetDir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static boolean isSupportFile(String name) {
        var lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".bin") || lower.endsWith(".material") || lower.endsWith(".skeleton.xml")
                || lower.endsWith(".mtl") || lower.endsWith(".png") || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg") || lower.endsWith(".dds") || lower.endsWith(".tga");
    }

    private static String baseName(String fileName) {
        int dot = fileName.indexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }
}
