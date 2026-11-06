package uz.dukeengine.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Loads a modular kit's pieces.
 *
 * <p>Two things happen here that are easy to miss until the dungeon comes out
 * black. First, jME's glTF loader gives every piece a <b>PBR</b> material, and
 * PBR takes its ambient light from an environment map — of which this client
 * has none, only a sun and a flat ambient, so a PBR scene renders nearly
 * unlit. Kenney's kits are flat-shaded palette art anyway, so the material is
 * rebuilt as plain lighting over the same texture, which is both correct and
 * what the art was drawn for.
 *
 * <p>Second, a floor of several hundred tiles is several hundred copies of
 * three meshes. They are cloned without cloning materials, and there is
 * exactly <em>one</em> material for the whole kit: how dark a piece looks is
 * decided by where its fragments stand, in the shader, so no piece needs a
 * material of its own to be dimmer than its neighbour.
 */
final class KitTiles implements TileSource {

    private static final Logger LOG = Logger.getLogger(KitTiles.class.getName());

    private final AssetManager assetManager;
    private final MissingAssets missing;
    private final GroundMaterials looks;
    private final SceneLight light;
    /** The kit in force: the theme's, or the one the game started with. */
    private final Supplier<Tileset> activeKit;

    KitTiles(AssetManager assetManager, MissingAssets missing, GroundMaterials looks, SceneLight light,
            Supplier<Tileset> activeKit) {
        this.assetManager = assetManager;
        this.missing = missing;
        this.looks = looks;
        this.light = light;
        this.activeKit = activeKit;
    }


    private final Map<String, Spatial> masters = new HashMap<>();

    /**
     * The atlas kit's shared materials, one per picture and cast.
     *
     * <p>Per tint because a tone may ask for the same stone in a colder cast,
     * and one skin for the lot would give whichever floor was built first the
     * casting vote over every later one.
     *
     * <p>And per texture, because a kit is not always one atlas. A floor of
     * beaten earth with a line of trees standing along its edges is two packs
     * and two pictures, and one skin over both hands the trees the floor's
     * atlas — every leaf then reads its colour from whatever happens to sit at
     * those coordinates on the other sheet, which came out as a row of grey
     * mushrooms.
     */
    private final Map<String, Material> skins = new HashMap<>();

    /**
     * One ready piece, dressed the way its kit wants and holding the fog.
     *
     * <p>Kits come in two sorts and the difference is not cosmetic. One is
     * drawn on a single colour atlas: every piece is the same picture, so one
     * material serves the lot and sharing it is most of what keeps a floor of
     * six hundred tiles cheap. The other ships no texture at all and says in
     * its own materials what colour each part of each piece is; give that one
     * a single skin and the whole kit turns one flat grey.
     *
     * <p>Either way the material has to be one that reads the fog, or the
     * pieces would be lit and never darkened. So the second sort is not left
     * with what it came with — it is rebuilt, one fogged material per material
     * it brought, keeping the colour and gaining the dark.
     *
     * <p>Cached by kit and tint rather than by path alone, because the same
     * piece under two tints is two different masters and every copy of one
     * shares its materials.
     */
    @Override
    public Spatial piece(String assetPath) {
        return piece(assetPath, 0xFFFFFF);
    }

    /**
     * The same, under a colour of this piece's own on top of the kit's.
     *
     * <p>Everything the plain call does, and it costs nothing more: the masters
     * and the skins were already kept per tint, so a second tint is a second
     * entry in maps that exist. The lid over the rock and the floors of two
     * different storeys are then three materials rather than one, over a floor
     * that is several hundred pieces.
     */
    @Override
    public Spatial piece(String assetPath, int packedRgb) {
        return piece(activeKit.get(), assetPath, packedRgb);
    }

    /**
     * The same, dressed as {@code kit} dresses its pieces rather than as the kit in force — for a map whose cells
     * wear looks of their own. Kept per kit's own tint and way of dressing, so two looks sharing a model share it
     * only where they dress it alike.
     */
    @Override
    public Spatial piece(Tileset kit, String assetPath, int packedRgb) {
        return piece(kit, assetPath, packedRgb, "");
    }

    /** The same, drawn with {@code picture} in place of its own; kept apart from it by the picture's name. */
    @Override
    public Spatial piece(Tileset kit, String assetPath, int packedRgb, String picture) {
        boolean own = kit != null && kit.keepsOwnMaterials();
        int tint = blend(kit == null ? 0xFFFFFF : kit.getTint(), packedRgb);
        com.jme3.texture.Texture drawnWith = picture == null || picture.isBlank() ? null : pictureFor(picture);
        String key = assetPath + "#" + Integer.toHexString(tint) + (own ? "#own" : "")
                + (drawnWith == null ? "" : "@" + picture);
        var master = masters.get(key);
        if (master == null) {
            try {
                master = assetManager.loadModel(assetPath);
            } catch (RuntimeException e) {
                // A missing piece is a missing file, not a broken client: draw
                // the rest of the floor and say which one went missing.
                if (missing.firstTime(assetPath)) {
                    LOG.warning(() -> "tile not found: " + assetPath + " (" + e.getMessage() + ")");
                }
                return null;
            }
            if (own) {
                refogOwnMaterials(master, Materials.rgb(tint), drawnWith);
            } else {
                // One skin per picture the kit draws on, and per tint over it.
                var atlas = drawnWith != null ? drawnWith : textureOf(master);
                master.setMaterial(skins.computeIfAbsent(
                        nameOf(atlas) + "#" + Integer.toHexString(tint),
                        _ -> tileMaterial(atlas, Materials.rgb(tint))));
            }
            masters.put(key, master);
        }
        return master.clone(false); // share the mesh and the material
    }

