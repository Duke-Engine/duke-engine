package uz.duke.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.Vector3f;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Clicking a skeleton does not sound like levelling up.
 *
 * <p>The bar describes whatever the player has picked out, so the line it reads
 * now says "a skeleton, 34 of 40" and a moment ago said "Erika, 7th level" — and
 * the thing that listens to that line for moments worth hearing was written when
 * only one card could ever be on it. A level is heard by the rank changing; a
 * card with no rank on it changed the rank twice per click, and both changes rang
 * the bell.
 *
 * <p>The kind of fault that never shows up in a screenshot and is deafening in
 * the game, which is why it is worth a test of its own.
 */
class SelectedCardSoundTest {

    /** A sink that writes down what it was asked to play. */
    private static final class Heard implements SoundSink {
        private final List<String> played = new ArrayList<>();

        @Override
        public void play(String assetPath, float gain, Vector3f at) {
            played.add(assetPath);
        }

        @Override
        public void music(String assetPath, float gain) {
        }

        @Override
        public void musicGain(float gain) {
        }
    }

    private static final String HERO =
            "name=Erika|title=O'q ustasi|rank=7-daraja|hp=128/200|xp=38/100"
                    + "|depth=III|depthWord=CHUQURLIK";

    private static final String SKELETON =
            "name=Skeleton|hp=34/40|depth=III|depthWord=CHUQURLIK|face=skull|stat=Zarba,7";

    private static GameSounds listening(Heard heard) {
        var bank = SoundBank.create()
                .cue("level_up", SoundBank.Channel.EFFECTS, false, 1f, 0f, List.of("lvl.ogg"))
                .cue("depth", SoundBank.Channel.EFFECTS, false, 1f, 0f, List.of("deep.ogg"))
                .build();
        return new GameSounds(new Sounds(bank, heard), 20f);
    }

    @Test
    void pickingOutACreatureAndLettingGoRingsNothing() {
        var heard = new Heard();
        var sounds = listening(heard);
        sounds.status(HeroPanel.Reading.parse(HERO), 0f);
        heard.played.clear();

        sounds.status(HeroPanel.Reading.parse(SKELETON), 1f);
        sounds.status(HeroPanel.Reading.parse(HERO), 2f);

        assertEquals(List.of(), heard.played,
                "looking at something is not a thing that happened to him");
    }

    /** And a level he really does gain is still heard. */
    @Test
    void anActualLevelIsStillHeard() {
        var heard = new Heard();
        var sounds = listening(heard);
        sounds.status(HeroPanel.Reading.parse(HERO), 0f);
        // He looks at a skeleton, lets go, and then levels.
        sounds.status(HeroPanel.Reading.parse(SKELETON), 1f);
        sounds.status(HeroPanel.Reading.parse(HERO), 2f);
        heard.played.clear();

        sounds.status(HeroPanel.Reading.parse(HERO.replace("7-daraja", "8-daraja")), 3f);

        assertTrue(heard.played.contains("lvl.ogg"),
                "the bell still rings for the thing it is for: " + heard.played);
    }

    /**
     * The floor is still the floor while he is looking at something.
     *
     * <p>Depth is on a creature's card on purpose — it belongs to the dungeon, not
     * to whatever is selected — so going down a floor with a skeleton picked out
     * is heard like any other.
     */
    @Test
    void goingDownIsHeardEvenWhileLookingAtSomething() {
        var heard = new Heard();
        var sounds = listening(heard);
        sounds.status(HeroPanel.Reading.parse(SKELETON), 0f);
        heard.played.clear();

        sounds.status(HeroPanel.Reading.parse(SKELETON.replace("depth=III", "depth=IV")), 1f);

        assertTrue(heard.played.contains("deep.ogg"), heard.played.toString());
    }
}
