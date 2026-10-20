package uz.dukeengine.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Logger;

import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.view.WorldSnapshot;
import uz.dukeengine.game.DukeGame;

/**
 * How the ground and what lies on it are drawn: its materials, painted, blended and fogged; the water's, the scorches'
 * and the roads'; and every material that reads the fog kept told where the fog holds, the haze round the camera, the
 * burning lights and the pictures slid over all of it.
 */
final class GroundLooks {

    private static final Logger LOG = Logger.getLogger(GroundLooks.class.getName());

    private final AssetManager assetManager;
    private final Visuals visuals;
    private final SceneLight light;
    /** The dark, as a picture the materials read by world position; null for a game that is never discovered. */
    private final FogMap fogMap;
    private final CameraFocus camera;
    private final Supplier<DukeGame> game;
    /** The grid the ground was last built from. */
    private final Supplier<PathGrid> builtFrom;
    private final Supplier<WorldSnapshot> snapshot;

    /**
     * Every material that samples the fog. Kept because the one thing in them that
     * changes with the world is its size, and a new floor is a different size.
     */
    private final List<Material> fogged = new ArrayList<>();
    private final java.util.Map<String, Material> scorchLooks = new java.util.HashMap<>();
    private final java.util.Set<String> missingScorches = new java.util.HashSet<>();

    GroundLooks(AssetManager assetManager, Visuals visuals, SceneLight light, FogMap fogMap, CameraFocus camera,
            Supplier<DukeGame> game, Supplier<PathGrid> builtFrom, Supplier<WorldSnapshot> snapshot) {
        this.assetManager = assetManager;
        this.visuals = visuals;
        this.light = light;
        this.fogMap = fogMap;
        this.camera = camera;
        this.game = game;
        this.builtFrom = builtFrom;
        this.snapshot = snapshot;
    }

    /** Whether there is a fog for the materials to read. */
    boolean fogs() {
        return fogMap != null;
    }

    /** A picture drawn flat in the world by the game's look: an unshaded material, or null where it will not load. */
    Material flatPicture(String path) {
        var picture = path == null ? null : groundTexture(path);
        return picture == null ? null : Materials.picture(assetManager, picture);
    }

    /** A material made elsewhere of a fogged one kept up with the fog as the client's own are. */
    void adoptFogged(Material material) {
        var def = material.getMaterialDef().getAssetName();
        if (def != null && def.endsWith("FoggedTerrain.j3md") && !fogged.contains(material)) {
            fogged.add(material);
            tellWhereTheFogHolds(material);
            if (fogMap != null) {
                material.setVector2("FogSize", fogMap.worldSize());
            }
        }
    }

    /**
     * Hand the burning things to the terrain shader.
     *
     * <p>The terrain is the one surface in the scene that does not read jME's
     * light list — it carries its own sun and its own ambient, because for most of
     * its life those were the only lights there were and a shader that spoke the
     * whole lighting protocol would have been a copy of it for a scene with one
     * light in it. That was a good trade until something burning flew down a
     * corridor: creatures lit up, drawn as they are with the engine's lighting,
     * and the floor under them did not.
     *
     * <p>So the lights go across as four pairs of vectors, every frame, to every
     * material built from that shader. An unused slot goes across black, which
     * costs the arithmetic and contributes nothing — cheaper than a branch, and
     * the same cost every frame, which is the property worth having.
     */
    void carryTheLightsToTheStone(List<com.jme3.light.PointLight> lights) {
        if (fogged.isEmpty()) {
            return;
        }
        for (int i = 0; i < TERRAIN_LIGHTS; i++) {
            var lamp = i < lights.size() ? lights.get(i) : null;
            var colour = lamp == null ? ColorRGBA.BlackNoAlpha : lamp.getColor();
            var at = lamp == null ? Vector3f.ZERO : lamp.getPosition();
            float radius = lamp == null ? 0f : lamp.getRadius();
            // Written into the slots the materials already hold rather than
            // replaced, so a material made before this frame is looking at the
            // same two arrays as one made after it.
            terrainLightColours[i].set(colour.r, colour.g, colour.b, 1f);
            terrainLightPlaces[i].set(at.x, at.y, at.z, radius <= 0f ? 0f : 1f / radius);
        }
        for (var material : fogged) {
            material.setParam("PointLightColours",
                    com.jme3.shader.VarType.Vector4Array, terrainLightColours);
            material.setParam("PointLightPositions",
                    com.jme3.shader.VarType.Vector4Array, terrainLightPlaces);
        }
    }

