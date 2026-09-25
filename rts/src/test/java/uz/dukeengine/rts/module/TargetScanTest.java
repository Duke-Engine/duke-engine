package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.rts.RtsTemplate;

/** How often a weapon with no target looks for one: every frame by default, as the game says otherwise. */
class TargetScanTest {

    private CombatTest.CombatLogic world;
    private GameObject tank;
    private int enemySide;

    private void world(int scanFrames) {
        var factory = new ThingFactory(RtsModules.withDefaults());
        factory.addTemplate(RtsTemplate.named("Tank").module(new ActiveBody.Data(400f))
                .module(new WeaponUpdate.Data(10f, 100f, 15, DamageType.NORMAL)).build());
        world = new CombatTest.CombatLogic(factory);
        world.init();
        world.setTargetScanFrames(scanFrames);
        var us = world.getPlayerList().addPlayer("Us");
        var them = world.getPlayerList().addPlayer("Them");
        us.setRelationshipTo(them, Relationship.ENEMIES);
        them.setRelationshipTo(us, Relationship.ENEMIES);
        tank = world.spawn(world.getThingFactory().findTemplate("Tank"), new Coord3D(100f, 100f, 0f), us.getIndex());
        enemySide = them.getIndex();
    }

    /** The frame an enemy that came into range is first taken, from the frame it came. */
    private int framesToTake() {
        world.update();
        var enemy = world.spawn(world.getThingFactory().findTemplate("Tank"), new Coord3D(150f, 100f, 0f), enemySide);
        for (int frame = 1; frame <= 30; frame++) {
            world.update();
            if (enemy.getId().equals(tank.findModule(WeaponUpdate.class).getTarget())) {
                return frame;
            }
        }
        return -1;
    }

    @Test
    void byDefaultAnEnemyInRangeIsTakenTheNextFrame() {
        world(1);
        assertEquals(1, framesToTake());
    }

    @Test
    void lookingEightFramesApartItIsTakenWithinEightAndNotBeforeItsLook() {
        world(8);
        int taken = framesToTake();
        assertEquals(0, Math.floorMod(world.getFrame() - 1 + tank.getId().value(), 8),
                "taken on its own frame of the round");
        assertEquals(true, taken >= 1 && taken <= 8, "within eight frames: " + taken);
    }
}
