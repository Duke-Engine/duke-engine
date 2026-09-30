package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.rts.message.GameMessage;
import uz.dukeengine.rts.module.OrderListener;
import uz.dukeengine.combat.module.WeaponUpdate;
import uz.dukeengine.combat.message.CombatOrder;

/** A unit's modules told the standard orders it is given — a weaponless one too, which passes them to its spawn. */
class OrdersHeardTest {

    /** A stinger site's hive: no weapon of its own; its soldier fights for it. */
    static final class Hive extends Module implements OrderListener {
        final List<String> heard = new ArrayList<>();
        GameObject soldier;

        Hive(GameObject owner) {
            super(owner);
        }

        @Override
        public void onOrder(uz.dukeengine.core.message.Command order) {
            heard.add(order.getClass().getSimpleName() + " @" + getOwner().getWorld().getFrame());
            if (order instanceof CombatOrder.AttackObject attack) {
                soldier.findModule(WeaponUpdate.class).attack(attack.target());
            }
        }
    }

    @Test
    void aWeaponlessUnitHearsItsOrdersTheFrameTheyAreAppliedAndSendsItsSoldier() {
        var game = DukeGame.create("hive").loadUnits(DukeGame.STARTER_UNITS).map(60, 40);
        var gla = game.addPlayer("GLA", Color.GREEN);
        var usa = game.addPlayer("USA", Color.BLUE);
        game.enemies(gla, usa).localPlayer(gla);
        game.spawn("Barracks", gla, 100f, 100f);
        game.spawn("Rifleman", gla, 120f, 100f);
        game.spawn("Rifleman", usa, 200f, 100f);
        game.runHeadless(1);
        var objects = game.getLogic().getObjects();
        var site = objects.get(0);
        var hive = new Hive(site);
        hive.soldier = objects.get(1);
        site.addModule(hive);
        var enemy = objects.get(2);

        game.postCommand(new CombatOrder.AttackObject(gla.getIndex(), List.of(site.getId()), enemy.getId()));
        game.runHeadless(2);
        game.postCommand(new CombatOrder.MoveTo(gla.getIndex(), List.of(site.getId()), new Coord3D(0f, 0f, 0f)));
        game.postCommand(new CombatOrder.StopMoving(gla.getIndex(), List.of(site.getId())));
        game.runHeadless(2);

        assertEquals(List.of("AttackObject", "MoveTo", "StopMoving"),
                hive.heard.stream().map(line -> line.substring(0, line.indexOf(' '))).toList(),
                "each order heard, in the order applied, weaponless and legless as it is");
        assertTrue(hive.soldier.findModule(WeaponUpdate.class).isAttacking(), "its soldier sent that frame");
    }
}
