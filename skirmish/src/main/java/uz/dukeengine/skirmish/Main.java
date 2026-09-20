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
        // The field, the ore on it and each side's corner all come out of maps/clearing/ — this method
        // names none of them.
        var match = Skirmish.on(args.length > 0 ? args[0] : null, 1000);
        var game = match.game();
        var sides = java.util.List.of(match.left(), match.right());
        for (int side = 0; side < sides.size() && side < match.field().starts().size(); side++) {
            var start = match.field().starts().get(side);
            var who = sides.get(side);
            int facing = side == 0 ? 1 : -1;
            game.spawn("Barracks", who, Skirmish.at(start.x()), Skirmish.at(start.y()));
            game.spawn("Depot", who, Skirmish.at(start.x() + facing * 2), Skirmish.at(start.y() - 3));
            game.spawn("Worker", who, Skirmish.at(start.x() + facing * 2), Skirmish.at(start.y() + 2));
            game.spawn("Soldier", who, Skirmish.at(start.x() + facing * 3), Skirmish.at(start.y() - 1));
            game.spawn("Archer", who, Skirmish.at(start.x() + facing * 3), Skirmish.at(start.y() + 1));
        }

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
