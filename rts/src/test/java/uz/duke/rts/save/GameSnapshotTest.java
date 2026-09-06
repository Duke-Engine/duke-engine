package uz.duke.rts.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.duke.rts.player.RtsPlayer;
import uz.duke.rts.RtsSimulation;
import uz.duke.rts.message.GameMessage;
import uz.duke.core.GameLogic;
import uz.duke.core.math.Coord3D;
import uz.duke.core.module.ModuleFactory;
import uz.duke.rts.player.Upgrade;
import uz.duke.core.thing.ObjectStatus;
import uz.duke.core.thing.ThingFactory;
import uz.duke.core.thing.ThingTemplateLoader;

class GameSnapshotTest {

    private static final String INI = """
            Object Tank
              KindOf = VEHICLE
              Body = ActiveBody Tag
                MaxHealth = 100
              End
              Update = MoveUpdate Tag
                Speed = 30
              End
            End
            """;

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

    private static TestLogic newLogic() {
        var thingFactory = new ThingFactory(ModuleFactory.withDefaults());
        new ThingTemplateLoader(thingFactory).load(INI);
        var logic = new TestLogic(thingFactory);
        logic.init();
        return logic;
    }

    @Test
    void roundTripRestoresIdenticalWorld() {
        var orig = newLogic();
        var usa = (RtsPlayer) orig.getPlayerList().addPlayer("USA");
        var china = orig.getPlayerList().addPlayer("China");
        usa.deposit(500);
        orig.purchaseUpgrade(usa.getIndex(), new Upgrade("Training", 100, 1.5f)); // money 400, bonus 1.5

        var tank = orig.getThingFactory().findTemplate("Tank");
        var a = orig.createObject(tank);
        a.setPlayerIndex(usa.getIndex());
        a.setPosition(new Coord3D(10f, 20f, 0f));
        a.getBody().damage(30f); // 70 hp

        var b = orig.createObject(tank);
        b.setPlayerIndex(china.getIndex());
        b.setPosition(new Coord3D(40f, 0f, 0f));
        b.setStatus(ObjectStatus.DISABLED);

        orig.update();
        orig.update(); // frame 2

        long checksumBefore = orig.checksum();
        String saved = GameSnapshot.save(orig);

        var restored = newLogic();
        GameSnapshot.load(saved, restored);

        // The whole observable world matches.
        assertEquals(checksumBefore, restored.checksum());
        assertEquals(orig.getFrame(), restored.getFrame());
        assertEquals(2, restored.getObjectCount());

        // Player state round-trips.
        var rUsa = restored.getRtsPlayer(usa.getIndex());
        assertEquals(400, rUsa.getMoney());
        assertEquals(1.5f, rUsa.getWeaponDamageBonus(), 1e-6f);
        assertTrue(rUsa.hasUpgrade("Training"));

        // Object state round-trips, including status and the next id.
        var rb = restored.findObject(b.getId());
        assertTrue(rb.hasStatus(ObjectStatus.DISABLED));
        assertEquals(70f, restored.findObject(a.getId()).getBody().getHealth(), 1e-6f);
        assertEquals(3, restored.createObject(tank).getId().value()); // nextObjectId preserved
    }

    @Test
    void savedTextIsStableForSameState() {
        var logic = newLogic();
        logic.getPlayerList().addPlayer("USA");
        var tank = logic.getThingFactory().findTemplate("Tank");
        logic.createObject(tank).setPosition(new Coord3D(1f, 2f, 3f));

        assertEquals(GameSnapshot.save(logic), GameSnapshot.save(logic)); // deterministic output
    }
}
