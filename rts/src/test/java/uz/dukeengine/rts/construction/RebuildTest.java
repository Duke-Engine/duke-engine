package uz.dukeengine.rts.construction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.rts.RtsSimulation;
import uz.dukeengine.rts.RtsTemplate;
import uz.dukeengine.rts.message.GameMessage;
import uz.dukeengine.rts.module.RtsModules;

/** A rebuild — the reference's {@code construct(…, isRebuild)} — asks none of what a player's order is asked. */
class RebuildTest {

    private static final Coord3D PLACE = new Coord3D(205f, 205f, 0f);

    private static final class World extends RtsSimulation {
        World(ThingFactory factory) {
            super(factory);
        }

        @Override
        protected void onRtsCommand(GameMessage command) {
            if (command instanceof GameMessage.Construct build) {
                construct(build);
            }
        }

        @Override
        protected void simulate() {
        }
    }

    private World world;
    private int player;
    private GameObject worker;

    private void setUp() {
        var factory = new ThingFactory(RtsModules.withDefaults());
        factory.addTemplate(RtsTemplate.named("Worker").geometry(new Geometry.Cylinder(3f, 6f))
                .module(new ActiveBody.Data(100f)).module(new MoveUpdate.Data(30f)).build());
        factory.addTemplate(RtsTemplate.named("Barracks").geometry(new Geometry.Box(9f, 9f, 16f))
                .module(new ActiveBody.Data(600f)).buildCost(100).buildTimeFrames(300).build());
        factory.addTemplate(RtsTemplate.named("Wreck").geometry(new Geometry.Cylinder(3f, 2f))
                .module(new ActiveBody.Data(10f)).build());
        world = new World(factory);
        world.init();
        world.setPathGrid(new PathGrid(40, 40));
        world.setPlacementRules(new PlacementRules(10f, 30f, 0.5f, 0.1f));
        player = world.getPlayerList().addPlayer("Builder").getIndex();
        world.getRtsPlayer(player).deposit(1000);
        worker = world.createObject(factory.findTemplate("Worker"));
        worker.setPlayerIndex(player);
        worker.setPosition(new Coord3D(100f, 100f, 0f));
        // in the way of a site, as a thing that does not move is
        world.createObject(factory.findTemplate("Wreck")).setPosition(PLACE);
    }

    private long sites() {
        return world.getObjects().stream().filter(one -> one.getTemplate().name().equals("Barracks")).count();
    }

    @Test
    void aRebuildPutsASiteOnAFootprintSomethingStandsOnAtNoCost() {
        setUp();

        assertTrue(world.rebuild(worker.getId(), "Barracks", PLACE, 90f));

        var site = world.getObjects().stream().filter(one -> one.getTemplate().name().equals("Barracks"))
                .findFirst().orElseThrow();
        assertTrue(site.hasStatus(ObjectStatus.UNDER_CONSTRUCTION), "a site, at once");
        assertEquals(PLACE.x(), site.getPosition().x());
        assertEquals(PLACE.y(), site.getPosition().y());
        assertEquals((float) StrictMath.toRadians(90f), site.getOrientation(), 1e-6f, "at the facing given");
        assertEquals(1000, world.getRtsPlayer(player).getMoney(), "at no cost");
    }

    @Test
    void theSameOrderFromAPlayerIsRefusedAsBefore() {
        setUp();

        world.issueCommand(new GameMessage.Construct(player, worker.getId(), "Barracks", PLACE, 90f));
        world.update();

        assertEquals(0, sites());
        assertNull(worker.findModule(BuildOrder.class), "not sent");
        assertEquals(1000, world.getRtsPlayer(player).getMoney());
    }
}
