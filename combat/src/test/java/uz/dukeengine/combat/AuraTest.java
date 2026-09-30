package uz.dukeengine.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import uz.dukeengine.combat.module.AuraListener;
import uz.dukeengine.combat.module.AuraUpdate;
import uz.dukeengine.combat.module.Weapon;
import uz.dukeengine.core.data.Binder;
import uz.dukeengine.core.data.DukeText;
import uz.dukeengine.core.event.EffectPlayed;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.Classified;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Kind;
import uz.dukeengine.core.thing.ObjectStatus;

/**
 * Words and health for what stands near a thing, for as long as it stays: the reference's propaganda tower, and a
 * leader's bonus, looked at on the aura's own clock and taken back when it can no longer give them.
 */
class AuraTest {

    private static final String CHEERED = "ENTHUSIASTIC";
    private static final Kind STRUCTURE = Kind.of("STRUCTURE");
    private static final Kind INFANTRY = Kind.of("INFANTRY");

    /** A template with kinds, as a game's own record has them. */
    private record Kinded(String name, List<ModuleData> modules, Set<Kind> kindOf) implements Classified {
    }

    /** What the tower's own module heard at each look: the frame and the names of those inside. */
    private static final class Ear extends Module implements AuraListener {
        final List<String> heard = new ArrayList<>();

        Ear(GameObject owner) {
            super(owner);
        }

        @Override
        public void onPulse(AuraUpdate aura, List<GameObject> inside) {
            heard.add(getOwner().getWorld().getFrame() + ":" + inside.stream()
                    .map(thing -> thing.getTemplate().name()).toList());
        }
    }

    private record Field(CombatWorld world, int red, int blue) {

        GameObject put(String template, int side, float x) {
            return world.spawn(world.findTemplate(template), new Coord3D(x, 0f, 0f), side);
        }

        void run(int frames) {
            for (int frame = 0; frame < frames; frame++) {
                world.update();
            }
        }
    }

    private static Field field(AuraUpdate.Data aura) {
        var world = new CombatWorld();
        world.init();
        var things = world.getThingFactory();
        things.addTemplate(new Kinded("Tower", List.of(new ActiveBody.Data(500f), aura), Set.of(STRUCTURE)));
        things.addTemplate(new Kinded("Soldier", List.of(new ActiveBody.Data(100f)), Set.of(INFANTRY)));
        things.addTemplate(new Kinded("Tank", List.of(new ActiveBody.Data(300f)), Set.of()));
        things.addTemplate(new Kinded("Wall", List.of(new ActiveBody.Data(800f)), Set.of(STRUCTURE)));
        var players = world.getPlayerList();
        var red = players.addPlayer("Red");
        var blue = players.addPlayer("Blue");
        red.setRelationshipTo(blue, Relationship.ENEMIES);
        blue.setRelationshipTo(red, Relationship.ENEMIES);
        return new Field(world, red.getIndex(), blue.getIndex());
    }

    private static AuraUpdate.Data cheering(float radius, int pulseFrames) {
        return new AuraUpdate.Data(radius, pulseFrames, List.of(CHEERED));
    }

    @Test
    void anAllyWithinReachHoldsTheWordsWhileThereAndGivesThemBackAtTheLookAfterItLeaves() {
        var field = field(cheering(50f, 10));
        field.put("Tower", field.red(), 0f);
        var soldier = field.put("Soldier", field.red(), 30f);
        var enemy = field.put("Soldier", field.blue(), 30f);
        field.run(1);
        assertTrue(soldier.hasCondition(CHEERED), "looked at on its first frame");
        assertFalse(enemy.hasCondition(CHEERED), "its own side and its allies, none named");

        soldier.setPosition(new Coord3D(80f, 0f, 0f));
        field.run(5);
        assertTrue(soldier.hasCondition(CHEERED), "held until the aura looks again");
        field.run(5);
        assertFalse(soldier.hasCondition(CHEERED), "and given back then");
    }

    @Test
    void whereTwoAurasMeetLeavingOneKeepsWhatTheOtherGives() {
        var field = field(cheering(50f, 10));
        field.put("Tower", field.red(), 0f);
        field.put("Tower", field.red(), 80f);
        var soldier = field.put("Soldier", field.red(), 40f);
        field.run(1);
        assertTrue(soldier.hasCondition(CHEERED));

        soldier.setPosition(new Coord3D(100f, 0f, 0f));
        for (int frame = 0; frame < 20; frame++) {
            field.run(1);
            assertTrue(soldier.hasCondition(CHEERED), "the second tower still holds him, every frame");
        }
        soldier.setPosition(new Coord3D(300f, 0f, 0f));
        field.run(10);
        assertFalse(soldier.hasCondition(CHEERED), "out of both");
    }

    @Test
    void itsHealIsAShareOfTheMostEachSecondTakenFromOneAuraAtATime() {
        var heals = new AuraUpdate.Data(50f, 10, List.of(), 0.3f, List.of(), List.of(), List.of(), null);
        var one = field(heals);
        one.put("Tower", one.red(), 0f);
        var lone = one.put("Soldier", one.red(), 30f);
        lone.getBody().setHealth(20f);
        one.run(30);
        assertEquals(50f, lone.getBody().getHealth(), 0.01f, "30% of 100 over a second");

        var two = field(heals);
        two.put("Tower", two.red(), 0f);
        two.put("Tower", two.red(), 10f);
        var between = two.put("Soldier", two.red(), 5f);
        between.getBody().setHealth(20f);
        two.run(30);
        assertEquals(50f, between.getBody().getHealth(), 0.01f, "two towers heal him no faster than one");
    }

