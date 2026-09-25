package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.scene.Node;
import java.util.Set;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;

/** A picture on the ground under a thing, chosen by the words it holds, fading in and out with them. */
class GroundPicturesTest {

    private static final String HORDE = "Common/Textures/dot.png";
    private static final String NATIONALISM = "Common/Textures/MissingTexture.png";
    private static final BiFunction<Float, Float, Float> FLAT = (x, y) -> 0f;

    private static final Visuals.UnitVisual RED_GUARD = Visuals.create().unit("RedGuard", look -> look
            .groundPicture(Set.of("HORDE"), HORDE, 14f, 14f, 30)
            .groundPicture(Set.of("HORDE", "NATIONALISM"), NATIONALISM, 14f, 14f, 30)).of("RedGuard");

    private final GroundPictures pictures = new GroundPictures(new DesktopAssetManager(true), new Node("ground"));

    /** A frame of the game's for the thing standing at {@code x}, holding {@code words}. */
    private void frame(Set<String> words, float x) {
        pictures.see(7, RED_GUARD, words, new Coord3D(x, 50f, 0f), 0f, 1f, FLAT);
    }

    @Test
    void aThingGainingTheWordShowsThePictureFollowingItAndLosingItFadesItOut() {
        frame(Set.of(), 10f);
        assertTrue(pictures.under(7).isEmpty(), "nothing said for no words: nothing laid");

        for (int frame = 0; frame < 30; frame++) {
            frame(Set.of("HORDE"), 10f + frame);
        }
        var laid = pictures.under(7).getFirst();
        assertEquals(HORDE, laid.picture());
        assertEquals(1f, laid.opacity(), 1e-4f, "whole after its 30 frames of fading in");
        assertTrue(laid.decal().showing());
        assertEquals(39f, laid.decal().node().getLocalTranslation().x, 1e-4f, "under it, where it walked to");
        assertEquals(50f, laid.decal().node().getLocalTranslation().z, 1e-4f, "the map's y the scene's z");

        frame(Set.of(), 40f);
        assertTrue(pictures.under(7).getFirst().leaving(), "the word gone: on its way out");
        for (int frame = 0; frame < 30; frame++) {
            frame(Set.of(), 40f);
        }
        assertTrue(pictures.under(7).isEmpty(), "and faded away");
    }

    @Test
    void withNationalismHeldTooItsOwnPictureIsChosen() {
        frame(Set.of("HORDE", "NATIONALISM"), 10f);

        assertEquals(NATIONALISM, pictures.under(7).getFirst().picture(), "the more said, the better it fits");
    }

    @Test
    void aThingOutOfSightTakesItsPictureWithIt() {
        frame(Set.of("HORDE"), 10f);

        pictures.forget(7);

        assertTrue(pictures.under(7).isEmpty(), "in the fog, nothing of it is drawn");
    }

    /** A soldier's shadow under him, and the horde's picture laid over it while he is in one. */
    @Test
    void aShadowStaysUnderThePictureTheWordsChoose() {
        var soldier = Visuals.create().unit("RedGuard", look -> look
                .groundPicture(Set.of(), NATIONALISM, 14f, 14f, 0)
                .groundPicture(Set.of("HORDE"), HORDE, 14f, 14f, 0)).of("RedGuard");

        pictures.see(3, soldier, Set.of("HORDE"), new Coord3D(0f, 0f, 0f), 0f, 1f, FLAT);
        assertEquals(java.util.List.of(NATIONALISM, HORDE),
                pictures.under(3).stream().map(GroundPictures.Shown::picture).toList(), "both, the shadow first");
        assertTrue(pictures.under(3).stream().noneMatch(GroundPictures.Shown::leaving));

        pictures.see(3, soldier, Set.of(), new Coord3D(0f, 0f, 0f), 0f, 1f, FLAT);
        assertEquals(java.util.List.of(NATIONALISM),
                pictures.under(3).stream().map(GroundPictures.Shown::picture).toList(), "the horde gone: its shadow");
    }

    @Test
    void aPictureOfNoWordsIsAlwaysThere() {
        var tank = Visuals.create().unit("Tank", look -> look.groundPicture(Set.of(), HORDE, 20f, 10f, 0))
                .of("Tank");

        pictures.see(3, tank, Set.of("ANYTHING"), new Coord3D(0f, 0f, 0f), 1f, 1f, FLAT);

        var shadow = pictures.under(3).getFirst();
        assertEquals(1f, shadow.opacity(), "a shadow, there from the first frame");
        assertEquals(20f, shadow.decal().across(), "as wide as it was said to be");
    }
}
