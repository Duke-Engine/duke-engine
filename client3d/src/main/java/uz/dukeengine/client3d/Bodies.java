package uz.dukeengine.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * A thing's drawn body as the game's look makes it: its model, its skin, its tint, whatever it carries, and the clips
 * it was named — everything about what it <em>is</em>, none of where it stands or what it is doing.
 */
final class Bodies {

    private static final Logger LOG = Logger.getLogger(Bodies.class.getName());

    private final AssetManager assetManager;
    private final MissingAssets missing;
    private final ProjectileEffects effects;

    Bodies(AssetManager assetManager, MissingAssets missing, ProjectileEffects effects) {
        this.assetManager = assetManager;
        this.missing = missing;
        this.effects = effects;
    }

    /** A model as the file has it, undressed; null, said once as a {@code kind}, where it will not load. */
    Spatial asFiled(String path, String kind) {
        try {
            return assetManager.loadModel(path);
        } catch (RuntimeException notThere) {
            missing.warn(path, kind);
            return null;
        }
    }

    /** A fresh copy of a model, or of one named piece of it, for something thrown off; null where it will not load. */
    Spatial debrisPiece(String model, String piece) {
        var loaded = asFiled(model, "debris model");
        return loaded == null || piece == null ? loaded : partOf(loaded, piece, model);
    }

    /**
     * Lift one named mesh out of a loaded model and stand it on its own.
     *
     * <p>Taken out of its parents entirely rather than hidden among them: the
     * piece is wanted at the origin, pointing along its own axis, and it arrives
     * carrying wherever it sat on the character — in a hand, at an angle, at head
     * height. Dropping that transform is what makes an arrow an arrow rather than
     * an arrow held by an invisible archer.
     *
     * <p>A part that is not there leaves the whole model, which is visibly wrong
     * and says so in the log — better than a unit that quietly disappears.
     */
    Spatial partOf(Spatial model, String partName, String path) {
        var found = new Spatial[1];
        model.depthFirstTraversal(spatial -> {
            if (found[0] == null && spatial instanceof Geometry
                    && partName.equals(spatial.getName())) {
                found[0] = spatial;
            }
        });
        if (found[0] == null) {
            missing.warn(path + "#" + partName, "model part");
            return model;
        }
        var piece = found[0];
        piece.removeFromParent();
        piece.setLocalTransform(new com.jme3.math.Transform()); // its own origin
        var holder = new Node(partName);
        holder.attachChild(piece);
        return holder;
    }

    /**
     * A creature's body: its model, its skin, its tint, whatever it carries, and
     * the clips it was named — or {@code null} for a thing that has no model, or
     * whose model would not load.
     *
     * <p>Apart from where a unit's node is made because the hero panel's live
     * portrait wants the same body built the same way, and a second copy of this
     * paragraph would be a portrait of somebody slightly else — a different tint,
     * an unarmed archer, a model that is lit and one that is not. Everything about
     * what a creature <em>is</em> is here; where it stands and what it is doing
     * belong to whoever asked for it.
     *
     * <p>Every material is its own, never shared with another body. A skinned
     * material holds the pose of the skeleton driving it, so two things on one
     * material both stand in whichever pose was written last — which is why the
     * portrait needs a body of its own rather than the one in the world.
     *
     * @param alsoWanted clips beyond the creature's own five. A unit asks for
     *                   none; the portrait asks for the ones only it plays, and nothing else
     *                   would ever fetch them off the library
     */
    Spatial buildBody(Visuals.UnitVisual visual, java.util.Collection<String> alsoWanted) {
        return buildBody(visual, alsoWanted, visual.modelPath);
    }

