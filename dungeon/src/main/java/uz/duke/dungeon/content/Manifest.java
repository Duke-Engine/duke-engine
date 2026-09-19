package uz.duke.dungeon.content;

import java.util.HashSet;
import java.util.List;

/**
 * The list of every data file the game is made of, in the order it is read: its
 * {@code Manifest} block. The order is the game's — monster kinds are drawn in it, heroes
 * offered in it and skills read in it — so moving a file up the list changes which monster a
 * seed rolls.
 */
public record Manifest(List<String> files) {

    public Manifest {
        files = files == null ? List.of() : List.copyOf(files);
        var seen = new HashSet<String>();
        for (var file : files) {
            if (!seen.add(file)) {
                throw new IllegalArgumentException(file + " is listed twice, and would be read twice");
            }
        }
    }
}
