package uz.dukeengine.rpg.hero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.combat.module.WeaponUpdate;
import uz.dukeengine.core.math.Hundredths;
import uz.dukeengine.core.module.DamageType;

/** A hero's weapon built for his first level keeps everything it was written with but its blow (E55). */
class HeroWeaponTest {

    @Test
    void aNamedTargetedWeaponKeepsItsNameAndTargets() {
        var rules = new AttributeRules(List.of(
                new Attribute("Strength", "STR", "", "", new Hundredths(1200), new Hundredths(0), new Hundredths(0))),
                100);
        var hero = new HeroAttributes(0, Attributes.of(200), Attributes.of(20));
        var written = new WeaponUpdate.Data(10f, 40f, 30, DamageType.NORMAL, 0f, true, List.of("GROUND", "AIR"),
                0, 6, 90, true, "KnightSword", List.of(), null, 12);

        var built = HeroBuild.weapon(written, hero, rules);

        assertEquals("KnightSword", built.name(), "fired.<name> still sounds per creature");
        assertEquals(List.of("GROUND", "AIR"), built.targets());
        assertEquals(6, built.clipSize());
        assertEquals(12, built.targetScanFrames());
        assertNotEquals(written.damage(), built.damage(), "only the blow is his level's");
    }
}
