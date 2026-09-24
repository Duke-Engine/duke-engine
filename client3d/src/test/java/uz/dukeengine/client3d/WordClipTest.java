package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uz.dukeengine.client3d.Visuals.ClipMode.HOLD;
import static uz.dukeengine.client3d.Visuals.ClipMode.LOOP;
import static uz.dukeengine.client3d.Visuals.ClipMode.LOOP_BACKWARDS;
import static uz.dukeengine.client3d.Visuals.ClipMode.ONCE;
import static uz.dukeengine.client3d.Visuals.ClipMode.ONCE_BACKWARDS;
import static uz.dukeengine.client3d.Visuals.ClipStart.FIRST;
import static uz.dukeengine.client3d.Visuals.ClipStart.LAST;
import static uz.dukeengine.client3d.Visuals.ClipStart.RANDOM;

import com.jme3.anim.AnimClip;
import com.jme3.anim.AnimComposer;
import com.jme3.anim.AnimTrack;
import com.jme3.anim.TransformTrack;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** A clip chosen by the words a thing holds, where it stands frame by frame of the game: a second is 30 of them. */
class WordClipTest {

    private static final double SECOND = 1.0;
    private static final double END = Math.nextDown(SECOND);

    /** A war factory's door: the one clip, opened once, held open, and run back shut. */
    private static Visuals.UnitVisual door() {
        return Visuals.create().unit("WarFactory", look -> look.model("models/abwarfact.glb")
                .clip(Set.of("DOOR_1_OPENING"), "ABWarFact_A8", ONCE, FIRST, null)
                .clip(Set.of("DOOR_1_WAITING_OPEN"), "ABWarFact_A8", HOLD, LAST, null)
                .clip(Set.of("DOOR_1_CLOSING"), "ABWarFact_A8", ONCE_BACKWARDS, LAST, null)).of("WarFactory");
    }

    @Test
    void aDoorOpensOnceFromItsFirstFrameIsHeldOnItsLastAndRunsBackShut() {
        var look = door();
        var clip = new WordClip();

        clip.choose(look.clipStateFor(Set.of("DOOR_1_OPENING")), look.clipStates, SECOND, 100, 7);
        assertEquals(0, clip.timeAt(100), "the first frame, the frame the word is set");
        assertEquals(0.5, clip.timeAt(115), 1e-6);
        assertEquals(END, clip.timeAt(130), "and on its last");
        assertEquals(END, clip.timeAt(200), "where it stays");

        clip.choose(look.clipStateFor(Set.of("DOOR_1_WAITING_OPEN")), look.clipStates, SECOND, 230, 7);
        assertEquals(END, clip.timeAt(230), "held on its last frame");
        assertEquals(END, clip.timeAt(900));

        clip.choose(look.clipStateFor(Set.of("DOOR_1_CLOSING")), look.clipStates, SECOND, 1000, 7);
        assertEquals(END, clip.timeAt(1000), "from its last frame");
        assertEquals(0.5, clip.timeAt(1015), 1e-6, "backwards");
        assertEquals(0, clip.timeAt(1030), "back to its first");
        assertEquals(0, clip.timeAt(1100), "and there it stays");
    }

    @Test
    void aLoopGoesRoundEitherWay() {
        var look = Visuals.create().unit("Radar", l -> l.model("models/radar.glb")
                .clip(Set.of("SPINNING"), "Spin", LOOP, null, null)
                .clip(Set.of("UNWINDING"), "Spin", LOOP_BACKWARDS, null, null)).of("Radar");
        var clip = new WordClip();
        clip.choose(look.clipStateFor(Set.of("SPINNING")), look.clipStates, SECOND, 0, 1);
        assertEquals(0.5, clip.timeAt(45), 1e-6, "a second and a half is half way round again");
        clip.choose(look.clipStateFor(Set.of("UNWINDING")), look.clipStates, SECOND, 100, 1);
        assertEquals(END, clip.timeAt(100), "backwards starts at its end");
        assertEquals(0.5, clip.timeAt(115), 1e-6);
        assertEquals(0.5, clip.timeAt(145), 1e-6, "and goes round again");
    }

