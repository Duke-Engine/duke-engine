package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

/** A factory that has its say as a unit is queued: an airfield's four parking spaces, each with its hangar door. */
class ReservationTest {

    /** Four spaces, the token for one its number; the door of the space a jet was made for, handed it. */
    static final class Parking extends Module implements ProductionReservation {
        record Data() implements ModuleData {
        }

        private final boolean[] taken = new boolean[4];
        final List<Integer> released = new ArrayList<>();
        final List<String> placed = new ArrayList<>();

        Parking(GameObject owner) {
            super(owner);
        }

        @Override
        public Object reserve(ThingTemplate unit) {
            for (int space = 0; space < taken.length; space++) {
                if (!taken[space]) {
                    taken[space] = true;
                    return space;
                }
            }
            return null;
        }

        @Override
        public void release(Object token) {
            taken[(Integer) token] = false;
            released.add((Integer) token);
        }

        @Override
        public void place(GameObject unit, Object token) {
            placed.add(unit.getTemplate().name() + " at hangar door " + token);
        }
    }

    @Test
    void aFifthJetIsRefusedFreeAndACancelNamesItsJobAndFreesItsSpace() {
        var factory = new ThingFactory(RtsModules.withDefaults()
                .register(Parking.Data.class, (owner, data) -> new Parking(owner)));
        var raptor = RtsTemplate.named("Raptor").module(new ActiveBody.Data(120f)).buildCost(1400)
                .buildTimeFrames(30).build();
        var airfield = RtsTemplate.named("Airfield").module(new ActiveBody.Data(1500f))
                .module(new ProductionUpdate.Data(List.of("Raptor"), List.of(), null, null))
                .module(new Parking.Data()).build();
        factory.addTemplate(raptor);
        factory.addTemplate(airfield);
        var logic = new CombatTest.CombatLogic(factory);
        logic.init();
        int usa = logic.getPlayerList().addPlayer("USA").getIndex();
        var side = logic.getRtsPlayer(usa);
        side.give(10_000);
        var field = logic.spawn(airfield, new Coord3D(0f, 0f, 0f), usa);
        var production = field.findModule(ProductionUpdate.class);
        var parking = field.findModule(Parking.class);

        for (int jet = 0; jet < 4; jet++) {
            assertTrue(production.queue(raptor));
        }
        assertFalse(production.queue(raptor), "a fifth has nowhere to park");
        assertEquals(10_000 - 4 * 1400, side.getMoney(), "and was not charged");
        assertEquals(4, production.getQueueSize());

        assertEquals(List.of(1, 2, 3, 4), production.getEntries().stream().map(ProductionUpdate.Queued::id).toList());
        production.cancel(1); // the second of four alike
        assertEquals(List.of(1), parking.released, "its space, the second, is free again");
        assertEquals(List.of(1, 3, 4), production.getEntries().stream().map(ProductionUpdate.Queued::id).toList(),
                "job 2 is the one gone");

        for (int frame = 0; frame < 40 && parking.placed.isEmpty(); frame++) {
            logic.update();
        }
        assertEquals(List.of("Raptor at hangar door 0"), parking.placed, "the first made, handed its own door");
    }
}