    /**
     * As many as the terrain shader declares; see {@code FoggedTerrain.frag}.
     */
    private static final int TERRAIN_LIGHTS = 8;
    /**
     * The two arrays handed to every terrain material, filled with darkness to
     * begin with.
     *
     * <p>Filled rather than empty, and set on a material the moment it is made:
     * an array uniform the shader reads and nothing ever wrote is a different
     * thing on every driver, and the one frame it would go wrong on is the first.
     */
    private final com.jme3.math.Vector4f[] terrainLightColours = darkness();
    private final com.jme3.math.Vector4f[] terrainLightPlaces = darkness();

    private static com.jme3.math.Vector4f[] darkness() {
        var slots = new com.jme3.math.Vector4f[TERRAIN_LIGHTS];
        java.util.Arrays.setAll(slots, i -> new com.jme3.math.Vector4f());
        return slots;
    }

    /** The water's materials for the world drawn now, which read the fog as the ground's do. */
    private final List<Material> waterLooks = new ArrayList<>();

    /** A new world: its scorches are each made for its fog. */
    void forgetScorches() {
        scorchLooks.clear();
    }

    /** A new world: its water makes its own. */
    void forgetWater() {
        waterLooks.clear();
    }

    /**
     * The one thing in a fogged material that a new world changes: how much ground its picture holds, and where.
     * The water's too, made before the picture took this world's size.
     */
    void fogResized() {
        for (var material : fogged) {
            material.setVector2("FogSize", fogMap.worldSize());
        }
        for (var material : waterLooks) {
            material.setVector2("FogSize", fogMap.worldSize());
        }
    }

    /** Every material that reads the fog told where its picture holds now: the ground's, the scorches' and the water's. */
    void tellEveryFogReaderWhereTheFogHolds() {
        forEveryFogReader(this::tellWhereTheFogHolds);
    }

    /** Each material that reads the fog: the ground's, the scorches' and the water's. */
    private void forEveryFogReader(java.util.function.Consumer<Material> told) {
        for (var material : fogged) {
            told.accept(material);
        }
        for (var material : scorchLooks.values()) {
            if (material != null) {
                told.accept(material);
            }
        }
        for (var material : waterLooks) {
            told.accept(material);
        }
    }

    /**
     * Where the fog's picture holds, told to one material that reads it where the picture is of a window round the
     * camera — past it, the shader draws never seen — and nothing where it is of the whole map, as it always was; and
     * the haze, where the game keeps one.
     */
    private void tellWhereTheFogHolds(Material material) {
        if (fogMap != null && fogMap.isWindowed()) {
            material.setVector4("FogWindow", fogMap.window());
        } else if (material.getParam("FogWindow") != null) {
            material.clearParam("FogWindow");
        }
        tellTheHaze(material);
    }

    /** Where the camera looks, as the haze round it was last told to the ground. */
    private float hazeToldX = Float.NaN;
    private float hazeToldZ = Float.NaN;

    /** The haze round where the camera looks told to one material that reads the fog, where the game keeps a haze. */
    private void tellTheHaze(Material material) {
        if (!visuals.hazes(game.get().getMapRecord()) || builtFrom.get() == null) {
            // A material kept from a hazy map drawn on one without.
            if (material.getParam("Haze") != null) {
                material.clearParam("Haze");
            }
            return;
        }
        float cell = builtFrom.get().getCellSize();
        material.setVector4("Haze", new com.jme3.math.Vector4f(camera.targetX(), camera.targetZ(),
                visuals.getHazeFrom() * cell, visuals.getHazeTo() * cell));
    }

    /** The haze kept round where the camera looks, told to the ground each time the camera moves. */
    void hazeRoundTheCamera() {
        if (!visuals.hazes(game.get().getMapRecord()) || fogMap == null || builtFrom.get() == null) {
            return;
        }
        if (camera.targetX() == hazeToldX && camera.targetZ() == hazeToldZ) {
            return;
        }
        hazeToldX = camera.targetX();
        hazeToldZ = camera.targetZ();
        forEveryFogReader(this::tellTheHaze);
    }

    /**
     * How a piece laid along the ground is drawn: the ground's own material over its picture — lit and shrouded as the
     * ground — blended by the picture's alpha over it, as the reference draws its roads. Null where it will not load.
     */
    Material stripLook(String path) {
        if (groundTexture(path) == null) {
            return null;
        }
        var look = ground(ColorRGBA.White, path);
        var state = look.getAdditionalRenderState();
        state.setBlendMode(com.jme3.material.RenderState.BlendMode.Alpha);
        state.setDepthWrite(false);
        state.setPolyOffset(-1f, -1f);
        return look;
    }

