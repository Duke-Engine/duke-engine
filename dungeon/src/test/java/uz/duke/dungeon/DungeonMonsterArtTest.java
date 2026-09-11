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

    /** Whether a model carries a colour map of its own, however deep it is hung. */
    private static boolean hasTexture(Spatial model) {
        if (model instanceof com.jme3.scene.Geometry geometry) {
            var material = geometry.getMaterial();
            return material != null && material.getParams().stream()
                    .anyMatch(param -> param.getValue() instanceof com.jme3.texture.Texture);
        }
        if (model instanceof Node node) {
            return node.getChildren().stream().anyMatch(DungeonMonsterArtTest::hasTexture);
        }
        return false;
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

    /**
     * Every monster is drawn in colours, whether the settings name them or not.
     *
     * <p>Two ways a creature gets its colours and both have to end somewhere real.
     * A kit that ships a skin beside the model has it named here, and a named file
     * that is missing loads to a blank creature. A kit that packs the picture into
     * the model names nothing — and then "no texture" has to mean <em>the one it
     * came with</em> rather than none, which is how a barrel once came out plain
     * white.
     *
     * <p>Written after the monsters first appeared entirely black. That has two
     * possible causes which look identical — a skin that is dark, or a material
     * the client cannot light — and each would have sent someone looking in the
     * wrong place. This settles the first for good: if a monster is ever black
     * again, it is the lighting.
     */
    @Test
    void everyMonsterIsDrawnInColoursRatherThanBlank() {
        for (var look : looks()) {
            if (look.texture() == null) {
                assertTrue(hasTexture(assets().loadModel(look.model())),
                        look.model() + " names no skin and carries none — it would render blank");
                continue;
            }
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
     * What each monster carries is shipped, and hangs on a bone it has.
     *
     * <p>A misspelt bone is a monster that quietly carries nothing, which looks
     * exactly like a weapon that failed to load — and a skeleton swinging an empty
     * fist through a clip built around a blade reads as a bug in the animation.
     */
    @Test
    void everyMonsterCarriesSomethingItCanHold() {
        for (var look : looks()) {
            var held = look.held();
            if (!held.isCarried()) {
                continue; // an empty-handed monster is allowed; it just punches
            }
            assertNotNull(assets().loadModel(held.model()),
                    held.model() + " is named but missing");
            var armature = control(assets().loadModel(look.model()), SkinningControl.class)
                    .getArmature();
            assertNotNull(armature.getJoint(held.bone()),
                    held.bone() + " is not a joint on " + look.model());
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

    /** The libraries are shipped, and they are libraries: many clips, no creature. */
    @Test
    void theAnimationLibrariesAreShippedAndFullOfClips() {
        assertFalse(SETTINGS.animationLibraries().isEmpty(),
                "the monsters were left with nowhere to take clips from");
        int clips = 0;
        for (var path : SETTINGS.animationLibraries()) {
            var composer = control(assets().loadModel(path), AnimComposer.class);
            assertNotNull(composer, path + " has no animations in it at all");
            clips += composer.getAnimClipsNames().size();
        }
        assertTrue(clips > 10, "only " + clips + " clips between them");
    }

    /** Every clip the settings name — defaults and per-kind overrides — is in one. */
    @Test
    void everyClipNamedIsInOneOfTheLibraries() {
        for (var look : looks()) {
            for (var clip : new String[] {look.idle(), look.walk(), look.attack()}) {
                assertNotNull(clip, "a monster was left without one of its three clips");
                assertTrue(inAMonsterLibrary(clip),
                        clip + " is named in dungeon.ini but is in none of the libraries");
            }
            // Optional, and off in the shipped file — but a name that is there has
            // to be a name a library answers to, or switching it on is a silence.
            if (look.hurt() != null) {
                assertTrue(inAMonsterLibrary(look.hurt()),
                        look.hurt() + " is named in dungeon.ini but is in no library");
            }
        }
    }

    /** Whether any library the monsters share carries a clip under this name. */
    private static boolean inAMonsterLibrary(String clip) {
        for (var path : SETTINGS.animationLibraries()) {
            var composer = control(assets().loadModel(path), AnimComposer.class);
            if (composer != null && composer.getAnimClip(clip) != null) {
                return true;
            }
        }
        return false;
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
    void everythingThatSwingsSwingsTheSameWay() {
        for (var kind : SETTINGS.monsters()) {
            var attack = SETTINGS.lookOf(kind).attack();
            if (attack.startsWith(RANGED)) {
                continue; // it shoots, and a shot is not a swing
            }
            assertEquals(SETTINGS.defaultAttack(), attack,
                    kind.name() + " swings differently from the rest of the dungeon");
        }
    }

    /** The clips this kit gives to things that shoot rather than reach. */
    private static final String RANGED = "Ranged_";

    /**
     * Anything drawn shooting really shoots.
     *
     * <p>The other half of the rule above, and the half that fails quietly. A
     * creature given a ranged clip and no projectile mimes: the crossbow comes up,
     * the bolt never leaves, and the damage lands out of nowhere on whatever it
     * was aimed at. Nothing raises, nothing logs, and it reads as the projectile
     * failing to load.
     */
    @Test
    void anythingDrawnShootingHasSomethingToShoot() {
        var file = uz.duke.dungeon.content.Content.read(uz.duke.dungeon.content.Content.MONSTERS)
                + uz.duke.dungeon.content.Content.read(uz.duke.dungeon.content.Content.CREATURES);
        for (var kind : SETTINGS.monsters()) {
            if (!SETTINGS.lookOf(kind).attack().startsWith(RANGED)) {
                continue;
            }
            assertTrue(launches(file, kind.name()),
                    kind.name() + " is drawn shooting but carries no Bow, so nothing leaves it");
        }
    }

    /** Whether the template of this name carries a launcher. */
    private static boolean launches(String creatureFiles, String template) {
        int at = creatureFiles.indexOf("Object " + template + "\n");
        if (at < 0) {
            return false;
        }
        int next = creatureFiles.indexOf("\nObject ", at + 1);
        var block = creatureFiles.substring(at, next < 0 ? creatureFiles.length() : next);
        return block.contains("Update = Bow ");
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
        assertNotNull(SETTINGS.deathClip(), "the monsters were left without one");
        assertTrue(inAMonsterLibrary(SETTINGS.deathClip()),
                SETTINGS.deathClip() + " is named in dungeon.ini but is in no library");

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
        for (var look : looks()) {
            var monster = loader.loadModel(look.model());
            int copied = 0;
            for (var path : SETTINGS.animationLibraries()) {
                copied += AnimationLibrary.copy(loader.loadModel(path), monster, clipsOf(look));
            }

            assertEquals(clipsOf(look).size(), copied,
                    look.model() + " took only " + copied + " of its "
                            + clipsOf(look).size() + " clips");
        }
    }

    /** And wearing them actually moves the monster's own bones. */
    @Test
    void anAnimatedMonsterReallyMoves() {
        var loader = assets();
        var look = looks().get(0);
        var monster = loader.loadModel(look.model());
        for (var path : SETTINGS.animationLibraries()) {
            AnimationLibrary.copy(loader.loadModel(path), monster, List.of(look.walk()));
        }

        var armature = control(monster, SkinningControl.class).getArmature();
        var knee = armature.getJoint("lowerleg.l");
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
        assertNotNull(hero.held().model(), "an archer with no bow is an archer miming");
        assertNotNull(assets().loadModel(hero.held().model()), hero.held().model() + " is named but missing");
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

        assertNotNull(hero.held().bone(), "the settings should say which bone holds the bow");
        assertNotNull(armature.getJoint(hero.held().bone()),
                hero.held().bone() + " is not a joint on his rig; it has "
                        + armature.getJointList().stream().map(com.jme3.anim.Joint::getName)
                                .toList());
    }

    /**
     * The bow is turned to match which side of it the string is on.
     *
     * <p>The hand bone gets a weapon into the hand and settles nothing about which
     * way round it goes: that is between the bone and the model, and this kit does
     * not lay every model out the same way — every other weapon in it runs along
     * its own {@code +Y} and the bow runs along {@code +Z}. Hung as it came, the
     * bow stood up correctly and faced the wrong way: string outward, knuckles
     * against the grip.
     *
     * <p>So the half-turn in the settings is not a preference, it is a consequence
     * of where the string is — and this is where the two are held together. Ship a
     * bow with its string on the other side and this says so rather than leaving
     * somebody to notice from a chair.
     */
    @Test
    void theBowIsTurnedToSuitWhichSideItsStringIsOn() {
        var hero = SETTINGS.hero();
        float[] middle = {Float.MAX_VALUE, -Float.MAX_VALUE};
        float[] tips = {Float.MAX_VALUE, -Float.MAX_VALUE};
        assets().loadModel(hero.held().model()).depthFirstTraversal(spatial -> {
            if (!(spatial instanceof com.jme3.scene.Geometry geometry)) {
                return;
            }
            var buffer = geometry.getMesh().getFloatBuffer(
                    com.jme3.scene.VertexBuffer.Type.Position);
            for (int i = 0; i + 2 < buffer.limit(); i += 3) {
                var into = Math.abs(buffer.get(i + 2)) > 0.8f ? tips : middle;
                into[0] = Math.min(into[0], buffer.get(i));
                into[1] = Math.max(into[1], buffer.get(i));
            }
        });

        // The grip bulges out of the middle of the bow to one side; the string is
        // the straight line the length of it on the other.
        boolean gripTowardPositiveX = middle[1] - tips[1] > Math.abs(tips[0] - middle[0]);
        assertTrue(gripTowardPositiveX || middle[0] < tips[0],
                "neither side of this bow bulges in the middle, so it has no grip to find: "
                        + "middle x " + middle[0] + ".." + middle[1]
                        + ", tips x " + tips[0] + ".." + tips[1]);
        assertEquals(gripTowardPositiveX ? 180f : 0f, hero.held().roll(), 0.001f,
                "the grip is toward " + (gripTowardPositiveX ? "+x" : "-x")
                        + " and the bone points +x at the archer, so HeldRoll is wrong");
    }

    /**
     * A creature told to glow has something on it that can.
     *
     * <p>The whole of {@code GLOW_PARTS} is a word matched against the names of a
     * model's meshes, and a word that matches nothing is the quietest kind of
     * mistake: the creature loads, is dressed, and simply does not glow. Nothing
     * raises and nothing logs, and from a chair it is indistinguishable from the
     * effect not having been written yet.
     */
    @Test
    void everyCreatureToldToGlowHasSomethingThatCan() {
        var settings = SETTINGS;
        for (var kind : settings.monsters()) {
            var look = settings.lookOf(kind);
            if (look.effect() == null || !look.hasModel()) {
                continue;
            }
            var recipe = settings.effects().stream()
                    .filter(e -> e.name().equals(look.effect())).findFirst().orElseThrow();
            if (!recipe.kinds().contains(uz.duke.client3d.Visuals.EffectVisual.GLOW_PARTS)) {
                continue;
            }
            var model = assets().loadModel(look.model());
            for (var part : recipe.parts()) {
                assertTrue(hasPartNamed(model, part),
                        kind.name() + " is told to light its " + part + ", and "
                                + look.model() + " has no mesh with that in its name");
            }
        }
    }

    /** Whether any mesh in the model carries this word in its name. */
    private static boolean hasPartNamed(Spatial model, String word) {
        var found = new boolean[1];
        model.depthFirstTraversal(spatial -> {
            if (spatial instanceof com.jme3.scene.Geometry geometry
                    && geometry.getName() != null && geometry.getName().contains(word)) {
                found[0] = true;
            }
        });
        return found[0];
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
        var arrow = SETTINGS.projectile(SETTINGS.arrowTemplate());
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
     * The hero and the monsters share their rig, and therefore their libraries.
     *
     * <p>This has said three things in its life and the change each time was the
     * art rather than the rule. It began as "the two share no joint at all, so a
     * clip cannot cross" — true of a Mixamo hero and a Quaternius bestiary. Then
     * as "a clip crosses and moves nothing", when the hero changed kits and a few
     * names happened to match. Now both come out of one pack on one rig, and the
     * fact worth holding is the plain one: the files the monsters use really do
     * move him, which is why there is one folder of clips and not two.
     */
    @Test
    void theHeroAndTheMonstersShareTheirClips() {
        var him = assets().loadModel(SETTINGS.hero().model());
        var walk = SETTINGS.lookOf(SETTINGS.monsters().get(0)).walk();
        int copied = 0;
        for (var path : SETTINGS.animationLibraries()) {
            copied += AnimationLibrary.copy(assets().loadModel(path), him, List.of(walk));
        }
        assertEquals(1, copied, walk + " did not go onto the hero at all");

        var knee = control(him, SkinningControl.class).getArmature().getJoint("lowerleg.l");
        var before = knee.getLocalRotation().clone();
        var composer = control(him, AnimComposer.class);
        composer.setCurrentAction(walk);
        for (int frame = 0; frame < 12; frame++) {
            composer.update(0.05f);
            him.updateLogicalState(0.05f);
        }

        assertFalse(before.equals(knee.getLocalRotation()),
                "the monsters' walk went onto him and moved nothing — the rigs have parted");
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
        var look = looks().get(0);
        var monster = loader.loadModel(look.model());

        com.jme3.anim.AnimClip borrowed = null;
        for (var path : SETTINGS.animationLibraries()) {
            var held = control(loader.loadModel(path), AnimComposer.class).getAnimClip(look.walk());
            if (held != null) {
                borrowed = held;
            }
        }
        assertNotNull(borrowed, look.walk() + " is in none of the libraries");
        var composer = control(monster, AnimComposer.class);
        composer.addAnimClip(borrowed); // the naive way

        var knee = control(monster, SkinningControl.class).getArmature().getJoint("lowerleg.l");
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
