package uz.dukeengine.rpg.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.event.ObjectDied;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.Death;
import uz.dukeengine.core.module.DeathType;
import uz.dukeengine.rpg.RpgWorld;
import uz.dukeengine.rpg.Seal;
import uz.dukeengine.rpg.hero.GrowableBody;

/** A foe who cannot be killed, only beaten; and a growable body that honours its damage scale. */
class UnyieldingTest {

    private static Skill unyielding(int belowPercent) {
        var d = Skill.DEFAULTS;
        return new Skill("Cursed", 'Q', SkillEffect.UNYIELDING, d.damage(), d.damagePerLevel(), d.radius(), d.range(),
                d.distance(), d.hitWidth(), d.boostPercent(), d.boostPerLevel(), d.durationFrames(), d.tickFrames(),
                d.slowFrames(), d.cooldownFrames(), d.cooldownPerLevel(), d.maxRank(), d.levelPerRank(),
                d.windUpFrames(), d.manaCost(), d.manaCostPerLevel(), d.projectile(), d.icon(), d.look(),
                d.castAnim(), d.castSeconds(), d.name(), d.blurb(), d.projectileSpeed(), d.heal(), belowPercent,
                d.summons(), d.maxSummoned(), d.summonExperiencePercent(), d.stunFrames(), d.manaRegen());
    }

    @Test
    void struckForTenTimesItsHealthItStandsAtAQuarterBeatenAndNothingDies() {
        var world = new RpgWorld(Map.of("Cursed", List.of(unyielding(25))), Seal.NONE)
                .template("Cursed", new ActiveBody.Data(100f), new SkillBook.Data())
                .template("Hero", new ActiveBody.Data(100f));
        var died = new ArrayList<ObjectDied>();
        world.onDied(died::add);
        var cursed = world.put("Cursed", world.monsters, 0f, 0f);
        var hero = world.put("Hero", world.heroes, 10f, 0f);
        var book = cursed.findModule(SkillBook.class);
        var blow = new Death(DeathType.NORMAL, hero.getId(), world.heroes);
        world.run(1); // told it is made
        assertFalse(book.isBeaten());

        cursed.getBody().damage(1000f, DamageType.NORMAL, blow);
        assertEquals(25f, cursed.getBody().getHealth(), 1e-3f, "held at a quarter");
        assertTrue(book.isBeaten(), "beaten on the frame the blow landed");

        world.run(5);
        cursed.getBody().damage(50f, DamageType.NORMAL, blow);
        world.run(5);
        assertEquals(25f, cursed.getBody().getHealth(), 1e-3f, "a second blow takes nothing below it");
        assertTrue(book.isBeaten());
        assertTrue(died.isEmpty(), "no death posted");
        assertFalse(cursed.isEffectivelyDead());
    }

    @Test
    void aGrowableBodyScaledToNothingTakesNothing() {
        var world = new RpgWorld(Map.of(), Seal.NONE).template("Hero", new ActiveBody.Data(100f));
        var body = new GrowableBody(world.put("Hero", world.heroes, 0f, 0f), new GrowableBody.Data(100f));

        body.setDamageScale(0f);
        body.damage(50f, DamageType.NORMAL);
        assertEquals(100f, body.getHealth(), 1e-6f);

        body.setDamageScale(0.5f);
        body.damage(50f, DamageType.NORMAL);
        assertEquals(75f, body.getHealth(), 1e-6f, "half of it");
    }
}
