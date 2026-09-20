package uz.duke.core.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A map is a folder that is found rather than a line in a list: what it is called, what it brings with it, and
 * what a screen can say about it without reading its cells.
 */
class MapPackageTest {

    private static final String CRYPT = """
            StaticMap
              ; the head is read on its own, so a comment in it is skipped
              Name = crypt
              DisplayName = The Crypt
              Players = 2
              Cells = [
                "###",
                "#0#",
                "###",
              ]
              Monsters = [
                Skeleton 1 1,
              ]
            End
            """;

    private static Path map(Path maps, String name, String text) throws IOException {
        var folder = Files.createDirectories(maps.resolve(name));
        Files.writeString(folder.resolve(name + ".map"), text);
        return folder;
    }

    @Test
    void aMapIsItsFolderAndWhateverElseIsInIt(@TempDir Path root) throws IOException {
        var maps = root.resolve("maps");
        var crypt = map(maps, "crypt", CRYPT);
        Files.writeString(crypt.resolve("monsters.duke"), "Monster\n  Name = Ghoul\nEnd\n");
        Files.writeString(crypt.resolve("props.duke"), "Prop\n  Name = Urn\nEnd\n");
        Files.write(crypt.resolve("preview.png"), new byte[] {1, 2, 3});

        var found = MapPackage.in(crypt);

        assertNotNull(found);
        assertEquals("crypt", found.name(), "a map is named for its folder and its own file");
        assertEquals(List.of("monsters.duke", "props.duke"),
                found.extras().stream().map(path -> path.getFileName().toString()).toList(),
                "the blocks it brings with it, in the order their files are named");
        assertEquals("preview.png", found.preview().getFileName().toString());
        assertTrue(found.extrasText().contains("Ghoul") && found.extrasText().contains("Urn"));
        assertTrue(found.text().startsWith("StaticMap"));
    }

    /** The head is the plain lines before the first list — enough to list a map without reading it. */
    @Test
    void theHeadIsReadWithoutTheCells(@TempDir Path root) throws IOException {
        var crypt = map(root.resolve("maps"), "crypt", CRYPT);

        var head = MapPackage.in(crypt).head();

        assertEquals(List.of("Name", "DisplayName", "Players"), List.copyOf(head.keySet()),
                "the rows of cells end the head, and everything after them is not read");
        assertEquals("The Crypt", head.get("DisplayName"));
        assertEquals("2", head.get("Players"));
    }

    @Test
    void everyFolderWithAMapInItIsOne(@TempDir Path root) throws IOException {
        var maps = root.resolve("maps");
        map(maps, "crypt", CRYPT);
        map(maps, "attic", CRYPT.replace("crypt", "attic"));
        Files.createDirectories(maps.resolve("notes")); // no map of its own name in it
        Files.writeString(maps.resolve("notes").resolve("todo.duke"), "Monster\n  Name = Later\nEnd\n");

        var found = MapPackages.under(maps);

        assertEquals(List.of("attic", "crypt"), found.stream().map(MapPackage::name).toList(),
                "in name order, and a folder with no map of its own is not one");
        assertNull(MapPackage.in(maps.resolve("notes")));
        assertEquals(List.of(), MapPackages.under(root.resolve("nowhere")));
    }

    /** A map the player has made stands in for a shipped map of the same name, as a game's block does for the kit's. */
    @Test
    void aPlayersOwnMapTakesThePlaceOfAShippedOne(@TempDir Path root) throws IOException {
        var theirs = root.resolve("maps");
        map(theirs, "tiny", CRYPT.replace("crypt", "tiny").replace("The Crypt", "Their Own"));

        var shipped = MapPackages.shipped("maps");
        var all = MapPackages.all("maps", theirs);

        assertEquals(List.of("tiny"), shipped.stream().map(MapPackage::name).toList(),
                "the test's own resources hold one map, which is how a game ships them");
        assertEquals(List.of("tiny"), all.stream().map(MapPackage::name).toList());
        assertEquals("Their Own", all.getFirst().head().get("DisplayName"), "the player's own wins");
    }

    @Test
    void aMapNamedOnTheCommandLineIsFoundByItsFileOrItsFolder(@TempDir Path root) throws IOException {
        var crypt = map(root.resolve("maps"), "crypt", CRYPT);

        assertEquals("crypt", MapPackage.of(crypt).name());
        assertEquals("crypt", MapPackage.of(crypt.resolve("crypt.map")).name());
        assertNull(MapPackage.of(root.resolve("nowhere")));
    }
}
