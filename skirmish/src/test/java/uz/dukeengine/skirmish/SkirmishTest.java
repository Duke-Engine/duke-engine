package uz.dukeengine.skirmish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.rts.message.GameMessage;
import uz.dukeengine.skirmish.content.Content;
import uz.dukeengine.skirmish.content.Unit;

/**
 * A second game on the engine, written from nothing rather than copied from the dungeon.
 *
 * <p>What is worth asserting is not that it is a good game — it is four files — but that it runs out of its own
 * data, with its own record, on the RTS library, and that where it differs from the dungeon it differs because
 * the engine let it rather than because something was bent.
 */
class SkirmishTest {

    @Test
    void theGamesFilesReadAsItsOwnRecords() {
        var units = Content.everything().stream().filter(Unit.class::isInstance).map(Unit.class::cast).toList();

        assertEquals(6, units.size(), "worker, soldier, archer, barracks, ore, depot");
        var soldier = units.stream().filter(u -> u.name().equals("Soldier")).findFirst().orElseThrow();
        assertEquals(100, soldier.buildCost(), "a unit here is bought, which the dungeon's creatures never are");
        assertEquals("models/units/soldier.glb", soldier.model(), "its look is in its block, not in Java");
        assertTrue(soldier.hasModel());
    }

    /**
     * Two sides meet and fight, with no brain script anywhere.
     *
     * <p>The first real difference the second game turned up. The dungeon walks its creatures at you from a
     * {@code MonsterBrain}; here {@code WeaponUpdate} takes a target of its own the moment one is in range, and
     * a side's orders only say <em>which</em> one. Nothing was added to the engine to get this.
     */
    @Test
    void twoSidesInRangeFightWithNoBrainAtAll() {
        var match = Skirmish.open(40, 30, 1000);
        var game = match.game();
        game.spawn("Soldier", match.left(), 60, 150);
        game.spawn("Soldier", match.right(), 70, 150);
        game.spawn("Worker", match.right(), 300, 40);
        game.runHeadless(1);
        assertEquals(3, game.getLogic().getObjects().size());

        var mine = game.getLogic().getObjects().get(0);
        var theirs = game.getLogic().getObjects().get(1);
        var farOff = game.getLogic().getObjects().get(2);
        float whole = theirs.getBody().getHealth();
        float untouched = farOff.getBody().getHealth();

        game.postCommand(new GameMessage.AttackObject(match.left().getIndex(), List.of(mine.getId()),
                theirs.getId()));
        game.runHeadless(120);

        assertTrue(theirs.getBody().getHealth() < whole, "what it was sent at should be losing health");
        assertEquals(untouched, farOff.getBody().getHealth(), 0.001f,
                "and a worker across the field should be untouched");
    }

    /**
     * Sent at something out of reach, it walks to it.
     *
     * <p>This is what {@code PursueUpdate} added. Before it, {@code WeaponUpdate} waited "for movement to
     * close in" and there was no movement in the RTS library to wait for: the unit stood where it was put.
     */
    @Test
    void aUnitSentAtSomethingOutOfReachWalksToIt() {
        var match = Skirmish.open(60, 40, 0);
        var game = match.game();
        game.spawn("Soldier", match.left(), 60f, 150f);
        game.spawn("Soldier", match.right(), 320f, 150f);
        game.runHeadless(1);
        var mine = game.getLogic().getObjects().getFirst();
        var theirs = game.getLogic().getObjects().get(1);
        float apart = mine.getPosition().distance(theirs.getPosition());

        game.postCommand(new GameMessage.AttackObject(match.left().getIndex(), List.of(mine.getId()),
                theirs.getId()));
        game.runHeadless(30 * 20);

        assertTrue(mine.getPosition().distance(theirs.getPosition()) < apart * 0.5f,
                "it should have closed most of the way: was " + apart + ", now "
                        + mine.getPosition().distance(theirs.getPosition()));
        assertTrue(theirs.getBody().getHealth() < theirs.getBody().getMaxHealth(),
                "and started hitting what it walked to");
    }