    /**
     * The same, drawn from a named file rather than from the visual's plain one.
     *
     * <p>Which file is the caller's: a template may name a model for a set of conditions — hurt, in snow,
     * at night — and which of them holds is decided per thing and per frame. Everything else about the body
     * is the same whichever file it came from.
     */
    Spatial buildBody(Visuals.UnitVisual visual, java.util.Collection<String> alsoWanted, String modelPath) {
        if (modelPath == null) {
            return null;
        }
        try {
            var body = assetManager.loadModel(modelPath);
            if (visual.modelPart != null) {
                body = partOf(body, visual.modelPart, modelPath);
            }
            body.setLocalScale(visual.scale);
            body.setLocalTranslation(0, visual.yOffset, 0);
            body.setLocalRotation(new Quaternion().fromAngles(0,
                    FastMath.DEG_TO_RAD * visual.facingDegrees, 0));
            dressModel(body, visual, modelPath);
            // After being dressed, because dressing replaces every material on
            // it and would put the fire out again.
            effects.lightThePartsOf(body, visual.effect);
            putInHisHand(body, visual);
            borrowAnimations(body, visual, alsoWanted);
            return body;
        } catch (RuntimeException e) {
            missing.warn(modelPath, "model");
            return null;
        }
    }

    /**
     * Give a loaded model the colour map and tint the game asked for, on a
     * material this client can actually light.
     *
     * <p>The loader's own material is replaced rather than added to, and the
     * reason is the whole bug this fixes. jME's glTF loader builds a
     * <b>PBR</b> material, and physically based shading takes its ambient light
     * from an environment map — a light probe. This client has a sun and a flat
     * ambient and no probe, so a PBR model comes out black: not untextured, not
     * mis-scaled, simply unlit. It looks exactly like a missing texture, which is
     * what makes it worth a comment.
     *
     * <p>Plain lighting over the same colour map is what the tiles already use and
     * what these flat-shaded kits are drawn for. It carries {@code NumberOfBones}
     * and {@code BoneMatrices}, so skinning keeps working — the one thing the PBR
     * material was being kept for.
     *
     * <p>A material per geometry per unit, never shared: a skinned material holds
     * the pose of the skeleton driving it, so two monsters on one material would
     * both stand in whichever pose was written last.
     */
    void dressModel(Spatial body, Visuals.UnitVisual visual, String modelPath) {
        com.jme3.texture.Texture skin = null;
        if (visual.texturePath != null) {
            try {
                skin = assetManager.loadTexture(skinKey(visual.texturePath, visual.modelPath));
            } catch (RuntimeException e) {
                missing.warn(visual.texturePath, "texture");
            }
        }
        var tint = visual.tint == null ? ColorRGBA.White : Materials.colour(visual.tint);
        var named = skin;
        var marked = blendsOf(modelPath);
        body.depthFirstTraversal(spatial -> {
            if (spatial instanceof Geometry geometry) {
                var loaded = geometry.getMaterial();
                var blend = loaded == null || loaded.getName() == null ? null : marked.get(loaded.getName());
                var colours = named != null ? named : skinOf(loaded);
                var coloured = ownColour(loaded, colours, modelPath).mult(tint);
                // A marked light or shadow stays as unlit as the file made it; the rest is lit as it always was.
                var dressed = blend != null && ModelBlends.unlit(loaded)
                        ? unlitMaterial(colours, coloured) : creatureMaterial(colours, coloured);
                ModelBlends.carryOver(loaded, dressed, geometry, blend);
                geometry.setMaterial(dressed);
            }
        });
    }

    /**
     * The colour a dressed piece of a model is drawn in before the look's tint. Where the piece has a picture — the
     * look's, or the one it came with — the picture carries its colours and this is white, the tint laid over it alone.
     * A glTF model's piece with none is drawn in the colour its file gives its surface, {@code baseColorFactor}, which
     * jME's loader holds as {@code BaseColor} — {@code Color} on an unlit surface: kept as a picture is kept, so a silver
     * key with no texture is drawn silver, not white. White for any other file, as every piece was.
     */
    static ColorRGBA ownColour(Material loaded, com.jme3.texture.Texture colours, String modelPath) {
        if (colours != null || loaded == null || !isGltf(modelPath)) {
            return ColorRGBA.White.clone();
        }
        for (var name : new String[] {"BaseColor", "Color"}) {
            if (loaded.getParamValue(name) instanceof ColorRGBA base) {
                return base.clone();
            }
        }
        return ColorRGBA.White.clone();
    }