    /**
     * How a mark burnt into the ground is drawn: its picture times the reference's one colour for every scorch — the
     * ground's ambient and half its sun ({@code updateScorches}) — blended by its alpha over the ground, and shrouded
     * as the ground is where the game keeps a fog. Null, said once, where the picture will not load.
     */
    Material scorchLook(String path) {
        if (scorchLooks.containsKey(path)) {
            return scorchLooks.get(path);
        }
        Material look = null;
        try {
            var picture = assetManager.loadTexture(path);
            picture.setWrap(com.jme3.texture.Texture.WrapMode.EdgeClamp);
            var shade = light.ambientColour().add(light.sunColour().mult(0.5f));
            shade.a = 1f;
            if (fogMap == null) {
                look = Materials.unshaded(assetManager, shade);
                look.setTexture("ColorMap", picture);
            } else {
                look = new Material(assetManager, "MatDefs/duke/FoggedTerrain.j3md");
                look.setColor("Color", ColorRGBA.White);
                look.setColor("Ambient", shade);
                look.setColor("Sun", ColorRGBA.Black);
                look.setVector3("SunDirection", light.sunDirection());
                look.setTexture("FogMap", fogMap.texture());
                look.setVector2("FogSize", fogMap.worldSize());
                tellWhereTheFogHolds(look);
                look.setTexture("ColorMap", picture);
                // Set, and dark: nothing burning lights a scorch, and an array never written differs by driver.
                look.setParam("PointLightColours", com.jme3.shader.VarType.Vector4Array, darkness());
                look.setParam("PointLightPositions", com.jme3.shader.VarType.Vector4Array, darkness());
            }
            var state = look.getAdditionalRenderState();
            state.setBlendMode(com.jme3.material.RenderState.BlendMode.Alpha);
            state.setDepthWrite(false);
            state.setPolyOffset(-1f, -1f);
        } catch (RuntimeException notThere) {
            if (missingScorches.add(path)) {
                LOG.warning(() -> "a scorch mark names a picture that will not load: " + path);
            }
        }
        scorchLooks.put(path, look);
        return look;
    }

    /**
     * A material for a piece of ground: the colour it is painted, and the picture the map named for it.
     *
     * <p>The picture is loaded by the name the map wrote and nothing else — no folder put in front of it,
     * no suffix taken off. Where it will not load, that is said once and the ground is drawn in its colour,
     * because a map with a typo in one palette entry should be a map with one grey field in it rather than
     * a black screen.
     *
     * <p>Set to repeat, because the ground's texture coordinates run across the world rather than 0..1
     * inside a cell — see {@code TerrainScene.paintGround}. Clamped, one cell would show the whole picture
     * and every cell after it the picture's last column, which is a map drawn in stripes.
     */
    private Material ground(ColorRGBA colour, String texture) {
        if (texture != null) {
            colour = tinted(colour, visuals.getSurface(texture));
        }
        var picture = texture == null ? null : groundTexture(texture);
        if (picture == null) {
            return foggedGround() ? plainFogged(colour, null) : Materials.lit(assetManager, colour);
        }
        if (!foggedGround()) {
            var material = Materials.lit(assetManager, colour);
            material.setTexture("DiffuseMap", picture);
            return material;
        }
        return plainFogged(colour, picture);
    }

    /**
     * Whether the ground is drawn with its own shader: where there is a fog to lay over it, and where the game lights
     * it by lights of its own or lays a picture over all of it, which only that shader draws.
     */
    private boolean foggedGround() {
        return fogMap != null || visuals.getGroundLight() != null || !visuals.getGroundShades().isEmpty();
    }

    /**
     * A picture laid over the ground and faded into it by the alpha each vertex carries.
     *
     * <p>The ground's own material with three things changed: it reads the vertex alpha, it blends over
     * what is under it, and it neither writes depth nor loses to the ground it lies exactly on — pulled a
     * hair toward the eye instead. It is only ever asked for by a map with a second layer, so the ground of
     * every other map is built exactly as it was.
     */
    private Material groundOverlay(ColorRGBA colour, String texture) {
        var material = ground(colour, texture);
        if (foggedGround()) {
            material.setBoolean("VertexAlpha", true);
        } else {
            material.setBoolean("UseVertexColor", true);
        }
        var state = material.getAdditionalRenderState();
        state.setBlendMode(com.jme3.material.RenderState.BlendMode.Alpha);
        state.setDepthWrite(false);
        state.setPolyOffset(-1f, -1f);
        return material;
    }

