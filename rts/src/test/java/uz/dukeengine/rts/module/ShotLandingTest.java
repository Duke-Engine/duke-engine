package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.event.WorldEvent;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.rts.RtsTemplate;
import uz.dukeengine.combat.event.ShotLanded;
import uz.dukeengine.combat.event.WeaponFired;
import uz.dukeengine.rts.message.GameMessage;
import uz.dukeengine.combat.module.ExperienceModule;
import uz.dukeengine.combat.module.ProjectileLauncher;
import uz.dukeengine.combat.module.Shot;
import uz.dukeengine.combat.module.Weapon;
import uz.dukeengine.combat.module.WeaponSet;
import uz.dukeengine.combat.module.WeaponSlot;
import uz.dukeengine.combat.module.WeaponUpdate;
import uz.dukeengine.combat.message.CombatOrder;

/**
 * A shot a launcher carried lands the way one that hit at once does: the direct hit, the blast, the kill
 * experience and the moment it landed — told apart from its instant neighbours by the weapon it came from.
 */
class ShotLandingTest {

    private static final Weapon GUN = new Weapon("Gun", 10f, 60f, 5, 0, DamageType.NORMAL, 0f, true,
            List.of(), 0, 0, true, null);
    private static final Weapon SHELL = new Weapon("Shell", 40f, 60f, 30, 0, DamageType.EXPLOSION, 8f, true,
            List.of(), 0, 0, true, null);

    /** A game's launcher: it carries the shots of the weapons it is told to, and declines the rest. */
    private static final class Rack extends Module implements ProjectileLauncher {
        private final Set<String> carries;
        private final List<Shot> shots = new ArrayList<>();

        Rack(GameObject owner, String... carries) {
            super(owner);
            this.carries = Set.of(carries);
        }

        @Override
        public boolean launch(GameObject shooter, GameObject victim, Shot shot) {
            if (!carries.contains(shot.weapon().name())) {
                return false;
            }
            shots.add(shot);
            return true;
        }
    }

    private CombatTest.CombatLogic logic;
    private int us;
    private int them;
    private final List<WorldEvent> events = new ArrayList<>();

    @BeforeEach
    void setUp() {
        var factory = new ThingFactory(RtsModules.withDefaults());
        factory.addTemplate(shooter("Humvee", new WeaponSlot("Gun"), new WeaponSlot("Shell")));
        factory.addTemplate(shooter("Mortar", new WeaponSlot("Shell")));
        factory.addTemplate(RtsTemplate.named("Bunker").geometry(new Geometry.Cylinder(3f, 6f))
                .module(new ActiveBody.Data(1000f, Map.of(DamageType.EXPLOSION, 0.5f)))
                .module(ExperienceModule.Data.ofThresholds(50, 1000))
                .build());
        logic = new CombatTest.CombatLogic(factory);
        logic.init();
        logic.addWeapons(List.of(GUN, SHELL));
        var red = logic.getPlayerList().addPlayer("Red");
        var blue = logic.getPlayerList().addPlayer("Blue");
        red.setRelationshipTo(blue, Relationship.ENEMIES);
        blue.setRelationshipTo(red, Relationship.ENEMIES);
        us = red.getIndex();
        them = blue.getIndex();
    }

    private static uz.dukeengine.core.thing.ThingTemplate shooter(String name, WeaponSlot... slots) {
        return RtsTemplate.named(name).geometry(new Geometry.Cylinder(3f, 6f))
                .module(new ActiveBody.Data(100f))
                .module(ExperienceModule.Data.ofThresholds(0, 1000))
                .module(WeaponUpdate.Data.sets(List.of(new WeaponSet(List.of(), List.of(slots)))))
                .build();
    }

    private GameObject spawn(String template, int player, float x) {
        var thing = logic.createObject(logic.getThingFactory().findTemplate(template));
        thing.setPlayerIndex(player);
        thing.setPosition(new Coord3D(x, 0f, 0f));
        return thing;
    }

    private void attack(GameObject attacker, GameObject victim, int frames) {
        logic.issueCommand(new CombatOrder.AttackObject(us, List.of(attacker.getId()), victim.getId()));
        for (int frame = 0; frame < frames; frame++) {
            logic.update();
            events.addAll(logic.drainEvents());
        }
    }

