package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.event.SoundPlayed;
import uz.dukeengine.core.event.WorldEvent;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.game.view.UnitView;
import uz.dukeengine.game.view.WorldSnapshot;

/**
 * Where a sound is heard from, how far it carries, and which sounds fog does not hide — the reference's {@code
 * AudioManager::update}, {@code MilesAudioManager::getEffectiveVolume} and {@code SoundManager::canPlayNow}.
 */
class SoundReachTest {

    /** What reached the sink, and what was stopped. */
    private static final class Heard implements SoundSink {
        final List<String> files = new ArrayList<>();
        final List<Float> gains = new ArrayList<>();
        final List<Vector3f> places = new ArrayList<>();
        final List<Range> ranges = new ArrayList<>();
        int stopped;
        int loops;

        @Override
        public void play(String assetPath, float gain, Vector3f at) {
            play(assetPath, gain, at, Range.OWN);
        }

        @Override
        public void play(String assetPath, float gain, Vector3f at, Range range) {
            files.add(assetPath);
            gains.add(gain);
            places.add(at);
            ranges.add(range);
        }

        @Override
        public Playing playStoppable(String assetPath, float gain, Vector3f at, Range range) {
            play(assetPath, gain, at, range);
            return () -> stopped++;
        }

        @Override
        public Playing loop(String assetPath, float gain, Vector3f at, Range range) {
            loops++;
            play(assetPath, gain, at, range);
            return () -> stopped++;
        }

        @Override
        public Playing music(String assetPath, float gain) {
            return Playing.NONE;
        }
    }

    private static final SoundBank.Hearing REFERENCE = new SoundBank.Hearing(175f, 800f, 0.02f, 50f, 0.333f, 0.2f,
            130f, 425f);

    private static SoundBank bank(SoundBank.Hearing hearing, SoundBank.Cue... cues) {
        var builder = SoundBank.create().hearing(hearing);
        for (var cue : cues) {
            builder.cue(cue);
        }
        return builder.build();
    }

    private static SoundBank.Cue placed(String name, float gain, SoundBank.Reach reach) {
        return new SoundBank.Cue(name, SoundBank.Channel.EFFECTS, true, gain, 0f, List.of("audio/" + name + ".wav"),
                null).reaching(reach);
    }

    @Test
    void theListenerStands50AboveThePointLookedAtOrAThirdOfTheWayToAnEyeNearerThan150() {
        var ground = new Vector3f(100f, 0f, 100f);
        float back = 310f / FastMath.tan(37.5f * FastMath.DEG_TO_RAD);
        var high = Sounds.listenerAt(ground, new Vector3f(100f, 310f, 100f + back), 50f, 0.333f);
        assertEquals(50f, high.y, 1e-3f, "50 above the point looked at");
        var low = Sounds.listenerAt(ground, new Vector3f(100f, 120f, 100f + back * 120f / 310f), 50f, 0.333f);
        assertEquals(39.96f, low.y, 1e-3f, "0.333 of the way");
    }

    @Test
    void anEye427FromTheListenerTakesAFifthOffAPlacedCueAndNothingOffAFlatOne() {
        var heard = new Heard();
        var sounds = new Sounds(bank(REFERENCE, placed("boom", 1f, SoundBank.Reach.DEFAULT),
                new SoundBank.Cue("click", SoundBank.Channel.UI, false, 1f, 0f, List.of("audio/click.wav"), null)),
                heard);
        var listener = new Vector3f(0f, 50f, 0f);
        sounds.hear(listener, 427f);
        sounds.play("boom", new Vector3f(10f, 0f, 0f), 0f);
        sounds.play("click", null, 1f);
        sounds.hear(listener, 100f);
        sounds.play("boom", new Vector3f(10f, 0f, 0f), 2f);

        assertEquals(0.8f, heard.gains.get(0), 1e-6f, "the eye far: 0.8 of its gain");
        assertEquals(1f, heard.gains.get(1), 1e-6f, "a flat cue at 1");
        assertEquals(1f, heard.gains.get(2), 1e-6f, "the eye near: all of it");
    }

    @Test
    void aCueOfRanges175And800IsWholeAt100HalfAt350NotStartedAt800AndStoppedPastIt() {
        assertEquals(1f, Sounds.fallOff(175f, 100f), "100 away, full loudness");
        assertEquals(0.5f, Sounds.fallOff(175f, 350f), 1e-6f, "350 away, half");

        var heard = new Heard();
        var sounds = new Sounds(bank(SoundBank.Hearing.AS_EVER, placed("tank", 1f,
                new SoundBank.Reach(175f, 800f, false))), heard);
        sounds.hear(new Vector3f(0f, 0f, 0f), 0f);
        assertFalse(sounds.play("tank", new Vector3f(800f, 0f, 0f), 0f), "asked 800 away, not started");
        assertTrue(sounds.play("tank", new Vector3f(700f, 0f, 0f), 1f), "700 away, playing");
        assertEquals(new SoundSink.Range(175f, 800f), heard.ranges.getLast(), "falling away as its ranges say");
        sounds.hear(new Vector3f(-200f, 0f, 0f), 0f);
        assertEquals(1, heard.stopped, "the listener 900 from it: stopped");
    }

