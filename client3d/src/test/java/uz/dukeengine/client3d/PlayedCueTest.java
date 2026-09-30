package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.Vector3f;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.view.UnitView;
import uz.dukeengine.core.view.WorldSnapshot;

/**
 * A sound cue the simulation played by name, as the reference plays a saboteur's feedback at the building he got into:
 * heard at its place as the cue's rules say, following the thing it rides; a name nothing of the game's plays nothing.
 */
class PlayedCueTest {

    /** A sink that writes down where each sound was put, and where a kept one was moved. */
    private static final class Heard implements SoundSink {
        final List<Vector3f> places = new ArrayList<>();
        final List<Vector3f> moves = new ArrayList<>();

        @Override
        public void play(String assetPath, float gain, Vector3f at) {
            places.add(at);
        }

        @Override
        public Playing playStoppable(String assetPath, float gain, Vector3f at) {
            places.add(at);
            return new Playing() {
                @Override
                public void stop() {
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
            .cue("SabotageBuilding", SoundBank.Channel.EFFECTS, true, 1f, 0f, List.of("audio/sfx/sabotage.wav"))
            .build();
    private static final Vector3f PLACE = new Vector3f(300f, 0f, 200f);

    private static WorldSnapshot frame(UnitView... units) {
        return new WorldSnapshot(0, 0f, false, 0, 0, List.of(units), List.of(), "", "", List.of(), true);
    }

    @Test
    void aCueNamedAtAPlaceIsHeardThereAsAListsSoundEntryThereIs() {
        var heard = new Heard();
        var sounds = new Sounds(BANK, heard);
        var noises = new GameSounds(sounds, 5f);

        assertTrue(noises.played("SabotageBuilding", PLACE, -1, true, 0f));
        sounds.play("SabotageBuilding", PLACE, 10f); // a list's sound entry at that place

        assertEquals(List.of(PLACE, PLACE), heard.places, "put at its place, fainter the further it is heard from");
    }

    @Test
    void aCueRidingAThingFollowsItUntilItIsGone() {
        var heard = new Heard();
        var noises = new GameSounds(new Sounds(BANK, heard), 5f);

        assertTrue(noises.played("SabotageBuilding", PLACE, 7, true, 0f));
        noises.frame(frame(new UnitView(7, "Bunker", 1, 50f, 60f, 0f, 100f, 100f, true, true, false, false, -1)), 1,
                0.1f);
        noises.frame(frame(), 1, 0.2f);
        noises.frame(frame(new UnitView(7, "Bunker", 1, 90f, 60f, 0f, 100f, 100f, true, true, false, false, -1)), 1,
                0.3f);

        assertEquals(List.of(new Vector3f(50f, 0f, 60f)), heard.moves, "moved with it, and let go once it was gone");
    }

    @Test
    void aNameThatIsNothingOfTheGamesPlaysNothing() {
        var heard = new Heard();
        var noises = new GameSounds(new Sounds(BANK, heard), 5f);

        assertFalse(noises.played("NoSuchThing", PLACE, -1, true, 0f));
        assertTrue(heard.places.isEmpty(), "nothing heard");
    }
}
