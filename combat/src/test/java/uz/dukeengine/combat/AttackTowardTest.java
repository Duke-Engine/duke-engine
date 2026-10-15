package uz.dukeengine.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.combat.message.CombatOrder;
import uz.dukeengine.combat.module.ProjectileLauncher;
import uz.dukeengine.combat.module.Shot;
import uz.dukeengine.combat.module.WeaponUpdate;
import uz.dukeengine.combat.network.CombatOrderCodec;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.ThingTemplate;

/** A swing toward a point strikes the enemy before him, not behind; held, it swings at the weapon's own pace (E76). */
class AttackTowardTest {

    /** A launcher that keeps where each shot toward a place was loosed to, and carries it nowhere. */
    private static final class Quiver extends Module implements ProjectileLauncher {
        final List<Coord3D> loosed = new ArrayList<>();

        Quiver(GameObject owner) {
            super(owner);
        }

        @Override
        public boolean launchAt(GameObject shooter, Coord3D place, Shot shot) {
            loosed.add(place);
            return true;
        }
    }

    private record Field(CombatWorld world, GameObject knight, GameObject ahead, GameObject behind) {
        void run(int frames) {
            for (int frame = 0; frame < frames; frame++) {
                world.update();
                world.drainEvents();
            }
        }
    }

    /** Ten a blow, nine frames' wind-up, thirty between blows, a reach of ten. */
    private static Field field(float range) {
        var world = new CombatWorld();
        world.init();
        var sword = new WeaponUpdate.Data(10f, range, 30, DamageType.NORMAL, 0f, true, List.of(), 0, 0, 0, true,
                "Sword", List.of(), null, 0, 9);
        world.getThingFactory().addTemplate(ThingTemplate.named("Knight").module(new ActiveBody.Data(100f))
                .module(sword).build());
        world.getThingFactory().addTemplate(ThingTemplate.named("Skeleton").module(new ActiveBody.Data(100f)).build());
        var players = world.getPlayerList();
        var red = players.addPlayer("Red");
        var blue = players.addPlayer("Blue");
        red.setRelationshipTo(blue, Relationship.ENEMIES);
        blue.setRelationshipTo(red, Relationship.ENEMIES);
        var knight = world.spawn(world.findTemplate("Knight"), new Coord3D(100f, 100f, 0f), red.getIndex());
        var ahead = world.spawn(world.findTemplate("Skeleton"), new Coord3D(108f, 100f, 0f), blue.getIndex());
        var behind = world.spawn(world.findTemplate("Skeleton"), new Coord3D(92f, 100f, 0f), blue.getIndex());
        knight.findModule(WeaponUpdate.class).holdFire();
        return new Field(world, knight, ahead, behind);
    }

    @Test
    void aSwingTowardAPointStrikesTheOneBeforeHimAtItsWindUpsEnd() {
        var field = field(10f);
        field.knight().findModule(WeaponUpdate.class).attackToward(new Coord3D(150f, 100f, 0f), false);
        field.run(9);
        assertEquals(100f, field.ahead().getBody().getHealth(), "still winding up");
        field.run(1);
        assertEquals(90f, field.ahead().getBody().getHealth(), "struck at the wind-up's end");
        assertEquals(100f, field.behind().getBody().getHealth(), "the one behind him untouched");
        field.run(25); // within its reload: after it, an idle weapon finds its own target as ever
        assertEquals(90f, field.ahead().getBody().getHealth(), "let go: one swing");
    }

    @Test
    void heldItSwingsAtTheWeaponsPaceAndAWalkDoesNotEndIt() {
        var field = field(10f);
        var weapon = field.knight().findModule(WeaponUpdate.class);
        weapon.attackToward(new Coord3D(150f, 100f, 0f), true);
        field.run(50);
        weapon.dropTarget(); // a walk's order: the swing toward the point goes on
        assertNotNull(weapon.getToward());
        field.run(50);
        assertEquals(70f, field.ahead().getBody().getHealth(), "three blows in a hundred frames");
        assertEquals(100f, field.behind().getBody().getHealth());
        weapon.attackToward(new Coord3D(150f, 100f, 0f), false);
        assertEquals(null, weapon.getToward(), "let go between swings: over");
    }

    @Test
    void aRangedOneLoosesAlongTheLineToItsRange() {
        var field = field(60f);
        var quiver = new Quiver(field.knight());
        field.knight().addModule(quiver);
        field.knight().findModule(WeaponUpdate.class).attackToward(new Coord3D(100f, 300f, 0f), false);
        field.run(12);
        assertEquals(1, quiver.loosed.size());
        assertEquals(100f, quiver.loosed.getFirst().x(), 1e-3f);
        assertEquals(160f, quiver.loosed.getFirst().y(), 1e-3f, "sixty along the line toward the point");
        assertEquals(100f, field.ahead().getBody().getHealth(), "carried, not struck");
    }

    @Test
    void theOrderRoundTrips() {
        var order = new CombatOrder.AttackToward(1, List.of(new ObjectId(4)), new Coord3D(1.5f, 2f, 0f), true);
        assertEquals(order, CombatOrderCodec.decode(CombatOrderCodec.encode(order)));
    }
}
