package uz.duke.core.thing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class KindTest {

    @Test
    void theSameNameAlwaysYieldsTheSameInstance() {
        // Interning is what keeps isKindOf an identity check rather than a
        // string compare, so it has to hold across call sites.
        assertSame(Kind.of("STRUCTURE"), Kind.of("STRUCTURE"));
        assertNotSame(Kind.of("STRUCTURE"), Kind.of("INFANTRY"));
    }

    @Test
    void namesAreCaseInsensitiveAndNormalisedToUpperCase() {
        assertSame(Kind.of("STRUCTURE"), Kind.of("structure"));
        assertSame(Kind.of("STRUCTURE"), Kind.of("Structure"));
        assertEquals("STRUCTURE", Kind.of("structure").name());
    }

    @Test
    void aGameCanInventItsOwnVocabulary() {
        // The engine ships no vocabulary at all; anything INI names works.
        var template = ThingTemplate.named("Wizard")
                .kindOf(Kind.of("SPELLCASTER"), Kind.of("FLYING"))
                .build();

        org.junit.jupiter.api.Assertions.assertTrue(template.isKindOf(Kind.of("SPELLCASTER")));
        org.junit.jupiter.api.Assertions.assertFalse(template.isKindOf(Kind.of("UNDEAD")));
        assertEquals(2, template.kindOf().size());
    }
}
