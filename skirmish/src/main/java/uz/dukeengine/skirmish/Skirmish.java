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
        commandBar(game);
        return new Match(game, left, right, field);
    }

    /**
     * What a worker may put down. This game's rule, and written here because it is one: a worker builds the
     * two buildings a side needs, and nothing else builds anything.
     */
    private static final java.util.List<String> A_WORKER_BUILDS = java.util.List.of("Barracks", "Depot");

    /**
     * What a selected thing offers: a building, one button per unit it may train, priced; a worker, one per
     * building it may put down — a button that aims at the ground, with that building as its ghost.
     *
     * <p>The engine asks and this answers, which is the whole of the seam. What a button <em>means</em> is
     * here and nowhere in the engine — that these are units, that they cost money, that a building is placed
     * rather than produced — and a different game answering the same question would put entirely different
     * words on entirely different buttons.
     *
     * <p>Whether a button may be pressed is this game's rule too: a barracks the player cannot afford is
     * drawn dim rather than taken away, so the bar stays the same shape as his money comes and goes.
     */
    private static void commandBar(DukeGame game) {
        game.commandBar(selection -> {
            var logic = game.getLogic();
            if (logic == null || selection.size() != 1) {
                return java.util.List.of(); // one at a time; a crowd has no one line to build
            }
            var chosen = logic.findObject(new uz.dukeengine.core.thing.ObjectId(selection.getFirst()));
            if (chosen == null || chosen.getPlayerIndex() != game.getLocalPlayerIndex()) {
                return java.util.List.of(); // not his
            }
            int purse = logic.getRtsPlayer(chosen.getPlayerIndex()).getMoney();
            var buttons = new java.util.ArrayList<uz.dukeengine.game.view.CommandButton>();
            var line = chosen.findModule(uz.dukeengine.rts.module.ProductionUpdate.class);
            if (line != null) {
                for (var name : line.getBuilds()) {
                    int cost = costOf(logic, name);
                    buttons.add(new uz.dukeengine.game.view.CommandButton(
                            "train:" + name, null, name + " " + cost, null, cost <= purse));
                }
            }
            if (chosen.findModule(uz.dukeengine.rts.module.HarvestUpdate.class) != null) {
                // A building is placed, not produced: the button arms the cursor, a ghost of the
                // building follows it, and the click is the place.
                for (var name : A_WORKER_BUILDS) {
                    int cost = costOf(logic, name);
                    buttons.add(new uz.dukeengine.game.view.CommandButton("build:" + name, null,
                            name + " " + cost, null, cost <= purse,
                            uz.dukeengine.game.view.CommandButton.Aim.GROUND, name));
                }
            }
            return buttons;
        });
        // Whether the ghost is green: the simulation's own answer for this player's site, asked of the world as it
        // stands — the answer his order will be given.
        game.aimFits((button, place, facing) -> !button.startsWith("build:")
                || game.getLogic().fits(game.getLocalPlayerIndex(), button.substring("build:".length()), place,
                        facing) == uz.dukeengine.rts.construction.Placement.Fit.FITS);
        game.onCommandPressed(press -> {
            if (press.selection().size() != 1) {
                return;
            }
            var chosen = new uz.dukeengine.core.thing.ObjectId(press.selection().getFirst());
            // Posted, never done: this runs on the window's thread, and the only safe thing to do with a
            // press there is put it in the queue -- it is applied on a frame boundary like every order.
            if (press.id().startsWith("train:")) {
                game.postCommand(new uz.dukeengine.rts.message.GameMessage.QueueProduction(
                        game.getLocalPlayerIndex(), chosen, press.id().substring("train:".length())));
            } else if (press.id().startsWith("build:") && press.place() != null) {
                game.postCommand(new uz.dukeengine.rts.message.GameMessage.Construct(
                        game.getLocalPlayerIndex(), chosen, press.id().substring("build:".length()),
                        press.place(), press.facing()));
            }
        });
    }

    private static int costOf(uz.dukeengine.rts.RtsSimulation logic, String name) {
        return logic.getThingFactory().findTemplate(name) instanceof uz.dukeengine.rts.Buildable priced
                ? priced.buildCost() : 0;
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
