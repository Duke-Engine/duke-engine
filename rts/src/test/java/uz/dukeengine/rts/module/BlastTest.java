package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.DeathType;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.rts.RtsSimulation;
import uz.dukeengine.rts.RtsTemplate;
import uz.dukeengine.rts.message.GameMessage;

/**
 * Whom a blast hurts, and its second ring — the reference's {@code RadiusDamageAffects} and secondary damage, as
 * {@code Weapon::dealDamageInternal} deals them.
 */
class BlastTest {

    private static final class World extends RtsSimulation {
        World(ThingFactory factory) {
            super(factory);
        }

        @Override
        protected void onRtsCommand(GameMessage command) {
        }

        @Override
        protected void simulate() {
        }
    }

    private static final Coord3D BLAST = new Coord3D(100f, 100f, 0f);

    private World world;
    private int mine;
    private int ally;
    private int foe;
    private GameObject firer;

    @BeforeEach
    void setUp() {
        var factory = new ThingFactory(RtsModules.withDefaults());
        factory.addTemplate(RtsTemplate.named("Soldier").module(new ActiveBody.Data(1000f)).build());
        factory.addTemplate(RtsTemplate.named("Tank").module(new ActiveBody.Data(1000f)).build());
        world = new World(factory);
        world.init();
        var players = world.getPlayerList();
        mine = players.addPlayer("Mine").getIndex();
        ally = players.addPlayer("Ally").getIndex();
        foe = players.addPlayer("Foe").getIndex();
        side(mine, ally, Relationship.ALLIES);
        side(mine, foe, Relationship.ENEMIES);
        side(ally, foe, Relationship.ENEMIES);
        firer = put("Soldier", mine, 100f, 99f);
    }

    private void side(int a, int b, Relationship relationship) {
        var players = world.getPlayerList();
        players.getPlayer(a).setRelationshipTo(players.getPlayer(b), relationship);
        players.getPlayer(b).setRelationshipTo(players.getPlayer(a), relationship);
    }

    private GameObject put(String template, int side, float x, float y) {
        return world.spawn(world.getThingFactory().findTemplate(template), new Coord3D(x, y, 0f), side);
    }

    private static Weapon bomb(List<Weapon.Affects> affects, float damage, float radius, float secondDamage,
            float secondRadius) {
        return new Weapon("Bomb", damage, 50f, 30, 30, DamageType.EXPLOSION, radius, true, List.of(), 0, 0, true,
                DeathType.NORMAL, List.of(), affects, secondDamage, secondRadius);
    }

    /** The blast of {@code weapon} lands at the blast point, with no direct hit: what each thing took. */
    private void blast(Weapon weapon) {
        WeaponUpdate.land(world, new Shot(firer.getId(), mine, weapon, 0, weapon.damage()), null, BLAST, null);
    }

    private static float took(GameObject thing) {
        return 1000f - thing.getBody().getHealth();
    }

    @Test
    void aBlastThatHurtsAlliesHurtsAnAllyAndOneThatDoesNotSparesHim() {
        var friend = put("Tank", ally, 101f, 100f);
        var enemy = put("Tank", foe, 99f, 100f);

        blast(bomb(List.of(Weapon.Affects.ALLIES, Weapon.Affects.ENEMIES), 300f, 50f, 0f, 0f));
        assertEquals(300f, took(friend), "a carpet bomb: everything within 50, allies included");
        assertEquals(300f, took(enemy));

        blast(bomb(List.of(Weapon.Affects.ENEMIES), 300f, 50f, 0f, 0f));
        assertEquals(300f, took(friend), "enemies only: the ally is spared");
        assertEquals(600f, took(enemy));

        blast(bomb(List.of(), 100f, 50f, 0f, 0f));
        assertEquals(300f, took(friend), "a blast naming none hurts its enemies alone, as every blast did");
        assertEquals(0f, took(firer), "and never the firer");
    }

    @Test
    void notSimilarSparesACopyOfTheFirerWhateverItsSide() {
        var copy = put("Soldier", foe, 101f, 100f);
        var tank = put("Tank", foe, 99f, 100f);

        blast(bomb(List.of(Weapon.Affects.ENEMIES, Weapon.Affects.NOT_SIMILAR), 100f, 50f, 0f, 0f));

        assertEquals(0f, took(copy), "a soldier's blast spares soldiers");
        assertEquals(100f, took(tank));
    }

    @Test
    void theFirerOnlyWhenItSaysSelfAndNothingInTheAirWhenItSaysNotAirborne() {
        var plane = put("Tank", foe, 101f, 100f);
        plane.setStatus(ObjectStatus.AIRBORNE);

        blast(bomb(List.of(Weapon.Affects.ENEMIES, Weapon.Affects.NOT_AIRBORNE), 100f, 50f, 0f, 0f));
        assertEquals(0f, took(plane), "not in the air");
        assertEquals(0f, took(firer));

        blast(bomb(List.of(Weapon.Affects.ENEMIES, Weapon.Affects.SELF), 100f, 50f, 0f, 0f));
        assertEquals(100f, took(plane));
        assertEquals(100f, took(firer), "a blast that says so hurts its firer");
    }

    @Test
    void whomItHurtsAndItsSecondRingAreReadFromItsBlock() {
        var block = uz.dukeengine.core.data.DukeText.parse("""
                Weapon
                  Name = ClusterMine
                  Damage = 50
                  SplashRadius = 3
                  Affects = [ENEMIES, NEUTRALS, NOT_AIRBORNE]
                  SecondaryDamage = 100
                  SecondaryRadius = 5
                End
                """, "weapons.duke").getFirst();

        var mine = new uz.dukeengine.core.data.Binder().bind(block, Weapon.class);

        assertEquals(List.of(Weapon.Affects.ENEMIES, Weapon.Affects.NEUTRALS, Weapon.Affects.NOT_AIRBORNE),
                mine.affects());
        assertEquals(100f, mine.secondaryDamage());
        assertEquals(5f, mine.secondaryRadius());
    }

    @Test
    void aClusterMineDealsItsFirstDamageWithinTheFirstRingAndItsSecondBeyondIt() {
        var near = put("Tank", foe, 102f, 100f);
        var far = put("Tank", foe, 100f, 104f);
        var beyond = put("Tank", foe, 106f, 100f);

        blast(bomb(List.of(Weapon.Affects.ENEMIES), 50f, 3f, 100f, 5f));

        assertEquals(50f, took(near), "50 at 2, within 3");
        assertEquals(100f, took(far), "100 at 4, beyond 3 and within 5");
        assertEquals(0f, took(beyond), "nothing at 6");
    }
}
