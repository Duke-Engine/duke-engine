package uz.dukeengine.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.combat.module.ProjectileLauncher;
import uz.dukeengine.combat.module.Shot;
import uz.dukeengine.combat.module.WeaponUpdate;
import uz.dukeengine.combat.network.CombatOrderCodec;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.thing.GameObject;

/** A swing toward a point strikes the enemy before him, not behind; held, it swings at the weapon's own pace (E76). */
class SwingTest {

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

    private record Field(ActionWorld world, GameObject knight, GameObject ahead, GameObject behind) {
    }

    /** Ten a blow, nine frames' wind-up, thirty between blows. */
    private static Field field(float range) {
        var sword = new WeaponUpdate.Data(10f, range, 30, DamageType.NORMAL, 0f, true, List.of(), 0, 0, 0, true,
                "Sword", List.of(), null, 0, 9);
        var world = new ActionWorld(Map.of()).template("Knight", new ActiveBody.Data(100f), sword)
                .template("Skeleton", new ActiveBody.Data(100f));
        var knight = world.put("Knight", world.heroes, 100f, 100f);
        var ahead = world.put("Skeleton", world.monsters, 108f, 100f);
        var behind = world.put("Skeleton", world.monsters, 92f, 100f);
        knight.findModule(WeaponUpdate.class).holdFire();
        return new Field(world, knight, ahead, behind);
    }

    @Test
    void aSwingTowardAPointStrikesTheOneBeforeHimAtItsWindUpsEnd() {
        var field = field(10f);
        Swing.toward(field.knight(), new Coord3D(150f, 100f, 0f), false);
        field.world().run(9);
        assertEquals(100f, field.ahead().getBody().getHealth(), "still winding up");
        field.world().run(1);
        assertEquals(90f, field.ahead().getBody().getHealth(), "struck at the wind-up's end");
        assertEquals(100f, field.behind().getBody().getHealth(), "the one behind him untouched");
        field.world().run(25); // within its reload: after it, an idle weapon finds its own target as ever
        assertEquals(90f, field.ahead().getBody().getHealth(), "let go: one swing");
    }

    @Test
    void heldItSwingsAtTheWeaponsPaceAndAWalkDoesNotEndIt() {
        var field = field(10f);
        var weapon = field.knight().findModule(WeaponUpdate.class);
        Swing.toward(field.knight(), new Coord3D(150f, 100f, 0f), true);
        field.world().run(50);
        assertEquals(true, ActionOrders.apply(ActionOrders.along(field.world().heroes,
                List.of(field.knight().getId()), ActionOrders.NONE), field.world()));
        assertNotNull(weapon.getPointAttack(), "a walk's order: the swing toward the point goes on");
        field.world().run(50);
        assertEquals(70f, field.ahead().getBody().getHealth(), "three blows in a hundred frames");
        assertEquals(100f, field.behind().getBody().getHealth());
        Swing.toward(field.knight(), new Coord3D(150f, 100f, 0f), false);
        assertNull(weapon.getPointAttack(), "let go between swings: over");
    }

    @Test
    void aRangedOneLoosesAlongTheLineToItsRange() {
        var field = field(60f);
        var quiver = new Quiver(field.knight());
        field.knight().addModule(quiver);
        Swing.toward(field.knight(), new Coord3D(100f, 300f, 0f), false);
        field.world().run(12);
        assertEquals(1, quiver.loosed.size());
        assertEquals(100f, quiver.loosed.getFirst().x(), 1e-3f);
        assertEquals(160f, quiver.loosed.getFirst().y(), 1e-3f, "sixty along the line toward the point");
        assertEquals(100f, field.ahead().getBody().getHealth(), "carried, not struck");
    }

    @Test
    void theOrderTravelsAndSwings() {
        var field = field(10f);
        var order = ActionOrders.toward(field.world().heroes, List.of(field.knight().getId()),
                new Coord3D(150f, 100f, 0f), false);
        assertEquals(order, CombatOrderCodec.decode(CombatOrderCodec.encode(order)));
        ActionOrders.apply(order, field.world());
        field.world().run(10);
        assertEquals(90f, field.ahead().getBody().getHealth());
    }
}
