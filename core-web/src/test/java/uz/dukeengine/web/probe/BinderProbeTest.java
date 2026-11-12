package uz.dukeengine.web.probe;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The probe on the JVM: what the browser's page must print. Written to build/probe-jvm.txt to compare with. */
class BinderProbeTest {

    @Test
    void bindsEveryUnit() throws IOException {
        var dir = Path.of("../skirmish/src/main/resources/data/units");
        var names = BinderProbe.UNITS.stream().map(name -> name + ".duke").toList();
        var texts = names.stream().map(name -> read(dir.resolve(name))).toList();
        var result = BinderProbe.describe(names, texts);
        Files.writeString(Path.of("build/probe-jvm.txt"), result);
        assertEquals(BinderProbe.UNITS.size(), result.lines().count());
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
