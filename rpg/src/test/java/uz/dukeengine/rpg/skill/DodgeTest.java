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
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.Death;
import uz.dukeengine.core.module.DeathType;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.rpg.RpgWorld;
import uz.dukeengine.rpg.Seal;

/** A roll of 16 over 9 frames, out of harm's way for 7, through creatures, short of stone, once in 75 frames (E80). */
class DodgeTest {

    private static Skill dodge() {
        var d = Skill.DEFAULTS;
        return new Skill("Hero", ' ', SkillEffect.DODGE, 0f, 0f, 0f, 0f, 16f, 0f, 0, 0, 9, 7, d.slowFrames(), 75,
                0, d.maxRank(), d.levelPerRank(), 0, 0, 0, "", d.icon(), d.look(), "Roll", d.castSeconds(), d.name(),
                d.blurb(), 0f, 0f, d.healBelowPercent(), d.summons(), d.maxSummoned(), d.summonExperiencePercent(), 0,
                d.manaRegen());
    }

    private static RpgWorld world(boolean wall) {
        var world = new RpgWorld(Map.of("Hero", List.of(dodge())), Seal.NONE)
                .template("Hero", new ActiveBody.Data(100f), new SkillBook.Data(), new StatusUpdate.Data())
                .template("Skeleton", new ActiveBody.Data(100f));
        var grid = new PathGrid(30, 30);
        if (wall) {
            for (int cy = 0; cy < 30; cy++) {
                grid.setBlocked(11, cy, true); // stone from x 110
            }
        }
        world.setPathGrid(grid);
        return world;
    }

    private static void strike(GameObject hero) {
        hero.getBody().damage(10f, DamageType.NORMAL, new Death(DeathType.NORMAL, null, -1));
    }

    @Test
    void heRollsThroughTheLineOutOfHarmsWayForItsFirstFrames() {
        var world = world(false);
        var hero = world.put("Hero", world.heroes, 100f, 100f);
        world.put("Skeleton", world.monsters, 108f, 100f);
        world.run(1);
        var book = hero.findModule(SkillBook.class);

        assertTrue(book.cast(' ', 1, null, new Coord3D(200f, 100f, 0f)));
        world.run(5);
        strike(hero);
        assertEquals(100f, hero.getBody().getHealth(), "a blow on its fifth frame takes nothing");
        world.run(4);
        strike(hero);
        assertEquals(90f, hero.getBody().getHealth(), "one on its ninth does");
        assertEquals(116f, hero.getPosition().x(), 1e-3f, "sixteen on, through the skeleton");
        assertFalse(book.cast(' ', 1, null, new Coord3D(200f, 100f, 0f)), "again within its cooldown: refused");
    }

    @Test
    void intoStoneItStopsShortAndStaggeredHeCannot() {
        var world = world(true);
        var hero = world.put("Hero", world.heroes, 100f, 100f);
        world.run(1);
        var book = hero.findModule(SkillBook.class);
        assertTrue(book.cast(' ', 1, null, new Coord3D(200f, 100f, 0f)));
        world.run(10);
        assertTrue(hero.getPosition().x() < 110f && hero.getPosition().x() > 105f, "at the stone: " + hero.getPosition());

        var stunned = world.put("Hero", world.heroes, 50f, 50f);
        world.run(1);
        stunned.findModule(StatusUpdate.class).apply(ObjectStatus.DISABLED, 30);
        assertFalse(stunned.findModule(SkillBook.class).cast(' ', 1, null, new Coord3D(200f, 50f, 0f)));
    }
}
