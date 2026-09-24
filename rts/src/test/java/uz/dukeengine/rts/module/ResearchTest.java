package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import uz.dukeengine.core.thing.ThingTemplate;
import uz.dukeengine.rts.RtsTemplate;
import uz.dukeengine.rts.event.UpgradeCompleted;
import uz.dukeengine.rts.event.WeaponFired;
import uz.dukeengine.rts.player.Upgrade;

/** Research queued at a building like a unit: charged, refunded, finished its time later, and reaching what it should. */
class ResearchTest {

    /** Three seconds of the game's thirty frames a second. */
    private static final Upgrade ARMOUR = new Upgrade("Upgrade_Armour", 400, Map.of("Armour", 1.2f), 90,
            Upgrade.Scope.PLAYER, "icons/upgrades/armour.png");
    private static final Upgrade MISSILES = new Upgrade("Upgrade_Missiles", 200, Map.of(), 30, Upgrade.Scope.PLAYER,
            null);
    private static final Upgrade ROOF = new Upgrade("Upgrade_Roof", 100, Map.of(), 10, Upgrade.Scope.OBJECT, null);

    private static final Weapon GUN = new Weapon("Gun", 1f, 100f, 1, 0, DamageType.NORMAL, 0f, true, List.of(), 0, 0,
            true);
    private static final Weapon MISSILE = new Weapon("Missile", 1f, 100f, 1, 0, DamageType.NORMAL, 0f, true,
            List.of(), 0, 0, true);

    /** What a game's module hears. */
    private static final class Heard extends Module implements UpgradeListener {
        final List<String> upgrades = new ArrayList<>();

        Heard(GameObject owner) {
            super(owner);
        }

        @Override
        public void onUpgrade(String name) {
            upgrades.add(name);
        }
    }

    private ProductionTest.TestLogic logic;
    private int red;
    private int blue;
    private ThingTemplate soldier;
    private GameObject lab;
    private GameObject otherLab;
    private final List<WorldEvent> events = new ArrayList<>();

    @BeforeEach
    void setUp() {
        var factory = new ThingFactory(RtsModules.withDefaults());
        soldier = RtsTemplate.named("Soldier").module(new ActiveBody.Data(50f)).buildCost(100).buildTimeFrames(3)
                .build();
        factory.addTemplate(soldier);
        factory.addTemplate(RtsTemplate.named("Lab").geometry(new Geometry.Box(8f, 8f, 10f))
                .module(new ActiveBody.Data(500f))
                .module(new ProductionUpdate.Data(List.of("Soldier"),
                        List.of("Upgrade_Armour", "Upgrade_Missiles", "Upgrade_Roof")))
                .build());
        factory.addTemplate(RtsTemplate.named("Humvee").geometry(new Geometry.Cylinder(3f, 4f))
                .module(new ActiveBody.Data(200f))
                .module(WeaponUpdate.Data.sets(List.of(
                        new WeaponSet(List.of(), List.of(new WeaponSlot("Gun"))),
                        new WeaponSet(List.of("Upgrade_Missiles"), List.of(new WeaponSlot("Missile"))))))
                .build());
        factory.addTemplate(RtsTemplate.named("Target").geometry(new Geometry.Cylinder(3f, 4f))
                .module(new ActiveBody.Data(1_000_000f)).build());
        logic = new ProductionTest.TestLogic(factory);
        logic.init();
        logic.addUpgrades(List.of(ARMOUR, MISSILES, ROOF));
        logic.addWeapons(List.of(GUN, MISSILE));
        var r = logic.getPlayerList().addPlayer("Red");
        var b = logic.getPlayerList().addPlayer("Blue");
        r.setRelationshipTo(b, Relationship.ENEMIES);
        b.setRelationshipTo(r, Relationship.ENEMIES);
        red = r.getIndex();
        blue = b.getIndex();
        logic.getRtsPlayer(red).deposit(1000);
        lab = logic.spawn(factory.findTemplate("Lab"), new Coord3D(0f, 0f, 0f), red);
        otherLab = logic.spawn(factory.findTemplate("Lab"), new Coord3D(300f, 0f, 0f), red);
    }

    private static ProductionUpdate line(GameObject factory) {
        return factory.findModule(ProductionUpdate.class);
    }

    private void run(int frames) {
        for (int frame = 0; frame < frames; frame++) {
            logic.update();
            events.addAll(logic.drainEvents());
        }
    }

    private int money() {
        return logic.getRtsPlayer(red).getMoney();
    }

