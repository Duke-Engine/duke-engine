package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;
import uz.dukeengine.rts.event.WeaponFired;

/**
 * A weapon's clip, in frames of 30 a second: 0.1 s is 3 frames, 1.0 s is 30, 2.0 s is 60.
 *
 * <p>Every test watches the shots themselves — the frames {@link WeaponFired} was posted on — against a target
 * too sturdy to die, so what is measured is the weapon's rhythm and nothing else.
 */
class WeaponClipTest {

    private record Shooting(CombatTest.CombatLogic logic, GameObject shooter, List<Integer> shots) {

        void run(int frames) {
            for (int frame = 0; frame < frames; frame++) {
                logic.update();
                for (var event : logic.drainEvents()) {
                    if (event instanceof WeaponFired fired) {
                        shots.add(fired.frame());
                    }
                }
            }
        }

        /** The shots, counted from the first. */
        List<Integer> fromTheFirst() {
            int first = shots.getFirst();
            return shots.stream().map(frame -> frame - first).toList();
        }

        WeaponUpdate weapon() {
            return shooter.findModule(WeaponUpdate.class);
        }
    }

    private static Shooting shooting(WeaponUpdate.Data weapon) {
        return shooting(weapon, uz.dukeengine.core.GameLogic.DEFAULT_RANDOM_SEED);
    }

    private static Shooting shooting(WeaponUpdate.Data weapon, long seed) {
        var factory = new ThingFactory(RtsModules.withDefaults());
        var gunner = ThingTemplate.named("Gunner").module(new ActiveBody.Data(100f)).module(weapon).build();
        var wall = ThingTemplate.named("Wall").module(new ActiveBody.Data(1_000_000f)).build();
        factory.addTemplate(gunner);
        factory.addTemplate(wall);
        var logic = new CombatTest.CombatLogic(factory);
        logic.init();
        logic.setRandomSeed(seed);
        var red = logic.getPlayerList().addPlayer("Red");
        var blue = logic.getPlayerList().addPlayer("Blue");
        red.setRelationshipTo(blue, Relationship.ENEMIES);
        blue.setRelationshipTo(red, Relationship.ENEMIES);
        var shooter = logic.createObject(gunner);
        shooter.setPlayerIndex(red.getIndex());
        shooter.setPosition(new Coord3D(0f, 0f, 0f));
        var target = logic.createObject(wall);
        target.setPlayerIndex(blue.getIndex());
        target.setPosition(new Coord3D(5f, 0f, 0f));
        return new Shooting(logic, shooter, new ArrayList<>());
    }

    private static WeaponUpdate.Data clip(int size, int delay, int delayMax, int reload, boolean reloadsItself) {
        return new WeaponUpdate.Data(1f, 20f, delay, DamageType.NORMAL, 0f, true, List.of(),
                delayMax, size, reload, reloadsItself);
    }

    /** One round, a 2-second reload, a 0.1-second delay: once every 2 seconds, and the delay never waited. */
    @Test
    void aClipOfOneFiresOnceAReload() {
        var shooting = shooting(clip(1, 3, 0, 60, true));
        shooting.run(200);

        assertEquals(List.of(0, 60, 120, 180), shooting.fromTheFirst());
    }

    /** Three rounds at 0.1 s, then a 1.0 s reload from the third: 0, 0.1, 0.2, then 1.2, 1.3, 1.4. */
    @Test
    void aClipOfThreeFiresABurstThenReloads() {
        var shooting = shooting(clip(3, 3, 0, 30, true));
        shooting.run(50);

        assertEquals(List.of(0, 3, 6, 36, 39, 42), shooting.fromTheFirst());
    }

    /** No clip: every delay, for as long as there is something to shoot — every weapon before clips. */
    @Test
    void noClipFiresEveryDelay() {
        var shooting = shooting(clip(0, 3, 0, 60, true));
        shooting.run(13);

        assertEquals(List.of(0, 3, 6, 9, 12), shooting.fromTheFirst());
        assertEquals(0, shooting.weapon().getRounds(), "nothing counted");
    }

    /** One that does not reload fires its clip and stops, OUT, until something refills it. */
    @Test
    void aWeaponThatDoesNotReloadStopsUntilRefilled() {
        var shooting = shooting(clip(2, 3, 0, 30, false));
        shooting.run(100);

        assertEquals(List.of(0, 3), shooting.fromTheFirst());
        assertEquals(WeaponStatus.OUT, shooting.weapon().getStatus());

        shooting.weapon().refill();
        assertEquals(WeaponStatus.READY, shooting.weapon().getStatus());
        assertEquals(2, shooting.weapon().getRounds());
        shooting.run(10);
        assertEquals(4, shooting.shots().size(), "a full clip again");
    }

