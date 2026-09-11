package uz.duke.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

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
     * The skins are real pictures, not black squares.
     *
     * <p>Written after the monsters first appeared entirely black. That has two
     * possible causes which look identical — a skin that is dark, or a material
     * the client cannot light — and each would have sent someone looking in the
     * wrong place. This settles the first for good: if a monster is ever black
     * again, it is the lighting.
     */
    @Test
    void everySkinIsAColouredPictureRatherThanADarkOne() {
        for (var look : looks()) {
            var image = assets().loadTexture(look.texture()).getImage();
            assertTrue(image.getWidth() >= 512 && image.getHeight() >= 512,
                    look.texture() + " is only " + image.getWidth() + "x" + image.getHeight());

            var pixels = image.getData(0).duplicate();
            pixels.rewind();
            long total = 0;
            int samples = 0;
            // Every few thousand bytes, so a big skin costs a glance rather than a scan.
            for (int at = 0; at + 3 < pixels.limit(); at += 997) {
                total += (pixels.get(at) & 0xFF) + (pixels.get(at + 1) & 0xFF)
                        + (pixels.get(at + 2) & 0xFF);
                samples += 3;
            }
            float brightness = total / (float) samples;
            assertTrue(brightness > 30f,
                    look.texture() + " averages " + brightness + "/255 — it really is a dark image");
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
            // Optional, and off in the shipped file — but a name that is there has
            // to be a name the library answers to, or switching it on is a silence.
            if (look.hurt() != null) {
                assertNotNull(composer.getAnimClip(look.hurt()),
                        look.hurt() + " is named in dungeon.ini but not in the library");
            }
        }
    }

    /** The clips a creature actually asked for, in the order the client wants them. */
    private static List<String> clipsOf(uz.duke.dungeon.content.MonsterLook look) {
        var named = new ArrayList<String>();
        for (var clip : new String[] {look.idle(), look.walk(), look.attack(), look.hurt()}) {
            if (clip != null) {
                named.add(clip);
            }
        }
        return named;
    }

    /**
     * Everything down here strikes the same way.
     *
     * <p>A deliberate choice rather than an accident of the defaults. The library
     * has a jab and a cross as well, and the small monsters had them — the jab is
     * a twitch, and a twitch does not read as a blow however much health it takes
     * off. One recognisable overhead swing, from the smallest to the boss, is what
     * makes a hit look like a hit, and a creature block quietly taking one of the
     * others back would undo it without showing up as anything but a diff.
     */
    @Test
    void everythingStrikesTheSameWay() {
        for (var kind : SETTINGS.monsters()) {
            assertEquals(SETTINGS.defaultAttack(), SETTINGS.lookOf(kind).attack(),
                    kind.name() + " swings differently from the rest of the dungeon");
        }
    }

    /**
     * The flinch is off, and off is a decision.
     *
     * <p>A blow struck and a blow taken are the same one-shot channel, so a
     * monster shot mid-swing drops the swing: hitting one reads as cancelling its
     * attack, which is a strange thing to hand out for nothing. It comes back as
     * something bought. Until then the file names none, and this is here so that
     * switching it on is a decision too rather than a merge.
     */
    @Test
    void nothingFlinchesYet() {
        for (var kind : SETTINGS.monsters()) {
            assertNull(SETTINGS.lookOf(kind).hurt(),
                    kind.name() + " has a flinch again — see the note in dungeon.ini");
        }
    }

    /**
     * Everything down here has something to play as it falls.
     *
     * <p>A body that blinks out of existence the instant it dies reads as a bug
     * even when it is not one, so the client keeps it a moment and plays this. A
     * clip name that is not in the library would put that back the way it was,
     * silently.
     */
    @Test
    void everythingHasADeathToPlay() {
        var composer = control(assets().loadModel(SETTINGS.animationLibrary()), AnimComposer.class);

        assertNotNull(SETTINGS.deathClip(), "the monsters were left without one");
        assertNotNull(composer.getAnimClip(SETTINGS.deathClip()),
                SETTINGS.deathClip() + " is named in dungeon.ini but not in the library");

        var hero = SETTINGS.hero();
        assertNotNull(hero.death(), "and so was the hero");
        assertTrue(inOneOfHisLibraries(hero.death()),
                hero.death() + " is named for the hero but is in none of his libraries");
    }

    /** Whether any library the hero names carries a clip under this name. */
    private static boolean inOneOfHisLibraries(String clip) {
        for (var path : SETTINGS.hero().animations()) {
            var composer = control(assets().loadModel(path), AnimComposer.class);
            if (composer != null && composer.getAnimClip(clip) != null) {
                return true;
            }
        }
        return false;
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
                    clipsOf(look));

            assertEquals(clipsOf(look).size(), copied,
                    look.model() + " took only " + copied + " of its "
                            + clipsOf(look).size() + " clips");
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

    // ---- the hero ----

    /**
     * The hero's model, everything he carries, and every library he draws his
     * movement from are all shipped and all load.
     *
     * <p>He comes from a different kit on a different skeleton, so none of what
     * holds for the monsters holds for him and all of it is worth asking again.
     */
    @Test
    void theHeroAndEverythingHeCarriesAreShipped() {
        var hero = SETTINGS.hero();
        assertTrue(hero.hasModel(), "the settings file should give the hero a model");

        assertNotNull(assets().loadModel(hero.model()));
        assertNotNull(hero.holds(), "an archer with no bow is an archer miming");
        assertNotNull(assets().loadModel(hero.holds()), hero.holds() + " is named but missing");
        assertFalse(hero.animations().isEmpty(), "he was left with nowhere to take clips from");
        for (var path : hero.animations()) {
            assertNotNull(assets().loadModel(path), path + " is named but missing");
        }
    }

    /**
     * The bone his bow hangs on is one his own rig actually has.
     *
     * <p>A character kit ships weapons apart from characters and rigs a bone to
     * hang them on — and a misspelt bone name is a hero who quietly carries
     * nothing, which looks exactly like a hero whose bow failed to load.
     */
    @Test
    void theBoneHisBowHangsOnIsOneHeHas() {
        var hero = SETTINGS.hero();
        var armature = control(assets().loadModel(hero.model()), SkinningControl.class)
                .getArmature();

        assertNotNull(hero.heldIn(), "the settings should say which bone holds the bow");
        assertNotNull(armature.getJoint(hero.heldIn()),
                hero.heldIn() + " is not a joint on his rig; it has "
                        + armature.getJointList().stream().map(com.jme3.anim.Joint::getName)
                                .toList());
    }

    /** Every clip he asks for is in one of the libraries he names. */
    @Test
    void everyClipTheHeroAsksForIsInOneOfHisLibraries() {
        var hero = SETTINGS.hero();
        assertEquals(5, hero.clips().size(),
                "idle, walk, attack, hurt and death — he was left short of " + hero.clips());
        for (var clip : hero.clips()) {
            assertTrue(inOneOfHisLibraries(clip),
                    clip + " is asked for but is in none of " + hero.animations());
        }
    }

    /** And every one of them really goes onto him. */
    @Test
    void theHeroWearsEveryClipHeAsksFor() {
        var hero = SETTINGS.hero();
        var him = assets().loadModel(hero.model());

        for (var path : hero.animations()) {
            AnimationLibrary.copy(assets().loadModel(path), him, hero.clips());
        }

        var composer = control(him, AnimComposer.class);
        assertNotNull(composer, "he carries no composer to put clips on");
        assertTrue(composer.getAnimClipsNames().containsAll(hero.clips()),
                "he ended up with " + composer.getAnimClipsNames());
    }

    /** And running actually moves him, rather than being copied onto nothing. */
    @Test
    void theHeroReallyRuns() {
        var hero = SETTINGS.hero();
        var him = assets().loadModel(hero.model());
        for (var path : hero.animations()) {
            AnimationLibrary.copy(assets().loadModel(path), him, hero.clips());
        }

        var armature = control(him, SkinningControl.class).getArmature();
        var knee = armature.getJoint("lowerleg.l");
        assertNotNull(knee, "his skeleton is not the one the animations were built on");
        var before = knee.getLocalRotation().clone();

        var composer = control(him, AnimComposer.class);
        composer.setCurrentAction(hero.walk());
        for (int frame = 0; frame < 12; frame++) {
            composer.update(0.05f);
            him.updateLogicalState(0.05f);
        }

        assertFalse(before.equals(knee.getLocalRotation()), "he is standing still");
    }

    /**
     * A clip that walks the character forward is held in place.
     *
     * <p>The hero's run used to be authored travelling — three units of it,
     * twenty once scaled — because in engines where animation drives movement
     * that is how a run is made. Here the simulation owns the position, so the
     * mesh simply drew itself further and further from its own unit: he walked
     * out of his selection ring, and his health bar stayed where he had been.
     * Nothing about that looks like an animation setting.
     *
     * <p>The kit he comes from now authors its clips on the spot, so there is
     * nothing left for the stripping to take off — which is not a reason to stop
     * asking. The invariant is the same whichever kit is shipped, and the next one
     * may well travel.
     *
     * <p>The stride's rise and fall is kept, since that happens on the spot.
     */
    @Test
    void theHerosRunDoesNotCarryHimOutOfHisOwnUnit() {
        var hero = SETTINGS.hero();
        var him = assets().loadModel(hero.model());
        for (var path : hero.animations()) {
            AnimationLibrary.copy(assets().loadModel(path), him, hero.clips());
        }

        var clip = control(him, AnimComposer.class).getAnimClip(hero.walk());
        var armature = control(him, SkinningControl.class).getArmature();
        boolean sawTheRoot = false;
        for (com.jme3.anim.AnimTrack<?> track : clip.getTracks()) {
            if (!(track instanceof com.jme3.anim.TransformTrack transform)
                    || !(transform.getTarget() instanceof com.jme3.anim.Joint joint)
                    || joint.getParent() != null
                    || transform.getTranslations() == null) {
                continue;
            }
            sawTheRoot = true;
            float travel = 0f;
            for (var step : transform.getTranslations()) {
                travel = Math.max(travel, Math.abs(step.x) + Math.abs(step.z));
            }
            assertTrue(travel < 0.001f, joint.getName() + " still travels " + travel);
        }
        assertTrue(sawTheRoot, "the root joint has no translation to check — did the rig change?");
    }

    /**
     * The arrow really is arrow-shaped.
     *
     * <p>It was once a mesh cut out of the hero — a 24MB file fetched for one
     * shaft, under a name the exporter had shuffled, so that the mesh called
     * {@code Eyes} was the arrow and the one called {@code Arrow} was his clothes.
     * The kit ships an arrow now, and it is its own file.
     *
     * <p>Still measured rather than trusted: whatever the settings name has to be
     * long, thin and cheap, because it is drawn by the dozen and flies point
     * first.
     */
    @Test
    void theArrowIsLongThinAndCheap() {
        var arrow = SETTINGS.arrowLook();
        assertTrue(arrow.hasModel(), "the settings should name a model for the arrow");

        var model = assets().loadModel(arrow.model());
        var found = new ArrayList<com.jme3.scene.Geometry>();
        model.depthFirstTraversal(spatial -> {
            if (spatial instanceof com.jme3.scene.Geometry geometry) {
                found.add(geometry);
            }
        });
        assertEquals(1, found.size(), arrow.model() + " holds " + found.size() + " meshes");

        found.get(0).updateModelBound();
        var box = (BoundingBox) found.get(0).getModelBound();
        float length = box.getZExtent() * 2f;
        float thickness = Math.max(box.getXExtent(), box.getYExtent()) * 2f;

        assertTrue(length > 0.4f, "an arrow should be long, but this is " + length);
        assertTrue(thickness < length / 5f,
                "and thin, but this is " + thickness + " across against " + length + " long");
        assertTrue(found.get(0).getMesh().getTriangleCount() < 500,
                "a projectile drawn by the dozen should be cheap, not "
                        + found.get(0).getMesh().getTriangleCount() + " triangles");
    }

    /**
     * The hero's own libraries are the ones that move him.
     *
     * <p>This used to say the opposite way round — that the monsters' library
     * could not touch him at all, because his rig and theirs shared no joint
     * names. The kit he comes from now happens to share a few, so a clip copies
     * across; what it does not do is move him. That is the fact worth holding,
     * and it is the one that matters: a clip can be added and drive nothing, which
     * is a hero standing perfectly still with a full list of animations.
     */
    @Test
    void theMonstersLibraryDoesNotMoveTheHero() {
        var him = assets().loadModel(SETTINGS.hero().model());
        AnimationLibrary.copy(assets().loadModel(SETTINGS.animationLibrary()), him,
                List.of("Walk_Loop"));
        var composer = control(him, AnimComposer.class);
        if (composer == null || composer.getAnimClip("Walk_Loop") == null) {
            return; // nothing crossed at all, which is the older and simpler answer
        }

        var knee = control(him, SkinningControl.class).getArmature().getJoint("lowerleg.l");
        var before = knee.getLocalRotation().clone();
        composer.setCurrentAction("Walk_Loop");
        for (int frame = 0; frame < 12; frame++) {
            composer.update(0.05f);
            him.updateLogicalState(0.05f);
        }

        assertEquals(before, knee.getLocalRotation(),
                "the monsters' library moved his legs, so he could share it");
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
