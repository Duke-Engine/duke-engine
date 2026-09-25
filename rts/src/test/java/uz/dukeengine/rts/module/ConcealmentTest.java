package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.Concealment;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;

/** A thing kept from a side is no target for it: not acquired, not ordered at, let go the frame it hides. */
class ConcealmentTest {

    /** A Pathfinder's stealth: hidden from every other side while it says so. */
    static final class Stealth extends Module implements Concealment {
        record Data() implements ModuleData {
        }

        boolean hidden = true;

        Stealth(GameObject owner) {
            super(owner);
        }

        @Override
        public boolean hiddenFrom(int player) {
            return hidden && player != getOwner().getPlayerIndex();
        }
    }

    private record Scene(CombatTest.CombatLogic logic, GameObject pathfinder, WeaponUpdate gun) {
    }

    private static Scene scene() {
        var factory = new ThingFactory(RtsModules.withDefaults()
                .register(Stealth.Data.class, (owner, data) -> new Stealth(owner)));
        var tankType = ThingTemplate.named("Tank").module(new ActiveBody.Data(400f))
                .module(new WeaponUpdate.Data(5f, 100f, 15, DamageType.NORMAL, 0f)).build();
        var pathfinderType = ThingTemplate.named("Pathfinder").module(new ActiveBody.Data(1000f))
                .module(new Stealth.Data()).build();
        factory.addTemplate(tankType);
        factory.addTemplate(pathfinderType);
        var logic = new CombatTest.CombatLogic(factory);
        logic.init();
        var usa = logic.getPlayerList().addPlayer("USA");
        var china = logic.getPlayerList().addPlayer("China");
        usa.setRelationshipTo(china, Relationship.ENEMIES);
        china.setRelationshipTo(usa, Relationship.ENEMIES);
        var pathfinder = logic.spawn(pathfinderType, new Coord3D(10f, 0f, 0f), usa.getIndex());
        var tank = logic.spawn(tankType, new Coord3D(0f, 0f, 0f), china.getIndex());
        return new Scene(logic, pathfinder, tank.findModule(WeaponUpdate.class));
    }

    private static void run(Scene scene, int frames) {
        for (int frame = 0; frame < frames; frame++) {
            scene.logic().update();
        }
    }

    @Test
    void hiddenItIsNotAcquiredNorOrderedAtAndItIsLetGoTheFrameItHides() {
        var scene = scene();
        var stealth = scene.pathfinder().findModule(Stealth.class);

        run(scene, 30);
        assertFalse(scene.gun().isAttacking(), "the tank beside it does not acquire it");
        assertFalse(scene.gun().attack(scene.pathfinder().getId()), "an order to attack it is refused");
        assertEquals(1000f, scene.pathfinder().getBody().getHealth());

        stealth.hidden = false; // detected
        run(scene, 1);
        assertTrue(scene.gun().isAttacking(), "seen, it may be fired at");
        run(scene, 20);
        assertTrue(scene.pathfinder().getBody().getHealth() < 1000f);

        stealth.hidden = true;
        run(scene, 1);
        assertFalse(scene.gun().isAttacking(), "let go the frame it hid again");
    }

    @Test
    void twoRunsEndOnTheSameChecksum() {
        long[] sums = new long[2];
        for (int run = 0; run < 2; run++) {
            var scene = scene();
            var stealth = scene.pathfinder().findModule(Stealth.class);
            for (int frame = 0; frame < 120; frame++) {
                stealth.hidden = (frame / 20) % 2 == 0;
                scene.logic().update();
            }
            sums[run] = scene.logic().checksum();
        }
        assertEquals(sums[0], sums[1]);
    }
}
