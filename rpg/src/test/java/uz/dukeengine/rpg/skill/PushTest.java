package uz.dukeengine.rpg.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.combat.module.StatusUpdate;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.rpg.RpgWorld;
import uz.dukeengine.rpg.Seal;

/** A push out from him: those within its radius thrown its distance along their own lines, stunned; short of stone (E82). */
class PushTest {

    /** Radius 60, Distance 30 over 10 frames, an arc 5 high, a stun of 20. */
    private static Skill push() {
        var d = Skill.DEFAULTS;
        return new Skill("Hero", 'R', SkillEffect.PUSH, 0f, 0f, 60f, 0f, 30f, 5f, 0, 0, 10, d.tickFrames(),
                d.slowFrames(), 0, 0, d.maxRank(), d.levelPerRank(), 0, 0, 0, "", d.icon(), d.look(), d.castAnim(),
                d.castSeconds(), d.name(), d.blurb(), 0f, 0f, d.healBelowPercent(), d.summons(), d.maxSummoned(),
                d.summonExperiencePercent(), 20, d.manaRegen());
    }

    private static RpgWorld world(boolean wall) {
        var world = new RpgWorld(Map.of("Hero", List.of(push())), Seal.NONE)
                .template("Hero", new ActiveBody.Data(100f), new SkillBook.Data())
                .template("Skeleton", new ActiveBody.Data(100f), new StatusUpdate.Data());
        var grid = new PathGrid(40, 40);
        if (wall) {
            for (int cy = 0; cy < 40; cy++) {
                grid.setBlocked(16, cy, true); // stone from x 160
            }
        }
        world.setPathGrid(grid);
        return world;
    }

    @Test
    void thoseWithinItsRadiusAreThrownOutAlongTheirOwnLinesAndStunned() {
        var world = world(false);
        var hero = world.put("Hero", world.heroes, 100f, 100f);
        var near = world.put("Skeleton", world.monsters, 110f, 100f);
        var mid = world.put("Skeleton", world.monsters, 100f, 140f);
        var far = world.put("Skeleton", world.monsters, 190f, 100f);
        world.run(1);

        assertTrue(hero.findModule(SkillBook.class).cast('R', 1));
        world.run(11);
        assertEquals(140f, near.getPosition().x(), 1e-3f, "ten out, thrown thirty further along its line");
        assertEquals(170f, mid.getPosition().y(), 1e-3f, "forty out, thirty further along its own line");
        assertTrue(near.hasStatus(ObjectStatus.DISABLED) && mid.hasStatus(ObjectStatus.DISABLED), "stunned as they land");
        assertEquals(190f, far.getPosition().x(), 1e-3f, "ninety out: untouched");
        assertFalse(far.hasStatus(ObjectStatus.DISABLED));
    }

    @Test
    void oneAgainstAWallStopsShortOfIt() {
        var world = world(true);
        var hero = world.put("Hero", world.heroes, 120f, 100f);
        var skeleton = world.put("Skeleton", world.monsters, 145f, 100f);
        world.run(1);
        assertTrue(hero.findModule(SkillBook.class).cast('R', 1));
        world.run(11);
        assertTrue(skeleton.getPosition().x() < 160f && skeleton.getPosition().x() > 150f,
                "short of the stone: " + skeleton.getPosition());
    }
}
