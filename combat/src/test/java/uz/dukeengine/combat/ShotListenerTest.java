package uz.dukeengine.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.combat.module.ProjectileLauncher;
import uz.dukeengine.combat.module.Shot;
import uz.dukeengine.combat.module.ShotListener;
import uz.dukeengine.combat.module.Weapon;
import uz.dukeengine.combat.module.WeaponSet;
import uz.dukeengine.combat.module.WeaponSlot;
import uz.dukeengine.combat.module.WeaponUpdate;
import uz.dukeengine.core.event.ObjectHurt;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.UpdateModule;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ThingTemplate;

/**
 * What a thing's weapons do, told to its own modules: each shot as it is fired, then each blow of it, how much after
 * armour — the frame a swing strikes, and the share a lifesteal takes.
 */
class ShotListenerTest {

    /** What its shooter's modules heard: "fired@frame" and "dealt:amount@frame", in order. */
    private static final class Tally extends Module implements ShotListener {
        final List<String> heard = new ArrayList<>();

        Tally(GameObject owner) {
            super(owner);
        }

        @Override
        public void onFired(Shot shot, GameObject victim) {
            heard.add("fired@" + getOwner().getWorld().getFrame());
        }

        @Override
        public void onDealt(Shot shot, GameObject victim, float damage) {
            heard.add("dealt:" + damage + "@" + getOwner().getWorld().getFrame());
        }
    }

    /** A launcher that carries every shot three frames and then lands it on its victim. */
    private static final class Mortar extends UpdateModule implements ProjectileLauncher {
        private final List<Object[]> flying = new ArrayList<>();

        Mortar(GameObject owner) {
            super(owner);
        }

        @Override
        public boolean launch(GameObject shooter, GameObject victim, Shot shot) {
            flying.add(new Object[] {shot, victim, getOwner().getWorld().getFrame() + 3});
            return true;
        }

        @Override
        public void update() {
            var world = getOwner().getWorld();
            for (var it = flying.iterator(); it.hasNext();) {
                var one = it.next();
                if ((int) one[2] == world.getFrame()) {
                    var victim = (GameObject) one[1];
                    WeaponUpdate.land(world, (Shot) one[0], victim, victim.getPosition(), null);
                    it.remove();
                }
            }
        }
    }

    private record Field(CombatWorld world, GameObject shooter, GameObject target, Tally tally, List<Float> hurts) {

        void run(int frames) {
            for (int frame = 0; frame < frames; frame++) {
                world.update();
                for (var event : world.drainEvents()) {
                    if (event instanceof ObjectHurt hurt) {
                        hurts.add(hurt.amount());
                    }
                }
            }
        }
    }

    private static Field field(float splash, boolean mortar) {
        var world = new CombatWorld();
        world.init();
        world.armoury().addWeapons(List.of(new Weapon("Gun", 10f, 60f, 100, 0, DamageType.NORMAL, splash, true,
                List.of(), 0, 0, true, null)));
        world.getThingFactory().addTemplate(ThingTemplate.named("Gunner").module(new ActiveBody.Data(100f))
                .module(WeaponUpdate.Data.sets(List.of(new WeaponSet(List.of(), List.of(new WeaponSlot("Gun"))))))
                .build());
        world.getThingFactory().addTemplate(ThingTemplate.named("Tank")
                .module(new ActiveBody.Data(1000f, Map.of(DamageType.NORMAL, 0.5f))).build());
        var players = world.getPlayerList();
        var red = players.addPlayer("Red");
        var blue = players.addPlayer("Blue");
        red.setRelationshipTo(blue, Relationship.ENEMIES);
        blue.setRelationshipTo(red, Relationship.ENEMIES);
        var shooter = world.spawn(world.findTemplate("Gunner"), Coord3D.ZERO, red.getIndex());
        var tally = new Tally(shooter);
        shooter.addModule(tally);
        if (mortar) {
            shooter.addModule(new Mortar(shooter));
        }
        var target = world.spawn(world.findTemplate("Tank"), new Coord3D(20f, 0f, 0f), blue.getIndex());
        return new Field(world, shooter, target, tally, new ArrayList<>());
    }

    @Test
    void aShotIsToldAsItIsFiredThenWhatItsBlowTookAfterArmour() {
        var field = field(0f, false);
        field.run(1);
        assertEquals(List.of("fired@0", "dealt:5.0@0"), field.tally().heard, "ten, halved by the tank's armour");
        assertEquals(List.of(5f), field.hurts(), "what the hurt says it took");
    }

    @Test
    void aCarriedShotDealsItsBlowTheFrameItLands() {
        var field = field(0f, true);
        field.run(5);
        assertEquals(List.of("fired@0", "dealt:5.0@3"), field.tally().heard);
    }

    @Test
    void aBlastsBystandersAreEachToldAsTheirBlowLands() {
        var field = field(15f, false);
        var beside = field.world().spawn(field.world().findTemplate("Tank"), new Coord3D(25f, 0f, 0f),
                field.target().getPlayerIndex());
        field.run(1);
        assertEquals(List.of("fired@0", "dealt:5.0@0", "dealt:5.0@0"), field.tally().heard,
                "the tank it hit, then the one beside it caught in the blast");
        assertTrue(beside.getBody().getHealth() < 1000f);
    }
}
