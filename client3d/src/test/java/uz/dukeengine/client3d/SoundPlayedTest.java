package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jme3.math.Vector3f;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.event.SoundPlayed;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.game.view.UnitView;
import uz.dukeengine.game.view.WorldSnapshot;

/**
 * A sound the simulation plays at a thing or a place — the reference's logic calling {@code addAudioEvent}: heard once
 * and following the thing, or held going while it is asked for again within its hold, a weapon's fire loop.
 */
class SoundPlayedTest {

    /** A sink that writes down what was started, looped, moved and stopped. */
    private static final class Heard implements SoundSink {
        final List<Vector3f> started = new ArrayList<>();
        final List<Vector3f> moves = new ArrayList<>();
        int loops;
        int stops;

        @Override
        public void play(String assetPath, float gain, Vector3f at) {
            started.add(at);
        }

        @Override
        public Playing playStoppable(String assetPath, float gain, Vector3f at) {
            started.add(at);
            return kept();
        }

        @Override
        public Playing loop(String assetPath, float gain, Vector3f at) {
            loops++;
            return kept();
        }

        private Playing kept() {
            return new Playing() {
                @Override
                public void stop() {
                    stops++;
                }

                @Override
                public void moveTo(Vector3f to) {
                    moves.add(to);
                }
            };
        }

        @Override
        public Playing music(String assetPath, float gain) {
            return Playing.NONE;
        }
    }

    private static final SoundBank BANK = SoundBank.create()
            .cue("VoiceRapidFire", SoundBank.Channel.VOICE, true, 1f, 0f, List.of("audio/voice/rapid_fire.wav"))
            .cue("GattlingLoop", SoundBank.Channel.EFFECTS, true, 1f, 0f, List.of("audio/sfx/gattling_loop.wav"))
            .build();

    private static WorldSnapshot frame(int frame, UnitView... units) {
        return new WorldSnapshot(frame, 0f, false, 0, 0, List.of(units), List.of(), "", "", List.of(), true);
    }

    private static UnitView tank(float x) {
        return new UnitView(7, "GattlingTank", 1, x, 60f, 0f, 100f, 100f, true, true, false, false, -1);
    }

    @Test
    void aSoundPlayedAtAThingIsHeardOnceThereAndFollowsIt() {
        var heard = new Heard();
        var noises = new GameSounds(new Sounds(BANK, heard), 5f);
        noises.sound(new SoundPlayed(10, "VoiceRapidFire", new Coord3D(50f, 60f, 0f), new ObjectId(7), 0),
                new Vector3f(50f, 0f, 60f), true, 0f);
        noises.frame(frame(10, tank(55f)), 1, 0.1f);

        assertEquals(List.of(new Vector3f(50f, 0f, 60f)), heard.started, "once, where the thing stood");
        assertEquals(List.of(new Vector3f(55f, 0f, 60f)), heard.moves, "and after it as it goes");
    }

    @Test
    void askedAgainWithinItsHoldItPlaysOnAndNotAskedItStops() {
        var heard = new Heard();
        var noises = new GameSounds(new Sounds(BANK, heard), 5f);
        for (int frame = 0; frame < 30; frame++) {
            if (frame % 3 == 0 && frame <= 18) {
                noises.sound(new SoundPlayed(frame, "GattlingLoop", new Coord3D(50f, 60f, 0f), new ObjectId(7), 5),
                        new Vector3f(50f, 0f, 60f), true, frame / 30f);
            }
            noises.frame(frame(frame, tank(50f)), 1, frame / 30f);
            if (frame == 23) {
                assertEquals(0, heard.stops, "asked last at 18: playing on to 23");
            }
        }
        assertEquals(1, heard.loops, "one sound, asked every 3 frames");
        assertEquals(1, heard.stops, "stopped once not asked within its 5 frames");
    }
}
