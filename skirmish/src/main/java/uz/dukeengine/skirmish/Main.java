package uz.dukeengine.skirmish;

import java.awt.Color;
import uz.dukeengine.client3d.Duke3D;
import uz.dukeengine.client3d.Sun;
import uz.dukeengine.client3d.Visuals;
import uz.dukeengine.skirmish.content.Content;
import uz.dukeengine.skirmish.content.Unit;

/**
 * Opens the skirmish in the engine's 3D client.
 *
 * <p><b>Read {@link #look} before anything else here.</b> That method is the whole reason this game exists: it is
 * the Java a second game has to write to get a model on screen, because {@code RtsTemplate} — the template every
 * RTS is supposed to build on — carries a name, a size, a price and its modules, and not one field about how the
 * thing looks. The dungeon solved that by inventing its own creature record with the look in it. This game had to
 * invent one too. Two games, the same invention, is what a missing seam looks like.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        var match = Skirmish.open(60, 44, 1000);
        var game = match.game();

        // A starting force each, at opposite ends. No base building yet: the point today is that a second game
        // reaches the screen at all.
        game.spawn("Barracks", match.left(), 80f, 140f);
        game.spawn("Soldier", match.left(), 120f, 120f);
        game.spawn("Soldier", match.left(), 120f, 160f);
        game.spawn("Worker", match.left(), 80f, 180f);
        game.spawn("Barracks", match.right(), 460f, 140f);
        game.spawn("Soldier", match.right(), 420f, 120f);
        game.spawn("Archer", match.right(), 420f, 160f);
        game.spawn("Worker", match.right(), 460f, 180f);

        Duke3D.launch(game, visuals());
    }

    /** Everything the client is told, built out of the game's own files. */
    private static Visuals visuals() {
        var visuals = Visuals.create();
        var everything = Content.everything();
        var sets = everything.stream().filter(uz.dukeengine.core.content.AnimationSet.class::isInstance)
                .map(uz.dukeengine.core.content.AnimationSet.class::cast)
                .collect(java.util.stream.Collectors.toMap(uz.dukeengine.core.content.AnimationSet::name, s -> s));
        for (var record : everything) {
            switch (record) {
                case Unit unit -> visuals.draw(unit, sets.get(unit.animations()));
                case Sun sun -> visuals.sunlight(new uz.dukeengine.client3d.Sunlight(sun.pitch(), sun.yaw(),
                        sun.strengthPercent() / 100f, sun.ambientPercent() / 100f,
                        sun.colour(), sun.ambientTint()));
                default -> {
                    // Blocks the client is not told about: the field itself, and anything added later.
                }
            }
        }
        return visuals;
    }

}
