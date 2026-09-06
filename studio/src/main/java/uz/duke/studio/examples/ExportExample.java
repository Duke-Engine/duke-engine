package uz.duke.studio.examples;

import java.nio.file.Path;
import uz.duke.studio.export.GameExporter;

/**
 * Ships the example game: exports Rohan vs Mordor as a standalone Gradle
 * project into {@code dist/RohanVsMordor} — the same thing the Studio's
 * "Export game" button does, runnable from the command line.
 */
public final class ExportExample {

    public static void main(String[] args) throws Exception {
        var engineRoot = Path.of("..").toAbsolutePath().normalize();
        var target = engineRoot.resolve("dist/RohanVsMordor");
        var summary = GameExporter.export(RohanVsMordor.build(), target, engineRoot, null);
        System.out.println(summary);
    }
}
