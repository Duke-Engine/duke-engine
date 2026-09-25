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
}
