package uz.dukeengine.rpg.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.rpg.RpgWorld;
import uz.dukeengine.rpg.Seal;

/**
 * An aura is pushed: its bearer gives it to those of its side within reach every pulse, and each reads what it holds
 * off its own list — kept for the buff's life after it leaves reach, and never given across a seal or to an enemy.
 */
class PushedAuraTest {

    private static Skill aura(SkillEffect kind, int boost, float radius) {
        var d = Skill.DEFAULTS;
        return new Skill("Bearer", 'Q', kind, d.damage(), d.damagePerLevel(), radius, d.range(), d.distance(),
                d.hitWidth(), boost, d.boostPerLevel(), d.durationFrames(), d.tickFrames(), d.slowFrames(),
                d.cooldownFrames(), d.cooldownPerLevel(), d.maxRank(), d.levelPerRank(), d.windUpFrames(),
                d.manaCost(), d.manaCostPerLevel(), d.projectile(), d.icon(), d.look(), d.castAnim(),
                d.castSeconds(), d.name(), d.blurb(), d.projectileSpeed(), d.heal(), d.healBelowPercent(),
                d.summons(), d.maxSummoned(), d.summonExperiencePercent(), d.stunFrames(), d.manaRegen());
    }

    private static RpgWorld world(Seal seal) {
        return new RpgWorld(Map.of("Bearer", List.of(aura(SkillEffect.DAMAGE_AURA, 25, 50f))), seal)
                .template("Bearer", new ActiveBody.Data(100f), new SkillBook.Data())
                .template("Ally", new ActiveBody.Data(100f), new SkillBook.Data());
    }

    @Test
    void anAllyInReachIsGivenItOnAPulseAndKeepsItForTheBuffsLifeAfterLeaving() {
        var world = world(Seal.NONE);
        world.put("Bearer", world.monsters, 0f, 0f);
        var ally = world.put("Ally", world.monsters, 30f, 0f);
        var enemy = world.put("Ally", world.heroes, 30f, 0f);
        world.run(RpgWorld.RULES.auraPulseFrames());
        assertEquals(25, SkillBook.auraOn(ally, SkillEffect.DAMAGE_AURA));
        assertEquals(1.25f, ally.findModule(SkillBook.class).damageMultiplier(), 1e-6f);
        assertEquals(0, SkillBook.auraOn(enemy, SkillEffect.DAMAGE_AURA), "never another side's");

        ally.setPosition(new Coord3D(200f, 0f, 0f));
        world.run(RpgWorld.RULES.buffLifeFrames() - RpgWorld.RULES.auraPulseFrames());
        assertEquals(25, SkillBook.auraOn(ally, SkillEffect.DAMAGE_AURA), "held while the buff lives");
        world.run(RpgWorld.RULES.buffLifeFrames());
        assertEquals(0, SkillBook.auraOn(ally, SkillEffect.DAMAGE_AURA), "and let go after");
    }

    @Test
    void aSealBetweenThemLendsNothing() {
        Seal wall = (w, a, b) -> (a.x() < 20f) != (b.x() < 20f);
        var world = world(wall);
        world.put("Bearer", world.monsters, 0f, 0f);
        var beyond = world.put("Ally", world.monsters, 30f, 0f);
        var beside = world.put("Ally", world.monsters, 10f, 0f);
        world.run(RpgWorld.RULES.auraPulseFrames());
        assertEquals(0, SkillBook.auraOn(beyond, SkillEffect.DAMAGE_AURA));
        assertEquals(25, SkillBook.auraOn(beside, SkillEffect.DAMAGE_AURA));
    }
}