    private static boolean isGltf(String modelPath) {
        return modelPath != null && (modelPath.endsWith(".glb") || modelPath.endsWith(".gltf"));
    }

    /** The materials of a model file the game marked with a blend of its own — see {@link ModelBlends}. Read once. */
    private final Map<String, Map<String, ModelBlends.Blend>> blendsByModel = new HashMap<>();

    private Map<String, ModelBlends.Blend> blendsOf(String modelPath) {
        if (modelPath == null || !modelPath.endsWith(".glb")) {
            return Map.of();
        }
        return blendsByModel.computeIfAbsent(modelPath, path -> {
            var found = assetManager.locateAsset(new com.jme3.asset.AssetKey<>(path));
            if (found == null) {
                return Map.of();
            }
            try (var in = found.openStream()) {
                return ModelBlends.read(in);
            } catch (java.io.IOException | RuntimeException e) {
                missing.warn(path, "model's materials");
                return Map.of();
            }
        });
    }

    /** An unlit material over a colour map, tinted: for a marked light or shadow the file drew without lighting. */
    private Material unlitMaterial(com.jme3.texture.Texture colours, ColorRGBA colour) {
        var material = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setColor("Color", colour);
        if (colours != null) {
            material.setTexture("ColorMap", colours);
        }
        return material;
    }

    /**
     * How a skin named for a model has to be read to land where the model's own skin did.
     *
     * <p>A glTF model's UVs count down from the top of its picture and its loader reads
     * its own skin that way, where a texture loaded by name alone is turned the other way
     * up. On a kit whose colours are swatches laid out in rows that is no small error:
     * every face samples the row below the one it was painted for, and the Skeleton
     * Healer's green robe came out cream. Anything that is not glTF is read as it always
     * was.
     */
    static com.jme3.asset.TextureKey skinKey(String texturePath, String modelPath) {
        var key = new com.jme3.asset.TextureKey(texturePath, !isGltf(modelPath));
        key.setGenerateMips(true);
        return key;
    }

    /**
     * Hang the thing this unit carries on the bone that is there to carry it.
     *
     * <p>jME builds the attachment node itself and parents it under the joint, so
     * the weapon is moved by the same clip that moves the hand and there is
     * nothing to keep in step each frame. What it does <em>not</em> do is dress
     * it: a weapon loads with the same PBR material the body does, and this client
     * cannot light one, so an undressed bow is a black bow. Nor does it know which
     * way round the model goes — see {@link Visuals.UnitVisual#heldTurn}.
     *
     * <p>A bone the rig does not have is a warning rather than a failure. The unit
     * is drawn empty-handed, which is a thing you can see and think about; a
     * missing model is not worth a black screen.
     */
    private void putInHisHand(Spatial body, Visuals.UnitVisual visual) {
        if (visual.carried.isEmpty()) {
            return;
        }
        var skin = findControl(body, com.jme3.anim.SkinningControl.class);
        if (skin == null) {
            return; // nothing rigged to hang anything on
        }
        for (var one : visual.carried) {
            hang(skin, one, visual);
        }
    }

