package uz.duke.rts.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.duke.core.math.Coord3D;
import uz.duke.core.module.ActiveBody;
import uz.duke.core.player.Relationship;
import uz.duke.core.thing.ThingFactory;
import uz.duke.core.thing.ThingTemplate;
import uz.duke.rts.RtsSimulation;
import uz.duke.rts.message.GameMessage;
import uz.duke.rts.module.RtsModules;
import uz.duke.rts.module.WeaponUpdate;

/** A shot is a moment: reported once when it happens, not for as long as it lasts. */
class WeaponFiredTest {

    static final class TestLogic extends RtsSimulation {
        TestLogic(ThingFactory thingFactory) {
            super(thingFactory);
        }

        @Override
        protected void onRtsCommand(GameMessage command) {
        }

        @Override
        protected void simulate() {
        }
    }

    private static final int RELOAD_FRAMES = 10;

    private static final ThingTemplate GUNNER = ThingTemplate.named("Gunner")
            .module("ActiveBody", new ActiveBody.Data(100f))
            .module("WeaponUpdate", new WeaponUpdate.Data(1f, 50f, RELOAD_FRAMES))
            .build();

    private static final ThingTemplate DUMMY = ThingTemplate.named("Dummy")
            .module("ActiveBody", new ActiveBody.Data(100000f)) // never dies, so firing continues
            .build();

    @Test
    void oneEventPerShotNotOnePerFrameOfAiming() {
        var thingFactory = new ThingFactory(RtsModules.withDefaults());
        thingFactory.addTemplate(GUNNER);
        thingFactory.addTemplate(DUMMY);
        var logic = new TestLogic(thingFactory);
        logic.init();

        int usa = logic.getPlayerList().addPlayer("USA").getIndex();
        int foe = logic.getPlayerList().addPlayer("China").getIndex();
        logic.getPlayer(usa).setRelationshipTo(logic.getPlayer(foe), Relationship.ENEMIES);
        logic.getPlayer(foe).setRelationshipTo(logic.getPlayer(usa), Relationship.ENEMIES);

        var gunner = logic.spawn(GUNNER, new Coord3D(0f, 0f, 0f), usa);
        var dummy = logic.spawn(DUMMY, new Coord3D(20f, 0f, 0f), foe);
        gunner.findModule(WeaponUpdate.class).attack(dummy.getId());

        int frames = 60;
        for (int frame = 0; frame < frames; frame++) {
            logic.update();
        }

        var shots = logic.drainEvents().stream().filter(WeaponFired.class::isInstance).toList();
        assertEquals(frames / RELOAD_FRAMES, shots.size(),
                "the reload cycle is invisible in state; the event is what makes it visible");

        var first = (WeaponFired) shots.get(0);
        assertEquals(gunner.getId(), first.shooter());
        assertEquals(dummy.getId(), first.target());
        assertTrue(first.where() == first.from(), "fog filtering uses the shooter's position");
    }
}
