package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;
import uz.dukeengine.rts.RtsTemplate;

/** A factory says what came out of it, the frame it came out, to its own modules and to game code. */
class ProductionListenerTest {

    /** What a game's module on one factory hears. */
    private static final class Heard extends Module implements ProductionListener {
        final List<GameObject> units = new ArrayList<>();

        Heard(GameObject owner) {
            super(owner);
        }

        @Override
        public void onProduced(GameObject unit) {
            units.add(unit);
        }
    }

    private record Released(GameObject factory, GameObject unit, int frame) {
    }

    private ProductionTest.TestLogic logic;
    private ThingTemplate soldier;
    private ThingTemplate tank;
    private GameObject west;
    private GameObject east;
    private final List<Released> released = new ArrayList<>();

    @BeforeEach
    void setUp() {
        var factory = new ThingFactory(RtsModules.withDefaults());
        soldier = RtsTemplate.named("Soldier").module(new ActiveBody.Data(50f)).buildCost(100).buildTimeFrames(3)
                .build();
        tank = RtsTemplate.named("Tank").module(new ActiveBody.Data(400f)).buildCost(300).buildTimeFrames(5)
                .build();
        var barracks = RtsTemplate.named("Barracks").module(new ActiveBody.Data(500f))
                .module(new ProductionUpdate.Data()).build();
        factory.addTemplate(soldier);
        factory.addTemplate(tank);
        factory.addTemplate(barracks);
        logic = new ProductionTest.TestLogic(factory);
        logic.init();
        int player = logic.getPlayerList().addPlayer("Red").getIndex();
        logic.getRtsPlayer(player).deposit(5000);
        west = building(barracks, player, 0f);
        east = building(barracks, player, 500f);
        logic.onProduced((from, unit) -> released.add(new Released(from, unit, logic.getFrame())));
    }

    private GameObject building(ThingTemplate template, int player, float x) {
        var thing = logic.createObject(template);
        thing.setPlayerIndex(player);
        thing.setPosition(new Coord3D(x, 0f, 0f));
        return thing;
    }

    private static ProductionUpdate line(GameObject factory) {
        return factory.findModule(ProductionUpdate.class);
    }

    private void run(int frames) {
        for (int frame = 0; frame < frames; frame++) {
            logic.update();
        }
    }

    @Test
    void twoFactoriesFinishingTogetherEachReportTheirOwnUnit() {
        line(west).queue(soldier);
        line(east).queue(soldier);

        run(3);

        assertEquals(2, released.size());
        assertEquals(released.get(0).frame(), released.get(1).frame(), "the same frame");
        for (var one : released) {
            assertEquals(soldier, one.unit().getTemplate());
            assertTrue(one.unit().getPosition().distance(one.factory().getPosition()) < 100f,
                    "the unit reported is the one that stepped out of that factory");
            assertTrue(logic.getObjects().contains(one.unit()), "and it is in the world as it is told");
        }
        assertTrue(released.get(0).factory() != released.get(1).factory());
    }

    @Test
    void aModuleOnAFactoryHearsExactlyWhatThatFactoryReleased() {
        var heard = new Heard(west);
        west.addModule(heard);
        line(west).queue(soldier);
        line(west).queue(soldier);
        line(east).queue(soldier);

        run(10);

        assertEquals(2, heard.units.size());
        var fromWest = released.stream().filter(one -> one.factory() == west).map(Released::unit).toList();
        assertEquals(fromWest, heard.units, "the west's two, and not the east's");
    }

    @Test
    void theQueueListsWhatIsQueuedInOrder() {
        line(west).queue(soldier);
        line(west).queue(tank);
        assertEquals(List.of(soldier, tank), line(west).getQueue());

        run(3);

        assertEquals(List.of(tank), line(west).getQueue(), "the soldier is out");
        assertEquals(1, line(west).getQueueSize());
    }
}
