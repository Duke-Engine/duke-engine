package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.rts.RtsSimulation;
import uz.dukeengine.rts.RtsTemplate;
import uz.dukeengine.rts.construction.Selling;
import uz.dukeengine.rts.event.StructureSold;
import uz.dukeengine.rts.message.GameMessage;
import uz.dukeengine.rts.thing.RtsKinds;
import uz.dukeengine.combat.module.WeaponUpdate;

/** The everyday orders of an RTS's bar: sell, attack-move, guard, evacuate, exit. */
class OrdersTest {

    private static final class World extends RtsSimulation {
        World(ThingFactory factory) {
            super(factory);
        }

        @Override
        protected void onRtsCommand(GameMessage command) {
            switch (command) {
                case GameMessage.Sell sell -> Selling.order(this, sell);
                case GameMessage.AttackMove move -> AttackMoveOrder.order(this, move);
                case GameMessage.Guard guard -> GuardOrder.order(this, guard);
                case GameMessage.Evacuate evacuate -> ContainModule.evacuate(this, evacuate);
                case GameMessage.ExitContainer exit -> ContainModule.exit(this, exit);
                default -> {
                }
            }
        }

        @Override
        protected void simulate() {
        }
    }

    private static World world() {
        var factory = new ThingFactory(RtsModules.withDefaults());
        factory.addTemplate(RtsTemplate.named("WarFactory").kindOf(RtsKinds.STRUCTURE)
                .module(new ActiveBody.Data(1000f)).module(new ProductionUpdate.Data()).buildCost(2000).build());
        factory.addTemplate(RtsTemplate.named("Tank").visionRange(100f).module(new ActiveBody.Data(400f))
                .module(new MoveUpdate.Data(30f)).module(new WeaponUpdate.Data(40f, 60f, 15, DamageType.NORMAL))
                .buildCost(800).buildTimeFrames(300).build());
        factory.addTemplate(RtsTemplate.named("Scout").module(new ActiveBody.Data(10_000f))
                .module(new MoveUpdate.Data(30f)).build());
        factory.addTemplate(RtsTemplate.named("Transport").module(new ActiveBody.Data(300f))
                .module(new MoveUpdate.Data(30f)).module(new ContainModule.Data(5)).build());
        factory.addTemplate(RtsTemplate.named("Soldier").module(new ActiveBody.Data(100f))
                .module(new MoveUpdate.Data(20f)).build());
        var world = new World(factory);
        world.init();
        int ours = world.getPlayerList().addPlayer("Ours").getIndex();
        int theirs = world.getPlayerList().addPlayer("Theirs").getIndex();
        world.getPlayerList().getPlayer(ours).setRelationshipTo(world.getPlayerList().getPlayer(theirs),
                Relationship.ENEMIES);
        world.getPlayerList().getPlayer(theirs).setRelationshipTo(world.getPlayerList().getPlayer(ours),
                Relationship.ENEMIES);
        world.getRtsPlayer(ours).deposit(5000);
        return world;
    }

    private static GameObject put(World world, String template, int side, float x, float y) {
        return world.spawn(world.getThingFactory().findTemplate(template), new Coord3D(x, y, 0f), side);
    }

    private static void run(World world, int frames) {
        for (int frame = 0; frame < frames; frame++) {
            world.update();
        }
    }

    @Test
    void aSoldWarFactoryPaysItsQueueBackAtOnceAndHalfItsCostWhenItIsDown() {
        var world = world();
        var sold = new ArrayList<GameObject>();
        world.onSold(sold::add);
        var factory = put(world, "WarFactory", 1, 200f, 200f);
        assertTrue(factory.findModule(ProductionUpdate.class).queue(world.getThingFactory().findTemplate("Tank")));
        assertEquals(4200, world.getRtsPlayer(1).getMoney());

        world.issueCommand(new GameMessage.Sell(1, factory.getId()));
        run(world, 1);
        assertEquals(5000, world.getRtsPlayer(1).getMoney(), "the tank paid back at once");
        assertTrue(factory.hasStatus(ObjectStatus.SOLD));
        assertFalse(factory.findModule(ProductionUpdate.class).isProducing());

        run(world, 170);
        assertFalse(factory.isDestroyed(), "still coming down");
        assertEquals(5000, world.getRtsPlayer(1).getMoney());
        var moments = new ArrayList<>(world.drainEvents());
        run(world, 15);
        moments.addAll(world.drainEvents());

        assertTrue(factory.isDestroyed(), "down and gone, some 180 frames after it was sold");
        assertEquals(6000, world.getRtsPlayer(1).getMoney(), "half its cost back");
        assertEquals(List.of(factory), sold, "its watchers told it went, sold");
        assertTrue(moments.stream().anyMatch(event -> event instanceof StructureSold gone
                && gone.refund() == 1000 && gone.building().equals(factory.getId())));
        assertFalse(factory.isEffectivelyDead(), "taken down, not killed");
    }