    /**
     * Rebuild every material a model brought as one that reads the fog,
     * keeping the colour it was given and multiplying the kit's tint over it.
     */
    private void refogOwnMaterials(Spatial model, ColorRGBA tint, com.jme3.texture.Texture picture) {
        if (model instanceof Geometry geometry) {
            var was = geometry.getMaterial();
            var colour = colourOf(was).mult(tint);
            var drawn = picture != null ? picture : textureOf(geometry);
            var made = !looks.fogs()
                    ? litKitMaterial(colour, drawn)
                    : looks.fogged(colour, light.ambientColour().mult(KIT_AMBIENT), drawn);
            keepCutOut(was, made, geometry);
            geometry.setMaterial(made);
            return;
        }
        if (model instanceof Node node) {
            for (var child : node.getChildren()) {
                refogOwnMaterials(child, tint, picture);
            }
        }
    }

    /**
     * What a model's own material says about its shape beyond its colour, carried over: a cut-out picture (a leaf spray,
     * glTF's MASK), both sides drawn, and each vertex's shade where its mesh brings colours and the material reads them.
     */
    private static void keepCutOut(Material was, Material made, Geometry geometry) {
        if (was == null) {
            return;
        }
        var cutOff = was.getParam("AlphaDiscardThreshold");
        boolean fogged = made.getMaterialDef().getMaterialParam("AlphaDiscard") != null;
        if (cutOff != null) {
            made.setFloat(fogged ? "AlphaDiscard" : "AlphaDiscardThreshold", (Float) cutOff.getValue());
        }
        if (was.getAdditionalRenderState().getFaceCullMode() == com.jme3.material.RenderState.FaceCullMode.Off) {
            made.getAdditionalRenderState().setFaceCullMode(com.jme3.material.RenderState.FaceCullMode.Off);
        }
        if (fogged && geometry.getMesh().getBuffer(com.jme3.scene.VertexBuffer.Type.Color) != null) {
            made.setBoolean("VertexShade", true);
        }
    }

    /** A picture a piece is drawn with in place of its own, loaded once; null for one that will not load. */
    private com.jme3.texture.Texture pictureFor(String path) {
        try {
            return assetManager.loadTexture(path);
        } catch (RuntimeException e) {
            if (missing.firstTime(path)) {
                LOG.warning(() -> "picture not found: " + path + " (" + e.getMessage() + ")");
            }
            return null;
        }
    }

    /**
     * What colour a loaded material says its surface is; white if it says nothing.
     */
    private ColorRGBA colourOf(Material material) {
        if (material == null) {
            return ColorRGBA.White;
        }
        for (var name : new String[]{"Diffuse", "Color", "BaseColor"}) {
            var param = material.getParam(name);
            if (param != null && param.getValue() instanceof ColorRGBA colour) {
                return colour.clone();
            }
        }
        return ColorRGBA.White;
    }

    private Material litKitMaterial(ColorRGBA colour, com.jme3.texture.Texture atlas) {
        var material = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        material.setBoolean("UseMaterialColors", true);
        material.setColor("Diffuse", colour);
        material.setColor("Ambient", KIT_AMBIENT.mult(colour));
        if (atlas != null) {
            material.setTexture("DiffuseMap", atlas);
        }
        return material;
    }

    /**
     * How much of the ambient the kit's palette art returns.
     */
    private static final ColorRGBA KIT_AMBIENT = new ColorRGBA(0.55f, 0.55f, 0.62f, 1f);

    private Material tileMaterial(com.jme3.texture.Texture atlas, ColorRGBA tint) {
        if (looks.fogs()) {
            return looks.fogged(tint, light.ambientColour().mult(KIT_AMBIENT).mult(tint), atlas);
        }
        var material = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        material.setBoolean("UseMaterialColors", true);
        material.setColor("Diffuse", tint);
        material.setColor("Ambient", KIT_AMBIENT.mult(tint));
        if (atlas != null) {
            material.setTexture("DiffuseMap", atlas);
        }
        return material;
    }

    /**
     * Two packed colours multiplied, channel by channel — white leaves the other alone.
     */
    private static int blend(int over, int under) {
        if (under == 0xFFFFFF) {
            return over;
        }
        int red = (over >> 16 & 0xFF) * (under >> 16 & 0xFF) / 255;
        int green = (over >> 8 & 0xFF) * (under >> 8 & 0xFF) / 255;
        int blue = (over & 0xFF) * (under & 0xFF) / 255;
        return red << 16 | green << 8 | blue;
    }

    /**
     * What a texture is called, for keying a skin by it; untextured pieces share one name.
     */
    private String nameOf(com.jme3.texture.Texture atlas) {
        var key = atlas == null ? null : atlas.getKey();
        return key == null ? "" : key.getName();
    }

    /**
     * The kit's colour atlas, taken off whatever material the loader made.
     */
    private com.jme3.texture.Texture textureOf(Spatial model) {
        if (model instanceof Geometry geometry && geometry.getMaterial() != null) {
            for (var param : geometry.getMaterial().getParams()) {
                if (param.getValue() instanceof com.jme3.texture.Texture texture) {
                    return texture;
                }
            }
        }
        if (model instanceof Node node) {
            for (var child : node.getChildren()) {
                var found = textureOf(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
