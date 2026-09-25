package uz.dukeengine.rts.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.rts.player.RtsPlayer;
import uz.dukeengine.rts.RtsSimulation;
import uz.dukeengine.rts.message.GameMessage;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ModuleFactory;
import uz.dukeengine.rts.player.Upgrade;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplateLoader;
import uz.dukeengine.rts.RtsTemplate;

class GameSnapshotTest {

    private static final String INI = """
            Object
              Name = Tank
              KindOf = [VEHICLE]
              Modules = [
                ActiveBody
                  MaxHealth = 100
                End,
                MoveUpdate
                  Speed = 30
                End
              ]
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
        RtsTemplate.register(new ThingTemplateLoader(thingFactory)).load(INI, "units.duke");
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
        orig.purchaseUpgrade(usa.getIndex(), Upgrade.weaponDamage("Training", 100, 1.5f)); // money 400, bonus 1.5

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

    /** A thing's condition words are saved with it: which weapons it carries hangs on them. */
    @Test
    void conditionWordsAreSavedWithTheThing() {
        var orig = newLogic();
        var tank = orig.createObject(orig.getThingFactory().findTemplate("Tank"));
        tank.setCondition("PLAYER_UPGRADE");
        tank.setCondition("VETERAN");

        var restored = newLogic();
        GameSnapshot.load(GameSnapshot.save(orig), restored);
        var back = restored.getObjects().getFirst();
        assertEquals(java.util.List.of("PLAYER_UPGRADE", "VETERAN"), java.util.List.copyOf(back.getConditions()));
    }

    /** A map revealed to a player stays revealed through a save, and the world sums the same. */
    @Test
    void aMapRevealedToAPlayerIsSavedWithTheWorld() {
        var orig = newLogic();
        orig.revealMapTo(2);

        var restored = newLogic();
        GameSnapshot.load(GameSnapshot.save(orig), restored);

        assertTrue(restored.isMapRevealedTo(2));
        assertFalse(restored.isMapRevealedTo(1));
        assertEquals(orig.checksum(), restored.checksum());
    }

    /** Where the simulation's random numbers stand is saved too, so a loaded game draws what the saved one would. */
    @Test
    void theRandomNumbersCarryOnFromWhereTheyWereSaved() {
        var orig = newLogic();
        orig.random().nextLong();
        var saved = GameSnapshot.save(orig);
        long next = orig.random().nextLong();

        var restored = newLogic();
        GameSnapshot.load(saved, restored);
        assertEquals(next, restored.random().nextLong());
    }

    /** Decks laid at run time, which are open, and the floor a thing stands on come back with a load. */
    @Test
    void theDecksAndTheFloorOfWhatStandsOnThemAreSavedWithTheWorld() {
        var orig = newLogic();
        orig.setPathGrid(new uz.dukeengine.core.pathfind.PathGrid(40, 40));
        orig.addDeck(new Coord3D(130f, 180f, 20f), new Coord3D(130f, 220f, 20f),
                new Coord3D(270f, 220f, 20f), new Coord3D(270f, 180f, 20f));
        int second = orig.addDeck(new Coord3D(130f, 280f, 5f), new Coord3D(130f, 320f, 5f),
                new Coord3D(270f, 320f, 5f), new Coord3D(270f, 280f, 5f));
        var tank = orig.spawn(orig.getThingFactory().findTemplate("Tank"), new Coord3D(200f, 205f, 20f), 0);
        orig.setDeckOpen(second, false);

        var restored = newLogic();
        restored.setPathGrid(new uz.dukeengine.core.pathfind.PathGrid(40, 40));
        GameSnapshot.load(GameSnapshot.save(orig), restored);

        assertEquals(2, restored.getPathGrid().decks().size(), "the decks laid at run time are laid again");
        assertTrue(restored.getPathGrid().deck(1).isOpen());
        assertFalse(restored.getPathGrid().deck(second).isOpen());
        assertEquals(1, restored.findObject(tank.getId()).getFloor(), "the tank is on the first deck");
        assertEquals(orig.checksum(), restored.checksum());
    }

    /** The line a thing is drawn along comes back with it, and weighs nothing in the sums. */
    @Test
    void theLineAThingIsDrawnAlongIsSavedWithIt() {
        var orig = newLogic();
        var tank = orig.createObject(orig.getThingFactory().findTemplate("Tank"));
        long before = orig.checksum();
        var line = new uz.dukeengine.core.thing.Span(new Coord3D(1f, 2f, 3f), new Coord3D(301f, 2f, 23f));
        tank.setSpan(line);
        assertEquals(before, orig.checksum(), "drawing only");

        var restored = newLogic();
        GameSnapshot.load(GameSnapshot.save(orig), restored);

        assertEquals(line, restored.findObject(tank.getId()).getSpan());
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
