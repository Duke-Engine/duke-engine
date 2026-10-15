package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.content.EffectList;

/** An ambience knob of its own, and the player's camera shake over the game's (E59). */
class PlayerSettingsTest {

    /** A sink that writes down each sound's loudness as it starts, and each a loop is set to. */
    private static final class Heard implements SoundSink {
        final List<Float> played = new ArrayList<>();
        final List<Float> loopGains = new ArrayList<>();

        @Override
        public void play(String assetPath, float gain, Vector3f at) {
            played.add(gain);
        }

        @Override
        public Playing loop(String assetPath, float gain, Vector3f at) {
            loopGains.add(gain);
            return new Playing() {
                @Override
                public void stop() {
                }

                @Override
                public void volume(float gain) {
                    loopGains.add(gain);
                }
            };
        }

        @Override
        public Playing music(String assetPath, float gain) {
            return Playing.NONE;
        }
    }

    @Test
    void ambienceAtNoughtSilencesTheLoopWhileEffectsStillSound() {
        var heard = new Heard();
        var sounds = new Sounds(SoundBank.create()
                .cue("ambience.forest", SoundBank.Channel.AMBIENCE, false, 1f, 0f, List.of("audio/ambience/forest.ogg"))
                .cue("hit", SoundBank.Channel.EFFECTS, false, 1f, 0f, List.of("audio/sfx/hit.ogg"))
                .build(), heard);

        sounds.loop("ambience.forest", 0f);
        assertEquals(1f, heard.loopGains.getLast(), "playing at its own loudness");
        sounds.volume(SoundBank.Channel.AMBIENCE, 0f);
        assertEquals(0f, heard.loopGains.getLast(), "silenced as the knob goes down");

        sounds.play("hit", null, 0f);
        assertTrue(heard.played.getLast() > 0f, "effects still sound");
    }

    @Test
    void shakeOffGivesNoKnock() {
        assertEquals(1f, DukeRtsApp.playerShake(0));
        assertEquals(0.5f, DukeRtsApp.playerShake(1));
        assertEquals(0f, DukeRtsApp.playerShake(2));

        var show = new ListShow(null, new Node(), null, (cue, at) -> { }, () -> Vector3f.ZERO, (x, z) -> 0f,
                (model, piece) -> new Node("piece"), (name, at) -> { }, null, null);
        show.shakeScale(1f * DukeRtsApp.playerShake(2));
        var strengths = EffectList.Shake.Strength.values();
        show.shake(Vector3f.ZERO, strengths[strengths.length - 1]);
        assertSame(Vector3f.ZERO, show.shakeNow());
    }
}
