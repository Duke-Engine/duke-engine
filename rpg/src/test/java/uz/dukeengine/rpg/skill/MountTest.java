package uz.dukeengine.rpg.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import uz.dukeengine.combat.module.StatusUpdate;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.Death;
import uz.dukeengine.core.module.DeathType;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.rpg.RpgWorld;
import uz.dukeengine.rpg.Seal;

/** A mount in the skill system: twice as quick, off at a blow, a cast refused while riding, none in a town (E89). */
class MountTest {

    private static Skill mount() {
        var d = Skill.DEFAULTS;
        return new Skill("Hero", 'M', SkillEffect.MOUNT, 0f, 0f, 0f, 0f, 0f, 0f, 100, 0, 0, d.tickFrames(),
                d.slowFrames(), 0, 0, d.maxRank(), d.levelPerRank(), 0, 0, 0, "", d.icon(), "", "", d.castSeconds(),
                d.name(), d.blurb(), 0f, 0f, d.healBelowPercent(), d.summons(), d.maxSummoned(),
                d.summonExperiencePercent(), 0, d.manaRegen(), List.of("town"), false, true);
    }

    private static Skill strike() {
        var d = Skill.DEFAULTS;
        return new Skill("Hero", 'Q', SkillEffect.EMPOWER, 0f, 0f, 0f, 0f, 0f, 0f, 10, 0, 30, d.tickFrames(),
                d.slowFrames(), 0, 0, d.maxRank(), d.levelPerRank(), 0, 0, 0, "", d.icon(), "", "", d.castSeconds(),
                d.name(), d.blurb(), 0f, 0f, d.healBelowPercent(), d.summons(), d.maxSummoned(),
                d.summonExperiencePercent(), 0, d.manaRegen());
    }

    private static RpgWorld world() {
        var world = new RpgWorld(Map.of("Hero", List.of(mount(), strike())), Seal.NONE)
                .template("Hero", new ActiveBody.Data(100f), new MoveUpdate.Data(60f), new SkillBook.Data(),
                        new StatusUpdate.Data());
        world.setZones(at -> at.x() < 0f ? Set.of("town") : Set.of());
        return world;
    }

    @Test
    void mountedHeWalksTwiceAsFarAndABlowTakesHimOff() {
        var world = world();
        var hero = world.put("Hero", world.heroes, 100f, 100f);
        world.run(1);
        var book = hero.findModule(SkillBook.class);

        assertTrue(book.cast('M', 1));
        world.run(1);
        assertTrue(book.isRiding());
        hero.getLocomotor().moveAlong(0);
        world.run(30);
        assertEquals(220f, hero.getPosition().x(), 1e-2f, "120 in a second: twice his sixty");

        assertFalse(book.cast('Q', 1), "a cast while he rides is refused, as the mount says");
        hero.getBody().damage(1f, DamageType.NORMAL, new Death(DeathType.NORMAL, null, -1));
        assertFalse(book.isRiding(), "a blow of one takes him off");
        float x = hero.getPosition().x();
        world.run(30);
        assertEquals(x + 60f, hero.getPosition().x(), 1e-2f, "his own sixty again");
    }

    @Test
    void inATownItIsRefusedWithNothingSpent() {
        var world = world();
        var hero = world.put("Hero", world.heroes, -50f, 100f);
        world.run(1);
        var book = hero.findModule(SkillBook.class);
        assertFalse(book.cast('M', 1), "not in a town");
        assertFalse(book.isRiding());
        hero.setPosition(new Coord3D(50f, 100f, 0f));
        assertTrue(book.cast('M', 1), "out of it, and nothing was spent: no cooldown in the way");
    }

    @Test
    void aMoveSpeedBuffQuickensHisLegsWhileItHolds() {
        var world = world();
        var hero = world.put("Hero", world.heroes, 100f, 100f);
        world.run(1);
        var book = hero.findModule(SkillBook.class);
        book.give(SkillEffect.MOVE_SPEED, 9, 50, world.getFrame() + 10);
        assertEquals(1.5f, book.speedShare(), 1e-6f);
        world.run(20);
        assertEquals(1f, book.speedShare(), 1e-6f, "run out");
    }
}
