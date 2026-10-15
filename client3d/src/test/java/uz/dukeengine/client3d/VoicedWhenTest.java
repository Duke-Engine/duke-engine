package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jme3.math.Vector3f;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.event.ObjectCreated;
import uz.dukeengine.core.event.WorldEvent;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.view.UnitView;
import uz.dukeengine.core.view.WorldSnapshot;

/** A thing's greeting voice, heard only as it came the ways the game names — and for every new thing where none. */
class VoicedWhenTest {

    private static final SoundBank BANK = SoundBank.create()
            .cue("spawned.Ranger", SoundBank.Channel.EFFECTS, false, 1f, 0f, List.of("audio/voice/ranger.wav"))
            .build();

    private static int greetings(Set<ObjectCreated.How> greeted, ObjectCreated.How how) {
        var heard = new ArrayList<String>();
        var noises = new GameSounds(new Sounds(BANK, new SoundSink() {
            @Override
            public void play(String assetPath, float gain, Vector3f at) {
                heard.add(assetPath);
            }

            @Override
            public Playing playStoppable(String assetPath, float gain, Vector3f at) {
                heard.add(assetPath);
                return Playing.NONE;
            }

            @Override
            public Playing music(String assetPath, float gain) {
                return Playing.NONE;
            }
        }), 5f, template -> Map.of(), greeted);
        var unit = new UnitView(7, "Ranger", 1, 50f, 60f, 0f, 100f, 100f, true, true, false, false, -1);
        List<WorldEvent> events = how == null ? List.of() : List.of(new ObjectCreated(1, new ObjectId(7), how));
        noises.frame(new WorldSnapshot(1, 0f, false, 0, 0, List.of(unit), events, "", "", List.of(), true), 1, 0.1f);
        return heard.size();
    }

    @Test
    void namedWaysAloneAreGreeted() {
        var produced = Set.of(ObjectCreated.How.PRODUCED);
        assertEquals(1, greetings(produced, ObjectCreated.How.PRODUCED));
        assertEquals(0, greetings(produced, ObjectCreated.How.UNLOADED), "a hold emptied says nothing");
        assertEquals(0, greetings(produced, null), "new with no word of how: spawned, not named");
        assertEquals(1, greetings(Set.of(ObjectCreated.How.SPAWNED), null));
    }

    @Test
    void neverNamedGreetsEveryNewThing() {
        assertEquals(1, greetings(null, null));
    }
}
