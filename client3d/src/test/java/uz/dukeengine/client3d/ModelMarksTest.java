package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.anim.AnimClip;
import com.jme3.anim.AnimComposer;
import com.jme3.anim.AnimTrack;
import com.jme3.anim.TransformTrack;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.List;
import org.junit.jupiter.api.Test;

/** An order answered with the game's own model: laid where it went, its clip once, gone when its frames are up. */
class ModelMarksTest {

    private static final OrderMark HINT = OrderMark.DEFAULTS.model("models/scmovehint.glb", "SCMoveHint", 40);

    private int loaded;

    /** The green disc and cross, with its clip. */
    private Spatial load(String path) {
        loaded++;
        var hint = new Node(path);
        var disc = new Node("Disc");
        hint.attachChild(disc);
        var clip = new AnimClip("SCMoveHint");
        clip.setTracks(new AnimTrack<?>[] {new TransformTrack(disc, new float[] {0f, 1f},
                new Vector3f[] {new Vector3f(), new Vector3f(0f, 2f, 0f)}, null, null)});
        var composer = new AnimComposer();
        hint.addControl(composer);
        composer.addAnimClip(clip);
        return hint;
    }

    @Test
    void aMoveTheGameGivesIsHintedForTwoUnitsAndNotForALoneBuilding() {
        var tanks = java.util.List.of(
                new uz.dukeengine.core.view.UnitView(4, "Tank", 1, 0f, 0f, 0f, 100f, 100f, false, true, false, false, -1),
                new uz.dukeengine.core.view.UnitView(7, "Tank", 1, 0f, 0f, 0f, 100f, 100f, false, true, false, false, -1));
        var factory = java.util.List.of(
                new uz.dukeengine.core.view.UnitView(9, "WarFactory", 1, 0f, 0f, 0f, 900f, 900f, true, true, false, false,
                        0));

        assertTrue(DukeRtsApp.hintsMove(HINT, java.util.Set.of(4, 7), tanks), "two units: the model is laid");
        assertFalse(DukeRtsApp.hintsMove(HINT, java.util.Set.of(9), factory), "a lone building: nothing");
        assertFalse(DukeRtsApp.hintsMove(OrderMark.DEFAULTS, java.util.Set.of(4, 7), tanks), "no model named: none");
    }

    @Test
    void aMoveLaysOneAtTheGroundPointPlayingItsClipFromTheStartAndItGoesWhenItsFramesAreUp() {
        var markers = new Node("markers");
        var marks = new ModelMarks(markers, this::load);
        marks.add(List.of(4, 7), 50f, 60f, 3f, HINT, 10f);

        assertEquals(1, marks.count());
        var hint = marks.markOf(List.of(7, 4));
        assertSame(markers, hint.getParent());
        assertEquals(new Vector3f(50f, 3f, 60f), hint.getLocalTranslation(), "on the ground where the order went");
        var composer = AnimationLibrary.findControl(hint, AnimComposer.class);
        assertSame(composer.getAction("SCMoveHint"), composer.getCurrentAction(), "its clip");
        assertEquals(0.0, composer.getTime(AnimComposer.DEFAULT_LAYER), 1e-9, "from its first frame");

        marks.update(10f + 39f / 30f, HINT);
        assertEquals(1, marks.count(), "39 frames on, still there");
        marks.update(10f + 40f / 30f, HINT);
        assertEquals(0, marks.count(), "40, gone");
        assertNull(hint.getParent());
    }

    @Test
    void aSecondOrderFromTheSameSelectionMovesTheMarkAndAnotherSelectionLaysItsOwn() {
        var markers = new Node("markers");
        var marks = new ModelMarks(markers, this::load);
        marks.add(List.of(4, 7), 50f, 60f, 0f, HINT, 10f);
        var first = marks.markOf(List.of(4, 7));
        marks.add(List.of(7, 4), 90f, 20f, 0f, HINT, 11f);

        assertEquals(1, marks.count(), "moved, not added");
        assertSame(first, marks.markOf(List.of(4, 7)));
        assertEquals(new Vector3f(90f, 0f, 20f), first.getLocalTranslation());
        assertEquals(1, loaded, "one model for the one selection");

        marks.add(List.of(9), 0f, 0f, 0f, HINT, 12f);
        assertEquals(2, marks.count(), "another selection, its own mark");
    }

    @Test
    void aGameNamingNothingGetsTheArrowheadsAndTheRingAndOneMayTurnTheRingOff() {
        assertNull(OrderMark.DEFAULTS.model(), "no model: the arrowheads");
        assertFalse(OrderMark.DEFAULTS.noAttackRing(), "and the ring round an attacked thing");
        assertTrue(OrderMark.DEFAULTS.attackRing(false).noAttackRing(), "an attack answered by nothing");
        assertEquals(OrderMark.DEFAULTS.seconds(), HINT.attackRing(false).seconds(), "the rest as it was");
    }
}
