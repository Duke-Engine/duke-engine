package uz.dukeengine.rpg.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.rpg.RpgWorld;
import uz.dukeengine.rpg.Seal;

/** Two auras of one kind on one bearer: the stronger is what is lent, whichever stands later in its file. */
class SameKindAurasTest {

    private static Skill aura(char key, SkillEffect kind, int boost, float radius) {
        var d = Skill.DEFAULTS;
        return new Skill("Bearer", key, kind, d.damage(), d.damagePerLevel(), radius, d.range(), d.distance(),
                d.hitWidth(), boost, d.boostPerLevel(), d.durationFrames(), d.tickFrames(), d.slowFrames(),
                d.cooldownFrames(), d.cooldownPerLevel(), d.maxRank(), d.levelPerRank(), d.windUpFrames(),
                d.manaCost(), d.manaCostPerLevel(), d.projectile(), d.icon(), d.look(), d.castAnim(),
                d.castSeconds(), d.name(), d.blurb(), d.projectileSpeed(), d.heal(), d.healBelowPercent(),
                d.summons(), d.maxSummoned(), d.summonExperiencePercent(), d.stunFrames(), d.manaRegen());
    }

    private static RpgWorld world(Skill... auras) {
        return new RpgWorld(Map.of("Bearer", List.of(auras)), Seal.NONE)
                .template("Bearer", new ActiveBody.Data(100f), new SkillBook.Data())
                .template("Ally", new ActiveBody.Data(100f), new SkillBook.Data());
    }

    @Test
    void theStrongerIsLentWhetherItComesFirstOrLastInTheFile() {
        for (var auras : List.of(
                List.of(aura('Q', SkillEffect.DAMAGE_AURA, 40, 50f), aura('W', SkillEffect.DAMAGE_AURA, 25, 50f)),
                List.of(aura('Q', SkillEffect.DAMAGE_AURA, 25, 50f), aura('W', SkillEffect.DAMAGE_AURA, 40, 50f)))) {
            var world = world(auras.toArray(Skill[]::new));
            world.put("Bearer", world.monsters, 0f, 0f);
            var ally = world.put("Ally", world.monsters, 30f, 0f);
            world.run(RpgWorld.RULES.auraPulseFrames());
            assertEquals(40, SkillBook.auraOn(ally, SkillEffect.DAMAGE_AURA));
        }
    }

    @Test
    void whoStandsBeyondTheStrongerButWithinTheWeakerKeepsTheWeaker() {
        var world = world(aura('Q', SkillEffect.DAMAGE_AURA, 40, 20f), aura('W', SkillEffect.DAMAGE_AURA, 25, 60f));
        world.put("Bearer", world.monsters, 0f, 0f);
        var near = world.put("Ally", world.monsters, 10f, 0f);
        var far = world.put("Ally", world.monsters, 50f, 0f);
        world.run(RpgWorld.RULES.auraPulseFrames());
        assertEquals(40, SkillBook.auraOn(near, SkillEffect.DAMAGE_AURA));
        assertEquals(25, SkillBook.auraOn(far, SkillEffect.DAMAGE_AURA));
    }

    @Test
    void aurasOfOtherKindsAreGivenInTheOrderOfTheFileAsBefore() {
        var world = world(aura('Q', SkillEffect.DAMAGE_AURA, 10, 50f), aura('W', SkillEffect.LIFESTEAL_AURA, 5, 50f),
                aura('E', SkillEffect.DAMAGE_AURA, 30, 50f));
        world.put("Bearer", world.monsters, 0f, 0f);
        var ally = world.put("Ally", world.monsters, 30f, 0f);
        world.run(RpgWorld.RULES.auraPulseFrames());
        assertEquals(List.of(SkillEffect.DAMAGE_AURA, SkillEffect.LIFESTEAL_AURA),
                ally.findModule(SkillBook.class).getBuffs().stream().map(Buff::kind).toList());
        assertEquals(30, SkillBook.auraOn(ally, SkillEffect.DAMAGE_AURA));
    }
}
