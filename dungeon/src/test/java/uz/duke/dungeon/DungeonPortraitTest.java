package uz.duke.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.anim.AnimComposer;
import com.jme3.asset.AssetManager;
import com.jme3.asset.DesktopAssetManager;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.Control;
import org.junit.jupiter.api.Test;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.dungeon.content.HeroLook;
import uz.duke.dungeon.content.PortraitArt;

/**
 * The live portrait is described, and everything it names exists.
 *
 * <p>The failure this guards against is the one every asset fault here has in
 * common: from a chair it looks like a rendering problem. A clip name the model
 * does not carry, a portrait for a creature with no model, a camera placed inside
 * the hero's head — none of them raises anything, and all of them come out as a
 * frame with something wrong in it that nobody can attribute.
 *
 * <p>The half this cannot see is whether the portrait is a good picture of him.
 * That needs eyes, and the numbers it needs are all in {@code dungeon.ini} where
 * eyes can change them.
 */
class DungeonPortraitTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    /** One loader for the class, as {@code DungeonMonsterArtTest} keeps one: big files. */
    private static final AssetManager ASSETS = new DesktopAssetManager(true);

    private static <T extends Control> T control(Spatial spatial, Class<T> type) {
        var found = spatial.getControl(type);
        if (found != null) {
            return found;
        }
        if (spatial instanceof Node node) {
            for (var child : node.getChildren()) {
                var deeper = control(child, type);
                if (deeper != null) {
                    return deeper;
                }
            }
        }
        return null;
    }

    /** The hero block of that name, or null — the portrait takes its art from it. */
    private static HeroLook heroNamed(String name) {
        return SETTINGS.heroes().stream()
                .filter(hero -> hero.name().equals(name))
                .findFirst()
                .orElse(null);
    }

    @Test
    void theHeroHasAPortrait() {
        var portraits = SETTINGS.portraits();

        assertFalse(portraits.isEmpty(), "dungeon.ini describes no portrait at all");
        assertNotNull(portraits.stream().filter(art -> art.name().equals("Rogue"))
                .findFirst().orElse(null), "the hero was left without one");
    }

    /**
     * Every portrait is the face of a creature the file describes.
     *
     * <p>A portrait names no art of its own on purpose — it takes the model, the
     * scale and the libraries from the hero's own block under the same name. Which
     * means a portrait naming a creature that has no block is a frame that stays
     * empty, and the name is the only thing that could be wrong.
     *
     * <p>Having a <em>model</em> is not required, and deliberately: a hero whose
     * art has not arrived yet is drawn as a coloured shape and framed by the
     * drawing, which is the client's own fallback and how this game started. What
     * is required is that his block exists, because that is what the name has to
     * match.
     */
    @Test
    void everyPortraitNamesACreatureTheFileDescribes() {
        for (var art : SETTINGS.portraits()) {
            var him = heroNamed(art.name());
            assertNotNull(him, art.name() + " has a portrait but no Hero block");
            assertEquals(art.name(), him.name(),
                    art.name() + "'s portrait found somebody else's block");
        }
    }

    /**
     * Every clip a portrait asks for is in one of that creature's own libraries.
     *
     * <p>The link nothing else checks. The clips are named in one file and live in
     * another, neither compiler sees either, and a misspelling comes out as a hero
     * who stands still through his own death — which reads as the portrait being
     * broken rather than as a word being wrong.
     */
    @Test
    void everyClipAPortraitAsksForIsInOneOfHisLibraries() {
        for (var art : SETTINGS.portraits()) {
            var him = heroNamed(art.name());
            assertNotNull(him, art.name() + " has a portrait but no Hero block");
            assertFalse(art.clips().isEmpty(), art.name() + "'s portrait names no clip at all");
            for (var clip : art.clips()) {
                assertTrue(inOneOfHisLibraries(him, clip),
                        clip + " is named for " + art.name() + "'s portrait but is in none of "
                                + him.animations());
            }
        }
    }

    private static boolean inOneOfHisLibraries(HeroLook him, String clip) {
        for (var path : him.animations()) {
            var composer = control(ASSETS.loadModel(path), AnimComposer.class);
            if (composer != null && composer.getAnimClip(clip) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Every selectable creature gets a face, without a block of its own.
     *
     * <p>Nearly the whole of the feature. A block per monster would be ten blocks
     * that say the same thing, and the eleventh monster would arrive without one.
     */
    @Test
    void everySelectableCreatureGetsAFace() {
        assertNotNull(SETTINGS.everyPortrait(),
                "dungeon.ini names no Portraits section, so only the hero has a face");
    }

    /** Which is where these are asked of: every block, the one for everybody too. */
    private static java.util.List<PortraitArt> allBlocks() {
        var all = new java.util.ArrayList<>(SETTINGS.portraits());
        if (SETTINGS.everyPortrait() != null) {
            all.add(SETTINGS.everyPortrait());
        }
        return all;
    }

    /**
     * The camera is somewhere a camera can be.
     *
     * <p>Fractions of what it is looking at, so the numbers are all small and a
     * misplaced decimal point is the likely fault — and showing 0.052 of a
     * creature is a screenful of one eyebrow.
     */
    @Test
    void theCameraStandsWhereACameraCan() {
        for (var art : allBlocks()) {
            assertTrue(art.show() >= 0.1f && art.show() <= 2f,
                    art.name() + "'s frame holds " + art.show() + " of him");
            assertTrue(art.head() >= 0f && art.head() <= 1.5f,
                    art.name() + "'s camera looks at " + art.head() + " of his height");
            assertTrue(art.fov() >= 10f && art.fov() <= 90f,
                    art.name() + "'s lens is " + art.fov() + " degrees");
        }
    }

    /**
     * And it looks at a face rather than at a forehead.
     *
     * <p>The one number a chair caught and arithmetic did not. This kit draws its
     * characters with a head nearly half their height — the head joint sits at
     * 0.55 of the model and the top of the hair at 1.0 — so a camera at 0.86 looks
     * at the middle of the forehead, and the first thing anybody saw of the
     * portrait was a fringe. The face is the upper middle of him, not the top.
     */
    @Test
    void theCameraLooksAtAFaceRatherThanAForehead() {
        for (var art : allBlocks()) {
            float topOfFrame = art.head() + art.show() / 2f;
            assertTrue(topOfFrame >= 0.9f && topOfFrame <= 1.15f,
                    art.name() + "'s frame ends at " + topOfFrame + " of him: it should stop"
                            + " just past the top of his head, not well above or below it");
        }
    }

    /** Hurt is a share of his health, and faster rather than slower. */
    @Test
    void beingHurtIsAShareOfHisHealth() {
        for (var art : allBlocks()) {
            assertTrue(art.hurtBelowPercent() > 0f && art.hurtBelowPercent() < 100f,
                    art.name() + " counts as hurt below " + art.hurtBelowPercent() + "%");
            assertTrue(art.hurtSpeed() > 0f,
                    art.name() + " breathes at " + art.hurtSpeed() + " times his own speed");
        }
    }

    /** How often it is drawn comes out of the file, not out of Java. */
    @Test
    void theRateIsTheFilesToSay() {
        assertEquals(24, SETTINGS.portraitFps(),
                "PortraitFps in Hud is what the panel is drawn at");
    }

    /**
     * A portrait describing a state is not the same as the model playing it.
     *
     * <p>The other half of the check above: the clips really go onto him, as one
     * model with all five on it, rather than being copied onto nothing. The copy
     * is what {@code AnimationLibrary} does, and it fails by arriving empty.
     */
    @Test
    void heWearsEveryClipHisPortraitPlays() {
        for (var art : SETTINGS.portraits()) {
            var him = heroNamed(art.name());
            if (!him.hasModel()) {
                continue; // his art has not arrived; there is nothing to put clips on
            }
            var model = ASSETS.loadModel(him.model());
            for (var path : him.animations()) {
                uz.duke.client3d.AnimationLibrary.copy(
                        ASSETS.loadModel(path), model, art.clips());
            }

            var composer = control(model, AnimComposer.class);
            assertNotNull(composer, art.name() + " carries no composer to put clips on");
            assertTrue(composer.getAnimClipsNames().containsAll(art.clips()),
                    art.name() + " ended up with " + composer.getAnimClipsNames()
                            + " rather than " + art.clips());
        }
    }

    /**
     * Changing the binding in the file changes what is played.
     *
     * <p>The promise the whole block is for, held from the reading end: no clip
     * name is written in Java, so a portrait rebound in the file is rebound in the
     * game. {@code uz.duke.client3d.PortraitMoodTest} holds the same promise from
     * the other side.
     */
    @Test
    void reboundInTheFileIsReboundInTheGame() {
        var rebound = DungeonSettings.parse("", """
                Hero
                  Name = Rogue
                  Model = models/heroes/ranger.glb
                  Portrait = PortraitArt
                    Calm = Idle_A
                    Fight = Melee_Unarmed_Idle
                    Dead = Death_B
                  End
                End
                """);

        var art = rebound.portraits().getFirst();
        assertEquals("Idle_A", art.calm());
        assertEquals("Melee_Unarmed_Idle", art.fight());
        assertEquals("Death_B", art.dead());
        assertEquals(PortraitArt.class, art.getClass());
    }

    /** And a block that names no clip at all is a portrait, not an empty frame. */
    @Test
    void aBlockThatNamesNoClipStillDescribesAPortrait() {
        var bare = DungeonSettings.parse("""
                World Dungeon
                  Portraits = Everyone
                    Head = 0.7
                    Show = 0.6
                  End
                End
                """);

        var art = bare.everyPortrait();
        assertNotNull(art, "the block was read but no portrait came out of it");
        assertEquals(0.7f, art.head(), 0.001f);
        assertEquals(0.6f, art.show(), 0.001f);
        // Nothing named: every creature wears its own idle and its own death, which
        // is what makes a monster's face free.
        assertNull(art.calm());
        assertNull(art.dead());
    }

    /** And a second hero is a second block, with no Java anywhere in the way. */
    @Test
    void aSecondHeroIsASecondBlock() {
        var two = DungeonSettings.parse("", """
                Hero
                  Name = Rogue
                  Model = models/heroes/ranger.glb
                  Portrait = PortraitArt
                    Calm = Ranged_Bow_Idle
                  End
                End
                Hero
                  Name = Mage
                  Model = models/heroes/mage.glb
                  Idle = Idle_A
                  Portrait = PortraitArt
                    Calm = Idle_A
                    Yaw = -30
                  End
                End
                """);

        assertEquals(2, two.heroes().size());
        assertEquals("Mage", two.heroes().get(1).name());
        assertEquals("models/heroes/mage.glb", two.heroes().get(1).model());
        assertEquals(2, two.portraits().size());
        assertEquals("Mage", two.portraits().get(1).name());
        assertEquals(-30f, two.portraits().get(1).yaw(), 0.001f);
        // And the first is untouched by the second, which is what a single unnamed
        // block could not promise: it was one set of fields, so two blocks was one
        // hero wearing whichever was read last.
        assertEquals("models/heroes/ranger.glb", two.heroes().getFirst().model());
        assertEquals("Ranged_Bow_Idle", two.portraits().getFirst().calm());
    }
}