    /**
     * One carried thing, on one bone. Each failure is its own and loses only itself.
     */
    private void hang(com.jme3.anim.SkinningControl skin, Visuals.UnitVisual.Carried one,
                      Visuals.UnitVisual visual) {
        if (one.path == null || one.bone == null) {
            return;
        }
        if (skin.getArmature().getJoint(one.bone) == null) {
            missing.warn(one.path + "@" + one.bone, "bone");
            return;
        }
        Spatial held;
        try {
            held = assetManager.loadModel(one.path);
        } catch (RuntimeException e) {
            missing.warn(one.path, "model");
            return;
        }
        var tint = visual.tint == null ? ColorRGBA.White : Materials.colour(visual.tint);
        held.depthFirstTraversal(spatial -> {
            if (spatial instanceof Geometry geometry) {
                var loaded = geometry.getMaterial();
                var colours = skinOf(loaded);
                geometry.setMaterial(creatureMaterial(colours, ownColour(loaded, colours, one.path).mult(tint)));
            }
        });
        held.setLocalScale(one.scale);
        held.setLocalRotation(new Quaternion().fromAngles(
                FastMath.DEG_TO_RAD * one.pitch,
                FastMath.DEG_TO_RAD * one.yaw,
                FastMath.DEG_TO_RAD * one.roll));
        held.setLocalTranslation(one.x, one.y, one.z);
        skin.getAttachmentsNode(one.bone).attachChild(held);
    }

    /**
     * The colour map a loaded material already holds.
     *
     * <p>Creatures come with a skin beside them and the game names it; kit props
     * do not — a barrel carries its colours inside its own glTF, pointing at the
     * pack's atlas. Naming no texture therefore means <em>the one it came
     * with</em>, not <em>none</em>. Reading it as none is how a barrel and a bare
     * tree came out plain white: the right shape, lit correctly, wearing the
     * tint over nothing at all.
     */
    static com.jme3.texture.Texture skinOf(Material material) {
        if (material == null) {
            return null;
        }
        // Asked by its role first: jME lists a glTF piece's normal and emissive maps before its base colour, and a
        // piece dressed in its normal map comes out pale lavender.
        for (var role : SKIN_ROLES) {
            var param = material.getTextureParam(role);
            if (param != null && param.getTextureValue() != null) {
                return param.getTextureValue();
            }
        }
        for (var param : material.getParams()) {
            if (param.getValue() instanceof com.jme3.texture.Texture texture) {
                return texture;
            }
        }
        return null;
    }

    /**
     * How much of its own colour a creature keeps where no light reaches it --
     * which is also where a hit flash starts from and fades back to.
     */
    static final float CREATURE_AMBIENT = 0.55f;

    /** The names a material holds its base colour's picture by, in the order asked. */
    private static final String[] SKIN_ROLES = {"BaseColorMap", "DiffuseMap", "ColorMap"};

    /** Flat lighting over a kit's own colour map, tinted. */
    private Material creatureMaterial(com.jme3.texture.Texture skin, ColorRGBA colour) {
        var material = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        material.setBoolean("UseMaterialColors", true);
        material.setColor("Diffuse", colour);
        material.setColor("Ambient", colour.mult(CREATURE_AMBIENT));
        material.setColor("Specular", ColorRGBA.Black); // kit art has no highlights
        material.setFloat("Shininess", 1f);
        if (skin != null) {
            material.setTexture("DiffuseMap", skin);
        }
        return material;
    }


    /**
     * The parts of {@code body} named with {@code prefix} painted {@code owner}: the colour each was drawn with, kept
     * the first time, times the owner's — so painting again for a new owner gives that colour times theirs.
     */
    static void paintOwner(Spatial body, String prefix, ColorRGBA owner) {
        body.depthFirstTraversal(spatial -> {
            if (!(spatial instanceof Geometry geometry) || !isHouseColoured(geometry, body, prefix)) {
                return;
            }
            var material = geometry.getMaterial();
            for (var channel : java.util.List.of("Diffuse", "Ambient")) {
                // From the colour it was drawn with, kept the first time: painted again for a new owner, it is that
                // colour times theirs, not the last owner's times theirs.
                ColorRGBA base = geometry.getUserData("house." + channel);
                if (base == null && material.getParamValue(channel) instanceof ColorRGBA was) {
                    base = was.clone();
                    geometry.setUserData("house." + channel, base);
                }
                if (base != null) {
                    material.setColor(channel, base.mult(owner));
                }
            }
        });
    }

