package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static uz.dukeengine.client3d.Visuals.ClipMode.LOOP;
import static uz.dukeengine.client3d.Visuals.ClipMode.ONCE;
import static uz.dukeengine.client3d.Visuals.ClipStart.FIRST;

import com.jme3.anim.AnimClip;
import com.jme3.anim.AnimComposer;
import com.jme3.anim.AnimTrack;
import com.jme3.anim.TransformTrack;
import com.jme3.asset.DesktopAssetManager;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import java.util.Set;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;

/** A thing the world keeps dead, drawn by the words it holds and its health: its clip, its model, what lay under it. */
class DeadLookTest {

    /** A second's clip moving {@code moved} from nothing to {@code to} along x. */
    private static AnimClip clip(String name, Node moved, float to) {
        var clip = new AnimClip(name);
        clip.setTracks(new AnimTrack<?>[] {new TransformTrack(moved, new float[] {0f, 1f},
                new Vector3f[] {new Vector3f(), new Vector3f(to, 0f, 0f)}, null, null)});
        return clip;
    }

    /** A soldier flung by a blast: flailing while he flies, landing when he strikes the ground, dead from frame 100. */
    @Test
    void aDeadThingThatGainsAWordPlaysItsClipAndLosingItTheClipOfTheWordsItHolds() {
        var soldier = new Node("Soldier");
        var body = new Node("Body");
        soldier.attachChild(body);
        var composer = new AnimComposer();
        soldier.addControl(composer);
        composer.addAnimClip(clip("Die", body, 10f));
        composer.addAnimClip(clip("Flail", body, 20f));
        composer.addAnimClip(clip("Land", body, 30f));
        var look = Visuals.create().unit("Soldier", l -> l.model("models/soldier.glb")
                .clip(Set.of("DYING", "EXPLODED_FLAILING"), "Flail", LOOP, null, null)
                .clip(Set.of("DYING", "EXPLODED_BOUNCING"), "Land", ONCE, FIRST, null)).of("Soldier");
        var words = new WordClip();

        var playing = WordClip.playDead(composer, words, "Die", look, Set.of("DYING"), 115, 7, "Die", 100, x -> { });
        assertEquals("Die", playing, "no word of its own: its death");
        composer.update(0f);
        assertEquals(5f, body.getLocalTranslation().x, 1e-3f, "half a second into it, fifteen frames after it died");

        playing = WordClip.playDead(composer, words, playing, look, Set.of("DYING", "EXPLODED_FLAILING"), 120, 7,
                "Die", 100, x -> { });
        assertEquals("Flail", playing, "a word gained: its clip");

        playing = WordClip.playDead(composer, words, playing, look, Set.of("DYING", "EXPLODED_BOUNCING"), 150, 7,
                "Die", 100, x -> { });
        assertEquals("Land", playing, "that word lost: the clip of the words it holds");

        playing = WordClip.playDead(composer, words, playing, look, Set.of("DYING"), 200, 7, "Die", 100, x -> { });
        assertEquals("Die", playing, "and with none, its death again");
        composer.update(0f);
        assertEquals(10f, body.getLocalTranslation().x, 1e-3f, "lain in on its last frame, long played out");
    }

    /** A building that keeps its ruin: its model for its words and no health is the ruin, from the frame it dies. */
    @Test
    void aDeadThingWhoseModelForNoHealthIsAnotherIsDrawnWithIt() {
        var look = Visuals.create().unit("Monument", l -> l.model("models/monument.glb")
                .model(Set.of("RUBBLE"), "models/monument_ruin.glb").whenHurt("RUBBLE", 0.01f)).of("Monument");

        assertEquals("models/monument.glb", look.modelFor(0.5f, Set.of(), Set.of()), "standing");
        assertEquals("models/monument_ruin.glb", look.modelFor(0f, Set.of(), Set.of()), "dead: its ruin");
    }

    /** What lay under it fading out from its death, as the reference's decal does: half gone half its frames after. */
    @Test
    void aDeadThingsGroundPictureFadesOutFromItsDeath() {
        var look = Visuals.create().unit("RedGuard", l -> l
                .groundPicture(Set.of("HORDE"), "Common/Textures/dot.png", 14f, 14f, 30)).of("RedGuard");
        var pictures = new GroundPictures(new DesktopAssetManager(true), new Node("ground"));
        var at = new Coord3D(10f, 50f, 0f);
        for (int frame = 0; frame < 30; frame++) {
            pictures.see(7, look, Set.of("HORDE"), at, 0f, 1f, (x, y) -> 0f);
        }
        assertEquals(1f, pictures.under(7).getFirst().opacity(), 1e-4f, "whole while it lived");

        for (int frame = 0; frame < 15; frame++) {
            pictures.see(7, look, null, at, 0f, 1f, (x, y) -> 0f); // dead, the word still held
        }
        assertEquals(0.5f, pictures.under(7).getFirst().opacity(), 1e-4f, "half gone fifteen frames on");
    }
}
