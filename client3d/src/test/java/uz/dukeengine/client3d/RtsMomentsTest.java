package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.Vector3f;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.event.ObjectDied;
import uz.dukeengine.core.event.WorldEvent;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.DeathType;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.game.view.UnitView;
import uz.dukeengine.game.view.WorldSnapshot;
import uz.dukeengine.rts.event.WeaponFired;

/**
 * What an RTS makes a sound for: the moments raised for a real-time strategy game's units, and the two things
 * a cue may now say about itself — who hears it, and whether it cuts off the last of itself.
 *
 * <p>No game's words: the templates, weapons and hurt words here are this test's own.
 */
class RtsMomentsTest {

    private static final int ME = 1;
    private static final int THEM = 2;

    /** A sink that writes down everything asked of it, loops and stops included. */
    private static final class Heard implements SoundSink {
        final List<String> played = new ArrayList<>();
        final List<String> looping = new ArrayList<>();
        final List<String> stopped = new ArrayList<>();

        @Override
        public void play(String assetPath, float gain, Vector3f at) {
            played.add(assetPath);
        }

        @Override
        public Playing playStoppable(String assetPath, float gain, Vector3f at) {
            played.add(assetPath);
            return () -> stopped.add(assetPath);
        }

        @Override
        public Playing loop(String assetPath, float gain, Vector3f at) {
            looping.add(assetPath);
            return () -> stopped.add(assetPath);
        }

        @Override
        public void music(String assetPath, float gain) {
        }

        @Override
        public void musicGain(float gain) {
        }
    }

    private static SoundBank.Builder effect(SoundBank.Builder bank, String name, String... files) {
        return bank.cue(name, SoundBank.Channel.EFFECTS, true, 1f, 0f, List.of(files));
    }

    private static UnitView unit(int id, String template, int player, float health, boolean moving) {
        return new UnitView(id, template, player, 10f * id, 0f, 0f, health, 100f, false, true, moving, false, -1);
    }

    private static WorldSnapshot frame(List<UnitView> units, WorldEvent... events) {
        return new WorldSnapshot(0, 0f, false, 0, 0, units, List.of(events), "", "", List.of(), true);
    }

    private static GameSounds noises(SoundBank bank, Heard heard) {
        return new GameSounds(new Sounds(bank, heard), 5f,
                template -> Map.of("DAMAGED", 0.7f, "REALLY_DAMAGED", 0.35f));
    }

    /** A voice with five takes: selecting plays one of the five. */
    @Test
    void selectingPlaysOneOfItsTakes() {
        var takes = List.of("sel1.ogg", "sel2.ogg", "sel3.ogg", "sel4.ogg", "sel5.ogg");
        var bank = SoundBank.create()
                .cue("selected.Humvee", SoundBank.Channel.VOICE, false, 1f, 0f, takes, null,
                        SoundBank.Audience.OWNER, true)
                .build();
        var heard = new Heard();
        var noises = noises(bank, heard);

        noises.selected(unit(1, "Humvee", ME, 100f, false), ME, 0f);

        assertEquals(1, heard.played.size());
        assertTrue(takes.contains(heard.played.getFirst()), heard.played.toString());
    }

    /** A voice kept for its owner: an enemy picked out to be looked at says nothing. */
    @Test
    void anEnemysSelectedLineIsNeverHeard() {
        var bank = SoundBank.create()
                .cue("selected", SoundBank.Channel.VOICE, false, 1f, 0f, List.of("sir.ogg"), null,
                        SoundBank.Audience.OWNER, false)
                .build();
        var heard = new Heard();
        var noises = noises(bank, heard);

        noises.selected(unit(7, "Tank", THEM, 100f, false), ME, 0f);
        assertTrue(heard.played.isEmpty());

        noises.selected(unit(1, "Tank", ME, 100f, false), ME, 1f);
        assertEquals(List.of("sir.ogg"), heard.played, "his own is heard");
    }

