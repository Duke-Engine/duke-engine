package uz.dukeengine.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** The main modules build and run without the action ones: none of their sources names either. */
class BoundaryTest {

    private static final List<String> MAIN_MODULES = List.of("core", "combat", "rts", "rpg", "game", "client3d",
            "rts-client-3d", "rpg-client-3d");

    private static final Pattern ACTION = Pattern.compile("\\buz\\.dukeengine\\.(client3d\\.)?action\\b");

    @Test
    void noMainModuleNamesTheActionModules() throws IOException {
        var root = Path.of("").toAbsolutePath().getParent();
        var offenders = new ArrayList<String>();
        for (var module : MAIN_MODULES) {
            var sources = root.resolve(module).resolve("src/main/java");
            assertTrue(Files.isDirectory(sources), "no sources at " + sources);
            try (var files = Files.walk(sources)) {
                for (var file : files.filter(f -> f.toString().endsWith(".java")).sorted().toList()) {
                    if (ACTION.matcher(Files.readString(file)).find()) {
                        offenders.add(root.relativize(file).toString());
                    }
                }
            }
        }
        assertEquals(List.of(), offenders);
    }
}
