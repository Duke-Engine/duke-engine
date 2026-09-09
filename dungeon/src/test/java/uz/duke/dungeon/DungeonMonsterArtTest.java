package uz.duke.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.anim.AnimComposer;
import com.jme3.anim.SkinningControl;
import com.jme3.asset.AssetManager;
import com.jme3.asset.DesktopAssetManager;
import com.jme3.bounding.BoundingBox;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.Control;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.duke.client3d.AnimationLibrary;
import uz.duke.dungeon.content.DungeonSettings;

/**
 * The monsters have bodies, skins and movement — and every name in the settings
 * file resolves to something that exists.
 *
 * <p>Every failure this guards against looks the same from a chair: a monster
 * that is invisible, blank, or standing perfectly still. A misspelt path, a
 * texture the loader silently declines to bind, a clip name that is not in the
 * library, or two files built on skeletons that share no joints — none of them
 * raises anything, and all of them are indistinguishable from a rendering
 * problem. Asset loading needs no display, so all of it can be settled in a
 * build instead.
 */
class DungeonMonsterArtTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    /**
     * One loader for the whole class, as the game has one.
     *
     * <p>Not tidiness: these are big files with two-thousand-pixel skins, and a
     * fresh loader per test caches nothing and re-reads every one of them. The
     * shared loader hands out a fresh copy of a model while the mesh and texture
     * behind it are read once — which is also exactly what happens when a room
     * spawns six monsters of the same kind.
     */
    private static final AssetManager ASSETS = new DesktopAssetManager(true);

    private static AssetManager assets() {
        return ASSETS;
    }

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

    private static List<uz.duke.dungeon.content.MonsterLook> looks() {
        var all = new ArrayList<uz.duke.dungeon.content.MonsterLook>();
        for (var kind : SETTINGS.monsters()) {
            var look = SETTINGS.lookOf(kind);
            if (look.hasModel()) {
                all.add(look);
            }
        }
        return all;
    }

    /** Every kind the file describes has been given something to be drawn as. */
    @Test
    void everyMonsterHasAModel() {
        assertEquals(SETTINGS.monsters().size(), looks().size(),
                "a kind with no model falls back to a coloured capsule among modelled ones");
    }

    /** And every model named is a file that is really there. */
    @Test
    void everyModelNamedIsShipped() {
        for (var look : looks()) {
            assertNotNull(assets().loadModel(look.model()), look.model() + " is named but missing");
        }
    }

    /** As is every skin — a model whose texture is missing loads, and comes out blank. */
    @Test
    void everySkinNamedIsShipped() {
        for (var look : looks()) {
            assertNotNull(assets().loadTexture(look.texture()),
                    look.texture() + " is named but missing");
        }
    }

    /**
     * Each kind is drawn as its own thing.
     *
     * <p>A free kit ships two models and the dungeon has six kinds, so kinds share
     * models on purpose — but no two may share a model <em>and</em> a skin
     * <em>and</em> a size, or they are the same monster wearing two names and the
     * player has no way to tell what is coming.
     */
    @Test
    void noTwoKindsLookAlike() {
        var seen = new ArrayList<String>();
        for (var look : looks()) {
            var signature = look.model() + "|" + look.texture() + "|" + look.modelScale()
                    + "|" + look.tint();
            assertFalse(seen.contains(signature), "two kinds are drawn identically: " + signature);
            seen.add(signature);
        }
    }

    /** Each is scaled to something like the height its creature block claims. */
    @Test
    void eachModelIsScaledToACreatureSizedThing() {
        for (var look : looks()) {
            var model = assets().loadModel(look.model());
            model.updateModelBound();
            model.updateGeometricState();
            float height = ((BoundingBox) model.getWorldBound()).getYExtent() * 2f * look.modelScale();

            assertTrue(height > 6f && height < 30f,
                    look.model() + " at scale " + look.modelScale()
                            + " stands " + height + " units tall");
        }
    }

    // ---- animation ----

    /** The library is shipped, and it is a library: many clips, no creature of its own. */
    @Test
    void theAnimationLibraryIsShippedAndFullOfClips() {
        var library = assets().loadModel(SETTINGS.animationLibrary());
        var composer = control(library, AnimComposer.class);

        assertNotNull(composer, "the library has no animations in it at all");
        assertTrue(composer.getAnimClipsNames().size() > 10,
                "only " + composer.getAnimClipsNames().size() + " clips");
    }

    /** Every clip the settings name — defaults and per-kind overrides — is in it. */
    @Test
    void everyClipNamedIsInTheLibrary() {
        var composer = control(assets().loadModel(SETTINGS.animationLibrary()), AnimComposer.class);

        for (var look : looks()) {
            for (var clip : new String[] {look.idle(), look.walk(), look.attack()}) {
                assertNotNull(clip, "a monster was left without one of its three clips");
                assertNotNull(composer.getAnimClip(clip),
                        clip + " is named in dungeon.ini but not in the library");
            }
        }
    }

    /**
     * The monsters and the library are on the same skeleton, so the clips can
     * actually be put on them.
     *
     * <p>This is the whole bargain of buying a model kit and an animation library
     * separately, and it is invisible until it fails: a clip retargets onto
     * nothing, is added anyway, and the monster stands still.
     */
    @Test
    void everyMonsterCanWearTheLibrarysAnimations() {
        var loader = assets();
        var library = loader.loadModel(SETTINGS.animationLibrary());

        for (var look : looks()) {
            var monster = loader.loadModel(look.model());
            int copied = AnimationLibrary.copy(library, monster,
                    List.of(look.idle(), look.walk(), look.attack()));

            assertEquals(3, copied, look.model() + " took only " + copied + " of its three clips");
        }
    }

    /** And wearing them actually moves the monster's own bones. */
    @Test
    void anAnimatedMonsterReallyMoves() {
        var loader = assets();
        var library = loader.loadModel(SETTINGS.animationLibrary());
        var look = looks().get(0);
        var monster = loader.loadModel(look.model());
        AnimationLibrary.copy(library, monster, List.of(look.walk()));

        var armature = control(monster, SkinningControl.class).getArmature();
        var knee = armature.getJoint("calf_l");
        assertNotNull(knee, "the creature kit's skeleton is not the one we think it is");
        var before = knee.getLocalRotation().clone();

        var composer = control(monster, AnimComposer.class);
        composer.setCurrentAction(look.walk());
        for (int frame = 0; frame < 12; frame++) {
            composer.update(0.05f);
            monster.updateLogicalState(0.05f);
        }

        assertFalse(before.equals(knee.getLocalRotation()),
                "the clip was copied but drives nothing — its tracks point elsewhere");
    }

    /**
     * Copying a clip without retargeting it does nothing, which is why
     * {@link AnimationLibrary} exists.
     *
     * <p>Kept as a test because it is the mistake anyone would make first, and it
     * fails silently: the clip is there, it plays, and the monster does not move.
     */
    @Test
    void aClipHandedOverUnchangedWouldDriveNothing() {
        var loader = assets();
        var library = loader.loadModel(SETTINGS.animationLibrary());
        var look = looks().get(0);
        var monster = loader.loadModel(look.model());

        var borrowed = control(library, AnimComposer.class).getAnimClip(look.walk());
        var composer = control(monster, AnimComposer.class);
        composer.addAnimClip(borrowed); // the naive way

        var knee = control(monster, SkinningControl.class).getArmature().getJoint("calf_l");
        var before = knee.getLocalRotation().clone();
        composer.setCurrentAction(look.walk());
        for (int frame = 0; frame < 12; frame++) {
            composer.update(0.05f);
            monster.updateLogicalState(0.05f);
        }

        assertTrue(before.equals(knee.getLocalRotation()),
                "if this ever moves, retargeting has become unnecessary and can go");
    }
}