    /** A loop follows its thing while it is there and stops when it dies. */
    @Test
    void anAmbientLoopStopsWhenItsThingDies() {
        var heard = new Heard();
        var noises = noises(effect(SoundBank.create(), "ambient.Reactor", "hum.ogg").build(), heard);
        var reactor = unit(3, "Reactor", ME, 100f, false);

        noises.frame(frame(List.of(reactor)), ME, 0f);
        noises.frame(frame(List.of(reactor)), ME, 0.1f);
        assertEquals(List.of("hum.ogg"), heard.looping, "started once, not once a frame");

        noises.frame(frame(List.of(), new ObjectDied(2, new ObjectId(3), "Reactor", ME, new Coord3D(30f, 0f, 0f),
                null, null, 0f)),
                ME, 0.2f);
        assertEquals(List.of("hum.ogg"), heard.stopped);
    }

    /** Swapped for the damaged loop where a hurt word comes to hold, and falling back where none was written. */
    @Test
    void anAmbientLoopTakesTheDamagedVariant() {
        var heard = new Heard();
        var bank = effect(effect(SoundBank.create(), "ambient.Reactor", "hum.ogg"),
                "ambient.Reactor.really_damaged", "crackle.ogg").build();
        var noises = noises(bank, heard);

        noises.frame(frame(List.of(unit(3, "Reactor", ME, 100f, false))), ME, 0f);
        noises.frame(frame(List.of(unit(3, "Reactor", ME, 60f, false))), ME, 0.1f);
        assertEquals(List.of("hum.ogg"), heard.looping, "damaged has no loop of its own: the plain one goes on");

        noises.frame(frame(List.of(unit(3, "Reactor", ME, 30f, false))), ME, 0.2f);
        assertEquals(List.of("hum.ogg", "crackle.ogg"), heard.looping);
        assertEquals(List.of("hum.ogg"), heard.stopped, "the plain one stopped for it");
    }

    /** Falling under 70% sounds damaged once — not again as it falls further, nor on the frame it is first seen. */
    @Test
    void fallingUnderSeventyPercentSoundsDamagedOnce() {
        var heard = new Heard();
        var bank = effect(effect(SoundBank.create(), "damaged.Tank", "clang.ogg"),
                "really_damaged.Tank", "smoke.ogg").build();
        var noises = noises(bank, heard);

        noises.frame(frame(List.of(unit(4, "Tank", ME, 100f, false))), ME, 0f);
        noises.frame(frame(List.of(unit(4, "Tank", ME, 69f, false))), ME, 0.1f);
        noises.frame(frame(List.of(unit(4, "Tank", ME, 50f, false))), ME, 0.2f);
        assertEquals(List.of("clang.ogg"), heard.played);

        noises.frame(frame(List.of(unit(4, "Tank", ME, 30f, false))), ME, 0.3f);
        assertEquals(List.of("clang.ogg", "smoke.ogg"), heard.played);

        noises.frame(frame(List.of(unit(9, "Tank", ME, 20f, false))), ME, 0.4f);
        assertEquals(2, heard.played.size(), "a thing first seen already hurt sounds nothing");
    }

    /** Named after the weapon that fired, so a unit with two weapons sounds two ways. */
    @Test
    void aShotSoundsAsItsWeapon() {
        var heard = new Heard();
        var bank = effect(effect(effect(SoundBank.create(), "fired.Gun", "rattle.ogg"),
                "fired.Missile", "whoosh.ogg"), "fired", "plain.ogg").build();
        var noises = noises(bank, heard);
        var shooter = unit(1, "Humvee", ME, 100f, false);

        noises.frame(frame(List.of(shooter), shot("Gun"), shot("Missile"), shot(null)), ME, 0f);

        assertEquals(List.of("rattle.ogg", "whoosh.ogg", "plain.ogg"), heard.played);
    }

    /** A game written before shots had names still hears its bow, until its file says fired. */
    @Test
    void aWeaponWithNoNameStillSoundsAsItDidInAGameThatNamesNoShot() {
        var heard = new Heard();
        var noises = noises(effect(SoundBank.create(), "arrow_fired", "bow.ogg").build(), heard);

        noises.frame(frame(List.of(unit(1, "Hero", ME, 100f, false)), shot(null)), ME, 0f);

        assertEquals(List.of("bow.ogg"), heard.played);
    }

    private static WeaponFired shot(String weapon) {
        return new WeaponFired(1, new ObjectId(1), new ObjectId(2), new Coord3D(10f, 0f, 0f),
                new Coord3D(20f, 0f, 0f), weapon);
    }

