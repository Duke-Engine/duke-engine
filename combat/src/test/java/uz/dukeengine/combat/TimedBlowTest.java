package uz.dukeengine.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.combat.event.WeaponFired;
import uz.dukeengine.combat.module.WeaponUpdate;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ThingTemplate;

/** A game that times a swing itself strikes it with {@code WeaponUpdate.blow}: no wind-up, no reload, a share of the damage. */
class TimedBlowTest {

    private record Field(CombatWorld world, GameObject knight, GameObject near, GameObject far) {
    }

    private static Field field() {
        var world = new CombatWorld();
        world.init();
        var sword = new WeaponUpdate.Data(10f, 30f, 60, DamageType.NORMAL, 0f, true, List.of(), 0, 0, 0, true,
                "Sword", List.of(), null, 0, 9);
        world.getThingFactory().addTemplate(ThingTemplate.named("Knight").module(new ActiveBody.Data(100f))
                .module(sword).build());
        world.getThingFactory().addTemplate(ThingTemplate.named("Skeleton").module(new ActiveBody.Data(100f)).build());
        var players = world.getPlayerList();
        var red = players.addPlayer("Red");
        var blue = players.addPlayer("Blue");
        red.setRelationshipTo(blue, Relationship.ENEMIES);
        blue.setRelationshipTo(red, Relationship.ENEMIES);
        var knight = world.spawn(world.findTemplate("Knight"), Coord3D.ZERO, red.getIndex());
        knight.findModule(WeaponUpdate.class).acquiresNothing(true);
        var near = world.spawn(world.findTemplate("Skeleton"), new Coord3D(10f, 0f, 0f), blue.getIndex());
        var far = world.spawn(world.findTemplate("Skeleton"), new Coord3D(-10f, 0f, 0f), blue.getIndex());
        return new Field(world, knight, near, far);
    }

    @Test
    void aBlowStrikesAtOnceForItsShareOfTheDamageAndWaitsForNothing() {
        var field = field();
        var weapon = field.knight().findModule(WeaponUpdate.class);
        assertEquals(1, weapon.blow(List.of(field.near()), 1.5f));
        assertEquals(85f, field.near().getBody().getHealth(), 1e-4f, "a half as much again, this frame");
        assertEquals(1, weapon.blow(List.of(field.near()), 0.5f), "and again at once: no reload held it");
        assertEquals(80f, field.near().getBody().getHealth(), 1e-4f);
    }

    @Test
    void aBlowStrikesEveryVictimItIsGivenInOrderAndToldOnce() {
        var field = field();
        var weapon = field.knight().findModule(WeaponUpdate.class);
        var fired = new ArrayList<WeaponFired>();
        assertEquals(2, weapon.blow(List.of(field.near(), field.far()), 1f));
        field.world().update();
        for (var event : field.world().drainEvents()) {
            if (event instanceof WeaponFired shot) {
                fired.add(shot);
            }
        }
        assertEquals(90f, field.near().getBody().getHealth(), 1e-4f);
        assertEquals(90f, field.far().getBody().getHealth(), 1e-4f);
        assertEquals(1, fired.size(), "one swing, one moment");
        assertEquals(field.near().getId(), fired.getFirst().target(), "aimed at the first it struck");
    }

    @Test
    void aSwingAtNobodyIsStillTold() {
        var field = field();
        assertEquals(0, field.knight().findModule(WeaponUpdate.class).blow(List.of(), 1f));
        field.world().update();
        assertTrue(field.world().drainEvents().stream().anyMatch(WeaponFired.class::isInstance),
                "the swish of a blade through air");
        assertEquals(100f, field.near().getBody().getHealth());
    }

    @Test
    void theWeaponsOwnAttackIsNotDisturbed() {
        var field = field();
        var weapon = field.knight().findModule(WeaponUpdate.class);
        weapon.blow(List.of(field.near()), 1f);
        assertTrue(weapon.getTarget() == null, "it took no target by being used");
    }
}
