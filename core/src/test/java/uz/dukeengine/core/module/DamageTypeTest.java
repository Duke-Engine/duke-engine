package uz.dukeengine.core.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.data.Binder;
import uz.dukeengine.core.data.DukeText;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ThingTemplate;

/**
 * A damage type a game names, which the engine has never heard of.
 *
 * <p>It was an enum of five whose own javadoc called itself "a representative subset". A combat table
 * cannot be a subset: every kind the game fires that the engine has no word for collapses onto
 * {@code NORMAL}, and a table whose rows all key one row is not a table.
 */
class DamageTypeTest {

    @Test
    void aTypeIsItsWordAndTwoOfTheSameWordAreOneType() {
        assertSame(DamageType.of("SMALL_ARMS"), DamageType.of("SMALL_ARMS"), "interned, so == is enough");
        assertSame(DamageType.of("small_arms"), DamageType.of("SMALL_ARMS"), "and case says nothing");
        assertSame(DamageType.FLAME, DamageType.of("flame"), "the five the engine names are words too");
        assertNotSame(DamageType.of("POISON"), DamageType.of("RADIATION"));
        assertEquals("SMALL_ARMS", DamageType.of("small_arms").name(), "folded to upper case once");
    }

    @Test
    void anArmourScalesAWordTheEngineHasNeverHeardOf() {
        var armour = new Armor(Map.of(
                DamageType.of("SMALL_ARMS"), 1.0f,      // infantry take rifles in full
                DamageType.ARMOR_PIERCING, 0.1f));      // and shrug off a tank shell

        assertEquals(1.0f, armour.getMultiplier(DamageType.of("SMALL_ARMS")));
        assertEquals(0.1f, armour.getMultiplier(DamageType.ARMOR_PIERCING));
        assertEquals(1.0f, armour.getMultiplier(DamageType.of("POISON")), "unlisted is still full damage");
        assertEquals(1.0f, armour.getMultiplier(DamageType.NORMAL), "and so is the default");
    }

    /** A block naming a type nothing else in the game uses loads; it is a weapon whose damage is unscaled. */
    @Test
    void aBlockMayNameATypeNothingHasRegistered() {
        var body = new Binder().bind(DukeText.parse("""
                ActiveBody
                  MaxHealth = 200
                  Armor = [SMALL_ARMS = 1.0, ARMOR_PIERCING = 0.1, POISON = 0.0]
                End
                """, "infantry.duke").getFirst(), ActiveBody.Data.class);

        assertEquals(Map.of(DamageType.of("SMALL_ARMS"), 1.0f,
                DamageType.ARMOR_PIERCING, 0.1f,
                DamageType.of("POISON"), 0.0f), body.armor());
    }

    /**
     * The same shots in the same order reach the same health, to the bit — which is the whole of why the
     * key is interned rather than hashed, and why what holds the multipliers keeps the order it was
     * written in.
     */
    @Test
    void theSameShotsInTheSameOrderReachTheSameHealth() {
        var written = new LinkedHashMap<DamageType, Float>();
        written.put(DamageType.of("SMALL_ARMS"), 1.0f);
        written.put(DamageType.of("POISON"), 0.33f);
        written.put(DamageType.ARMOR_PIERCING, 0.1f);
        var shots = new DamageType[] {DamageType.of("POISON"), DamageType.ARMOR_PIERCING,
                DamageType.of("SMALL_ARMS"), DamageType.of("RADIATION"), DamageType.of("POISON")};

        assertEquals(Float.floatToIntBits(fought(written, shots)),
                Float.floatToIntBits(fought(written, shots)), "bit for bit, twice");
    }

    private static float fought(Map<DamageType, Float> armour, DamageType[] shots) {
        var body = new ActiveBody(new GameObject(new ObjectId(1), ThingTemplate.named("X").build()),
                new ActiveBody.Data(1000f, armour));
        for (var shot : shots) {
            body.damage(7.3f, shot);
        }
        return body.getHealth();
    }

    /**
     * Two spellings of one word are one type, so an armour that writes both has written the same row
     * twice — and is told so, rather than one of the two quietly winning. The reader's duplicate check
     * is on the key it <em>read</em>, not on the text, which is what lets it see through the spelling.
     */
    @Test
    void anArmourThatWritesOneTypeTwiceIsToldSoHoweverItWasSpelt() {
        var written = """
                ActiveBody
                  MaxHealth = 100
                  Armor = [FLAME = 0.5, flame = 2.0]
                End
                """;
        var error = org.junit.jupiter.api.Assertions.assertThrows(
                uz.dukeengine.core.data.DataException.class,
                () -> new Binder().bind(DukeText.parse(written, "twice.duke").getFirst(),
                        ActiveBody.Data.class));

        assertEquals("twice.duke:3: 'flame' is written twice in 'Armor'", error.getMessage());
    }
}
