package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;
import uz.dukeengine.rts.RtsTemplate;

/**
 * A factory's several doors, one for each job — the reference's airfield, a hangar door for each parking space:
 * opening 2000 ms, waiting open 3000 ms, closing 2000 ms.
 */
class DoorsTest {

    /** A parking space for each job, its token the door the test names next. */
    static final class Hangars extends Module implements ProductionReservation {
        record Data() implements ModuleData {
        }

        int next;

        Hangars(GameObject owner) {
            super(owner);
        }

        @Override
        public Object reserve(ThingTemplate unit) {
            return next;
        }

        @Override
        public void release(Object token) {
        }

        @Override
        public void place(GameObject unit, Object token) {
        }

        @Override
        public int door(Object token) {
            return (Integer) token;
        }
    }

    private CombatTest.CombatLogic logic;
    private GameObject airfield;
    private ProductionUpdate production;
    private Hangars hangars;
    private ThingTemplate raptor;

    private void world() {
        var factory = new ThingFactory(RtsModules.withDefaults()
                .register(Hangars.Data.class, (owner, data) -> new Hangars(owner)));
        raptor = RtsTemplate.named("Raptor").module(new ActiveBody.Data(120f)).buildCost(100).buildTimeFrames(30)
                .build();
        var doors = new ArrayList<ProductionUpdate.Door>();
        for (int n = 1; n <= 4; n++) {
            doors.add(new ProductionUpdate.Door(60, 90, 60, "DOOR_" + n + "_OPENING", "DOOR_" + n + "_WAITING_OPEN",
                    "DOOR_" + n + "_CLOSING"));
        }
        var field = RtsTemplate.named("Airfield").module(new ActiveBody.Data(1500f))
                .module(new ProductionUpdate.Data(List.of("Raptor"), List.of(), null, doors, null, false))
                .module(new Hangars.Data()).build();
        factory.addTemplate(raptor);
        factory.addTemplate(field);
        logic = new CombatTest.CombatLogic(factory);
        logic.init();
        int usa = logic.getPlayerList().addPlayer("USA").getIndex();
        logic.getRtsPlayer(usa).give(10_000);
        airfield = logic.spawn(field, new Coord3D(100f, 100f, 0f), usa);
        production = airfield.findModule(ProductionUpdate.class);
        hangars = airfield.findModule(Hangars.class);
    }

    private List<String> doorWords() {
        return airfield.getConditions().stream().filter(word -> word.startsWith("DOOR_")).sorted().toList();
    }

    private long raptors() {
        return logic.getObjects().stream().filter(thing -> thing.getTemplate() == raptor).count();
    }

    private void frames(int count) {
        for (int frame = 0; frame < count; frame++) {
            logic.update();
        }
    }

    @Test
    void aJobForTheThirdDoorOpensOnlyThatDoorAndIsMadeOnceItIsOpen() {
        world();
        hangars.next = 2;
        assertTrue(production.queue(raptor));

        frames(30);
        assertEquals(List.of("DOOR_3_OPENING"), doorWords(), "built, its own door opening and no other");
        assertEquals(0, raptors());
        frames(59);
        assertEquals(0, raptors(), "not before the door is open");
        frames(1);
        assertEquals(1, raptors(), "made 2000 ms on, as it opens");
        assertEquals(List.of("DOOR_3_WAITING_OPEN"), doorWords());
    }

    @Test
    void aDoorHeldOpenStaysOpenAndClosesOnceLetGo() {
        world();
        production.holdDoorOpen(1, true);
        frames(60 + 90 + 30);
        assertEquals(List.of("DOOR_2_WAITING_OPEN"), doorWords(), "held, open past its waiting time");

        production.holdDoorOpen(1, false);
        frames(1);
        assertEquals(List.of("DOOR_2_CLOSING"), doorWords(), "let go, it closes");
        frames(60);
        assertEquals(List.of(), doorWords(), "closed 2000 ms after it was let go");
    }

    @Test
    void twoJobsOnTwoDoorsOpenBoth() {
        world();
        hangars.next = 0;
        assertTrue(production.queue(raptor));
        hangars.next = 1;
        assertTrue(production.queue(raptor));

        boolean both = false;
        for (int frame = 0; frame < 400 && raptors() < 2; frame++) {
            logic.update();
            var words = doorWords();
            both |= words.stream().anyMatch(word -> word.startsWith("DOOR_1_"))
                    && words.stream().anyMatch(word -> word.startsWith("DOOR_2_"));
        }
        assertEquals(2, raptors());
        assertTrue(both, "each opened its own door, the two open together");
    }
}
