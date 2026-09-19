package uz.duke.dungeon.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import uz.duke.dungeon.content.DungeonSettings;

/** A shipped map is what its seed draws: a file nobody can rebuild is a file nobody dares change. */
class MapWriterTest {

    @Test
    void theFirstMapIsWhatItsSeedDraws() throws IOException {
        var shipped = Files.readString(Path.of("src/main/resources/data/maps/first.duke"));
        assertEquals(shipped, MapWriter.text(DungeonSettings.load(), MapWriter.FIRST));
    }
}
