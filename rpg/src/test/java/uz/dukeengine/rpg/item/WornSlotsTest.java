package uz.dukeengine.rpg.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Worn slots by kind and a backpack beside them: only what is worn acts, and each move between them. */
class WornSlotsTest {

    private static final List<String> SLOTS = List.of("Qurol", "Zirh", "Uzuk");
    private static final Loot SWORD = worn("sword", Stat.ATTACK, 10, "Qurol");
    private static final Loot AXE = worn("axe", Stat.ATTACK, 25, "Qurol");
    private static final Loot RING = worn("ring", Stat.HEALTH, 50, "Uzuk");
    private static final Loot KEY = new Loot("key", "Key", "", "KEY", 0, 0, 1, "", "", 0, 0, 1, ItemUse.UNLOCK, "");

    private static Loot worn(String id, String kind, int value, String slot) {
        return new Loot(id, id, "", kind, value, 1, 1, "", "", 0, 0, 1, ItemUse.NONE, "", slot);
    }

    /** A backpack of {@code backpack} places: places 0.. are its, then Qurol, Zirh, Uzuk. */
    private static LootBag bag(int backpack) {
        return new LootBag(SLOTS, backpack, LootBag.JOIN, LootBag.TOP_LEVEL);
    }

    @Test
    void aThingPickedUpIsWornWhereItsSlotIsEmptyAndCarriedOtherwise() {
        var bag = bag(10);
        assertTrue(bag.take(SWORD, 0, 0));
        assertTrue(bag.take(AXE, 0, 0));
        assertTrue(bag.take(KEY, 0, 0));

        assertSame(SWORD, bag.at(bag.wornPlace("Qurol")), "the first weapon worn");
        assertSame(AXE, bag.at(0), "the second carried");
        assertSame(KEY, bag.at(1), "a key is never worn");
        assertEquals(10, bag.attackPercent(), "only what is worn acts");
        assertEquals(13, bag.slots().size(), "the backpack's places, then the worn slots'");
        assertTrue(bag.holds(ItemUse.UNLOCK), "the key carried is held");
    }

    @Test
    void aThingInTheBackpackIsPutOnAndWhatWasWornThereComesBack() {
        var bag = bag(10);
        bag.take(SWORD, 0, 0);
        bag.take(AXE, 0, 0);

        assertEquals(LootBag.Shift.WORN, bag.shift(0));
        assertSame(AXE, bag.at(bag.wornPlace("Qurol")));
        assertSame(SWORD, bag.at(0), "swapped into the place the axe came from");
        assertEquals(25, bag.attackPercent());

        bag.take(KEY, 0, 0);
        assertEquals(LootBag.Shift.NOT_WEARABLE, bag.shift(1), "a key names no slot");
        assertEquals(LootBag.Shift.NOTHING, bag.shift(5));
    }

    @Test
    void aWornThingIsTakenOffIntoTheBackpackAndKeptOnWhenItIsFull() {
        var bag = bag(1);
        bag.take(RING, 0, 0);
        int ring = bag.wornPlace("Uzuk");
        bag.take(KEY, 0, 0);

        assertEquals(LootBag.Shift.FULL, bag.shift(ring), "no room in the backpack");
        assertSame(RING, bag.at(ring));
        assertEquals(50, bag.health());

        bag.remove(0);
        assertEquals(LootBag.Shift.TAKEN_OFF, bag.shift(ring));
        assertSame(RING, bag.at(0));
        assertNull(bag.at(ring));
        assertEquals(0, bag.health(), "carried, it acts on nothing");
    }

    @Test
    void aFullBackpackRefusesWhatCannotBeWornAndAWornThingIsDroppedByItsPlace() {
        var bag = bag(1);
        bag.take(KEY, 0, 0);
        assertTrue(bag.take(SWORD, 0, 0), "worn: its slot was empty");
        assertFalse(bag.take(AXE, 0, 0), "a second weapon has nowhere to go");
        assertTrue(bag.isFull());

        int weapon = bag.wornPlace("Qurol");
        assertSame(SWORD, bag.remove(weapon), "a worn thing is taken out by its place, as a drop takes it");
        assertEquals(0, bag.attackPercent());
    }

    @Test
    void aSavedBagIsPutBackPlaceByPlace() {
        var bag = bag(10);
        assertTrue(bag.put(3, AXE));
        assertTrue(bag.put(bag.wornPlace("Uzuk"), RING));
        assertFalse(bag.put(bag.wornPlace("Zirh"), SWORD), "a weapon is not worn as armour");
        assertFalse(bag.put(3, SWORD), "a place already taken");

        assertSame(AXE, bag.at(3));
        assertEquals(50, bag.health());
        assertEquals(0, bag.attackPercent());
    }

    @Test
    void aBagGivenNoSlotsIsTheBagItAlwaysWasEverySlotActing() {
        var bag = new LootBag(6, LootBag.JOIN, LootBag.TOP_LEVEL);
        bag.take(SWORD, 0, 0);
        bag.take(AXE, 0, 0);

        assertFalse(bag.wears());
        assertEquals(35, bag.attackPercent(), "everything in it acts");
        assertEquals(6, bag.slots().size());
        assertEquals(-1, bag.wornPlace("Qurol"));
        assertEquals(LootBag.Shift.NOT_WEARABLE, bag.shift(0));
    }
}