    /** Starting to move is a moment; being on the move is not another one each frame. */
    @Test
    void startingToMoveSoundsOnce() {
        var heard = new Heard();
        var noises = noises(effect(SoundBank.create(), "moving.Tank", "engine.ogg").build(), heard);

        noises.frame(frame(List.of(unit(4, "Tank", ME, 100f, false))), ME, 0f);
        noises.frame(frame(List.of(unit(4, "Tank", ME, 100f, true))), ME, 0.1f);
        noises.frame(frame(List.of(unit(4, "Tank", ME, 100f, true))), ME, 0.2f);

        assertEquals(List.of("engine.ogg"), heard.played);
    }

    /** An order, for the first unit given it: ordered.<order>.<template>. */
    @Test
    void anOrderIsAnsweredByTheFirstUnitGivenIt() {
        var heard = new Heard();
        var bank = effect(effect(SoundBank.create(), "ordered.move.Humvee", "moving_out.ogg"),
                "ordered.attack", "engaging.ogg").build();
        var noises = noises(bank, heard);
        var humvee = unit(1, "Humvee", ME, 100f, false);

        noises.ordered("move", humvee, ME, 0f);
        noises.ordered("attack", humvee, ME, 1f);

        assertEquals(List.of("moving_out.ogg", "engaging.ogg"), heard.played, "falling back like every name");
    }

    /** A cue that interrupts cuts off the last of itself; one that does not lets the two overlap. */
    @Test
    void aCueThatInterruptsStopsTheLastOfItself() {
        var heard = new Heard();
        var bank = SoundBank.create()
                .cue("ordered.move", SoundBank.Channel.VOICE, false, 1f, 0f, List.of("yes.ogg"), null,
                        SoundBank.Audience.EVERYONE, true)
                .build();
        var noises = noises(bank, heard);
        var humvee = unit(1, "Humvee", ME, 100f, false);

        noises.ordered("move", humvee, ME, 0f);
        assertTrue(heard.stopped.isEmpty());
        noises.ordered("move", humvee, ME, 5f);
        assertEquals(List.of("yes.ogg"), heard.stopped, "the first answer cut off by the second");
        assertEquals(2, heard.played.size());
    }

    /** A death sounds by how it came: the shell's own cry where the game wrote one, the plain one where not. */
    @Test
    void aDeathPlaysTheCueForItsTypeAndFallsBackToThePlainOne() {
        var heard = new Heard();
        var bank = effect(effect(SoundBank.create(), "died.Soldier", "fall.ogg"),
                "died.Soldier.exploded", "thrown.ogg").build();
        var noises = noises(bank, heard);

        noises.frame(frame(List.of(), died(4, DeathType.of("EXPLODED"))), ME, 0f);
        noises.frame(frame(List.of(), died(5, DeathType.NORMAL)), ME, 1f);

        assertEquals(List.of("thrown.ogg", "fall.ogg"), heard.played);
    }

    /** The look falls back the same way, and the corpse falls over by the clip for its death. */
    @Test
    void aDeathsLookAndClipFallBackToThePlainOnes() {
        var visuals = Visuals.create()
                .moment("died.Soldier", "blood", 1f)
                .moment("died.Soldier.exploded", "gibs", 1f)
                .unit("Soldier", look -> look.die("Die").die("exploded", "Die_Thrown"));

        assertEquals("gibs", visuals.getMoment("died.Soldier.exploded").effect());
        assertEquals("blood", visuals.getMoment("died.Soldier.burned").effect());
        assertEquals(null, visuals.getMoment("died.Tank.exploded"), "nothing was given to a tank");
        assertEquals("Die_Thrown", visuals.of("Soldier").dieAnimFor(DeathType.of("EXPLODED")));
        assertEquals("Die", visuals.of("Soldier").dieAnimFor(DeathType.of("BURNED")));
    }

    private static ObjectDied died(int id, DeathType how) {
        return new ObjectDied(2, new ObjectId(id), "Soldier", THEM, new Coord3D(30f, 0f, 0f), how, new ObjectId(1),
                0f);
    }
}
