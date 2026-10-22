package uz.dukeengine.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;
import uz.dukeengine.combat.module.WeaponUpdate;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.ThingTemplate;

/**
 * A thing set apart from its side's relation to another player — the reference's {@code
 * TEAM_SET_OVERRIDE_RELATION_TO_TEAM}: friendly to them while the rest of its side is not, and hostile again once the
 * override is cleared.
 */
class RelationOverrideTest {

    @Test
    void aThingOverriddenFriendlyIsNotAcquiredWhileTheRestOfItsSideIsAndClearingItRestoresTheOldRelation() {
        var world = new CombatWorld();
        world.init();
        world.getThingFactory().addTemplate(ThingTemplate.named("Gunner").module(new ActiveBody.Data(100f))
                .module(new WeaponUpdate.Data(1f, 60f, 10, DamageType.NORMAL)).build());
        world.getThingFactory().addTemplate(ThingTemplate.named("Truck").module(new ActiveBody.Data(1000f)).build());
        var players = world.getPlayerList();
        var red = players.addPlayer("Red");
        var blue = players.addPlayer("Blue");
        red.setRelationshipTo(blue, Relationship.ENEMIES);
        blue.setRelationshipTo(red, Relationship.ENEMIES);
        var gunner = world.spawn(world.findTemplate("Gunner"), Coord3D.ZERO, red.getIndex());
        var convoy = world.spawn(world.findTemplate("Truck"), new Coord3D(10f, 0f, 0f), blue.getIndex());
        var other = world.spawn(world.findTemplate("Truck"), new Coord3D(40f, 0f, 0f), blue.getIndex());
        var weapon = gunner.findModule(WeaponUpdate.class);

        convoy.setRelationOverride(red.getIndex(), Relationship.ALLIES);
        long overridden = world.checksum();
        run(world, 30);

        assertEquals(other.getId(), weapon.getTarget(), "the nearer convoy passed over, the rest of its side taken");
        assertEquals(1000f, convoy.getBody().getHealth());
        assertEquals(Relationship.ALLIES, world.getRelationship(gunner, convoy));
        assertEquals(Relationship.ENEMIES, world.getRelationship(gunner, other));

        convoy.clearRelationOverride(red.getIndex());
        assertNotEquals(overridden, world.checksum(), "the override is in the checksum");
        assertEquals(Relationship.ENEMIES, world.getRelationship(gunner, convoy), "its side's relation again");
        assertEquals(Relationship.ENEMIES, world.getRelationship(red.getIndex(), convoy));
        weapon.holdFire();
        run(world, 30);

        assertEquals(convoy.getId(), weapon.getTarget(), "the nearest enemy again");
    }

    private static void run(CombatWorld world, int frames) {
        for (int frame = 0; frame < frames; frame++) {
            world.update();
            world.drainEvents();
        }
    }
}
