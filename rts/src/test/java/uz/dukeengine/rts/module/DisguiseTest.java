package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.Disguise;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.rts.RtsTemplate;
import uz.dukeengine.rts.message.GameMessage;
import uz.dukeengine.rts.network.CommandCodec;

/**
 * A thing passing itself off as none of an enemy's targets — the reference's bomb truck disguised as one of his
 * vehicles: not acquired by his weapons, not taken by a plain attack order, taken by a forced one.
 */
class DisguiseTest {

    /** Passing itself off to every side but its own. */
    static final class Disguised extends Module implements Disguise {
        Disguised(GameObject owner) {
            super(owner);
        }

        @Override
        public boolean fools(int player) {
            return true;
        }
    }

    private CombatTest.CombatLogic world;
    private GameObject truck;
    private GameObject tank;

    private void world() {
        var factory = new ThingFactory(RtsModules.withDefaults());
        factory.addTemplate(RtsTemplate.named("BombTruck").module(new ActiveBody.Data(300f)).build());
        factory.addTemplate(RtsTemplate.named("Crusader").module(new ActiveBody.Data(480f))
                .module(new WeaponUpdate.Data(20f, 150f, 15, DamageType.NORMAL)).build());
        world = new CombatTest.CombatLogic(factory);
        world.init();
        var gla = world.getPlayerList().addPlayer("GLA");
        var usa = world.getPlayerList().addPlayer("USA");
        gla.setRelationshipTo(usa, Relationship.ENEMIES);
        usa.setRelationshipTo(gla, Relationship.ENEMIES);
        truck = world.spawn(world.getThingFactory().findTemplate("BombTruck"), new Coord3D(100f, 100f, 0f), 0);
        tank = world.spawn(world.getThingFactory().findTemplate("Crusader"), new Coord3D(150f, 100f, 0f), 1);
        truck.addModule(new Disguised(truck));
        truck.drawAs("Crusader", 1);
    }

    @Test
    void anEnemysTankBesideItDoesNotAcquireItAndAPlainOrderIsRefusedWhileAForcedOneIsTaken() {
        world();
        for (int frame = 0; frame < 30; frame++) {
            world.update();
        }
        var weapon = tank.findModule(WeaponUpdate.class);
        assertNull(weapon.getTarget(), "left alone");
        assertEquals(300f, truck.getBody().getHealth());

        assertFalse(weapon.attack(truck.getId()), "a plain attack order on it is refused");
        assertTrue(weapon.attack(truck.getId(), true), "a forced one is taken");
        for (int frame = 0; frame < 30; frame++) {
            world.update();
        }
        assertEquals(truck.getId(), weapon.getTarget(), "and kept");
        assertTrue(truck.getBody().getHealth() < 300f, "and fired on");
    }

    @Test
    void itsOwnSideIsNeverFooledAndTheLookIsSaidOnTheThing() {
        world();
        assertFalse(truck.isDisguisedFrom(0));
        assertTrue(truck.isDisguisedFrom(1));
        assertEquals("Crusader", truck.getDrawnAs());
        assertEquals(1, truck.getWearsColoursOf());
    }

    @Test
    void aForcedAttackTravelsTheWire() {
        var forced = new GameMessage.AttackObject(1, List.of(new ObjectId(4)), new ObjectId(7), true);
        var plain = new GameMessage.AttackObject(1, List.of(new ObjectId(4)), new ObjectId(7));
        var packet = new uz.dukeengine.core.network.CommandPacket(3, 1, List.of(forced, plain));

        assertEquals(packet, CommandCodec.INSTANCE.decode(CommandCodec.INSTANCE.encode(packet)));
    }
}
