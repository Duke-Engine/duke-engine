package uz.dukeengine.core.map;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Where the bytes of a map's files come from, by the name of the file inside its package — {@code crypt.map}.
 * A map package reads its text through this, so a platform with no file system can hand it bytes from somewhere
 * else; the machine's own folders are the only source there is for now.
 */
public interface MapSource {

    /** The whole file of that name, or an {@link IOException} where there is none. */
    byte[] read(String name) throws IOException;

    /** The files of a folder of the machine's own — or of a jar's file system, which a {@link Path} reaches the same. */
    static MapSource in(Path folder) {
        return name -> Files.readAllBytes(folder.resolve(name));
    }
}
