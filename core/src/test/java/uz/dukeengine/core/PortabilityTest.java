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
 * The logic modules stay portable: they can be compiled for a browser or a phone later, so what only a desktop JVM
 * has is kept to the few packages that already name it.
 */
class PortabilityTest {

    private static final List<String> LOGIC_MODULES = List.of("core", "combat", "rts", "rpg");

    private static final Pattern DESKTOP_UI = Pattern.compile("\\b(java\\.awt|javax\\.swing|javax\\.imageio)\\b");

    /** Sockets and channels: {@code URI} and {@code URLEncoder} are plain text work and are fine anywhere. */
    private static final Pattern SOCKETS = Pattern.compile(
            "\\bjava\\.net\\.(Socket|ServerSocket|DatagramSocket|HttpURLConnection|http)\\b|\\bjava\\.nio\\.channels\\b");

    private static final Pattern FILES = Pattern.compile("\\bjava\\.io\\.File\\b|\\bjava\\.nio\\.file\\b");

    /**
     * JDK methods TeaVM's class library lacks: {@code Unsigned}, {@code ReadOnly} and {@code Jdk} stand in for them.
     * Nor has it a regex's {@code \R}, {@code String.lines()} or {@code Properties.store} (no ISO-8859-1).
     */
    private static final Pattern MISSING_IN_BROWSER = Pattern.compile(
            "\\b(parseUnsignedInt|parseUnsignedLong|unmodifiableSortedSet|unmodifiableSortedMap|newKeySet"
            + "|onSpinWait|getResources|getImplementationVersion)\\(|\\.(lines|store)\\(|\\\\\\\\R");
    private static final String STAND_INS = "core/src/main/java/uz/dukeengine/core/data/(ReadOnly|Jdk)\\.java";

    private static final String NETWORK = "core/src/main/java/uz/dukeengine/core/network/";
    private static final String MAP = "core/src/main/java/uz/dukeengine/core/map/";

    @Test
    void noLogicModuleUsesADesktopUi() throws IOException {
        assertEquals(List.of(), offenders(DESKTOP_UI, null));
    }

    @Test
    void socketsStayInTheNetworkPackage() throws IOException {
        assertEquals(List.of(), offenders(SOCKETS, NETWORK));
    }

    @Test
    void filesStayInTheMapPackage() throws IOException {
        assertEquals(List.of(), offenders(FILES, MAP));
    }

    @Test
    void noLogicModuleCallsWhatABrowserLacks() throws IOException {
        assertEquals(List.of(), offenders(MISSING_IN_BROWSER, STAND_INS));
    }

    /** The sources of the logic modules that match {@code pattern} outside the one folder (a pattern) allowed to. */
    private static List<String> offenders(Pattern pattern, String allowedFolder) throws IOException {
        var root = Path.of("").toAbsolutePath().getParent();
        var found = new ArrayList<String>();
        for (var module : LOGIC_MODULES) {
            var sources = root.resolve(module).resolve("src/main/java");
            assertTrue(Files.isDirectory(sources), "no sources at " + sources);
            try (var files = Files.walk(sources)) {
                for (var file : files.filter(f -> f.toString().endsWith(".java")).sorted().toList()) {
                    var name = root.relativize(file).toString().replace('\\', '/');
                    if ((allowedFolder == null || !name.matches(allowedFolder + ".*"))
                            && pattern.matcher(Files.readString(file)).find()) {
                        found.add(name);
                    }
                }
            }
        }
        return found;
    }
}
