package uz.dukeengine.core.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.event.ObjectHurt;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.ObjectTemplate;
import uz.dukeengine.core.thing.ThingFactory;

/**
 * What a body tells and how it takes a blow: each blow and heal told to the thing's modules, a body that sees the
 * whole blow before taking it, and a body's damage scale.
 */
class BodyHooksTest {

    /** Writes down what it is told. */
    static final class Ears extends Module implements DamageListener {
        record Data() implements ModuleData {
        }

        final List<String> heard = new ArrayList<>();

        Ears(GameObject owner) {
            super(owner);
        }

        @Override
        public void onDamage(DamageType type, float amount, ObjectId attacker, int frame) {
            heard.add(type.name() + " " + amount + " by " + (attacker == null ? "-" : attacker.value()) + " @" + frame);
        }

        @Override
        public void onHealing(float amount) {
            heard.add("healed " + amount);
        }
    }

    /** A hive's body: every blow passed whole to another thing's, or, with nothing to pass it to, swallowed. */
    static final class Hive extends BodyModule {
        record Data() implements ModuleData {
        }

        GameObject spawn;
        private float health = 500f;

        Hive(GameObject owner) {
            super(owner);
        }

        @Override
        public void damage(float amount, DamageType type, Death blow, Coord3D at, Coord3D from) {
            if (spawn != null) {
                spawn.getBody().damage(amount, type, blow, at, from);
            }
        }

        @Override
        public float getHealth() {
            return health;
        }

        @Override
        public float getMaxHealth() {
            return 500f;
        }

        @Override
        public void setHealth(float health) {
            this.health = health;
        }

        @Override
        public void damage(float amount, DamageType type) {
            health -= amount;
        }

        @Override
        public void heal(float amount) {
            health += amount;
        }
    }

    private static final DamageType UNRESISTABLE = DamageType.of("UNRESISTABLE");

    private GameLogic world;
    private GameObject soldier;
    private GameObject shooter;

    @BeforeEach
    void setUp() {
        var factory = new ThingFactory(ModuleFactory.withDefaults()
                .register(Ears.Data.class, (owner, data) -> new Ears(owner))
                .register(Hive.Data.class, (owner, data) -> new Hive(owner)));
        factory.addTemplate(ObjectTemplate.named("Soldier")
                .module(new ActiveBody.Data(100f, Map.of(DamageType.FLAME, 0f))).module(new Ears.Data()).build());
        factory.addTemplate(ObjectTemplate.named("Stinger").module(new Hive.Data()).module(new Ears.Data()).build());
        world = new GameLogic(factory) {
            @Override
            protected void simulate() {
            }
        };
        world.init();
        world.setUnresistableDamage(UNRESISTABLE);
        soldier = world.spawn(factory.findTemplate("Soldier"), new Coord3D(10f, 10f, 0f), 1);
        shooter = world.spawn(factory.findTemplate("Soldier"), new Coord3D(50f, 10f, 0f), 2);
    }

    private Death byShooter() {
        return new Death(DeathType.NORMAL, shooter.getId(), 2);
    }

    @Test
    void eachBlowThatTakesHealthIsToldAtOnceAndEachHealToo() {
        var body = soldier.getBody();
        body.damage(10f, DamageType.NORMAL, byShooter());
        body.damage(15f, DamageType.NORMAL, byShooter());
        body.damage(40f, DamageType.FLAME, byShooter()); // none of it after armour
        body.heal(5f);

        assertEquals(List.of("NORMAL 10.0 by 2 @0", "NORMAL 15.0 by 2 @0", "healed 5.0"),
                soldier.findModule(Ears.class).heard, "two blows in one frame, no word of the harmless one, a heal");
    }

    @Test
    void aThingAlreadyDeadIsToldNothing() {
        var body = soldier.getBody();
        body.damage(100f, DamageType.NORMAL, byShooter());
        var ears = soldier.findModule(Ears.class);
        ears.heard.clear();

        body.damage(10f, DamageType.NORMAL, byShooter());
        body.heal(10f);

        assertEquals(List.of(), ears.heard);
    }

    @Test
    void aHivePassesTheWholeBlowOnAndAKillNamesItsKillerAndDeath() {
        var hive = world.spawn(world.getThingFactory().findTemplate("Stinger"), new Coord3D(0f, 0f, 0f), 1);
        ((Hive) hive.getBody()).spawn = soldier;
        world.drainEvents();
        var blow = new Death(DeathType.of("BURNED"), shooter.getId(), 2);

        hive.getBody().damage(100f, DamageType.NORMAL, blow);

        assertEquals(500f, hive.getBody().getHealth(), "the hive took none of it");
        assertEquals(List.of(), hive.findModule(Ears.class).heard, "and told none of it");
        var hurt = world.drainEvents().stream().filter(ObjectHurt.class::isInstance).map(ObjectHurt.class::cast)
                .toList();
        assertEquals(1, hurt.size());
        assertEquals(soldier.getId(), hurt.getFirst().object(), "the soldier was hurt");
        assertEquals(shooter.getId(), hurt.getFirst().attacker(), "by whoever hit the hive");
        assertEquals(blow, soldier.getBody().getDeath(), "and died of the blow the hive was dealt");
    }

    @Test
    void aHiveWithNoSpawnLeftSwallowsTheBlow() {
        var hive = world.spawn(world.getThingFactory().findTemplate("Stinger"), new Coord3D(0f, 0f, 0f), 1);
        world.drainEvents();

        hive.getBody().damage(100f, DamageType.SNIPER, byShooter());

        assertEquals(500f, hive.getBody().getHealth());
        assertEquals(List.of(), world.drainEvents(), "nothing happened, and nothing says it did");
    }

    @Test
    void aBodyAtNineTenthsTakesNinetyOfAHundredButAllOfUnresistableDamage() {
        var body = soldier.getBody();
        body.setDamageScale(0.9f);

        body.damage(100f * 0.5f, DamageType.NORMAL, byShooter());
        assertEquals(55f, body.getHealth(), 1e-4f, "50 taken as 45");
        assertEquals(50f, body.estimateDamage(50f, DamageType.NORMAL), "estimates are left as they were");

        body.damage(30f, UNRESISTABLE, byShooter());
        assertEquals(25f, body.getHealth(), 1e-4f, "unresistable damage taken in full");
        assertEquals(List.of("NORMAL 45.0 by 2 @0", "UNRESISTABLE 30.0 by 2 @0"),
                soldier.findModule(Ears.class).heard, "and told as taken");
    }

    @Test
    void aBodysScaleIsInTheChecksumAndOneSumsAsBefore() {
        long before = world.checksum();
        soldier.getBody().setDamageScale(0.9f);
        long scaled = world.checksum();
        soldier.getBody().setDamageScale(1f);

        assertNotEquals(before, scaled, "a scaled body sums differently");
        assertEquals(before, world.checksum(), "and one at 1 as it always did");
        assertTrue(before != 0L);
    }
}
