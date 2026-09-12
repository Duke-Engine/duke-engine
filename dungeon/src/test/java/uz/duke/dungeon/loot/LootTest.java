package uz.duke.dungeon.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.duke.core.thing.GameObject;
import uz.duke.dungeon.Dungeon;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.game.DukeGame;

/**
 * What a dead monster leaves, and what picking it up is worth.
 *
 * <p>Nothing here asserts a balance figure — which items exist and what they are
 * worth is written in {@code dungeon.ini}, so a re-tune should move these tests
 * with it. What is held still is the mechanism: that the draw is the seed's and
 * not the clock's, that a monster's own draw does not depend on when it died,
 * that the floor decides what may be found, and that a run's findings die with it.
 */
class LootTest {

    private static final DungeonSettings SHIPPED = DungeonSettings.load();

    private static final List<Loot> DECK = List.of(
            new Loot("Blade", "Blade", "", LootKind.ATTACK, 10, 10, 1),
            new Loot("Plate", "Plate", "", LootKind.ARMOUR, 5, 10, 3));

    /** Always drops, so a test about what drops is not a test about whether. */
    private static LootTable always(long seed) {
        return new LootTable(DECK, seed, 100, 100, 0);
    }

    // ---- the draw ----

    @Test
    void theSameMonsterAlwaysLeavesTheSameThing() {
        var table = always(99L);
        var first = table.dropFor(41, 2, false);
        assertNotNull(first);
        assertEquals(first.id(), table.dropFor(41, 2, false).id(),
                "a seed is the whole run, the loot included");
    }

    /**
     * And it does not depend on the order the player killed things in.
     *
     * <p>A generator advanced once per death would be reproducible too, but only
     * for a player who took the same route — which would make a replay depend on
     * where he walked rather than on what he ordered.
     */
    @Test
    void theOrderOfDeathsDoesNotChangeTheDraw() {
        var table = always(99L);
        var alone = table.dropFor(41, 2, false);
        table.dropFor(7, 2, false);
        table.dropFor(8, 2, false);
        table.dropFor(9, 2, false);
        assertEquals(alone.id(), table.dropFor(41, 2, false).id());
    }

    @Test
    void differentMonstersLeaveDifferentThings() {
        var table = always(99L);
        boolean anyDifferent = false;
        for (int id = 1; id <= 40 && !anyDifferent; id++) {
            anyDifferent = !table.dropFor(id, 3, false).id().equals(table.dropFor(1, 3, false).id());
        }
        assertTrue(anyDifferent, "every monster leaving the same item is not a table");
    }

    @Test
    void mostDeathsLeaveNothing() {
        var stingy = new LootTable(DECK, 5L, 20, 100, 0);
        int dropped = 0;
        for (int id = 1; id <= 200; id++) {
            if (stingy.dropFor(id, 1, false) != null) {
                dropped++;
            }
        }
        // Twenty percent of two hundred, give or take the draw.
        assertTrue(dropped > 20 && dropped < 60, "dropped " + dropped + " of 200");
    }

    @Test
    void theBossAlwaysLeavesSomething() {
        var stingy = new LootTable(DECK, 5L, 0, 100, 0);
        assertNull(stingy.dropFor(3, 1, false), "nothing drops at zero percent");
        assertNotNull(stingy.dropFor(3, 1, true), "except from the thing guarding the way down");
    }

    @Test
    void aDeepItemIsNotFoundInTheFirstRooms() {
        var table = always(1234L);
        for (int id = 1; id <= 60; id++) {
            assertEquals("Blade", table.dropFor(id, 1, false).id(),
                    "Plate has MinDepth 3 and must not appear on the first floor");
        }
        boolean anyPlate = false;
        for (int id = 1; id <= 60 && !anyPlate; id++) {
            anyPlate = "Plate".equals(table.dropFor(id, 3, false).id());
        }
        assertTrue(anyPlate, "and must appear once the floor is deep enough");
    }

    @Test
    void whatIsFoundIsWorthMoreDeeperDown() {
        // One item, so the two depths are certainly comparing the same thing:
        // with a second in the deck a deeper floor could simply have drawn it.
        var growing = new LootTable(List.of(DECK.get(0)), 7L, 100, 100, 50);
        int shallow = growing.dropFor(11, 1, false).value();
        int deep = growing.dropFor(11, 3, false).value();
        assertTrue(deep > shallow, shallow + " -> " + deep);
        // Computed from the depth in one step: two floors at fifty percent is +100%.
        assertEquals(shallow * 2, deep);
    }