    /** The ground's materials, both kinds — see {@link Surfaces}. */
    final Surfaces surfaces = new Surfaces() {
        @Override
        public Material of(ColorRGBA colour, String texture) {
            return ground(colour, texture);
        }

        @Override
        public Material overlay(ColorRGBA colour, String texture) {
            return groundOverlay(colour, texture);
        }

        @Override
        public Material blended(List<GroundPaint.Surface> surfaces, float[] spans) {
            return blendedGround(surfaces, spans);
        }
    };

    /** A surface's colour times the tint the game gave its picture ({@link Visuals#surface}). */
    static ColorRGBA tinted(ColorRGBA colour, Visuals.SurfaceLook look) {
        int tint = look.tint();
        return colour.mult(new ColorRGBA((tint >> 16 & 0xFF) / 255f, (tint >> 8 & 0xFF) / 255f, (tint & 0xFF) / 255f,
                1f));
    }

    /** A white picture of one texel, for a blended ground's surfaces that are colours or are not used. */
    private com.jme3.texture.Texture white;

    private com.jme3.texture.Texture white() {
        if (white == null) {
            var image = new com.jme3.texture.Image(com.jme3.texture.Image.Format.RGBA8, 1, 1,
                    com.jme3.util.BufferUtils.createByteBuffer(new byte[] {-1, -1, -1, -1}),
                    com.jme3.texture.image.ColorSpace.Linear);
            white = new com.jme3.texture.Texture2D(image);
            white.setWrap(com.jme3.texture.Texture.WrapMode.Repeat);
        }
        return white;
    }

    /**
     * The material of ground blended from its paint: the ground's own fogged shader, its blended branch, each surface's
     * picture — white for a colour — laid across the world at its span and multiplied by its colour; the game's noise
     * and sharpness for the borders ({@link Visuals#paintBlend}).
     */
    private Material blendedGround(List<GroundPaint.Surface> surfaces, float[] spans) {
        var material = plainFogged(ColorRGBA.White, null);
        material.setBoolean("Blended", true);
        var scales = new float[TerrainScene.BLEND_SLOTS];
        var varied = new float[TerrainScene.BLEND_SLOTS];
        var tints = new com.jme3.math.Vector4f[TerrainScene.BLEND_SLOTS];
        for (int slot = 0; slot < TerrainScene.BLEND_SLOTS; slot++) {
            var surface = slot < surfaces.size() ? surfaces.get(slot) : null;
            var picture = surface == null || surface.texture() == null ? null : groundTexture(surface.texture());
            material.setTexture("Surface" + slot, picture == null ? white() : picture);
            var look = surface == null || surface.texture() == null ? Visuals.SurfaceLook.PLAIN
                    : visuals.getSurface(surface.texture());
            var colour = surface == null ? ColorRGBA.Black : tinted(surface.colour(), look);
            varied[slot] = look.variation();
            tints[slot] = new com.jme3.math.Vector4f(colour.r, colour.g, colour.b, 1f);
            scales[slot] = slot < spans.length && spans[slot] > 0f ? 1f / spans[slot] : 1f;
        }
        material.setVector4("SurfaceScale0", new com.jme3.math.Vector4f(scales[0], scales[1], scales[2], scales[3]));
        material.setVector4("SurfaceScale1", new com.jme3.math.Vector4f(scales[4], scales[5], scales[6], scales[7]));
        material.setParam("SurfaceTints", com.jme3.shader.VarType.Vector4Array, tints);
        var blend = visuals.getPaintBlend() == null ? Visuals.PaintBlend.SMOOTH : visuals.getPaintBlend();
        material.setFloat("BlendSharp", blend.sharpness());
        float cell = builtFrom.get() == null ? uz.dukeengine.core.pathfind.PathGrid.DEFAULT_CELL_SIZE
                : builtFrom.get().getCellSize();
        var variation = blend.variation() == null ? null : groundTexture(blend.variation());
        if (variation != null) {
            material.setTexture("Variation", variation);
            material.setFloat("VariationScale", 1f / (blend.variationCoverage() * cell));
            material.setVector4("SurfaceVariation0",
                    new com.jme3.math.Vector4f(varied[0], varied[1], varied[2], varied[3]));
            material.setVector4("SurfaceVariation1",
                    new com.jme3.math.Vector4f(varied[4], varied[5], varied[6], varied[7]));
        }
        var cliffs = visuals.getPaintCliffs();
        var rock = cliffs == null ? null : groundTexture(cliffs.picture());
        if (rock != null) {
            material.setTexture("Cliff", rock);
            material.setFloat("CliffScale", 1f / (cliffs.coverage() * cell));
            material.setFloat("CliffSlope",
                    com.jme3.math.FastMath.cos(cliffs.slope() * com.jme3.math.FastMath.DEG_TO_RAD));
        }
        var noise = blend.noise() == null ? null : groundTexture(blend.noise());
        if (noise != null) {
            material.setTexture("BlendNoise", noise);
            material.setFloat("NoiseScale", 1f / (blend.noiseCoverage() * cell));
            material.setFloat("BlendRagged", blend.raggedness());
        }
        return material;
    }