    /** A Raptor sent off half way through its reload on its pad has half its clip, as the reference refills it. */
    @Test
    void aPartRefillFillsItToAtLeastItsShareAndMakesItReady() {
        var shooting = shooting(clip(4, 3, 0, 240, false));
        shooting.run(100);
        assertEquals(WeaponStatus.OUT, shooting.weapon().getStatus());

        shooting.weapon().refill(0.5f);
        assertEquals(2, shooting.weapon().getRounds(), "half of four");
        assertEquals(WeaponStatus.READY, shooting.weapon().getStatus());

        shooting.weapon().refill(0.25f);
        assertEquals(2, shooting.weapon().getRounds(), "a clip holding more keeps it");

        shooting.weapon().refill(1f);
        assertEquals(4, shooting.weapon().getRounds());
    }

    @Test
    void itSaysWhereItStands() {
        var shooting = shooting(clip(2, 3, 0, 30, true));
        var weapon = shooting.weapon();
        assertEquals(WeaponStatus.READY, weapon.getStatus(), "full and ready from the start");
        assertEquals(2, weapon.getRounds());

        shooting.run(1);
        assertEquals(WeaponStatus.BETWEEN_SHOTS, weapon.getStatus());
        assertEquals(1, weapon.getRounds());
        shooting.run(3);
        assertEquals(WeaponStatus.RELOADING, weapon.getStatus(), "the second shot emptied it");
        shooting.run(29);
        assertEquals(WeaponStatus.RELOADING, weapon.getStatus(), "a frame of the reload still to go");
        shooting.run(1);
        assertEquals(1, weapon.getRounds(), "reloaded, and the first round of the new clip fired at once");
    }

    /** Its clip read from outside, as {@code slotsNow} tells its slots: a clip of six fired twice reads four of six. */
    @Test
    void aSlotsClipIsReadFromOutside() {
        var six = shooting(clip(6, 3, 0, 30, true));
        six.run(4); // shots on the first frame and the fourth
        var slot = six.weapon().slotsNow(0).getFirst();
        assertEquals(0, slot.slot());
        assertEquals(6, slot.clipSize());
        assertEquals(4, slot.rounds(), "four of six");

        var none = shooting(clip(0, 3, 0, 60, true));
        none.run(4);
        var plain = none.weapon().slotsNow(0).getFirst();
        assertEquals(0, plain.clipSize(), "no clip: none held full");
        assertEquals(0, plain.rounds(), "and none counted");
    }

    /**
     * An emptied clip read as the reference holds it: one that reloads itself full again from the shot that emptied
     * it, its status saying the reload is under way, as the reference's pips read ({@code Weapon::reloadWithBonus}
     * fills the clip as the reload begins); one that does not reload empty, and unchanged until something refills it.
     */
    @Test
    void anEmptiedClipReadsAsTheReferenceHoldsIt() {
        var reloading = shooting(clip(2, 3, 0, 30, true));
        reloading.run(4); // both rounds
        var slot = reloading.weapon().slotsNow(0).getFirst();
        assertEquals(WeaponStatus.RELOADING, slot.status());
        assertEquals(2, slot.rounds(), "full from the shot that emptied it, the reload still to wait out");

        var out = shooting(clip(2, 3, 0, 30, false));
        out.run(4);
        assertEquals(WeaponStatus.OUT, out.weapon().slotsNow(0).getFirst().status());
        assertEquals(0, out.weapon().slotsNow(0).getFirst().rounds(), "empty");
        out.run(100);
        assertEquals(0, out.weapon().slotsNow(0).getFirst().rounds(), "unchanged until something refills it");
        out.weapon().refill();
        assertEquals(2, out.weapon().slotsNow(0).getFirst().rounds(), "and full once it does");
    }

    /**
     * A delay between 3 and 9 frames, drawn from the simulation's own numbers: the same seed, the same delays —
     * and the delays do differ, or this would prove nothing.
     */
    @Test
    void theSameSeedGivesTheSameDelays() {
        var once = shooting(clip(0, 3, 9, 0, true), 42L);
        var again = shooting(clip(0, 3, 9, 0, true), 42L);
        var other = shooting(clip(0, 3, 9, 0, true), 43L);
        once.run(300);
        again.run(300);
        other.run(300);

        assertEquals(once.shots(), again.shots());
        assertNotEquals(once.shots(), other.shots());
        var gaps = new java.util.TreeSet<Integer>();
        for (int i = 1; i < once.shots().size(); i++) {
            int gap = once.shots().get(i) - once.shots().get(i - 1);
            assertTrue(gap >= 3 && gap <= 9, "gap " + gap);
            gaps.add(gap);
        }
        assertTrue(gaps.size() > 3, "and they vary: " + gaps);
    }

    /** What a game adds to a unit: a horde, a rank — every wait divided by it, floored. */
    private static final class Frenzy extends Module implements RateOfFireModifier {
        Frenzy(GameObject owner) {
            super(owner);
        }

        @Override
        public float rateOfFireMultiplier() {
            return 2f;
        }
    }

    /** Twice the rate of fire: a 3-frame delay floors to 1, a 30-frame reload halves to 15. */
    @Test
    void aRateOfFireBonusShortensBothWaits() {
        var shooting = shooting(clip(3, 3, 0, 30, true));
        shooting.shooter().addModule(new Frenzy(shooting.shooter()));
        shooting.run(25);

        assertEquals(List.of(0, 1, 2, 17, 18, 19), shooting.fromTheFirst());
    }
}
