package uz.dukeengine.action;

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
import uz.dukeengine.core.pathfind.TraversalLink;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.rpg.skill.Skill;
import uz.dukeengine.rpg.skill.SkillBook;
import uz.dukeengine.rpg.skill.SkillEffect;

/** A roll of 16 over 9 frames, out of harm's way for 7, through creatures, short of stone, once in 75 frames (E80). */
class DodgeTest {

    private static Skill dodge() {
        return new Skill("Hero", ' ', SkillEffect.MODULE, 0f, 0f, 0f, 0f, 16f, 0f, 0, 0, 9, 7, 0, 75,
                0, 4, 0, 0, 0, 0, "", "", "", "Roll", 0f, "", "", 0f, 0f, 0, Map.of(), 0, 0, 0, 0);
    }

    private static ActionWorld world(boolean wall) {
        var world = new ActionWorld(Map.of("Hero", List.of(dodge())))
                .template("Hero", new ActiveBody.Data(100f), new SkillBook.Data(), new StatusUpdate.Data(),
                        new Dodge.Data())
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

    /** A dodge whose line meets a low way across is taken over it (E81). */
    @Test
    void aDodgeAcrossALowWayGoesOverIt() {
        var world = world(true);
        world.setTraversalLinks(List.of(new TraversalLink(new Coord3D(105f, 100f, 0f),
                new Coord3D(125f, 100f, 0f), "vault", 12, 6f, "Vault", false, true)));
        var hero = world.put("Hero", world.heroes, 100f, 100f);
        world.run(1);
        assertTrue(hero.findModule(SkillBook.class).cast(' ', 1, null, new Coord3D(200f, 100f, 0f)));
        world.run(10);
        assertEquals(125f, hero.getPosition().x(), 1e-3f, "over the low wall, at its far end");
    }

    /** One with no module that casts it is refused, nothing spent. */
    @Test
    void withoutTheModuleItIsRefused() {
        var world = new ActionWorld(Map.of("Plain", List.of(dodge())))
                .template("Plain", new ActiveBody.Data(100f), new SkillBook.Data());
        var plain = world.put("Plain", world.heroes, 100f, 100f);
        world.run(1);
        assertFalse(plain.findModule(SkillBook.class).cast(' ', 1, null, new Coord3D(200f, 100f, 0f)));
        assertEquals(100f, plain.getPosition().x(), 1e-3f);
    }
}