    private final java.util.Map<String, com.jme3.texture.Texture> groundTextures =
            new java.util.HashMap<>();

    /** How sharply the ground's pictures are drawn at a slant: anisotropic filtering, as far as the card allows. */
    static final int GROUND_ANISOTROPY = 8;

    /**
     * A ground picture set to repeat, mipmapped and filtered at a slant as a model's skin is — ground seen far off
     * under a tilted camera otherwise shimmers, every texel of a distant field fighting for one pixel.
     */
    static com.jme3.texture.Texture forTheGround(com.jme3.texture.Texture texture) {
        texture.setWrap(com.jme3.texture.Texture.WrapMode.Repeat);
        texture.setMinFilter(com.jme3.texture.Texture.MinFilter.Trilinear);
        texture.setAnisotropicFilter(GROUND_ANISOTROPY);
        return texture;
    }

    /** The picture a palette entry names, loaded once and set to repeat, or null with a word about why. */
    com.jme3.texture.Texture groundTexture(String path) {
        return groundTextures.computeIfAbsent(path, named -> {
            try {
                return forTheGround(assetManager.loadTexture(named));
            } catch (RuntimeException notThere) {
                LOG.log(java.util.logging.Level.WARNING,
                        "the ground names a picture that will not load: {0} ({1})",
                        new Object[] {named, notThere.getMessage()});
                return null;
            }
        });
    }

    /**
     * How much of the ambient plain terrain returns — what {@link Materials#lit} asks for.
     */
    private static final float PLAIN_AMBIENT = 0.7f;

    /** Fogged ground, returning as much of the ambient as plain terrain does. */
    Material plainFogged(ColorRGBA colour, com.jme3.texture.Texture picture) {
        return fogged(colour, light.ambientColour().mult(PLAIN_AMBIENT), picture);
    }

    /**
     * Terrain that reads the dark out of {@link FogMap} at the place each fragment
     * stands.
     *
     * <p>The lighting is written out here rather than handed to the engine's
     * because the shader is only ever used on terrain and the terrain is lit by
     * exactly one sun and one flat ambient. Passing them as material parameters is
     * a few lines; a shader that spoke the engine's light protocol would be a copy
     * of its whole lighting pipeline, for a scene that has one light in it.
     *
     * @param ambient how much of the ambient the surface returns, already
     *                multiplied by the ambient light itself
     */
    Material fogged(ColorRGBA color, ColorRGBA ambient,
                            com.jme3.texture.Texture atlas) {
        var material = new Material(assetManager, "MatDefs/duke/FoggedTerrain.j3md");
        material.setColor("Color", color);
        material.setColor("Ambient", ambient);
        material.setColor("Sun", light.sunColour());
        material.setVector3("SunDirection", light.sunDirection());
        material.setTexture("FogMap", fogMap != null ? fogMap.texture() : noFog());
        material.setVector2("FogSize", fogMap != null ? fogMap.worldSize() : new com.jme3.math.Vector2f(1f, 1f));
        tellWhereTheFogHolds(material);
        if (atlas != null) {
            material.setTexture("ColorMap", atlas);
        }
        var shades = visuals.getGroundShades();
        for (int i = 0; i < shades.size(); i++) {
            var picture = groundTexture(shades.get(i).picture());
            if (picture != null) {
                material.setTexture(i == 0 ? "ShadeMap" : "ShadeMap2", picture);
            }
        }
        if (!shades.isEmpty()) {
            material.setFloat("ShadeShown", shadesShown ? 1f : 0f);
            placeShades(material, snapshot.get() == null ? 0f : snapshot.get().gameTimeSeconds());
        }
        material.setParam("PointLightColours",
                com.jme3.shader.VarType.Vector4Array, terrainLightColours);
        material.setParam("PointLightPositions",
                com.jme3.shader.VarType.Vector4Array, terrainLightPlaces);
        fogged.add(material);
        return material;
    }

