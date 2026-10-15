package uz.dukeengine.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.combat.message.CombatOrder;
import uz.dukeengine.combat.module.Weapon;
import uz.dukeengine.combat.module.WeaponSet;
import uz.dukeengine.combat.module.WeaponSlot;
import uz.dukeengine.combat.module.WeaponUpdate;
import uz.dukeengine.combat.network.CombatOrderCodec;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.ThingTemplate;

/** The force-attack key's two orders: a place of the ground shelled, and one's own thing fired on (round 21, ask 2). */
class GroundAttackTest {

    private record Field(CombatWorld world, WeaponUpdate gun, uz.dukeengine.core.thing.GameObject enemy,
            uz.dukeengine.core.thing.GameObject own) {

        void run(int frames) {
            for (int frame = 0; frame < frames; frame++) {
                world.update();
                world.drainEvents();
            }
        }
    }

    private static Field field() {
        var world = new CombatWorld();
        world.init();
        world.armoury().addWeapons(List.of(new Weapon("Shell", 10f, 60f, 10, 0, DamageType.NORMAL, 15f, true,
                List.of(), 0, 0, true, null)));
        world.getThingFactory().addTemplate(ThingTemplate.named("Gunner").module(new ActiveBody.Data(100f))
                .module(WeaponUpdate.Data.sets(List.of(new WeaponSet(List.of(), List.of(new WeaponSlot("Shell"))))))
                .build());
        world.getThingFactory().addTemplate(ThingTemplate.named("Tank").module(new ActiveBody.Data(1000f)).build());
        var players = world.getPlayerList();
        var red = players.addPlayer("Red");
        var blue = players.addPlayer("Blue");
        red.setRelationshipTo(blue, Relationship.ENEMIES);
        blue.setRelationshipTo(red, Relationship.ENEMIES);
        var gunner = world.spawn(world.findTemplate("Gunner"), Coord3D.ZERO, red.getIndex());
        var enemy = world.spawn(world.findTemplate("Tank"), new Coord3D(45f, 30f, 0f), blue.getIndex());
        var own = world.spawn(world.findTemplate("Tank"), new Coord3D(30f, 0f, 0f), red.getIndex());
        return new Field(world, gunner.findModule(WeaponUpdate.class), enemy, own);
    }

    @Test
    void aPlaceOfTheGroundIsShelledUntilANewOrderLetsItGo() {
        var field = field();
        var place = new Coord3D(40f, 30f, 0f);
        assertTrue(field.gun().attackGround(place));
        field.run(30);

        assertTrue(field.enemy().getBody().getHealth() < 1000f, "the blast round the place catches what stands by it");
        assertEquals(place, field.gun().getGroundTarget());
        field.gun().holdFire();
        assertEquals(null, field.gun().getGroundTarget(), "a new order lets it go");
    }

    @Test
    void onesOwnThingIsFiredOnByAForcedOrderAndNeverOtherwise() {
        var field = field();
        field.gun().attack(field.own().getId(), false);
        field.run(30);
        assertEquals(1000f, field.own().getBody().getHealth(), "not forced: its own side is never fired on");

        field.gun().attack(field.own().getId(), true);
        field.run(30);
        assertTrue(field.own().getBody().getHealth() < 1000f, "forced, by its player: fired on");
    }

    @Test
    void aGroundAttackGoesDownTheWireAndComesBackTheSame() {
        var order = new CombatOrder.AttackGround(2, List.of(new ObjectId(7), new ObjectId(9)),
                new Coord3D(12.5f, 40f, 3f), true);
        assertEquals(order, CombatOrderCodec.decode(CombatOrderCodec.encode(order)));
    }
}