    @Test
    void aBuildingKilledWhileItComesDownGivesNothingBack() {
        var world = world();
        var factory = put(world, "WarFactory", 1, 200f, 200f);
        world.issueCommand(new GameMessage.Sell(1, factory.getId()));
        run(world, 60);

        factory.getBody().setHealth(0f);
        run(world, 200);

        assertEquals(5000, world.getRtsPlayer(1).getMoney());
    }

    @Test
    void attackMovingPastAnEnemyInSightTakesItOnAndThenArrives() {
        var world = world();
        var tank = put(world, "Tank", 1, 100f, 100f);
        var enemy = put(world, "Soldier", 2, 400f, 160f);
        enemy.setStatus(ObjectStatus.DISABLED);

        world.issueCommand(new GameMessage.AttackMove(1, List.of(tank.getId()), new Coord3D(800f, 100f, 0f)));
        run(world, 900);

        assertTrue(enemy.isEffectivelyDead(), "taken on as it came into sight");
        assertTrue(tank.getPosition().distance(new Coord3D(800f, 100f, 0f)) <= 10f,
                "and then on to the point: " + tank.getPosition());
    }

    @Test
    void aGuardTakesOnAnEnemyWithin18OfItsVisionAndWalksBackOnceItFleesPast22() {
        var world = world();
        var guard = put(world, "Tank", 1, 500f, 500f);
        var scout = put(world, "Scout", 2, 500f + 190f, 500f);
        world.issueCommand(new GameMessage.Guard(1, List.of(guard.getId()), new Coord3D(500f, 500f, 0f), null,
                GameMessage.Guard.Mode.NORMAL));
        run(world, 30);
        var order = guard.findModule(GuardOrder.class);
        assertNull(order.getTarget(), "190 away is past 1.8 of its 100 of sight: left alone");

        scout.setPosition(new Coord3D(500f + 170f, 500f, 0f));
        run(world, 20);
        assertEquals(scout.getId(), order.getTarget(), "170 away, it takes it on");

        scout.setPosition(new Coord3D(500f + 230f, 500f, 0f));
        run(world, 5);
        assertNull(order.getTarget(), "past 2.2 of its sight from the place it guards, it lets go");
        assertTrue(order.isReturning() || guard.getPosition().distance(new Coord3D(500f, 500f, 0f)) <= 10f);
        run(world, 300);
        assertTrue(guard.getPosition().distance(new Coord3D(500f, 500f, 0f)) <= 10f,
                "and walks back: " + guard.getPosition());
    }

    @Test
    void anEvacuatedTransportIsEmptyAndItsPassengersBesideIt() {
        var world = world();
        var transport = put(world, "Transport", 1, 300f, 300f);
        var hold = transport.findModule(ContainModule.class);
        var riders = new ArrayList<GameObject>();
        for (int n = 0; n < 3; n++) {
            var rider = put(world, "Soldier", 1, 300f, 300f);
            assertTrue(hold.load(rider));
            riders.add(rider);
        }

        world.issueCommand(new GameMessage.ExitContainer(1, riders.getFirst().getId()));
        run(world, 1);
        assertEquals(2, hold.getPassengerCount(), "one out");
        world.issueCommand(new GameMessage.Evacuate(1, transport.getId()));
        run(world, 1);

        assertEquals(0, hold.getPassengerCount());
        for (var rider : riders) {
            assertFalse(rider.isContained());
            assertTrue(rider.getPosition().distance(transport.getPosition()) < 10f, "beside it");
        }
    }

    @Test
    void theSameOrdersGiveTheSameWorldOnTwoPeers() {
        var first = world();
        var second = world();
        for (var world : List.of(first, second)) {
            var factory = put(world, "WarFactory", 1, 200f, 200f);
            var tank = put(world, "Tank", 1, 100f, 100f);
            put(world, "Scout", 2, 300f, 120f);
            world.issueCommand(new GameMessage.Sell(1, factory.getId()));
            world.issueCommand(new GameMessage.AttackMove(1, List.of(tank.getId()), new Coord3D(600f, 100f, 0f)));
        }

        run(first, 240);
        run(second, 240);

        assertEquals(first.checksum(), second.checksum());
    }
}
