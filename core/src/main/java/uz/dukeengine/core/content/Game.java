package uz.dukeengine.core.content;

import java.util.HashSet;
import java.util.List;

/**
 * The game itself: every data file it is made of, in the order they are read, and which map it
 * opens on. Its {@code Game} block, in {@code data/game.duke}.
 *
 * @param files    every data file, in the order it is read. The order is the game's — monster kinds
 *     are drawn in it, heroes offered in it, skills read in it and stages listed in it — so moving a
 *     file up the list changes which monster a seed rolls
 * @param startMap the map the game opens on, by its {@code Name}; none for the choice of the
 *     endless descent or a stage. Beaten by {@code --map=} on the command line
 */
public record Game(List<String> files, String startMap) {

    public Game {
        files = files == null ? List.of() : List.copyOf(files);
        startMap = startMap == null || startMap.isBlank() ? null : startMap;
        var seen = new HashSet<String>();
        for (var file : files) {
            if (!seen.add(file)) {
                throw new IllegalArgumentException(file + " is listed twice, and would be read twice");
            }
        }
    }
}
