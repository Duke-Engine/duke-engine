package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.rts.RtsTemplate;

/** A tunnel network: one hold for the side, in at any tunnel and out at any other, gone with the last one. */
class SharedHoldTest {

    private ProductionTest.TestLogic logic;
    private GameObject tunnelA;
    private GameObject tunnelB;
    private final List<GameObject> rebels = new ArrayList<>();

    private void world() {
        var things = new ThingFactory(RtsModules.withDefaults());
        var tunnel = RtsTemplate.named("TunnelNetwork").geometry(new Geometry.Cylinder(10f, 8f))
                .kindOf(uz.dukeengine.rts.thing.RtsKinds.STRUCTURE).module(new ActiveBody.Data(1000f)).module(new ContainModule.Data(10, "Tunnel")).build();
        var rebel = RtsTemplate.named("Rebel").geometry(new Geometry.Cylinder(2f, 4f))
                .module(new ActiveBody.Data(100f)).build();
        things.addTemplate(tunnel);
        things.addTemplate(rebel);
        logic = new ProductionTest.TestLogic(things);
        logic.init();
        int gla = logic.getPlayerList().addPlayer("GLA").getIndex();
        tunnelA = placed(tunnel, gla, 100f, 100f);
        tunnelB = placed(tunnel, gla, 500f, 500f);
        for (int one = 0; one < 11; one++) {
            rebels.add(placed(rebel, gla, 120f, 100f));
        }
    }

    private GameObject placed(uz.dukeengine.core.thing.ThingTemplate template, int side, float x, float y) {
        var thing = logic.createObject(template);
        thing.setPlayerIndex(side);
        thing.setPosition(new Coord3D(x, y, 0f));
        return thing;
    }

    private static ContainModule hold(GameObject tunnel) {
        return tunnel.findModule(ContainModule.class);
    }

    @Test
    void aSoldierEntersTunnelAAndLeavesFromTunnelB() {
        world();
        var rebel = rebels.getFirst();
        assertTrue(hold(tunnelA).load(rebel));
        assertTrue(rebel.isContained());
        assertEquals(List.of(rebel.getId()), hold(tunnelB).getPassengers(), "one list: seen from B too");

        hold(tunnelB).unload(rebel);
        assertFalse(rebel.isContained());
        assertTrue(uz.dukeengine.core.thing.Footprint.of(tunnelB).separation(uz.dukeengine.core.thing.Footprint.of(rebel))
                >= 0f && rebel.getPosition().distance(tunnelB.getPosition()) < 20f, "out beside B, far from A");
        assertEquals(List.of(), hold(tunnelA).getPassengers());
    }

    @Test
    void withTenInsideTheEleventhIsRefusedAtAnyTunnel() {
        world();
        for (int one = 0; one < 10; one++) {
            assertTrue(hold(one % 2 == 0 ? tunnelA : tunnelB).load(rebels.get(one)));
        }
        assertFalse(hold(tunnelA).load(rebels.get(10)), "full, at A");
        assertFalse(hold(tunnelB).load(rebels.get(10)), "and at B");
        assertEquals(10, hold(tunnelB).getPassengerCount());
    }

    @Test
    void theyLiveThroughTheLossOfATunnelAndDieWithTheLast() {
        world();
        for (int one = 0; one < 5; one++) {
            hold(tunnelA).load(rebels.get(one));
        }
        tunnelA.getBody().setHealth(0f);
        logic.update();
        logic.update();
        assertTrue(rebels.subList(0, 5).stream().noneMatch(GameObject::isEffectivelyDead), "B still stands");
        assertEquals(5, hold(tunnelB).getPassengerCount(), "and holds them");

        tunnelB.getBody().setHealth(0f);
        logic.update();
        assertTrue(rebels.subList(0, 5).stream().allMatch(GameObject::isEffectivelyDead), "the last gone: all of them");
        logic.update();
        assertTrue(rebels.subList(0, 5).stream().noneMatch(logic.getObjects()::contains), "and taken away");
        assertTrue(rebels.subList(5, 11).stream().noneMatch(GameObject::isEffectivelyDead), "those outside live");
    }

    @Test
    void aSoldTunnelLeavesTheNetworkAndItsPassengersStayInTheOtherUntilTheLastIsSold() {
        world();
        hold(tunnelA).load(rebels.get(0));
        hold(tunnelA).load(rebels.get(1));
        var sell = new uz.dukeengine.rts.message.GameMessage.Sell(tunnelA.getPlayerIndex(), tunnelA.getId());

        assertTrue(uz.dukeengine.rts.construction.Selling.order(logic, sell));
        logic.update();
        assertTrue(rebels.get(0).isContained() && rebels.get(1).isContained(), "still inside, in the network");
        assertEquals(2, hold(tunnelB).getPassengerCount(), "held by the other tunnel");
        assertFalse(hold(tunnelA).load(rebels.get(2)), "and the sold one takes no one in");

        assertTrue(uz.dukeengine.rts.construction.Selling.order(logic,
                new uz.dukeengine.rts.message.GameMessage.Sell(tunnelB.getPlayerIndex(), tunnelB.getId())));
        assertFalse(rebels.get(0).isContained() || rebels.get(1).isContained(), "the last sold: out they come");
        for (var rebel : rebels.subList(0, 2)) {
            assertTrue(rebel.getPosition().distance(tunnelB.getPosition()) < 20f, "beside it: " + rebel.getPosition());
        }
    }

    @Test
    void aNetworkThatSaysSoLosesItsPassengersWithoutADeath() {
        var things = new ThingFactory(RtsModules.withDefaults());
        var quiet = RtsTemplate.named("QuietTunnel").geometry(new Geometry.Cylinder(10f, 8f))
                .module(new ActiveBody.Data(1000f))
                .module(new ContainModule.Data(10, "Tunnel", false, null, true, null)).build();
        var rebel = RtsTemplate.named("Rebel").geometry(new Geometry.Cylinder(2f, 4f))
                .module(new ActiveBody.Data(100f)).build();
        things.addTemplate(quiet);
        things.addTemplate(rebel);
        logic = new ProductionTest.TestLogic(things);
        logic.init();
        int gla = logic.getPlayerList().addPlayer("GLA").getIndex();
        var tunnel = placed(quiet, gla, 100f, 100f);
        var inside = placed(rebel, gla, 120f, 100f);
        hold(tunnel).load(inside);
        logic.drainEvents();

        tunnel.getBody().setHealth(0f);
        logic.update();
        logic.update();

        assertFalse(logic.getObjects().contains(inside), "gone with the network");
        var died = logic.drainEvents().stream().filter(uz.dukeengine.core.event.ObjectDied.class::isInstance)
                .map(uz.dukeengine.core.event.ObjectDied.class::cast).map(e -> e.object()).toList();
        assertEquals(List.of(tunnel.getId()), died, "the tunnel's death told, and none for the one inside");
    }
}