    /**
     * Whether a mesh is one of the parts marked for its owner: its own name, or the name of anything it
     * hangs under inside this body, begins with the prefix the game gave — letter case aside.
     *
     * <p>The ancestors as well as the mesh, because a loader does not always keep a name where the art put
     * it. A glTF node called {@code HOUSECOLOR01} may reach the scene as a node of that name holding a
     * geometry called something else entirely, and it is the node the artist named.
     */
    static boolean isHouseColoured(Spatial geometry, Spatial body, String prefix) {
        for (Spatial at = geometry; at != null; at = at.getParent()) {
            var name = at.getName();
            if (name != null && name.regionMatches(true, 0, prefix, 0, prefix.length())) {
                return true;
            }
            if (at == body) {
                return false; // nothing above the body is part of it
            }
        }
        return false;
    }

    /**
     * Fetch this unit's animations out of a library file built on the same
     * skeleton, and put them on the model that has none.
     *
     * <p>The library is loaded once and kept: it is a large file and every monster
     * on the floor wants clips out of it. What each monster gets is its own,
     * though — a clip's tracks point straight at the joints they drive, so a clip
     * shared between two monsters would animate whichever of them was built first
     * and leave the other standing.
     */
    private void borrowAnimations(Spatial body, Visuals.UnitVisual visual,
                                  java.util.Collection<String> alsoWanted) {
        var wanted = new java.util.ArrayList<String>();
        for (var name : new String[]{visual.idleAnim, visual.walkAnim,
                visual.attackAnim, visual.hurtAnim, visual.dieAnim}) {
            if (name != null) {
                wanted.add(name);
            }
        }
        for (var name : alsoWanted) {
            if (name != null && !wanted.contains(name)) {
                wanted.add(name);
            }
        }
        for (var name : visual.dieAnims.values()) {
            if (!wanted.contains(name)) {
                wanted.add(name);
            }
        }
        for (var picks : visual.rolePicks.values()) {
            for (var pick : picks) {
                if (!wanted.contains(pick.clip())) {
                    wanted.add(pick.clip());
                }
            }
        }
        // And whatever else the game said this one needs -- a gesture it casts
        // with, say. A clip nobody asked for by name is not copied, and then
        // nothing can play it: see Visuals.UnitVisual.alsoAnimation.
        for (var name : visual.otherAnims) {
            if (!wanted.contains(name)) {
                wanted.add(name);
            }
        }
        for (var source : visual.animations) {
            var library = animationLibraries.get(source.assetPath());
            if (library == null) {
                try {
                    library = assetManager.loadModel(source.assetPath());
                } catch (RuntimeException e) {
                    missing.warn(source.assetPath(), "animation library");
                    continue;
                }
                animationLibraries.put(source.assetPath(), library);
            }
            boolean arrived = source.clipName() == null
                    ? AnimationLibrary.copy(library, body, wanted) > 0
                    : AnimationLibrary.copySingle(library, body, source.clipName());
            if (!arrived && missing.firstTime(source.assetPath() + "#clips")) {
                var path = source.assetPath();
                LOG.warning(() -> "no animation was taken from " + path
                        + " — either it holds none, or it and the model are on"
                        + " different skeletons");
            }
        }
    }

    /**
     * Animation libraries, loaded once each and shared by everything that borrows.
     */
    private final Map<String, Spatial> animationLibraries =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** An animation library read ahead of the first thing that borrows from it — see {@link ArtLoad}. */
    void readLibrary(String path) {
        animationLibraries.computeIfAbsent(path, assetManager::loadModel);
    }

    static <T extends com.jme3.scene.control.Control> T findControl(Spatial spatial, Class<T> type) {
        var found = new Object[1];
        spatial.depthFirstTraversal(s -> {
            var control = s.getControl(type);
            if (control != null && found[0] == null) {
                found[0] = control;
            }
        });
        return type.cast(found[0]);
    }
}
