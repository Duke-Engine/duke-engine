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
        assertEquals(1f, PlayerChoices.playerShake(0));
        assertEquals(0.5f, PlayerChoices.playerShake(1));
        assertEquals(0f, PlayerChoices.playerShake(2));

        var show = new ListShow(null, new Node(), null, (_, _) -> { }, () -> Vector3f.ZERO, (_, _) -> 0f,
                (_, piece) -> new Node("piece"), (_, _) -> { }, null, null);
        show.shakeScale(1f * PlayerChoices.playerShake(2));
        var strengths = EffectList.Shake.Strength.values();
        show.shake(Vector3f.ZERO, strengths[strengths.length - 1]);
        assertSame(Vector3f.ZERO, show.shakeNow());
    }

    @Test
    void aLoopAtAPlaceIsHeardThereUnderItsKeyAndStoppedByIt() {
        var at = new ArrayList<Vector3f>();
        var stops = new int[1];
        var sounds = new Sounds(SoundBank.create()
                .cue("campfire", SoundBank.Channel.AMBIENCE, true, 1f, 0f, List.of("audio/ambience/fire.ogg"))
                .build(), new SoundSink() {
                    @Override
                    public void play(String assetPath, float gain, Vector3f where) {
                    }

                    @Override
                    public Playing loop(String assetPath, float gain, Vector3f where) {
                        at.add(where);
                        return new Playing() {
                            @Override
                            public void stop() {
                                stops[0]++;
                            }

                            @Override
                            public void volume(float gain) {
                            }
                        };
                    }

                    @Override
                    public Playing music(String assetPath, float gain) {
                        return Playing.NONE;
                    }
                });

        sounds.loopAt("fire-1", "campfire", new Vector3f(10f, 0f, 20f));
        sounds.update(0.1f);
        assertEquals(List.of(new Vector3f(10f, 0f, 20f)), at, "placed, once");
        sounds.stopLoopAt("fire-1", 0f);
        assertEquals(1, stops[0]);
        sounds.update(0.1f);
        assertEquals(1, at.size(), "not started again once stopped");
    }
}