    private List<String> fired() {
        return events.stream().filter(WeaponFired.class::isInstance).map(e -> ((WeaponFired) e).weapon()).toList();
    }

    private List<ShotLanded> landed() {
        events.addAll(logic.drainEvents());
        return events.stream().filter(ShotLanded.class::isInstance).map(ShotLanded.class::cast).toList();
    }

    /** The shell first, for its damage, then the gun while it reloads — each named, with its slot. */
    @Test
    void theLauncherIsToldWhichWeaponFiredAndFromWhichSlot() {
        var humvee = spawn("Humvee", us, 0f);
        var rack = new Rack(humvee, "Gun", "Shell");
        humvee.addModule(rack);

        attack(humvee, spawn("Bunker", them, 20f), 2);

        assertEquals(List.of("Shell", "Gun"), rack.shots.stream().map(s -> s.weapon().name()).toList());
        assertEquals(List.of(1, 0), rack.shots.stream().map(Shot::slot).toList());
        assertEquals(humvee.getId(), rack.shots.getFirst().shooter());
        assertEquals(us, rack.shots.getFirst().side());
    }

    /** A gun beside a launched shell: the gun still hits at once, and only the shell is carried. */
    @Test
    void onlyTheLaunchedWeaponsShotsAreHandedOver() {
        var humvee = spawn("Humvee", us, 0f);
        var rack = new Rack(humvee, "Shell");
        humvee.addModule(rack);
        var bunker = spawn("Bunker", them, 20f);

        attack(humvee, bunker, 12);

        long guns = fired().stream().filter("Gun"::equals).count();
        assertTrue(guns >= 2, "the gun kept firing while the shell reloaded: " + fired());
        assertEquals(fired().stream().filter("Shell"::equals).count(), rack.shots.size());
        assertTrue(rack.shots.stream().allMatch(s -> s.weapon() == SHELL), "the rack never saw the gun");
        assertEquals(1000f - 10f * guns, bunker.getBody().getHealth(), 0.01f, "only the gun has hit so far");
    }

    /** Landed, a carried shell does what it would have done fired at once — the same figure through the armour. */
    @Test
    void aLandedShotDealsWhatTheInstantOneDoes() {
        var instantBunker = spawn("Bunker", them, 20f);
        attack(spawn("Mortar", us, 0f), instantBunker, 1);
        float instant = 1000f - instantBunker.getBody().getHealth();
        assertEquals(20f, instant, 0.01f, "40, halved by the bunker's armour");
        assertEquals(instantBunker.getId(), landed().getFirst().victim(), "and it said where it landed");

        var mortar = spawn("Mortar", us, 200f);
        var rack = new Rack(mortar, "Shell");
        mortar.addModule(rack);
        var carriedBunker = spawn("Bunker", them, 220f);
        attack(mortar, carriedBunker, 1);
        assertEquals(1000f, carriedBunker.getBody().getHealth(), 0.01f, "nothing yet, the shell is in the air");

        WeaponUpdate.land(logic, rack.shots.getFirst(), carriedBunker, carriedBunker.getPosition(),
                mortar.getPosition());

        assertEquals(instant, 1000f - carriedBunker.getBody().getHealth(), 0.01f);
    }

    /** Where a client draws an instant hit landing: halfway up what it hit, with the blast's reach. */
    @Test
    void anInstantHitLandsAtTheMiddleOfItsVictim() {
        var bunker = spawn("Bunker", them, 20f);
        attack(spawn("Mortar", us, 0f), bunker, 1);

        var landed = landed().getFirst();
        assertEquals(new Coord3D(20f, 0f, 3f), landed.where(), "a bunker 6 tall, struck 3 up");
        assertEquals(8f, landed.radius(), 0f);
    }

