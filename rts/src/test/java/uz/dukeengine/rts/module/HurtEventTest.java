package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.event.ObjectHurt;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.rts.RtsTemplate;
import uz.dukeengine.rts.message.GameMessage;
import uz.dukeengine.combat.module.WeaponUpdate;
import uz.dukeengine.combat.message.CombatOrder;

/** Every blow that takes health says so: what was struck, how hard after armour, by whom, and where on it. */
class HurtEventTest {

    private static final DamageType SMALL_ARMS = DamageType.of("SMALL_ARMS");

    private CombatTest.CombatLogic logic;
    private int us;
    private int them;
    private final List<ObjectHurt> hurt = new ArrayList<>();

    @BeforeEach
    void setUp() {
        var factory = new ThingFactory(RtsModules.withDefaults());
        factory.addTemplate(RtsTemplate.named("Rifleman").geometry(new Geometry.Cylinder(2f, 6f))
                .module(new ActiveBody.Data(100f))
                .module(new WeaponUpdate.Data(10f, 60f, 30, SMALL_ARMS)).build());
        factory.addTemplate(RtsTemplate.named("Mortar").geometry(new Geometry.Cylinder(2f, 6f))
                .module(new ActiveBody.Data(100f))
                .module(new WeaponUpdate.Data(10f, 100f, 30, DamageType.EXPLOSION, 20f)).build());
        factory.addTemplate(RtsTemplate.named("Tank").geometry(new Geometry.Box(6f, 4f, 4f))
                .module(new ActiveBody.Data(1000f, Map.of(SMALL_ARMS, 0.25f, DamageType.FLAME, 0f))).build());
        logic = new CombatTest.CombatLogic(factory);
        logic.init();
        var a = logic.getPlayerList().addPlayer("Us");
        var b = logic.getPlayerList().addPlayer("Them");
        a.setRelationshipTo(b, Relationship.ENEMIES);
        b.setRelationshipTo(a, Relationship.ENEMIES);
        us = a.getIndex();
        them = b.getIndex();
    }

    private GameObject spawn(String template, int player, float x, float y) {
        return logic.spawn(logic.getThingFactory().findTemplate(template), new Coord3D(x, y, 0f), player);
    }

    private void attack(GameObject attacker, GameObject victim) {
        logic.issueCommand(new CombatOrder.AttackObject(us, List.of(attacker.getId()), victim.getId()));
        logic.update();
        logic.drainEvents().stream().filter(ObjectHurt.class::isInstance).map(ObjectHurt.class::cast)
                .forEach(hurt::add);
    }

    @Test
    void aHitSaysHowHardAfterArmourByWhomAndThatItLandedHalfwayUp() {
        var rifleman = spawn("Rifleman", us, 0f, 0f);
        var tank = spawn("Tank", them, 30f, 0f);

        attack(rifleman, tank);

        var blow = hurt.getFirst();
        assertEquals(tank.getId(), blow.object());
        assertEquals("Tank", blow.templateName());
        assertEquals(SMALL_ARMS, blow.damageType());
        assertEquals(2.5f, blow.amount(), 1e-5f, "10, a quarter of it through the armour");
        assertEquals(rifleman.getId(), blow.attacker());
        assertEquals(new Coord3D(30f, 0f, 2f), blow.where(), "its middle: halfway up a thing 4 tall");
        assertEquals(rifleman.getPosition(), blow.from(), "and it came from the rifleman");
    }

    @Test
    void aBlastStrikesEachBystanderAtItsPointNearestTheBlast() {
        var mortar = spawn("Mortar", us, 0f, 0f);
        var target = spawn("Tank", them, 50f, 0f);
        var beside = spawn("Tank", them, 50f, 16f);

        attack(mortar, target);

        var onBeside = hurt.stream().filter(blow -> blow.object().equals(beside.getId())).findFirst().orElseThrow();
        assertEquals(50f, onBeside.where().x(), 1e-4f);
        assertEquals(12f, onBeside.where().y(), 1e-4f, "its near side, 4 in from its middle towards the blast");
        assertEquals(target.getPosition(), onBeside.from(), "the blast is where the blow came from");
    }

    @Test
    void aBlowThatTakesNothingSaysNothing() {
        var tank = spawn("Tank", them, 0f, 0f);

        tank.getBody().damage(50f, DamageType.FLAME, null);
        tank.getBody().damage(-10f);
        tank.getBody().heal(5f);

        assertTrue(logic.drainEvents().stream().noneMatch(ObjectHurt.class::isInstance),
                "immune to fire, a negative blow, a heal: none of them is a blow that hurt");
    }
}
