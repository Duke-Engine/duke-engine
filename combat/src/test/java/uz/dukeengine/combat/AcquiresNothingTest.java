package uz.dukeengine.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.combat.module.WeaponUpdate;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.ThingTemplate;

/** A weapon that picks nothing by itself: idle beside enemies, an order works, and after it idle again (E87). */
class AcquiresNothingTest {

    @Test
    void itFightsOnlyWhenTold() {
        var world = new CombatWorld();
        world.init();
        world.getThingFactory().addTemplate(ThingTemplate.named("Hero").module(new ActiveBody.Data(100f))
                .module(new WeaponUpdate.Data(50f, 30f, 10, DamageType.NORMAL)).build());
        world.getThingFactory().addTemplate(ThingTemplate.named("Skeleton").module(new ActiveBody.Data(40f)).build());
        var players = world.getPlayerList();
        var red = players.addPlayer("Red");
        var blue = players.addPlayer("Blue");
        red.setRelationshipTo(blue, Relationship.ENEMIES);
        blue.setRelationshipTo(red, Relationship.ENEMIES);
        var hero = world.spawn(world.findTemplate("Hero"), Coord3D.ZERO, red.getIndex());
        var first = world.spawn(world.findTemplate("Skeleton"), new Coord3D(10f, 0f, 0f), blue.getIndex());
        var second = world.spawn(world.findTemplate("Skeleton"), new Coord3D(0f, 10f, 0f), blue.getIndex());
        var weapon = hero.findModule(WeaponUpdate.class);
        weapon.acquiresNothing(true);
        weapon.holdFire();

        run(world, 60);
        assertFalse(weapon.isAttacking(), "enemies in reach, and no target picked");
        assertEquals(40f, first.getBody().getHealth());

        assertTrue(weapon.attack(first.getId()));
        run(world, 5);
        assertTrue(first.isEffectivelyDead(), "told: it struck");
        run(world, 60);
        assertEquals(40f, second.getBody().getHealth(), "and after, idle again: the second untouched");
        assertFalse(weapon.isAttacking());
    }

    private static void run(CombatWorld world, int frames) {
        for (int frame = 0; frame < frames; frame++) {
            world.update();
            world.drainEvents();
        }
    }
}
