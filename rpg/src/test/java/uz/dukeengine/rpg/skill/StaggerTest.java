package uz.dukeengine.rpg.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.combat.module.StatusUpdate;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.Death;
import uz.dukeengine.core.module.DeathType;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.rpg.RpgWorld;
import uz.dukeengine.rpg.Seal;

/** A hero's stagger is a passive of his book: a heavy or critical blow stuns him at his flinch, then not again a while. */
class StaggerTest {

    /** Stuns 11 frames at a blow of 20 or more, his flinch {@code Hurt}, and no second for 36 frames after. */
    private static Skill stagger() {
        var d = Skill.DEFAULTS;
        return new Skill("Knight", 'E', SkillEffect.STAGGER, 20f, 0f, 0f, 0f, 0f, 0f, 0, 0, 36, d.tickFrames(),
                d.slowFrames(), d.cooldownFrames(), d.cooldownPerLevel(), d.maxRank(), d.levelPerRank(), 0, 0, 0,
                "", d.icon(), d.look(), "Hurt", d.castSeconds(), d.name(), d.blurb(), 0f, 0f, d.healBelowPercent(),
                d.summons(), d.maxSummoned(), d.summonExperiencePercent(), 11, d.manaRegen());
    }

    private static void strike(GameObject hero, float amount, boolean critical) {
        hero.getBody().damage(amount, DamageType.NORMAL, new Death(DeathType.NORMAL, null, -1, critical));
    }

    @Test
    void aHeavyOrCriticalBlowStaggersHimForItsFramesAndNotAgainWhileHeIsSpared() {
        var world = new RpgWorld(Map.of("Knight", List.of(stagger())), Seal.NONE)
                .template("Knight", new ActiveBody.Data(1000f), new SkillBook.Data(), new StatusUpdate.Data());
        var hero = world.put("Knight", world.heroes, 0f, 0f);
        world.run(1);

        strike(hero, 5f, false);
        assertFalse(hero.hasStatus(ObjectStatus.DISABLED), "a light blow does nothing");
        assertNull(hero.getBusyClip());

        strike(hero, 1f, true);
        assertTrue(hero.hasStatus(ObjectStatus.DISABLED), "a critical blow, however small, staggers");
        assertEquals("Hurt", hero.getBusyClip(), "at his flinch");
        world.run(10);
        assertTrue(hero.hasStatus(ObjectStatus.DISABLED));
        assertEquals("Hurt", hero.getBusyClip());
        world.run(2);
        assertFalse(hero.hasStatus(ObjectStatus.DISABLED), "eleven frames, and free");
        assertNull(hero.getBusyClip(), "the flinch ends with it");

        strike(hero, 50f, false);
        assertFalse(hero.hasStatus(ObjectStatus.DISABLED), "a heavy blow while he is spared does nothing");
        world.run(40);
        strike(hero, 50f, false);
        assertTrue(hero.hasStatus(ObjectStatus.DISABLED), "and once he is not, it staggers him");
    }
}
