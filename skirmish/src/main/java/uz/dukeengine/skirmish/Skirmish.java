package uz.dukeengine.skirmish;

import java.awt.Color;
import uz.dukeengine.game.DukeGame;
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
    public record Match(DukeGame game, uz.dukeengine.game.GamePlayer left, uz.dukeengine.game.GamePlayer right) {
    }

    private Skirmish() {
    }

    /** An open field this many cells across and down, with both sides set up and at war. */
    public static Match open(int cellsWide, int cellsHigh, int purse) {
        var game = DukeGame.create("Duke Skirmish")
                .templates(loader -> loader.type(Unit.class))
                .loadUnits(Content.units())
                .world(world())
                .map(cellsWide, cellsHigh);
        var left = game.addPlayer("Left", Color.CYAN);
        var right = game.addPlayer("Right", Color.ORANGE);
        game.enemies(left, right).money(left, purse).money(right, purse).localPlayer(left);
        return new Match(game, left, right);
    }

    /** The world block of the game's own files: one field, flat. */
    public static Field world() {
        return Content.everything().stream()
                .filter(Field.class::isInstance).map(Field.class::cast)
                .findFirst().orElseThrow(() -> new IllegalStateException("the game's files hold no Field block"));
    }
}
