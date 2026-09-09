package uz.duke.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.duke.core.math.Coord3D;
import uz.duke.core.module.ActiveBody;
import uz.duke.core.module.Module;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.ThingFactory;
import uz.duke.core.thing.ThingTemplate;

/**
 * What stops a production line is the game's decision, not the engine's.
 *
 * <p>The power check used to live inside {@code ProductionUpdate}, which made
 * every game on this engine play Generals' rule about under-powered bases —
 * including games with no notion of power, where it was invisible only because
 * their balance happened to be zero.
 */
class ProductionGateTest {

    /** A gate a game might write: the line runs only when someone says so. */
    private static final class Permit extends Module implements ProductionGate {
        private boolean open = true;

        Permit(GameObject owner) {
            super(owner);
        }

        @Override
        public boolean canProduce() {
            return open;
        }
    }

    private PowerTest.TestLogic logic;
    private ThingTemplate soldier;
    private ThingTemplate plainFactory;
    private int player;

    @BeforeEach
    void setUp() {
        var thingFactory = new ThingFactory(RtsModules.withDefaults());
        soldier = ThingTemplate.named("Soldier")
                .module("ActiveBody", new ActiveBody.Data(50f))
                .buildCost(10)
                .buildTimeFrames(2)
                .build();
        plainFactory = ThingTemplate.named("Factory")
                .module("ActiveBody", new ActiveBody.Data(400f))
                .module("ProductionUpdate", new ProductionUpdate.Data())
                .build();
        thingFactory.addTemplate(soldier);
        thingFactory.addTemplate(plainFactory);

        logic = new PowerTest.TestLogic(thingFactory);
        logic.init();
        player = logic.getPlayerList().addPlayer("Side").getIndex();
        logic.getRtsPlayer(player).deposit(1000);
    }

    private GameObject factory() {
        var built = logic.createObject(plainFactory);
        built.setPlayerIndex(player);
        built.setPosition(Coord3D.ZERO);
        return built;
    }

    private int soldiers() {
        return (int) logic.getObjects().stream()
                .filter(o -> o.getTemplate().getName().equals("Soldier"))
                .count();
    }

    /** A factory with nothing attached builds without interruption. */
    @Test
    void aFactoryWithNoGatesJustBuilds() {
        var built = factory();
        built.findModule(ProductionUpdate.class).queue(soldier);

        for (int frame = 0; frame < 5; frame++) {
            logic.update();
        }

        assertEquals(1, soldiers(), "nothing was holding the line");
    }

    /** A gate the engine has never heard of stops it, and lets it go again. */
    @Test
    void aGameWrittenGateHoldsAndReleasesTheLine() {
        var built = factory();
        var permit = new Permit(built);
        built.addModule(permit);
        built.findModule(ProductionUpdate.class).queue(soldier);

        permit.open = false;
        for (int frame = 0; frame < 10; frame++) {
            logic.update();
        }
        assertEquals(0, soldiers(), "the gate held it");

        permit.open = true;
        for (int frame = 0; frame < 5; frame++) {
            logic.update();
        }
        assertEquals(1, soldiers(), "and released it");
    }

    /** Every gate has to agree — one closed gate is enough to stop the line. */
    @Test
    void allGatesMustAgree() {
        var built = factory();
        var first = new Permit(built);
        var second = new Permit(built);
        built.addModule(first);
        built.addModule(second);
        built.findModule(ProductionUpdate.class).queue(soldier);

        second.open = false;
        for (int frame = 0; frame < 10; frame++) {
            logic.update();
        }

        assertEquals(0, soldiers(), "one dissenting gate stops it");
    }

    /**
     * The capacity rule is one gate among others, and a game that never attaches
     * it never plays by it — which is the difference from before.
     */
    @Test
    void theCapacityRuleAppliesOnlyWhereItIsAskedFor() {
        var ungated = factory();
        ungated.addModule(new PowerModule(ungated, new PowerModule.Data(0, 50)));
        ungated.findModule(ProductionUpdate.class).queue(soldier);

        for (int frame = 0; frame < 5; frame++) {
            logic.update();
        }

        assertTrue(PowerGrid.surplus(logic, player) < 0, "the side is over capacity");
        assertEquals(1, soldiers(), "but this factory never agreed to care");
    }
}
