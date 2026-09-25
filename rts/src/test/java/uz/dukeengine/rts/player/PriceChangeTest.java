package uz.dukeengine.rts.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.rts.RtsSimulation;
import uz.dukeengine.rts.RtsTemplate;
import uz.dukeengine.rts.message.GameMessage;
import uz.dukeengine.rts.module.ProductionUpdate;
import uz.dukeengine.rts.module.RtsModules;
import uz.dukeengine.rts.thing.RtsKinds;

/** A side's price for a kind of thing: a captured oil refinery's vehicles a tenth off, charged and given back. */
class PriceChangeTest {

    private static final class World extends RtsSimulation {
        World(ThingFactory factory) {
            super(factory);
        }

        @Override
        protected void onRtsCommand(GameMessage command) {
        }

        @Override
        protected void simulate() {
        }
    }

    @Test
    void aVehicleATenthOffIsChargedAndRefundedAtThatPriceAndABuildingIsNot() {
        var factory = new ThingFactory(RtsModules.withDefaults());
        var tank = RtsTemplate.named("Crusader").kindOf(RtsKinds.VEHICLE).module(new ActiveBody.Data(480f))
                .buildCost(900).buildTimeFrames(300).build();
        var reactor = RtsTemplate.named("Reactor").kindOf(RtsKinds.STRUCTURE).module(new ActiveBody.Data(800f))
                .buildCost(900).build();
        var warFactory = RtsTemplate.named("WarFactory").kindOf(RtsKinds.STRUCTURE)
                .module(new ActiveBody.Data(2000f)).module(new ProductionUpdate.Data()).build();
        factory.addTemplate(tank);
        factory.addTemplate(reactor);
        factory.addTemplate(warFactory);
        var world = new World(factory);
        world.init();
        int usa = world.getPlayerList().addPlayer("USA").getIndex();
        var side = world.getRtsPlayer(usa);
        side.give(5000);
        var production = world.spawn(warFactory, new Coord3D(0f, 0f, 0f), usa).findModule(ProductionUpdate.class);

        side.addPriceChange(RtsKinds.VEHICLE, -0.1f); // the refinery captured
        assertTrue(production.queue(tank));
        assertEquals(4190, side.getMoney(), "810 for a 900 vehicle");
        production.cancel(0);
        assertEquals(5000, side.getMoney(), "and 810 back");
        assertEquals(900, side.priceOf(reactor), "a building costs what it did");

        side.addPriceChange(RtsKinds.VEHICLE, -0.1f); // a second refinery: the same change counts once
        assertEquals(810, side.priceOf(tank));
        side.removePriceChange(RtsKinds.VEHICLE, -0.1f);
        assertEquals(810, side.priceOf(tank), "given twice, taken away once: still 810");
        side.removePriceChange(RtsKinds.VEHICLE, -0.1f);
        assertEquals(900, side.priceOf(tank), "taken away again: 900");
    }

    /**
     * A factory that gives back the price at the moment of the cancel, as the reference's does: a tank queued at 900
     * and called off with the refinery captured gives back 810; one queued at 810 and called off after it was lost, 900.
     * A factory that does not say so gives back what was paid.
     */
    @Test
    void aCancelGivesBackTheSidesPriceNowWhereTheFactorySaysSo() {
        var factory = new ThingFactory(RtsModules.withDefaults());
        var tank = RtsTemplate.named("Crusader").kindOf(RtsKinds.VEHICLE).module(new ActiveBody.Data(480f))
                .buildCost(900).buildTimeFrames(300).build();
        var priceNow = RtsTemplate.named("PriceNow").kindOf(RtsKinds.STRUCTURE).module(new ActiveBody.Data(2000f))
                .module(new ProductionUpdate.Data(java.util.List.of(), java.util.List.of(), null, java.util.List.of(),
                        null, true))
                .build();
        var paid = RtsTemplate.named("Paid").kindOf(RtsKinds.STRUCTURE).module(new ActiveBody.Data(2000f))
                .module(new ProductionUpdate.Data()).build();
        factory.addTemplate(tank);
        factory.addTemplate(priceNow);
        factory.addTemplate(paid);
        var world = new World(factory);
        world.init();
        int usa = world.getPlayerList().addPlayer("USA").getIndex();
        var side = world.getRtsPlayer(usa);
        side.give(5000);
        var now = world.spawn(priceNow, new Coord3D(0f, 0f, 0f), usa).findModule(ProductionUpdate.class);
        var asPaid = world.spawn(paid, new Coord3D(200f, 0f, 0f), usa).findModule(ProductionUpdate.class);

        assertTrue(now.queue(tank));
        side.addPriceChange(RtsKinds.VEHICLE, -0.1f);
        now.cancel(0);
        assertEquals(5000 - 90, side.getMoney(), "queued at 900, called off at 810: 810 back");

        assertTrue(now.queue(tank));
        side.removePriceChange(RtsKinds.VEHICLE, -0.1f);
        now.cancel(0);
        assertEquals(5000, side.getMoney(), "queued at 810, called off at 900: 900 back");

        assertTrue(asPaid.queue(tank));
        side.addPriceChange(RtsKinds.VEHICLE, -0.1f);
        asPaid.cancel(0);
        assertEquals(5000, side.getMoney(), "the other factory gives back the 900 paid");
    }
}