    /** A shot says which slot fired it, and whether it strikes what it touches rather than from a distance. */
    @Test
    void aShotSaysItsSlotAndWhetherItStrikesByContact() {
        var humvee = spawn("Humvee", us, 0f);
        attack(humvee, spawn("Bunker", them, 20f), 2);
        var shots = events.stream().filter(WeaponFired.class::isInstance).map(WeaponFired.class::cast).toList();
        assertEquals(List.of(1, 0), shots.stream().map(WeaponFired::slot).toList(), "the shell's slot, the gun's");
        assertTrue(shots.stream().noneMatch(WeaponFired::contact), "both fire from 60 away");
        assertEquals(8f, shots.getFirst().radius(), 0f);

        var knife = new Weapon("Knife", 5f, 5f, 30, 0, DamageType.NORMAL, 0f, true, List.of(), 0, 0, true);
        logic.addWeapons(List.of(knife));
        logic.getThingFactory().addTemplate(shooter("Commando", new WeaponSlot("Knife")));
        events.clear();
        attack(spawn("Commando", us, 100f), spawn("Bunker", them, 106f), 1);
        var stab = events.stream().filter(WeaponFired.class::isInstance).map(WeaponFired.class::cast)
                .findFirst().orElseThrow();
        assertTrue(stab.contact(), "a reach of 5 is under a pathfinding cell: it strikes what it touches");
    }

    /** The blast catches an enemy beside the victim, and the kill is the shooter's rank. */
    @Test
    void aLandedShotSplashesAndCreditsTheKill() {
        var mortar = spawn("Mortar", us, 0f);
        var rack = new Rack(mortar, "Shell");
        mortar.addModule(rack);
        var victim = spawn("Bunker", them, 20f);
        var beside = spawn("Bunker", them, 25f);
        var far = spawn("Bunker", them, 40f);
        attack(mortar, victim, 1);
        victim.getBody().damage(990f); // ten left

        WeaponUpdate.land(logic, rack.shots.getFirst(), victim, victim.getPosition(), mortar.getPosition());

        assertTrue(victim.isEffectivelyDead());
        assertEquals(980f, beside.getBody().getHealth(), 0.01f, "the blast, through its armour");
        assertEquals(1000f, far.getBody().getHealth(), 0.01f);
        assertEquals(50, mortar.findModule(ExperienceModule.class).getExperience(), "the kill was the mortar's");
    }

    /** The mortar was destroyed while its shell flew: the shell lands all the same, and no one is credited. */
    @Test
    void aShotLandsAfterItsShooterIsGone() {
        var mortar = spawn("Mortar", us, 0f);
        var rack = new Rack(mortar, "Shell");
        mortar.addModule(rack);
        var victim = spawn("Bunker", them, 20f);
        attack(mortar, victim, 1);
        var shot = rack.shots.getFirst();
        logic.destroyObject(mortar);
        logic.update();
        assertNull(logic.findObject(shot.shooter()));

        WeaponUpdate.land(logic, shot, victim, victim.getPosition(), null);

        assertEquals(980f, victim.getBody().getHealth(), 0.01f);
        assertEquals(shot.shooter(), landed().getLast().shooter());
    }

    /** A shell that comes down on open ground — its victim moved — only blasts what stands round it. */
    @Test
    void aShotWithNoVictimOnlySplashes() {
        var mortar = spawn("Mortar", us, 0f);
        var rack = new Rack(mortar, "Shell");
        mortar.addModule(rack);
        var target = spawn("Bunker", them, 20f);
        attack(mortar, target, 1);
        var left = spawn("Bunker", them, 47f);
        var right = spawn("Bunker", them, 53f);
        var friend = spawn("Bunker", us, 50f);
        var where = new Coord3D(50f, 0f, 0f);

        WeaponUpdate.land(logic, rack.shots.getFirst(), null, where, mortar.getPosition());

        assertEquals(980f, left.getBody().getHealth(), 0.01f);
        assertEquals(980f, right.getBody().getHealth(), 0.01f);
        assertEquals(1000f, friend.getBody().getHealth(), 0.01f, "the blast spares its own side");
        assertEquals(1000f, target.getBody().getHealth(), 0.01f, "and the one it was fired at is far away");
        var landed = landed().getLast();
        assertNull(landed.victim());
        assertEquals(where, landed.where());
        assertEquals("Shell", landed.weapon());
    }
}
