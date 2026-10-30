package uz.dukeengine.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The neutral modules run on every platform: what only a desktop or only a phone has lives in a platform module, and
 * the files a neutral module reads come through an interface. The lists of files that still break this are the work
 * left; they only shrink.
 */
class NeutralModulesTest {

    private static final List<String> NEUTRAL_MODULES = List.of("core", "combat", "rts", "rpg", "action", "game",
            "client3d", "rts-client-3d", "rpg-client-3d", "client3d-action", "client3d-touch");

    private static final Pattern PLATFORM = Pattern.compile("\\b(java\\.awt|javax\\.swing|javax\\.imageio|org\\.lwjgl"
            + "|android|com\\.jme3\\.system\\.lwjgl|com\\.jme3\\.app\\.Android\\w*)\\."
            + "|\\bcom\\.jme3\\.(texture\\.plugins\\.AWT\\w*|system\\.JmeDesktopSystem)\\b");

    private static final Pattern FILES = Pattern.compile("\\bjava\\.io\\.File\\b|\\bjava\\.nio\\.file\\b");

    /** Where a map is read from a folder or a zip: the one place that is the files interface. */
    private static final String MAP = "core/src/main/java/uz/dukeengine/core/map/";

    private static final String GAME = "game/src/main/java/uz/dukeengine/game/";

    private static final List<String> STILL_ON_A_PLATFORM = List.of(
            GAME + "DukeGame.java", GAME + "GamePlayer.java");

    private static final List<String> STILL_ON_FILES = List.of(
            GAME + "DukeGame.java");

    @Test
    void noNeutralModuleNamesAPlatform() throws IOException {
        assertEquals(STILL_ON_A_PLATFORM, offenders(PLATFORM, null));
    }

    @Test
    void neutralModulesReachFilesOnlyThroughTheMapPackage() throws IOException {
        assertEquals(STILL_ON_FILES, offenders(FILES, MAP));
    }

    /** The sources of the neutral modules that match {@code pattern} outside the one folder allowed to. */
    private static List<String> offenders(Pattern pattern, String allowedFolder) throws IOException {
        var root = Path.of("").toAbsolutePath().getParent();
        var found = new ArrayList<String>();
        for (var module : NEUTRAL_MODULES) {
            var sources = root.resolve(module).resolve("src/main/java");
            assertTrue(Files.isDirectory(sources), "no sources at " + sources);
            try (var files = Files.walk(sources)) {
                for (var file : files.filter(f -> f.toString().endsWith(".java")).sorted().toList()) {
                    var name = root.relativize(file).toString().replace('\\', '/');
                    if ((allowedFolder == null || !name.startsWith(allowedFolder))
                            && pattern.matcher(Files.readString(file)).find()) {
                        found.add(name);
                    }
                }
            }
        }
        return found;
    }
}
