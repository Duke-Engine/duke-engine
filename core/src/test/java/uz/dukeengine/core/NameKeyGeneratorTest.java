package uz.dukeengine.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NameKeyGeneratorTest {

    private NameKeyGenerator gen;

    @BeforeEach
    void setUp() {
        gen = new NameKeyGenerator();
        gen.init();
    }

    @Test
    void sameNameGivesSameKey() {
        var a = gen.nameToKey("Crusader");
        var b = gen.nameToKey("Crusader");
        assertEquals(a, b);
        assertSame(a, b);
    }

    @Test
    void differentNamesGiveDifferentKeys() {
        var a = gen.nameToKey("Crusader");
        var b = gen.nameToKey("Overlord");
        assertNotEquals(a, b);
    }

    @Test
    void keyRoundTripsToName() {
        var key = gen.nameToKey("Paladin");
        assertEquals("Paladin", gen.keyToName(key));
    }

    @Test
    void invalidKeyIsRecognised() {
        assertFalse(NameKeyType.INVALID.isValid());
        assertEquals("", gen.keyToName(NameKeyType.INVALID));
        assertTrue(gen.nameToKey("anything").isValid());
    }

    @Test
    void lowercaseKeyFoldsCase() {
        assertEquals(gen.nameToLowercaseKey("Crusader"), gen.nameToLowercaseKey("CRUSADER"));
    }

    @Test
    void resetClearsCatalogue() {
        var before = gen.nameToKey("First");
        gen.reset();
        var after = gen.nameToKey("Second");
        // After reset, ids restart from 1, so the first new name reuses id 1.
        assertEquals(before.id(), after.id());
    }
}