    @Test
    void threeSecondsOfResearchIsDoneAtFrameNinetyChargedWhenQueuedRefundedWhenCalledOff() {
        assertTrue(line(lab).queueResearch(ARMOUR));
        assertEquals(600, money(), "charged the moment it is queued");

        run(89);
        assertFalse(logic.hasUpgrade(red, "Upgrade_Armour"), "not at frame 89");
        assertEquals(89f / 90f, line(lab).getEntries().getFirst().progress(), 1e-5f, "and nearly there");
        run(1);
        assertTrue(logic.hasUpgrade(red, "Upgrade_Armour"), "done at frame 90");
        assertEquals(1.2f, logic.getRtsPlayer(red).getBonus("Armour"), 1e-6f, "with its effects");
        var done = events.stream().filter(UpgradeCompleted.class::isInstance).map(UpgradeCompleted.class::cast)
                .findFirst().orElseThrow();
        assertEquals("Upgrade_Armour", done.upgrade());
        assertTrue(done.sideWide());
        assertEquals(lab.getId(), done.researcher());

        assertTrue(line(otherLab).queueResearch(MISSILES));
        assertEquals(400, money());
        assertTrue(line(otherLab).cancel(0));
        assertEquals(600, money(), "called off: every coin back");
        assertTrue(line(otherLab).getEntries().isEmpty());
    }

    @Test
    void aSideWideUpgradeReachesAUnitBuiltAfterward() {
        line(lab).queueResearch(MISSILES);
        run(30);
        line(lab).queue(soldier);
        var heard = new ArrayList<GameObject>();
        logic.onProduced((factory, unit) -> heard.add(unit));

        run(3);

        var made = heard.getFirst();
        assertTrue(made.hasCondition("Upgrade_Missiles"), "made with the side's upgrade");
    }

    @Test
    void aWeaponSetChosenByTheUpgradesWordSwitchesWhenItIsDone() {
        var humvee = logic.spawn(logic.getThingFactory().findTemplate("Humvee"), new Coord3D(0f, 50f, 0f), red);
        var listener = new Heard(humvee);
        humvee.addModule(listener);
        logic.spawn(logic.getThingFactory().findTemplate("Target"), new Coord3D(40f, 50f, 0f), blue);
        line(lab).queueResearch(MISSILES);

        run(29);
        assertEquals("Gun", lastFired());
        run(3);

        assertEquals("Missile", lastFired(), "the set named for the upgrade, once it is done");
        assertEquals(List.of("Upgrade_Missiles"), listener.upgrades, "and the Humvee's own module was told");
    }

    @Test
    void theSideQueuesAnUpgradeOfTheSidesOnceAndAnObjectsOwnOncePerBuilding() {
        assertTrue(line(lab).queueResearch(ARMOUR));
        assertTrue(logic.isUpgradeQueued(red, "Upgrade_Armour"));
        assertFalse(line(otherLab).queueResearch(ARMOUR), "queued at one building is queued for the side");
        assertEquals(600, money(), "and the refusal cost nothing");
        run(90);
        assertFalse(line(otherLab).queueResearch(ARMOUR), "and once done, never again");

        assertTrue(line(lab).queueResearch(ROOF));
        line(otherLab).queue(soldier);
        assertTrue(line(otherLab).queueResearch(ROOF), "a building's own upgrade: each building its own");
        run(10);
        assertTrue(lab.hasCondition("Upgrade_Roof"));
        assertFalse(otherLab.hasCondition("Upgrade_Roof"), "the other built a soldier first");
        assertFalse(logic.getObjects().stream().filter(one -> one != lab).anyMatch(one -> one.hasCondition(
                "Upgrade_Roof")), "and nothing but the building that researched it has its own upgrade");
        assertFalse(line(lab).queueResearch(ROOF), "done on this one");
    }

    @Test
    void cancellingAUnitRefundsItAndTheNextStartsFromNothing() {
        line(lab).queue(soldier);
        line(lab).queueResearch(ROOF);
        run(2);
        assertEquals(1000 - 100 - 100, money());

        assertTrue(line(lab).cancel(0));

        assertEquals(900, money(), "the soldier's hundred back");
        assertEquals(0f, line(lab).getEntries().getFirst().progress(), 0f, "the roof starts from nothing");
        assertFalse(line(lab).cancel(5), "nothing there");
    }

    private String lastFired() {
        var fired = events.stream().filter(WeaponFired.class::isInstance).map(WeaponFired.class::cast).toList();
        return fired.isEmpty() ? null : fired.getLast().weapon();
    }
}
