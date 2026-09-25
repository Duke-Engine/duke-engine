package uz.dukeengine.rts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.rts.message.GameMessage;
import uz.dukeengine.rts.module.RtsModules;

/**
 * The frame's checksum takes in what the sides keep and where the random numbers stand, as the reference sums every
 * player's money, sciences and upgrades and its random seed ({@code GameLogic::getCRC}): two machines that part company
 * over any of them are told on the next checked frame, not minutes later when a thing moves otherwise.
 */
class SideChecksumTest {

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

    private static World world() {
        var things = new ThingFactory(RtsModules.withDefaults());
        things.addTemplate(RtsTemplate.named("Tank").module(new ActiveBody.Data(300f)).module(new MoveUpdate.Data(20f))
                .build());
        var world = new World(things);
        world.init();
        int side = world.getPlayerList().addPlayer("USA").getIndex();
        world.getRtsPlayer(side).give(1000);
        var tank = world.spawn(things.findTemplate("Tank"), new Coord3D(10f, 10f, 0f), side);
        tank.findModule(MoveUpdate.class).moveTo(new Coord3D(80f, 10f, 0f));
        for (int frame = 0; frame < 30; frame++) {
            world.update();
        }
        return world;
    }

    @Test
    void twoWorldsAlikeButForOneMoneyOrOneDrawOrOneGrantSumDifferently() {
        assertEquals(world().checksum(), world().checksum(), "two copies of one world sum alike");

        var richer = world();
        richer.getRtsPlayer(1).give(1);
        assertNotEquals(world().checksum(), richer.checksum(), "1 money apart");

        var drawn = world();
        drawn.random().nextLong();
        assertNotEquals(world().checksum(), drawn.checksum(), "one random number more drawn");

        var granted = world();
        granted.getRtsPlayer(1).grant("SCIENCE_Paladin");
        assertNotEquals(world().checksum(), granted.checksum(), "one word granted");
    }
}
