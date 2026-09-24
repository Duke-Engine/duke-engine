package uz.dukeengine.rts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;
import uz.dukeengine.rts.message.GameMessage;
import uz.dukeengine.rts.module.HarvestUpdate;
import uz.dukeengine.rts.module.ProductionUpdate;
import uz.dukeengine.rts.module.RtsModules;
import uz.dukeengine.rts.module.SupplyModule;
import uz.dukeengine.rts.player.RtsPlayer;
import uz.dukeengine.rts.save.GameSnapshot;

/** What a side took in and what it paid out, kept apart where its balance nets them. */
class BooksTest {

    private static final int TANK_COST = 700;

    /** A world whose own code queues a tank the frame the first load comes in. */
    private static final class World extends RtsSimulation {
        GameObject factory;
        int earnedOn = -1;
        int spentOn = -1;

        World(ThingFactory factory) {
            super(factory);
        }

        @Override
        protected void onRtsCommand(GameMessage command) {
        }

        @Override
        protected void simulate() {
            var side = getRtsPlayer(1);
            if (factory == null || earnedOn >= 0 || side.getEarned() == 0) {
                return;
            }
            earnedOn = getFrame();
            if (factory.findModule(ProductionUpdate.class).queue(getThingFactory().findTemplate("Tank"))) {
                spentOn = getFrame();
            }
        }
    }

    private World world;
    private RtsPlayer side;

    @BeforeEach
    void setUp() {
        var things = new ThingFactory(RtsModules.withDefaults());
        things.addTemplate(ThingTemplate.named("Truck").module(new ActiveBody.Data(100f))
                .module(new HarvestUpdate.Data(100, 10, 0f)).build());
        things.addTemplate(ThingTemplate.named("Supplies").module(new SupplyModule.Data(1000)).build());
        things.addTemplate(RtsTemplate.named("WarFactory").module(new ActiveBody.Data(1000f))
                .module(new ProductionUpdate.Data()).buildCost(2000).build());
        things.addTemplate(RtsTemplate.named("Tank").module(new ActiveBody.Data(300f)).buildCost(TANK_COST)
                .buildTimeFrames(300).build());
        world = new World(things);
        world.init();
        side = world.getRtsPlayer(world.getPlayerList().addPlayer("USA").getIndex());
        side.give(1000);
    }

    private GameObject stand(String template) {
        return world.spawn(world.getThingFactory().findTemplate(template), Coord3D.ZERO, side.getIndex());
    }

    @Test
    void aSupplyTrucksLoadIsEarnedWhileAFactorySpendsTheSameFrame() {
        stand("Truck");
        stand("Supplies");
        world.factory = stand("WarFactory");

        for (int frame = 0; frame < 12; frame++) {
            world.update();
        }

        assertTrue(world.earnedOn >= 0, "a load came in");
        assertEquals(world.earnedOn, world.spentOn, "and the tank was paid for the same frame");
        assertEquals(100, side.getEarned(), "what came in, which the balance alone would hide");
        assertEquals(TANK_COST, side.getSpent());
        assertEquals(1000 + 100 - TANK_COST, side.getMoney());
    }

    @Test
    void theMoneyItStartsWithIsNeitherEarnedNorSpent() {
        assertEquals(1000, side.getMoney());
        assertEquals(0, side.getEarned());
        assertEquals(0, side.getSpent());
    }

    @Test
    void whatComesBackIsTakenOffWhatWasSpentNotCountedAsEarned() {
        var line = stand("WarFactory").findModule(ProductionUpdate.class);
        line.queue(world.getThingFactory().findTemplate("Tank"));
        assertEquals(TANK_COST, side.getSpent());

        line.cancel(0);

        assertEquals(0, side.getSpent(), "called off: never spent");
        assertEquals(0, side.getEarned(), "and not earned either");
        assertEquals(1000, side.getMoney());
    }

    @Test
    void aSaveKeepsTheBooks() {
        side.deposit(450);
        side.withdraw(300);

        var text = GameSnapshot.save(world);
        var things = new ThingFactory(RtsModules.withDefaults());
        var restored = new World(things);
        restored.init();
        GameSnapshot.load(text, restored);

        var again = restored.getRtsPlayer(side.getIndex());
        assertEquals(side.getMoney(), again.getMoney());
        assertEquals(450, again.getEarned());
        assertEquals(300, again.getSpent());
    }
}
