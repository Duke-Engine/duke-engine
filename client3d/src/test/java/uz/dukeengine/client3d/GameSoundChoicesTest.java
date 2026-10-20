package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.Vector3f;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.view.UnitView;
import uz.dukeengine.core.view.WorldSnapshot;

/** A flat loop the game starts and fades (E56), and footsteps by the ground under them (E57). */
class GameSoundChoicesTest {

    /** A sink that writes down each sound started, and each loudness a loop is set to. */
    private static final class Heard implements SoundSink {
        final List<String> started = new ArrayList<>();
        final List<Float> gains = new ArrayList<>();
        int stops;

        @Override
        public void play(String assetPath, float gain, Vector3f at) {
            started.add(assetPath);
        }

        @Override
        public Playing loop(String assetPath, float gain, Vector3f at) {
            started.add(assetPath);
            return new Playing() {
                @Override
                public void stop() {
                    stops++;
                }

                @Override
                public void volume(float gain) {
                    gains.add(gain);
                }
            };
        }

        @Override
        public Playing music(String assetPath, float gain) {
            return Playing.NONE;
        }
    }

    @Test
    void aLoopFadesInAndOutAndStopsAtTheEndOfItsFade() {
        var heard = new Heard();
        var sounds = new Sounds(SoundBank.create().cue("ambience.forest", SoundBank.Channel.EFFECTS, false, 1f, 0f,
                List.of("audio/ambience/forest.ogg")).build(), heard);

        sounds.loop("ambience.forest", 1f);
        sounds.loop("ambience.forest", 1f);
        assertEquals(1, heard.started.size(), "one loop a cue");
        assertEquals(0f, heard.gains.getLast(), "from silence");
        sounds.update(0.5f);
        float half = heard.gains.getLast();
        sounds.update(0.5f);
        float whole = heard.gains.getLast();
        assertTrue(half > 0f && half < whole, "rising: " + half + " then " + whole);

        sounds.stopLoop("ambience.forest", 1f);
        sounds.update(0.5f);
        assertEquals(whole / 2f, heard.gains.getLast(), 1e-4f, "falling over its fade");
        assertEquals(0, heard.stops, "still playing half way out");
        sounds.update(0.6f);
        assertEquals(1, heard.stops, "stopped at the end of the fade, not cut");
    }

    private static WorldSnapshot walking(int frame, float x) {
        var walker = new UnitView(3, "Wolf", 1, x, 20f, 0f, 100f, 100f, false, true, true, false, -1);
        return new WorldSnapshot(frame, 0f, false, 0, 0, List.of(walker), List.of(), "", "", List.of(), true);
    }

    @Test
    void aCreatureWalkingFromGrassOntoRockIsHeardOnEach() {
        var heard = new Heard();
        var bank = SoundBank.create()
                .cue("walking.Wolf.grass", SoundBank.Channel.EFFECTS, false, 1f, 0f,
                        List.of("audio/sfx/step_grass.ogg"))
                .cue("walking.Wolf.rock", SoundBank.Channel.EFFECTS, false, 1f, 0f, List.of("audio/sfx/step_rock.ogg"))
                .build();
        var noises = new GameSounds(new Sounds(bank, heard), 5f);
        noises.surfaces((x, _) -> x < 50f ? "grass" : "rock");

        noises.frame(walking(1, 10f), 1, 0f);
        noises.frame(walking(2, 20f), 1, 1f);
        noises.frame(walking(3, 60f), 1, 2f);

        assertTrue(heard.started.contains("audio/sfx/step_grass.ogg"), heard.started.toString());
        assertEquals("audio/sfx/step_rock.ogg", heard.started.getLast(), "then the rock under it");
    }
}