    @Test
    void itTakesItsWordsBackWhenDisabledAndGivesThemAgainWhenItWorks() {
        var field = field(cheering(50f, 10));
        var tower = field.put("Tower", field.red(), 0f);
        var soldier = field.put("Soldier", field.red(), 30f);
        field.run(1);
        tower.setStatus(ObjectStatus.DISABLED);
        field.run(1);
        assertFalse(soldier.hasCondition(CHEERED), "a stunned tower cheers nobody");
        assertTrue(tower.findModule(AuraUpdate.class).getInside().isEmpty());

        tower.clearStatus(ObjectStatus.DISABLED);
        field.run(10);
        assertTrue(soldier.hasCondition(CHEERED), "back at its next look");
    }

    @Test
    void itTakesItsWordsBackWhenItsThingDiesAndWhenItIsTakenOff() {
        var dies = field(cheering(50f, 10));
        var tower = dies.put("Tower", dies.red(), 0f);
        var soldier = dies.put("Soldier", dies.red(), 30f);
        dies.run(1);
        tower.getBody().damage(10_000f);
        dies.run(1);
        assertFalse(soldier.hasCondition(CHEERED));
        assertNull(dies.world().findObject(tower.getId()), "gone");

        var dropped = field(cheering(50f, 10));
        var leader = dropped.put("Tower", dropped.red(), 0f);
        var follower = dropped.put("Soldier", dropped.red(), 30f);
        dropped.run(1);
        leader.removeModule(leader.findModule(AuraUpdate.class));
        assertFalse(follower.hasCondition(CHEERED), "a skill given up takes its aura's words with it at once");
    }

    @Test
    void whomItReachesIsWhatItsAffectsAndKindsSay() {
        var self = field(new AuraUpdate.Data(50f, 10, List.of(CHEERED), 0f,
                List.of(Weapon.Affects.SELF, Weapon.Affects.ENEMIES), List.of(), List.of(), null));
        var tower = self.put("Tower", self.red(), 0f);
        var ally = self.put("Soldier", self.red(), 20f);
        var enemy = self.put("Soldier", self.blue(), 20f);
        self.run(1);
        assertTrue(tower.hasCondition(CHEERED), "SELF: itself");
        assertTrue(enemy.hasCondition(CHEERED), "ENEMIES: its enemies");
        assertFalse(ally.hasCondition(CHEERED), "and, having named whom, not its allies");

        var kinds = field(new AuraUpdate.Data(50f, 10, List.of(CHEERED), 0f, List.of(), List.of(),
                List.of(STRUCTURE), null));
        kinds.put("Tower", kinds.red(), 0f);
        var wall = kinds.put("Wall", kinds.red(), 20f);
        var tank = kinds.put("Tank", kinds.red(), 20f);
        kinds.run(1);
        assertFalse(wall.hasCondition(CHEERED), "the reference's tower passes buildings by");
        assertTrue(tank.hasCondition(CHEERED));

        var infantry = field(new AuraUpdate.Data(50f, 10, List.of(CHEERED), 0f, List.of(), List.of(INFANTRY),
                List.of(), null));
        infantry.put("Tower", infantry.red(), 0f);
        var man = infantry.put("Soldier", infantry.red(), 20f);
        var vehicle = infantry.put("Tank", infantry.red(), 20f);
        infantry.run(1);
        assertTrue(man.hasCondition(CHEERED));
        assertFalse(vehicle.hasCondition(CHEERED), "a leader of foot alone");
    }

    @Test
    void itsReachIsMeasuredAlongTheGroundAsTheReferenceMeasuresIt() {
        var field = field(cheering(50f, 10));
        field.put("Tower", field.red(), 0f);
        var above = field.world().spawn(field.world().findTemplate("Soldier"), new Coord3D(40f, 0f, 200f),
                field.red());
        field.run(1);
        assertTrue(above.hasCondition(CHEERED), "40 across, however high");
    }

    @Test
    void eachLookIsToldToItsThingAndPlaysItsPulse() {
        var field = field(new AuraUpdate.Data(50f, 10, List.of(), 0f, List.of(), List.of(), List.of(),
                "PropagandaPulse"));
        var tower = field.put("Tower", field.red(), 0f);
        var ear = new Ear(tower);
        tower.addModule(ear);
        field.put("Soldier", field.red(), 30f);
        field.put("Tank", field.red(), 90f);
        var pulses = new ArrayList<Integer>();
        for (int frame = 0; frame < 21; frame++) {
            field.world().update();
            for (var event : field.world().drainEvents()) {
                if (event instanceof EffectPlayed played && "PropagandaPulse".equals(played.name())) {
                    assertEquals(tower.getId(), played.riding());
                    pulses.add(played.frame());
                }
            }
        }
        assertEquals(List.of(0, 10, 20), pulses);
        assertEquals(List.of("0:[Soldier]", "10:[Soldier]", "20:[Soldier]"), ear.heard);
    }

    @Test
    void anAuraIsReadFromItsBlock() {
        var aura = new Binder().bind(DukeText.parse("""
                AuraUpdate
                  Radius = 150
                  PulseFrames = 60
                  Words = [ENTHUSIASTIC]
                  HealShareEachSecond = 0.01
                  ExceptKinds = [STRUCTURE]
                  PulseEffect = PropagandaPulse
                End
                """, "tower.duke").getFirst(), AuraUpdate.Data.class);
        assertEquals(new AuraUpdate.Data(150f, 60, List.of(CHEERED), 0.01f, List.of(), List.of(), List.of(STRUCTURE),
                "PropagandaPulse"), aura);

        var bare = new Binder().bind(DukeText.parse("""
                AuraUpdate
                  Radius = 40
                End
                """, "leader.duke").getFirst(), AuraUpdate.Data.class);
        assertEquals(1, bare.pulseFrames(), "what a block leaves out: every frame");
        assertEquals(List.of(), bare.affects());
    }
}
