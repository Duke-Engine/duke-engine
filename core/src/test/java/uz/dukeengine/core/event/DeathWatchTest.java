package uz.dukeengine.core.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.Death;
import uz.dukeengine.core.module.DeathType;
import uz.dukeengine.core.module.ModuleFactory;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;

/** A death told to the simulation's own code as it is reaped, with whose side dealt it. */
class DeathWatchTest {

    private static final class World extends GameLogic {
        World(ThingFactory factory) {
            super(factory);
        }

        @Override
        protected void simulate() {
        }
    }

    private static final ThingTemplate SOLDIER = ThingTemplate.named("Soldier").module(new ActiveBody.Data(50f)).build();

    private final List<ObjectDied> heard = new ArrayList<>();

    private World world() {
        var factory = new ThingFactory(ModuleFactory.withDefaults());
        factory.addTemplate(SOLDIER);
        var world = new World(factory);
        world.init();
        world.onDied(heard::add);
        return world;
    }

    @Test
    void aDeathIsHeardAsItIsReapedBesideTheClientsEvent() {
        var world = world();
        var soldier = world.spawn(SOLDIER, new Coord3D(40f, 40f, 0f), 3);

        // A shell whose tank is long gone: the blow still says whose side fired it.
        soldier.getBody().damage(999f, DamageType.NORMAL, new Death(DeathType.NORMAL, new ObjectId(77), 2));
        world.update();

        assertEquals(1, heard.size());
        var died = heard.getFirst();
        assertEquals(3, died.playerIndex());
        assertEquals(new ObjectId(77), died.killer());
        assertEquals(2, died.killerPlayerIndex(), "the killer's side, though the killer is nowhere");
        var events = world.drainEvents();
        assertTrue(events.contains(died), "and the client's event is still there, the same death");
    }

    @Test
    void aBlowThatDoesNotSayItsSideFindsItFromAKillerLeavingTheSameFrame() {
        var world = world();
        var victim = world.spawn(SOLDIER, Coord3D.ZERO, 1);
        var killer = world.spawn(SOLDIER, new Coord3D(20f, 0f, 0f), 2);

        victim.getBody().damage(999f, DamageType.NORMAL, new Death(DeathType.NORMAL, killer.getId()));
        killer.getBody().damage(999f); // both fall together
        world.update();

        var victimsDeath = heard.stream().filter(died -> died.object().equals(victim.getId())).findFirst().orElseThrow();
        assertEquals(2, victimsDeath.killerPlayerIndex());
        var killersDeath = heard.stream().filter(died -> died.object().equals(killer.getId())).findFirst().orElseThrow();
        assertEquals(-1, killersDeath.killerPlayerIndex(), "a death by nobody is nobody's");
    }

    @Test
    void aThingRemovedWithoutDyingIsNotHeard() {
        var world = world();
        world.spawn(SOLDIER, Coord3D.ZERO, 1).markDestroyed(); // sold, or cleared away: not a death

        world.update();

        assertTrue(heard.isEmpty());
    }
}