    @Test
    void aLoopStoppedOutOfReachPlaysAgainOnceBackWithin800() {
        var heard = new Heard();
        var sounds = new Sounds(bank(SoundBank.Hearing.AS_EVER, placed("ambient.Mill", 1f,
                new SoundBank.Reach(175f, 800f, false))), heard);
        var noises = new GameSounds(sounds, 10f);
        var mill = new UnitView(1, "Mill", 0, 700f, 0f, 0f, 100f, 100f, true, true, false, false, -1);

        sounds.hear(new Vector3f(0f, 0f, 0f), 0f);
        noises.frame(frame(List.of(mill), List.of(), List.of()), 0, 0f);
        assertEquals(1, heard.loops, "within reach: looping");
        sounds.hear(new Vector3f(-200f, 0f, 0f), 0f);
        noises.frame(frame(List.of(mill), List.of(), List.of()), 0, 1f);
        assertEquals(1, heard.stopped, "900 away: stopped");
        sounds.hear(new Vector3f(0f, 0f, 0f), 0f);
        noises.frame(frame(List.of(mill), List.of(), List.of()), 0, 2f);
        assertEquals(2, heard.loops, "back within 800: playing again");
    }

    @Test
    void underAFloorOf002ACueOfGainATenthStopsOnceMoreThan875Away() {
        var heard = new Heard();
        var hearing = new SoundBank.Hearing(0f, 0f, 0.02f, 0f, 0f, 0f, 0f, 0f);
        var sounds = new Sounds(bank(hearing, placed("hum", 0.1f, new SoundBank.Reach(175f, 100000f, false))),
                heard);
        sounds.hear(new Vector3f(0f, 0f, 0f), 0f);
        assertTrue(sounds.play("hum", new Vector3f(870f, 0f, 0f), 0f));
        sounds.hear(new Vector3f(-4f, 0f, 0f), 0f);
        assertEquals(0, heard.stopped, "874 away: 0.02, still heard");
        sounds.hear(new Vector3f(-6f, 0f, 0f), 0f);
        assertEquals(1, heard.stopped, "876 away: under the floor, stopped");
    }

    @Test
    void aSoundAboutAThing100UpIsPlaced100Up() {
        var heard = new Heard();
        var sounds = new Sounds(bank(SoundBank.Hearing.AS_EVER, placed("spawned.Jet", 1f, SoundBank.Reach.DEFAULT)),
                heard);
        var noises = new GameSounds(sounds, 10f);
        var jet = new UnitView(1, "Jet", 0, 50f, 60f, 0f, 100f, 100f, false, true, false, false, -1, 100f, 0f, 0f,
                true, 0);
        noises.frame(frame(List.of(jet), List.of(), List.of()), 0, 0f);
        assertEquals(new Vector3f(50f, 100f, 60f), heard.places.getFirst());
    }

    @Test
    void whereTheViewerDoesNotSeeOnlyACueFogDoesNotHideIsHeardAndALoopFogDoesNotHidePlaysAtItsPlace() {
        var heard = new Heard();
        var sounds = new Sounds(bank(SoundBank.Hearing.AS_EVER,
                placed("died.Crate", 1f, new SoundBank.Reach(0f, 0f, true)),
                placed("died.Tank", 1f, SoundBank.Reach.DEFAULT),
                placed("ambient.Birds", 1f, new SoundBank.Reach(0f, 0f, true)),
                placed("ambient.Tank", 1f, SoundBank.Reach.DEFAULT)), heard);
        var noises = new GameSounds(sounds, 10f);
        List<WorldEvent> unseen = List.of(
                new uz.dukeengine.core.event.ObjectDied(5, new uz.dukeengine.core.thing.ObjectId(7), "Crate", 0,
                        new Coord3D(300f, 300f, 0f), uz.dukeengine.core.module.DeathType.NORMAL, null, 0f, -1),
                new uz.dukeengine.core.event.ObjectDied(5, new uz.dukeengine.core.thing.ObjectId(8), "Tank", 1,
                        new Coord3D(310f, 300f, 0f), uz.dukeengine.core.module.DeathType.NORMAL, null, 0f, -1));
        var birds = new UnitView(3, "Birds", -1, 400f, 400f, 0f, 1f, 1f, true, false, false, false, -1);
        var tank = new UnitView(4, "Tank", 1, 420f, 400f, 0f, 1f, 1f, false, true, false, false, -1);
        noises.frame(frame(List.of(), unseen, List.of(birds, tank)), 0, 0f);

        assertEquals(List.of("audio/ambient.Birds.wav", "audio/died.Crate.wav"), heard.files,
                "the crate's fall heard, the tank's not; the birds' loop at its place, the tank's engine not");
        assertEquals(new Vector3f(400f, 0f, 400f), heard.places.getFirst());
    }

    @Test
    void aSoundPlayedWhereTheViewerDoesNotSeeIsHeardOnlyWhereFogDoesNotHideIt() {
        var sounds = new Sounds(bank(SoundBank.Hearing.AS_EVER,
                placed("Nuke", 1f, new SoundBank.Reach(0f, 0f, true)), placed("Step", 1f, SoundBank.Reach.DEFAULT)),
                new Heard());
        assertTrue(sounds.throughFog("Nuke"));
        assertFalse(sounds.throughFog("Step"));
        assertNotNull(new SoundPlayed(1, "Nuke", new Coord3D(0f, 0f, 0f), null, 0));
        assertNull(sounds.resolved("nothing"));
    }

    private static WorldSnapshot frame(List<UnitView> units, List<WorldEvent> unseen, List<UnitView> hidden) {
        return new WorldSnapshot(1, 0f, false, 0, 0, units, List.of(), "", "", List.of(), true, true, null, false,
                null, List.of(), List.of(), List.of(), null, List.of(), unseen, hidden);
    }
}
