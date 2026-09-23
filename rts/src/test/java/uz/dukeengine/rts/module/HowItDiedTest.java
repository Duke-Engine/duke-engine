package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.event.ObjectDied;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.Death;
import uz.dukeengine.core.module.DeathType;
import uz.dukeengine.core.module.DieModule;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplateLoader;
import uz.dukeengine.rts.RtsTemplate;
import uz.dukeengine.rts.message.GameMessage;

/** A death knows how it came: the weapon's death type, or being run over, and whose it was. */
class HowItDiedTest {

    private static final DeathType EXPLODED = DeathType.of("EXPLODED");

    /** What a game's die module does: remember how it went. */
    private static final class Witness extends Module implements DieModule {
        private Death seen;

        Witness(GameObject owner) {
            super(owner);
        }

        @Override
        public void onDie(Death death) {
            seen = death;
        }
    }

    private CombatTest.CombatLogic logic;
    private int us;
    private int them;
    private final List<ObjectDied> died = new ArrayList<>();

    @BeforeEach
    void setUp() {
        var factory = new ThingFactory(RtsModules.withDefaults());
        RtsTemplate.register(new ThingTemplateLoader(factory)).load("""
                Object
                  Name = Mortar
                  Modules = [
                    ActiveBody
                      MaxHealth = 100
                    End,
                    WeaponUpdate
                      Damage = 500
                      AttackRange = 60
                      ReloadFrames = 30
                      DeathType = exploded
                    End
                  ]
                End
                Object
                  Name = Rifleman
                  Modules = [
                    ActiveBody
                      MaxHealth = 100
                    End,
                    WeaponUpdate
                      Damage = 500
                      AttackRange = 60
                      ReloadFrames = 30
                    End
                  ]
                End
                Object
                  Name = Soldier
                  Modules = [
                    ActiveBody
                      MaxHealth = 100
                    End,
                    Crushable
                      CrushableLevel = 0
                    End
                  ]
                End
                Object
                  Name = Car
                  Modules = [
                    ActiveBody
                      MaxHealth = 100
                    End,
                    Crushable
                      CrushableLevel = 1
                    End
                  ]
                End
                """, "how_it_died.duke");
        factory.addTemplate(RtsTemplate.named("Truck").geometry(new Geometry.Cylinder(5f, 4f))
                .module(new ActiveBody.Data(500f)).module(new MoveUpdate.Data(30f))
                .module(new CrushUpdate.Data(1)).build());
        logic = new CombatTest.CombatLogic(factory);
        logic.init();
        var red = logic.getPlayerList().addPlayer("Red");
        var blue = logic.getPlayerList().addPlayer("Blue");
        red.setRelationshipTo(blue, Relationship.ENEMIES);
        blue.setRelationshipTo(red, Relationship.ENEMIES);
        us = red.getIndex();
        them = blue.getIndex();
    }

    private GameObject spawn(String template, int player, float x, float y) {
        var thing = logic.createObject(logic.getThingFactory().findTemplate(template));
        thing.setPlayerIndex(player);
        thing.setPosition(new Coord3D(x, y, 0f));
        return thing;
    }

    private Witness watch(GameObject thing) {
        var witness = new Witness(thing);
        thing.addModule(witness);
        return witness;
    }

    private void run(int frames) {
        for (int frame = 0; frame < frames; frame++) {
            logic.update();
            logic.drainEvents().stream().filter(ObjectDied.class::isInstance).map(ObjectDied.class::cast)
                    .forEach(died::add);
        }
    }

    private void attack(GameObject attacker, GameObject victim) {
        logic.issueCommand(new GameMessage.AttackObject(us, List.of(attacker.getId()), victim.getId()));
        run(2);
    }

    @Test
    void aShellKillsWithTheDeathItsWeaponDeals() {
        var mortar = spawn("Mortar", us, 0f, 0f);
        var soldier = spawn("Soldier", them, 20f, 0f);
        var witness = watch(soldier);

        attack(mortar, soldier);

        assertSame(EXPLODED, witness.seen.type(), "written lower-case, read as the one word");
        assertEquals(mortar.getId(), witness.seen.killer());
        var death = died.getFirst();
        assertSame(EXPLODED, death.deathType());
        assertEquals(mortar.getId(), death.killer());
    }

    @Test
    void aWeaponThatSaysNothingKillsNormally() {
        var rifleman = spawn("Rifleman", us, 0f, 0f);
        var witness = watch(spawn("Soldier", them, 20f, 0f));

        attack(rifleman, witness.getOwner());

        assertSame(DeathType.NORMAL, witness.seen.type());
        assertEquals(rifleman.getId(), died.getFirst().killer());
    }

    @Test
    void aSoldierDrivenOverDiesCrushed() {
        var truck = spawn("Truck", us, 0f, 0f);
        var soldier = watch(spawn("Soldier", them, 30f, 0f));
        var car = spawn("Car", them, 30f, 1f);
        var friend = spawn("Soldier", us, 30f, -1f);
        var beside = spawn("Soldier", them, 30f, 12f);

        truck.findModule(MoveUpdate.class).moveTo(new Coord3D(60f, 0f, 0f));
        run(90);

        assertTrue(soldier.getOwner().isEffectivelyDead() || logic.findObject(soldier.getOwner().getId()) == null);
        assertSame(CrushUpdate.CRUSHED, soldier.seen.type());
        assertEquals(truck.getId(), soldier.seen.killer());
        assertEquals(List.of(soldier.getOwner().getId()), died.stream().map(ObjectDied::object).toList(),
                "the car is as heavy as the truck, the friend is a friend, and the other was not in the way");
        assertSame(CrushUpdate.CRUSHED, died.getFirst().deathType());
        assertFalse(car.isEffectivelyDead());
        assertFalse(friend.isEffectivelyDead());
        assertFalse(beside.isEffectivelyDead());
    }

    @Test
    void aTruckStandingStillCrushesNothing() {
        spawn("Truck", us, 0f, 0f);
        var underneath = spawn("Soldier", them, 1f, 0f);

        run(10);

        assertFalse(underneath.isEffectivelyDead());
        assertNull(underneath.getBody().getDeath());
    }
}
