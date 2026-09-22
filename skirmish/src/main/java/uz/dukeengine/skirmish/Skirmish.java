package uz.dukeengine.skirmish;

import java.awt.Color;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.core.map.MapTerrain;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.skirmish.content.Battlefield;
import uz.dukeengine.skirmish.content.Content;
import uz.dukeengine.skirmish.content.Field;
import uz.dukeengine.skirmish.content.Unit;

/**
 * A skirmish, assembled.
 *
 * <p>Two sides, a purse each, and an open field. Nothing in this class is a rule — the units, their prices and
 * what a barracks may build are all files — and nothing in it is the dungeon's: there is no hero to choose, no
 * floor to descend, no experience, no loot. It is here to find out what the engine owes a second game.
 */
public final class Skirmish {

    /** Two sides on an open field, nothing spawned yet. */
    public record Match(DukeGame game, uz.dukeengine.game.GamePlayer left, uz.dukeengine.game.GamePlayer right,
            Battlefield field) {
    }

    private Skirmish() {
    }

    /** An open field this many cells across and down, with both sides set up and at war. */
    public static Match open(int cellsWide, int cellsHigh, int purse) {
        return set(DukeGame.create("Duke Skirmish")
                .templates(loader -> loader.type(Unit.class))
                .loadUnits(Content.units())
                .world(world())
                .map(cellsWide, cellsHigh), purse, null);
    }

    /**
     * A match on a map the game ships, laid as the map file draws it and with everything the file puts on it
     * already standing there.
     *
     * <p>The whole point of a map package: the ground, the sides' corners and what is on the field are all in
     * one folder, and this method names none of them.
     */
    public static Match on(String map, int purse) {
        var field = Content.map(map);
        var game = DukeGame.create("Duke Skirmish")
                .templates(loader -> loader.type(Unit.class))
                .loadUnits(Content.units())
                .world(world())
                .map(field.width(), field.height());
        game.applyMapTerrain(MapTerrain.of(field, PathGrid.DEFAULT_CELL_SIZE, 0f));
        var match = set(game, purse, field);
        for (var thing : field.things()) {
            game.spawn(thing.template(), match.left(), at(thing.x()), at(thing.y()));
        }
        return match;
    }

    /** The two sides, at war, with a purse each. */
    private static Match set(DukeGame game, int purse, Battlefield field) {
        var left = game.addPlayer("Left", Color.CYAN);
        var right = game.addPlayer("Right", Color.ORANGE);
        game.enemies(left, right).money(left, purse).money(right, purse).localPlayer(left);
        return new Match(game, left, right, field);
    }

    /** The middle of a cell, in world units — where a thing put in a cell stands. */
    public static float at(float cell) {
        return (cell + 0.5f) * PathGrid.DEFAULT_CELL_SIZE;
    }

    /** The world block of the game's own files: one field, flat. */
    public static Field world() {
        return Content.everything().stream()
                .filter(Field.class::isInstance).map(Field.class::cast)
                .findFirst().orElseThrow(() -> new IllegalStateException("the game's files hold no Field block"));
    }
}
