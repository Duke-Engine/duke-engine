package uz.dukeengine.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.combat.event.WeaponFired;
import uz.dukeengine.combat.module.Engaging;
import uz.dukeengine.combat.module.Hold;
import uz.dukeengine.combat.module.Weapon;
import uz.dukeengine.combat.module.WeaponSet;
import uz.dukeengine.combat.module.WeaponSlot;
import uz.dukeengine.combat.module.WeaponUpdate;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.player.Player;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ThingTemplate;

/**
 * Combat on core alone: weapons linked from the world's armoury, a side's bonus, a hold's say over its passengers and
 * a computer's side — in a world that is no RTS's.
 */
class CombatAloneTest {

    private static Weapon gun(float damage) {
        return new Weapon("Gun", damage, 60f, 5, 0, DamageType.NORMAL, 0f, true, List.of(), 0, 0, true, null);
    }

    private record Field(CombatWorld world, GameObject shooter, GameObject target, List<String> fired) {

        void run(int frames) {
            for (int frame = 0; frame < frames; frame++) {
                world.update();
                for (var event : world.drainEvents()) {
                    if (event instanceof WeaponFired shot) {
                        fired.add(shot.weapon());
                    }
                }
            }
        }
    }

    private static Field field(CombatWorld world, float damage) {
        world.init();
        world.armoury().addWeapons(List.of(gun(damage)));
        world.getThingFactory().addTemplate(ThingTemplate.named("Gunner").module(new ActiveBody.Data(100f))
                .module(WeaponUpdate.Data.sets(List.of(new WeaponSet(List.of(), List.of(new WeaponSlot("Gun"))))))
                .build());
        world.getThingFactory().addTemplate(ThingTemplate.named("Wall").module(new ActiveBody.Data(1000f)).build());
        var players = world.getPlayerList();
        var red = players.addPlayer("Red");
        var blue = players.addPlayer("Blue");
        red.setRelationshipTo(blue, Relationship.ENEMIES);
        blue.setRelationshipTo(red, Relationship.ENEMIES);
        var shooter = world.spawn(world.findTemplate("Gunner"), Coord3D.ZERO, red.getIndex());
        var target = world.spawn(world.findTemplate("Wall"), new Coord3D(20f, 0f, 0f), blue.getIndex());
        return new Field(world, shooter, target, new ArrayList<>());
    }

    @Test
    void aWeaponLinkedFromTheArmouryFiresInAWorldThatIsNoRts() {
        var field = field(new CombatWorld(), 10f);
        field.run(1);
        assertEquals(List.of("Gun"), field.fired());
        assertEquals(990f, field.target().getBody().getHealth(), 1e-3f);
    }

    /** A side of a game's own whose weapons deal twice what its things' do. */
    private static final class Veterans extends Player implements ArmedSide {
        Veterans(int index, String name) {
            super(index, name);
        }

        @Override
        public float getWeaponDamageBonus() {
            return 2f;
        }
    }

    @Test
    void aSidesBonusMultipliesWhatItsWeaponsDeal() {
        var field = field(new CombatWorld(Veterans::new), 10f);
        field.run(1);
        assertEquals(980f, field.target().getBody().getHealth(), 1e-3f, "ten dealt twice over");
    }

    /** A hold of a game's own that lets its passengers fire, or not. */
    private static final class Bunker extends Module implements Hold {
        private final boolean firingPorts;
        private GameObject inside;

        Bunker(GameObject owner, boolean firingPorts) {
            super(owner);
            this.firingPorts = firingPorts;
        }

        @Override
        public boolean passengerFires(GameObject passenger) {
            return firingPorts && passenger == inside;
        }
    }

    private static void holdIn(Field field, boolean firingPorts) {
        var bunker = field.world().spawn(field.world().findTemplate("Wall"), new Coord3D(0f, 30f, 0f),
                field.shooter().getPlayerIndex());
        var hold = new Bunker(bunker, firingPorts);
        hold.inside = field.shooter();
        bunker.addModule(hold);
        field.shooter().setContained(true);
    }

    @Test
    void aContainedThingFiresOnlyWhereItsHoldSaysSo() {
        var shut = field(new CombatWorld(), 10f);
        holdIn(shut, false);
        shut.run(10);
        assertTrue(shut.fired().isEmpty(), "a hold with no ports keeps its passenger's fire in");

        var ported = field(new CombatWorld(), 10f);
        holdIn(ported, true);
        ported.run(1);
        assertEquals(List.of("Gun"), ported.fired(), "one with ports lets it fire");
    }

    @Test
    void whetherAComputerPlaysASideIsCoresPlayersToSay() {
        var field = field(new CombatWorld(), 10f);
        assertFalse(Engaging.computer(field.shooter()));
        field.world().getPlayerList().getPlayer(field.shooter().getPlayerIndex()).setComputer(true);
        assertTrue(Engaging.computer(field.shooter()));
    }

    @Test
    void aWorldThatKeepsNoArmouryLendsAnEmptyOne() {
        var empty = Armoury.of(null);
        assertEquals(null, empty.findWeapon("Gun"));
        assertEquals(1, empty.getTargetScanFrames());
    }
}
