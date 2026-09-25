package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;

/** A target that throws its attackers' aim off: a direct-fire gun misses it, a blast wide enough still hurts it. */
class AimOffsetTest {

    /** An Aurora on its attack run: shots at it are aimed twenty units off, while it says so. */
    static final class Sneaky extends Module implements AimOffset {
        record Data() implements ModuleData {
        }

        boolean running = true;

        Sneaky(GameObject owner) {
            super(owner);
        }

        @Override
        public Coord3D aimOffset() {
            return running ? new Coord3D(20f, 0f, 0f) : null;
        }
    }

    /** What the Aurora has lost after a gun with {@code splash} has fired at it for a while. */
    private static float lost(float splash, boolean running) {
        var factory = new ThingFactory(RtsModules.withDefaults()
                .register(Sneaky.Data.class, (owner, data) -> new Sneaky(owner)));
        var gunner = ThingTemplate.named("Gunner").module(new ActiveBody.Data(100f))
                .module(new WeaponUpdate.Data(10f, 200f, 10, DamageType.NORMAL, splash)).build();
        var aurora = ThingTemplate.named("Aurora").module(new ActiveBody.Data(1000f)).module(new Sneaky.Data())
                .build();
        factory.addTemplate(gunner);
        factory.addTemplate(aurora);
        var logic = new CombatTest.CombatLogic(factory);
        logic.init();
        var red = logic.getPlayerList().addPlayer("Red");
        var blue = logic.getPlayerList().addPlayer("Blue");
        red.setRelationshipTo(blue, Relationship.ENEMIES);
        blue.setRelationshipTo(red, Relationship.ENEMIES);
        logic.spawn(gunner, new Coord3D(0f, 0f, 0f), red.getIndex());
        var target = logic.spawn(aurora, new Coord3D(50f, 0f, 0f), blue.getIndex());
        target.findModule(Sneaky.class).running = running;
        for (int frame = 0; frame < 60; frame++) {
            logic.update();
        }
        return 1000f - target.getBody().getHealth();
    }

    @Test
    void aGunWithNoBlastFiringAtATargetAimedTwentyOffDoesItNoHarm() {
        assertEquals(0f, lost(0f, true));
    }

    @Test
    void aBlastWiderThanTheOffsetStillHurtsIt() {
        assertTrue(lost(25f, true) > 0f, "twenty off, within a blast of twenty-five");
    }

    @Test
    void withNoOffsetTheGunHitsAsBefore() {
        assertTrue(lost(0f, false) > 0f);
    }
}
