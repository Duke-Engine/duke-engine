package uz.dukeengine.rpg.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.LogicRandom;

/** The loot table draws from the world's own random numbers; a bag adds up any word an item names. */
class LootTest {

    private static final Loot SWORD = new Loot("sword", "Sword", "", Stat.ATTACK, 10, 3, 1);
    private static final Loot CROWN = new Loot("crown", "Crown", "", "LUCK", 4, 1, 3);
    private static final Loot KEY = new Loot("key", "Key", "", "KEY", 0, 0, 1, "", "", 0, 0, 1, ItemUse.UNLOCK, "");

    private static List<String> drops(long seed) {
        var table = new LootTable(List.of(SWORD, CROWN), 50, 100, 10);
        var random = new LogicRandom(seed);
        var out = new ArrayList<String>();
        for (int death = 0; death < 200; death++) {
            var item = table.dropFor(random, 1 + death % 5, death % 7 == 0);
            out.add(item == null ? "-" : item.id() + ":" + item.value());
        }
        return out;
    }

    @Test
    void theSameStreamDropsTheSameThings() {
        assertEquals(drops(42L), drops(42L));
        assertTrue(drops(42L).contains("-"), "an ordinary death mostly leaves nothing");
    }

    @Test
    void anItemIsFoundFromItsTierAndWorthMoreFurtherIn() {
        var always = new LootTable(List.of(CROWN), 100, 100, 50);
        var random = new LogicRandom(7L);
        assertNull(always.dropFor(random, 2, false), "below its tier");
        assertEquals(8, always.dropFor(random, 3, false).value(), "half as much again for each tier past the first");
        assertNull(new LootTable(List.of(SWORD), 0, 0, 0).dropFor(random, 1, true));
    }

    @Test
    void threeAlikeJoinAndAKeyNever() {
        var bag = new LootBag(6, 3, 3);
        for (int i = 0; i < 3; i++) {
            assertTrue(bag.take(SWORD, 0, 0));
            assertTrue(bag.take(KEY, 0, 0));
        }
        assertEquals(4, bag.getFound().size(), "one sword of the second level, three keys");
        assertEquals(30, bag.total(Stat.ATTACK));
        assertEquals(30, bag.attackPercent());
        assertTrue(bag.holds(ItemUse.UNLOCK));
        bag.removeAll(ItemUse.UNLOCK);
        assertFalse(bag.holds(ItemUse.UNLOCK));
    }

    @Test
    void aWordOfTheGamesOwnIsAddedUpLikeAnyOther() {
        var bag = new LootBag();
        bag.take(CROWN, 0, 0);
        bag.take(CROWN.worth(6), 0, 0);
        assertEquals(10, bag.total("LUCK"));
        assertEquals(0, bag.total(Stat.ATTACK));
        assertNotNull(bag.at(0));
    }
}