    /** A unit is bought: the order goes to a building, the money leaves the purse, and the unit arrives. */
    @Test
    void aSideBuysASoldierAndItComesOutOfThePurse() {
        var match = Skirmish.open(40, 30, 1000);
        var game = match.game();
        game.spawn("Barracks", match.left(), 80f, 150f);
        game.runHeadless(1);
        var barracks = game.getLogic().getObjects().getFirst();
        int purse = uz.dukeengine.rts.player.RtsPlayer.of(game.getLogic(), match.left().getIndex()).getMoney();

        game.postCommand(new GameMessage.QueueProduction(match.left().getIndex(), barracks.getId(), "Soldier"));
        game.runHeadless(30 * 8);

        var built = game.getLogic().getObjects().stream()
                .filter(o -> o.getTemplate().name().equals("Soldier")).toList();
        assertEquals(1, built.size(), "the barracks should have finished one soldier");
        assertEquals(purse - 100,
                uz.dukeengine.rts.player.RtsPlayer.of(game.getLogic(), match.left().getIndex()).getMoney(),
                "and it should have cost what its block says");
    }

    /**
     * A worker walks to a pile, loads, and carries it back to the depot.
     *
     * <p>The second gap this game turned up, now closed. {@code HarvestUpdate} used to find the nearest pile
     * anywhere on the map at any distance and bank a load without moving — no walking, no carrying, no depot,
     * and so no reason to put a pile anywhere in particular. Now the distance is the economy.
     */
    @Test
    void aWorkerWalksToThePileAndCarriesItBackToTheDepot() {
        var match = Skirmish.open(60, 40, 0);
        var game = match.game();
        game.spawn("Depot", match.left(), 60f, 60f);
        game.spawn("Worker", match.left(), 120f, 120f);
        game.spawn("OreNode", match.left(), 300f, 260f);
        game.runHeadless(1);
        var worker = game.getLogic().getObjects().stream()
                .filter(o -> o.getTemplate().name().equals("Worker")).findFirst().orElseThrow();
        var stood = worker.getPosition();

        game.runHeadless(60);
        assertTrue(stood.distance(worker.getPosition()) > 20f,
                "it should have set off for the pile rather than banking on the spot");

        game.runHeadless(30 * 40);
        assertTrue(uz.dukeengine.rts.player.RtsPlayer.of(game.getLogic(), match.left().getIndex()).getMoney() > 0,
                "and brought something back");
    }

    /** With no depot at all it banks where it stands, which is what the module did before it could walk. */
    @Test
    void withNoDepotItBanksWhereItStands() {
        var match = Skirmish.open(60, 40, 0);
        var game = match.game();
        game.spawn("Worker", match.left(), 70f, 70f);
        game.spawn("OreNode", match.left(), 80f, 70f);
        game.runHeadless(30 * 10);

        assertTrue(uz.dukeengine.rts.player.RtsPlayer.of(game.getLogic(), match.left().getIndex()).getMoney() > 0,
                "a side with nowhere to unload still gets its money");
    }

    /**
     * The map package end to end: found under {@code maps/} rather than listed, read as this game's own
     * record, laid as the ground the game walks on, and with what the file puts on it standing there.
     *
     * <p>This is the engine's map seam exercised by a game that is not the dungeon — which is the only way to
     * know the seam is a seam and not the dungeon's shape with a different name on it.
     */
    @Test
    void theFieldIsLaidFromItsOwnMapFolder() {
        var match = Skirmish.on("clearing", 500);
        var game = match.game();
        game.runHeadless(1);

        assertEquals("The Clearing", match.field().displayName());
        assertEquals(48, match.field().width());
        assertEquals(32, match.field().height());
        assertEquals(6, game.getLogic().getObjects().size(), "the ore the file puts on the field");

        // Read through the engine's own MapThing, which counts in cells: `OreNode 9 7` is the ninth
        // cell across and the seventh down, whatever the field's cells turn out to be worth in the world.
        var ore = match.field().things().getFirst();
        assertEquals("OreNode", ore.template());
        assertEquals(9f, ore.x());
        assertEquals(7f, ore.y());

        // The rock down the middle is rock, and the open ground around it is not.
        assertTrue(game.getTerrain().isBlocked(23, 15), "the spine of rock should be in the way");
        assertFalse(game.getTerrain().isBlocked(10, 15), "and the field around it should not be");
    }

    /** A side walks its worker out of its own corner and mines what the map put there. */
    @Test
    void aSideStartsWhereTheMapSaysAndMinesWhatIsOnIt() {
        var match = Skirmish.on("clearing", 0);
        var game = match.game();
        var start = match.field().starts().getFirst();
        game.spawn("Depot", match.left(), Skirmish.at(start.x()), Skirmish.at(start.y()));
        game.spawn("Worker", match.left(), Skirmish.at(start.x() + 2), Skirmish.at(start.y()));
        game.runHeadless(30 * 40);

        assertTrue(uz.dukeengine.rts.player.RtsPlayer.of(game.getLogic(), match.left().getIndex()).getMoney() > 0,
                "the worker should have found the map's ore and brought some back");
    }
}
