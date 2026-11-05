package uz.dukeengine.core.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Maps found by {@code maps.list} and read by a class loader: no folder of the machine's is asked for. */
class MapListTest {

    /** A class loader that holds only these resources, so a map read through it cannot have come off a disk. */
    private static final class InMemory extends ClassLoader {
        private final Map<String, String> files;

        InMemory(Map<String, String> files) {
            super(null);
            this.files = files;
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            var text = files.get(name);
            return text == null ? null : new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    void theListNamesTheMapsAndTheirFilesAndTheyAreReadByName() {
        var loader = new InMemory(Map.of(
                "maps/maps.list", "crypt/crypt.map\r\ncrypt/preview.png\ncrypt/zed.duke\ncrypt/a.duke\nstray/readme.txt\nloose.map\n",
                "maps/crypt/crypt.map", "Name = Crypt\r\nPlayers = 2\nGrid = [\n",
                "maps/crypt/a.duke", "A", "maps/crypt/zed.duke", "Z"));

        var maps = MapPackages.listed(loader, "maps");

        assertEquals(1, maps.size(), "a folder with no map file of its name, and a loose file, are no map");
        var crypt = maps.getFirst();
        assertEquals("crypt", crypt.name());
        assertEquals("Crypt", crypt.head().get("Name"));
        assertEquals("A\nZ\n", crypt.extrasText(), "extras in name order");
        assertEquals("maps/crypt/preview.png", crypt.previewResource());
    }

    @Test
    void noListMeansNoMapsFromTheList() {
        assertTrue(MapPackages.listed(new InMemory(Map.of()), "maps").isEmpty());
    }

    @Test
    void theBuildWroteTheListOfTheTestMapsAndItIsFoundByIt() {
        var tiny = MapPackages.listed(MapListTest.class.getClassLoader(), "maps").stream()
                .filter(m -> m.name().equals("tiny")).findFirst().orElse(null);

        assertNotNull(tiny, "maps/maps.list is generated from src/test/resources/maps");
        assertNotNull(tiny.text());
        assertNull(tiny.previewResource());
    }

    @Test
    void everyRootsListIsReadAndAMapInTwoRootsTakesTheFilesOfBothReadFromTheFirst(@TempDir Path first,
            @TempDir Path second) throws Exception {
        write(first.resolve("maps/maps.list"), "crypt/crypt.map\ncrypt/preview.png\n");
        write(first.resolve("maps/crypt/crypt.map"), "Name = First\nPlayers = 2\nGrid = [\n");
        write(second.resolve("maps/maps.list"), "vault/vault.map\ncrypt/crypt.map\ncrypt/extra.duke\n");
        write(second.resolve("maps/vault/vault.map"), "Name = Vault\nPlayers = 2\nGrid = [\n");
        write(second.resolve("maps/crypt/crypt.map"), "Name = Second\nPlayers = 2\nGrid = [\n");
        write(second.resolve("maps/crypt/extra.duke"), "X");

        try (var loader = new URLClassLoader(new URL[] {first.toUri().toURL(), second.toUri().toURL()}, null)) {
            var maps = MapPackages.listed(loader, "maps");

            assertEquals(List.of("crypt", "vault"), maps.stream().map(MapPackage::name).toList());
            var crypt = maps.getFirst();
            assertEquals("First", crypt.head().get("Name"), "read from the first root");
            assertEquals("X\n", crypt.extrasText(), "with the file only the second root lists");
            assertEquals("maps/crypt/preview.png", crypt.previewResource());
            assertEquals("Vault", maps.getLast().head().get("Name"));
        }
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }
}