    // ---- what a bag comes to ----

    @Test
    void aBagAddsUpWhatIsInIt() {
        var bag = new LootBag();
        bag.take(DECK.get(0), 0, 10);
        bag.take(DECK.get(0), 0, 10);
        bag.take(DECK.get(1), 0, 10);

        assertEquals(20, bag.attackPercent());
        assertEquals(5, bag.armourPercent());
        assertEquals(0, bag.health());
        assertEquals(3, bag.getFound().size());
    }

    @Test
    void theMessageStopsBeingSaid() {
        var bag = new LootBag();
        bag.take(DECK.get(0), 100, 30);

        assertEquals("Blade", bag.noteAt(100));
        assertEquals("Blade", bag.noteAt(129));
        assertEquals("", bag.noteAt(130), "it has been said; the panel goes quiet again");
    }

    // ---- in the game ----

    private static GameObject find(DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().getName().equals(template))
                .findFirst().orElse(null);
    }

    /**
     * A monster killed on a real floor leaves a chest, and walking the hero into
     * it hands over what it held.
     *
     * <p>Driven through the shipped game rather than a fixture, because the two
     * halves that could go wrong are both wiring: that a monster carries the drop
     * at all, and that the chest the data file describes is one the pickup module
     * is attached to.
     */
    @Test
    void aChestIsLeftAndPickedUp() {
        var settings = DungeonSettings.parse("""
                DungeonLoot Drops
                  Template = Chest
                  DropPercent = 100
                  BossDropPercent = 100
                  PickupRange = 14
                  ValuePercentPerDepth = 0
                  NoteFrames = 90
                End
                """);
        var session = Dungeon.newSession(21L, settings);
        var game = session.game();
        game.runHeadless(1);

        var victim = game.getLogic().getObjects().stream()
                .filter(object -> object.getPlayerIndex() != game.getLocalPlayerIndex())
                .filter(object -> object.getBody() != null)
                .findFirst().orElseThrow();
        var where = victim.getPosition();
        game.getLogic().destroyObject(victim);
        game.runHeadless(2);

        var chest = find(game, "Chest");
        assertNotNull(chest, "a monster that always drops should have left something");
        var lying = chest.findModule(LootUpdate.class);
        assertNotNull(lying);
        assertNotNull(lying.getHolding(), "and it should be holding something");
        var item = lying.getHolding();

        // Walk the hero onto it. Placed rather than ordered: this is about the
        // pickup, not about the pathfinder.
        var hero = find(game, "Rogue");
        hero.setPosition(where);
        game.runHeadless(2);

        assertNull(find(game, "Chest"), "the chest is gone the moment it is his");
        assertEquals(List.of(item.id()),
                session.progress().getLoot().getFound().stream().map(Loot::id).toList());
    }

    @Test
    void whatHeFoundIsFeltAndThenLostWithTheRun() {
        var session = Dungeon.newSession(21L);
        var game = session.game();
        game.runHeadless(1);
        var player = game.getLogic().getRtsPlayer(game.getLocalPlayerIndex());
        float plain = player.getWeaponDamageBonus();

        session.progress().getLoot().take(
                new Loot("Blade", "Blade", "", LootKind.ATTACK, 25, 10, 1), 0, 30);
        game.runHeadless(2);
        assertTrue(player.getWeaponDamageBonus() > plain,
                "a sword he found should reach the arrow he looses");

        game.getLogic().destroyObject(find(game, "Rogue"));
        game.runHeadless(DungeonSettings.load().respawnDelayFrames() + 4);
        assertTrue(session.progress().getLoot().getFound().isEmpty(),
                "a new run starts with nothing, what he found included");
        assertEquals(plain, player.getWeaponDamageBonus(), 0.0001f,
                "and the bonus goes with it rather than outliving him");
    }

    @Test
    void theFileDecidesWhatCanBeFound() {
        assertFalse(SHIPPED.loot().isEmpty(), "the shipped game leaves something behind");
        assertEquals("Chest", SHIPPED.lootTemplate());
        for (var item : SHIPPED.loot()) {
            assertFalse(item.name().isBlank(), item.id() + " has nothing to say for itself");
            assertTrue(item.value() > 0, item.id() + " is worth nothing");
        }
    }
}