    @Test
    void twoStatesSharingAKeepGroupHandTheFrameOverAndAnyOtherStartsAgain() {
        var look = Visuals.create().unit("Tank", l -> l.model("models/tank.glb")
                .clip(Set.of("MOVING"), "Drive", LOOP, null, "move")
                .clip(Set.of("MOVING", "REALLYDAMAGED"), "DriveDamaged", LOOP, null, "move")
                .clip(Set.of("MOVING", "SNOW"), "DriveSnow", LOOP, null, null)).of("Tank");
        var clip = new WordClip();
        clip.choose(look.clipStateFor(Set.of("MOVING")), look.clipStates, SECOND, 0, 3);
        clip.choose(look.clipStateFor(Set.of("MOVING", "REALLYDAMAGED")), look.clipStates, 2 * SECOND, 9, 3);
        assertEquals(0.6, clip.timeAt(9), 1e-6, "three tenths through the one, three tenths through the other");
        assertEquals(1.1, clip.timeAt(24), 1e-6, "and on from there");

        clip.choose(look.clipStateFor(Set.of("MOVING", "SNOW")), look.clipStates, SECOND, 30, 3);
        assertEquals(0, clip.timeAt(30), "no group in common: from its start");
    }

    @Test
    void aRandomStartIsTheSameOnEveryMachineAndInsideTheClip() {
        var look = Visuals.create().unit("Tree", l -> l.model("models/tree.glb")
                .clip(Set.of(), "Sway", LOOP, RANDOM, null)).of("Tree");
        var here = new WordClip();
        var there = new WordClip();
        here.choose(look.clipStateFor(Set.of()), look.clipStates, SECOND, 40, 12);
        there.choose(look.clipStateFor(Set.of()), look.clipStates, SECOND, 40, 12);
        assertEquals(here.timeAt(40), there.timeAt(40), "the same thing, the same frame: the same start");
        assertTrue(here.timeAt(40) >= 0 && here.timeAt(40) < SECOND);
    }

    @Test
    void theModelShowsTheFrameTheGameIsAtHoldingItsLastWhateverTheWindowDoes() {
        var door = new Node("Door");
        var factory = new Node("WarFactory");
        factory.attachChild(door);
        var swing = new AnimClip("ABWarFact_A8");
        swing.setTracks(new AnimTrack[] {new TransformTrack(door, new float[] {0f, 1f},
                new Vector3f[] {new Vector3f(), new Vector3f(10f, 0f, 0f)},
                null, null)});
        var composer = new AnimComposer();
        factory.addControl(composer);
        composer.addAnimClip(swing);
        var clip = new WordClip();
        clip.choose(1, door().clipStates, swing.getLength(), 50, 7); // held open, on its last frame

        // What the client does each frame it draws.
        composer.setCurrentAction("ABWarFact_A8", AnimComposer.DEFAULT_LAYER, true).setSpeed(0);
        composer.setTime(AnimComposer.DEFAULT_LAYER, clip.timeAt(50));
        composer.update(0.5f); // half a second of the window's own time
        assertEquals(10f, door.getLocalTranslation().x, 1e-3f, "its last frame: not wrapped round to its first");
        composer.update(0.5f);
        assertEquals(10f, door.getLocalTranslation().x, 1e-3f, "and not moved by the window's time");
    }

    @Test
    void aThingGivenNoClipsByWordsOrHoldingNoneOfThemPlaysItsRoles() {
        var plain = Visuals.create().unit("Ranger", l -> l.model("models/ranger.glb").idle("Idle")).of("Ranger");
        assertEquals(-1, plain.clipStateFor(Set.of("DOOR_1_OPENING")), "no clips by words at all");
        assertEquals(-1, door().clipStateFor(Set.of("DAMAGED")), "none of its words named: its idle, as today");

        var clip = new WordClip();
        clip.choose(0, door().clipStates, SECOND, 5, 7);
        assertTrue(clip.choose(-1, door().clipStates, 0, 6, 7), "leaving a clip chosen by words is a change");
        assertEquals(0, clip.timeAt(6));
    }
}