    /**
     * How a map's water is drawn: the water's own shader over its picture — laid across the world for standing water,
     * along a river for running — in the ground's light times the game's tint, shrouded as the ground. Null where its
     * picture will not load.
     */
    Material waterLook(Visuals.WaterLook look, boolean river) {
        var path = river && look.riverPicture() != null ? look.riverPicture() : look.picture();
        var picture = path == null ? null : groundTexture(path);
        if (path != null && picture == null) {
            return null;
        }
        var material = new Material(assetManager, "MatDefs/duke/Water.j3md");
        if (picture != null) {
            material.setTexture("ColorMap", picture);
        }
        if (!river && look.overlay() != null && groundTexture(look.overlay()) != null) {
            material.setTexture("OverlayMap", groundTexture(look.overlay()));
        }
        if (river && look.riverEdge() != null) {
            try {
                var edge = assetManager.loadTexture(look.riverEdge());
                edge.setWrap(com.jme3.texture.Texture.WrapMode.EdgeClamp);
                material.setTexture("EdgeMap", edge);
            } catch (RuntimeException notThere) {
                LOG.warning(() -> "a river's banks name a picture that will not load: " + look.riverEdge());
            }
        }
        material.setBoolean("Along", river);
        // W3DWater: the ground's light on flat ground, times the water's own colour for the hour.
        var shine = visuals.getGroundLight() != null ? visuals.getGroundLight().at(Vector3f.UNIT_Y)
                : light.ambientColour().add(light.sunColour().mult(Math.max(0f, -light.sunDirection().y)));
        int tint = look.tint();
        material.setColor("Color", new ColorRGBA(Math.min(1f, shine.r) * (tint >> 16 & 0xFF) / 255f,
                Math.min(1f, shine.g) * (tint >> 8 & 0xFF) / 255f, Math.min(1f, shine.b) * (tint & 0xFF) / 255f,
                (tint >>> 24) / 255f));
        material.setTexture("FogMap", fogMap != null ? fogMap.texture() : noFog());
        material.setVector2("FogSize", fogMap != null ? fogMap.worldSize() : new com.jme3.math.Vector2f(1f, 1f));
        tellWhereTheFogHolds(material);
        waterLooks.add(material);
        return material;
    }

    private com.jme3.texture.Texture2D noFogTexture;

    /** A fog of nothing, for ground drawn by the fog's shader where the game keeps no fog. */
    private com.jme3.texture.Texture2D noFog() {
        if (noFogTexture == null) {
            var clear = com.jme3.util.BufferUtils.createByteBuffer(4);
            clear.put(new byte[] {0, 0, 0, 0}).flip();
            noFogTexture = new com.jme3.texture.Texture2D(
                    new com.jme3.texture.Image(com.jme3.texture.Image.Format.RGBA8, 1, 1, clear,
                            com.jme3.texture.image.ColorSpace.Linear));
        }
        return noFogTexture;
    }

    /** Whether the pictures laid over all the ground show — see {@link Duke3D#groundShades}. */
    private volatile boolean shadesShown = true;

    /** The pictures laid over all the ground shown or put away — see {@link Duke3D#groundShades}. */
    void groundShades(boolean shown) {
        this.shadesShown = shown;
    }

    /** Where each picture laid over all the ground lies {@code seconds} into the game, told to a ground material. */
    private void placeShades(Material material, float seconds) {
        var shades = visuals.getGroundShades();
        for (int i = 0; i < shades.size(); i++) {
            var place = shades.get(i).placeAt(seconds);
            material.setVector4(i == 0 ? "ShadePlace" : "ShadePlace2",
                    new com.jme3.math.Vector4f(place[0], place[1], place[2], place[3]));
        }
    }

    /** The pictures over all the ground slid on with the game's time, and shown or put away as the game says. */
    void slideTheShades() {
        var snapshot = this.snapshot.get();
        if (visuals.getGroundShades().isEmpty() || snapshot == null) {
            return;
        }
        for (var material : fogged) {
            if (material.getParam("ShadeMap") != null) {
                placeShades(material, snapshot.gameTimeSeconds());
                material.setFloat("ShadeShown", shadesShown ? 1f : 0f);
            }
        }
    }
}
