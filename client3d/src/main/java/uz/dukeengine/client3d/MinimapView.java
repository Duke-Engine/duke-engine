package uz.dukeengine.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.Renderer;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Quad;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

import uz.dukeengine.core.view.UnitView;
import uz.dukeengine.core.view.WorldSnapshot;
import uz.dukeengine.game.DukeGame;

/**
 * The minimap: the ground as the player knows it, a dot for each thing he sees, the game's marks, and the outline of
 * what the camera sees — in the corner, or in the socket a part's bar cuts for it. A click on it moves the camera.
 */
final class MinimapView {

    private static final Logger LOG = Logger.getLogger(MinimapView.class.getName());

    private final AssetManager assetManager;
    private final Visuals visuals;
    private final Supplier<DukeGame> game;
    private final Supplier<WorldSnapshot> snapshot;
    private final CameraFocus camera;
    private final Camera cam;
    private final Renderer renderer;
    private final List<HudPart> parts;
    private final Supplier<Discovery> discovery;
    private final Function<String, com.jme3.texture.Texture> groundTexture;
    /** The colour a thing's dot is drawn in. */
    private final Function<UnitView, ColorRGBA> colourOf;
    private final com.jme3.system.Timer timer;
    /** The minimap's ground: one picture, a texel a cell, repainted where the player's knowledge changed. */
    private final MinimapPicture minimapPicture = new MinimapPicture();

    // minimap: fixed-size overlay in the bottom-right corner
    private static final float MINIMAP_SIZE = 190f;
    private final Node minimapNode = new Node("minimap");
    /**
     * Backdrop and rock cells — replaced as a unit when the world changes.
     */
    private final Node minimapTerrainNode = new Node("minimap-terrain");
    private final Map<Integer, Geometry> minimapDots = new HashMap<>();
    private MinimapProjection minimap = new MinimapProjection(700f, 450f, MINIMAP_SIZE);
    private float minimapX;       // screen position of the minimap's origin
    private float minimapY;
    /**
     * How much the minimap is shrunk to fit the hero bar's socket, or 1 when it
     * is drawn in the corner as an RTS draws it.
     *
     * <p>Scaled rather than rebuilt at the socket's size: the projection and the
     * cells are laid out once per world, and a window resize must not mean
     * rebuilding a few thousand quads.
     */
    private float minimapScale = 1f;
    /**
     * The camera's footprint, drawn as an outline over the minimap.
     */
    private Geometry viewportOutline;

    /** Each painted surface's colour, by its palette value: a picture's mean, or the palette's own colour. */
    private final Map<String, ColorRGBA> surfaceColours = new HashMap<>();

    /**
     * The ground's own colour at a cell of {@code grid} — the painted surface of the map's cell it lies in — or null
     * where the map is not painted.
     */
    private BiFunction<Integer, Integer, ColorRGBA> groundColours(uz.dukeengine.core.pathfind.PathGrid grid) {
        var paint = GroundPaint.of(game.get().getMapRecord());
        if (grid == null || paint == null) {
            return (cx, cy) -> null;
        }
        int k = Math.max(1, grid.cellsPerMapCell());
        return (cx, cy) -> {
            var value = paint.pictureAt(cx / k, cy / k);
            return value == null || value.isBlank() ? null : surfaceColours.computeIfAbsent(value, this::colourOfSurface);
        };
    }

    /** A surface's colour: {@code #RRGGBB} as written, a picture by the mean of its texels, null for one not loaded. */
    private ColorRGBA colourOfSurface(String value) {
        if (value.startsWith("#")) {
            return GroundPaint.colourOf(value);
        }
        var texture = groundTexture.apply(value);
        return texture == null || texture.getImage() == null ? null : meanOf(texture.getImage());
    }

    /** The mean colour of a picture, read a texel in every few so a large one costs little. */
    static ColorRGBA meanOf(com.jme3.texture.Image image) {
        var raster = com.jme3.texture.image.ImageRaster.create(image);
        int step = Math.max(1, Math.max(image.getWidth(), image.getHeight()) / 64);
        float r = 0f;
        float g = 0f;
        float b = 0f;
        int n = 0;
        var texel = new ColorRGBA();
        for (int y = 0; y < image.getHeight(); y += step) {
            for (int x = 0; x < image.getWidth(); x += step) {
                raster.getPixel(x, y, texel);
                r += texel.r;
                g += texel.g;
                b += texel.b;
                n++;
            }
        }
        return n == 0 ? null : new ColorRGBA(r / n, g / n, b / n, 1f);
    }

    MinimapView(AssetManager assetManager, Visuals visuals, Supplier<DukeGame> game, Supplier<WorldSnapshot> snapshot,
            CameraFocus camera, Camera cam, Renderer renderer, List<HudPart> parts, Supplier<Discovery> discovery,
            Function<String, com.jme3.texture.Texture> groundTexture, Function<UnitView, ColorRGBA> colourOf,
            com.jme3.system.Timer timer) {
        this.assetManager = assetManager;
        this.visuals = visuals;
        this.game = game;
        this.snapshot = snapshot;
        this.camera = camera;
        this.cam = cam;
        this.renderer = renderer;
        this.parts = parts;
        this.discovery = discovery;
        this.groundTexture = groundTexture;
        this.colourOf = colourOf;
        this.timer = timer;
    }

    /**
     * Assemble the minimap once: its terrain layer, then the viewport outline over it.
     */
    void build(Node gui) {
        minimapY = 34f; // above the hint line, until a hero bar claims it
        minimapNode.attachChild(minimapTerrainNode);
        rebuild();
        buildViewportOutline();
        gui.attachChild(minimapNode);
    }

    /**
     * The minimap's static layer — map bounds and blocked terrain — for the world
     * as it stands now. Like the 3D terrain it empties its node first, so a new
     * world replaces the old picture instead of being drawn over it.
     */
    void rebuild() {
        minimapTerrainNode.detachAllChildren();
        var grid = game.get().getTerrain();
        float worldW = grid == null ? 700f : grid.getWidth() * grid.getCellSize();
        float worldH = grid == null ? 450f : grid.getHeight() * grid.getCellSize();
        // The ground and its stone as one picture on one quad, the cells unwalked where there is discovery: an
        // undiscovered floor is as black as undiscovered stone. Of the ground round the camera, on a wide world.
        minimapPicture.ground(visuals.getMinimapUnseenShows(), groundColours(grid));
        minimapPicture.rebuild(grid, visuals.drawsFog(), visuals.getFog().tintColour(), visuals.getMinimapSpan());
        minimap = minimapPicture.isWindowed() ? minimapRoundTheCamera(grid)
                : new MinimapProjection(worldW, worldH, MINIMAP_SIZE);
        // The camera may look at exactly what the minimap draws, and no further.
        // Until there is a map there is nothing to fence it into: the sizes above
        // are only something to draw an empty minimap at.
        camera.keepInside(grid == null ? 0f : worldW, grid == null ? 0f : worldH);
        minimapX = cam.getWidth() - minimap.widthPixels() - 12f;

        var ground = new Geometry("mm-ground", new Quad(minimap.widthPixels(), minimap.heightPixels()));
        ground.setMaterial(Materials.picture(assetManager, minimapPicture.texture()));
        minimapTerrainNode.attachChild(ground);
        minimapGround = ground;
        follow();
        place();
    }

    /** The quad the minimap's ground is drawn on, whose picture slides under it on a wide world. */
    private Geometry minimapGround;

    /** The minimap's projection of the {@link Visuals#minimapSpan} of ground round where the camera looks now. */
    private MinimapProjection minimapRoundTheCamera(uz.dukeengine.core.pathfind.PathGrid grid) {
        float cell = grid.getCellSize();
        float span = minimapPicture.span() * cell;
        return new MinimapProjection(camera.targetX() - span / 2f, camera.targetZ() - span / 2f, span, span,
                MINIMAP_SIZE);
    }

    /**
     * On a wide world, the minimap kept on the ground round where the camera looks: the cells coming into its window
     * painted, its picture slid under its quad by a fraction of a cell as the camera moves, and its projection — the
     * things on it, the view's outline, a click on it — moved with it. Nothing where it shows the whole map.
     */
    void follow() {
        var grid = game.get().getTerrain();
        if (!minimapPicture.isWindowed() || grid == null || minimapGround == null) {
            return;
        }
        float cell = grid.getCellSize();
        minimapPicture.follow(camera.targetX(), camera.targetZ(), discovery.get(), renderer);
        minimap = minimapRoundTheCamera(grid);
        float span = minimapPicture.span();
        var corners = minimapPicture.corners(camera.targetX() / cell - span / 2f, camera.targetZ() / cell - span / 2f);
        var mesh = minimapGround.getMesh();
        var coordinates = mesh.getFloatBuffer(com.jme3.scene.VertexBuffer.Type.TexCoord);
        coordinates.clear();
        coordinates.put(corners).flip();
        mesh.getBuffer(com.jme3.scene.VertexBuffer.Type.TexCoord).setUpdateNeeded();
    }

    /**
     * Put the minimap where the hero bar says, or back in the corner when there
     * is no bar.
     *
     * <p>The bar owns the socket because it owns the arrangement; the minimap owns
     * the picture. Neither has to know the other's arithmetic — one hands over a
     * rectangle in window pixels and the other fits itself to it.
     */
    void place() {
        float[] socket = null;
        float panelHeight = 0f;
        for (var part : parts) {
            socket = socket != null ? socket : part.minimapSocket();
            panelHeight = Math.max(panelHeight, part.panelHeight());
        }
        if (socket != null) {
            minimapScale = socket[2] / Math.max(1f, Math.max(minimap.widthPixels(),
                    minimap.heightPixels()));
            // Centred in its socket: a map that is not square leaves a margin, and
            // the margin belongs on both sides rather than all on one.
            minimapX = socket[0] + (socket[2] - minimap.widthPixels() * minimapScale) / 2f;
            minimapY = socket[1] + (socket[2] - minimap.heightPixels() * minimapScale) / 2f;
        } else {
            // In the corner, over a bar that is showing without a socket for it.
            minimapScale = 1f;
            minimapX = cam.getWidth() - minimap.widthPixels() - 12f;
            minimapY = 34f + panelHeight;
        }
        minimapNode.setLocalScale(minimapScale);
        // Over the hole cut for it, at the depth the bar says: the GUI bucket is drawn in order of depth, and a map
        // behind the hole looks exactly like a minimap that stopped working. The bar's depths grow with its scale, so
        // a depth of the map's own was under the hole once the bar was drawn larger than the design.
        minimapNode.setLocalTranslation(minimapX, minimapY, socket != null ? socket[3] : 0f);
    }

    /**
     * Paint the minimap with what the player knows: black where they have not
     * been, dim where they have, bright where they are looking — the cells whose state changed, on its one picture.
     *
     * <p>The unit dots need no help — the snapshot only ever carries what the
     * engine's own fog lets through, so a monster in a room the player walked out
     * of is already gone from it. That is the whole "where did it go?" of the
     * thing, and it comes free.
     */
    void paintWhatIsKnown() {
        minimapPicture.paint(discovery.get(), renderer);
    }

    /**
     * If the cursor is over the minimap, move the camera there. Returns true if handled.
     */
    boolean click(Vector2f cursor) {
        float localX = (cursor.x - minimapX) / minimapScale;
        float localY = (cursor.y - minimapY) / minimapScale;
        if (!minimap.contains(localX, localY)) {
            return false;
        }
        camera.lookAt(minimap.toWorldX(localX), minimap.toWorldY(localY));
        return true;
    }

    /**
     * The outline showing what the camera can see, drawn as an empty loop.
     *
     * <p>An outline rather than a filled box on purpose: the minimap's whole job is
     * showing where the rooms and the fighting are, and a translucent rectangle
     * over a third of it would dim exactly the part the player is looking at.
     */
    private void buildViewportOutline() {
        var mesh = new com.jme3.scene.Mesh();
        mesh.setMode(com.jme3.scene.Mesh.Mode.LineLoop);
        mesh.setBuffer(com.jme3.scene.VertexBuffer.Type.Position, 3, new float[VIEWPORT_CORNERS * 3]);
        mesh.setDynamic();
        mesh.updateBound();

        viewportOutline = new Geometry("mm-viewport", mesh);
        viewportOutline.setMaterial(Materials.unshaded(assetManager, new ColorRGBA(1f, 1f, 1f, 0.85f)));
        viewportOutline.setLocalTranslation(0, 0, 2); // above the dots
        minimapNode.attachChild(viewportOutline);
    }

    private static final int VIEWPORT_CORNERS = 4;

    /**
     * Redraw the viewport outline from where the camera is now — it moves and
     * resizes as the player pans and zooms.
     *
     * <p>The camera looks at the ground from an angle, so what it sees is a
     * trapezium rather than a rectangle: the far edge of the screen covers more
     * ground than the near one. Rather than approximate that with a box, each
     * screen corner is cast onto the ground and the true quadrilateral is drawn.
     */
    void outline(WorldRegion worldRegion, BiFunction<Float, Float, Vector3f> groundUnder) {
        // The corners of the world's part of the window, in turn round it so the loop does not cross.
        float[][] corners = worldRegion.corners(cam.getWidth(), cam.getHeight());
        var worldXs = new float[VIEWPORT_CORNERS];
        var worldYs = new float[VIEWPORT_CORNERS];
        for (int i = 0; i < VIEWPORT_CORNERS; i++) {
            var ground = groundUnder.apply(corners[i][0], corners[i][1]);
            // World y is the ground plane's z: the map lies in x/z, y is height.
            worldXs[i] = ground.x;
            worldYs[i] = ground.z;
        }

        var outline = minimap.viewportOutline(worldXs, worldYs);
        var vertices = new float[VIEWPORT_CORNERS * 3];
        for (int i = 0; i < outline.length; i++) {
            vertices[i * 3] = outline[i].x();
            vertices[i * 3 + 1] = outline[i].y();
        }
        var mesh = viewportOutline.getMesh();
        mesh.setBuffer(com.jme3.scene.VertexBuffer.Type.Position, 3, vertices);
        mesh.updateBound();
    }

    /** A see-through thing's blip on its own side's radar, blinking once a second; every other blip whole. */
    private void blink(Geometry dot, UnitView view, int frame) {
        var word = visuals.getSeeThroughWord();
        boolean blinking = word != null && view.allied() && view.conditions().contains(word);
        var colour = (ColorRGBA) dot.getMaterial().getParamValue("Color");
        float alpha = blinking ? StealthLook.radarAlpha(frame) : 1f;
        if (colour != null && colour.a != alpha) {
            dot.getMaterial().setColor("Color", new ColorRGBA(colour.r, colour.g, colour.b, alpha));
            dot.getMaterial().getAdditionalRenderState().setBlendMode(
                    alpha < 1f ? com.jme3.material.RenderState.BlendMode.Alpha : com.jme3.material.RenderState.BlendMode.Off);
        }
    }

    /**
     * Live unit dots, coloured by player, sized up for structures.
     */
    void syncDots() {
        var snapshot = this.snapshot.get();
        var seen = new HashSet<Integer>();
        for (var view : snapshot.units()) {
            seen.add(view.id());
            var dot = minimapDots.computeIfAbsent(view.id(), id -> {
                float size = view.structure() ? 6f : 3.5f;
                var geometry = new Geometry("mm-dot", new Quad(size, size));
                geometry.setMaterial(Materials.unshaded(assetManager, colourOf.apply(view)));
                minimapNode.attachChild(geometry);
                return geometry;
            });
            var wanted = colourOf.apply(view);
            var now = (ColorRGBA) dot.getMaterial().getParamValue("Color");
            if (now == null || now.r != wanted.r || now.g != wanted.g || now.b != wanted.b) {
                // A blip wears the colours the thing does to this viewer: a disguise's, as the reference's radar does.
                float alpha = now == null ? 1f : now.a;
                dot.getMaterial().setColor("Color", new ColorRGBA(wanted.r, wanted.g, wanted.b, alpha));
            }
            var point = minimap.toMinimap(view.x(), view.y());
            dot.setLocalTranslation(point.x() - 2f, point.y() - 2f, 1);
            if (minimapPicture.isWindowed()) {
                // The ground round the camera: a thing past it has no place on the minimap.
                dot.setCullHint(minimap.contains(point.x(), point.y()) ? Spatial.CullHint.Inherit
                        : Spatial.CullHint.Always);
            }
            blink(dot, view, snapshot.frame());
        }
        var gone = minimapDots.entrySet().iterator();
        while (gone.hasNext()) {
            var entry = gone.next();
            if (!seen.contains(entry.getKey())) {
                entry.getValue().removeFromParent();
                gone.remove();
            }
        }
    }

    /**
     * Whether a screen point is inside the minimap, which takes its own clicks.
     */
    boolean covers(Vector2f at) {
        float wide = minimap.widthPixels() * minimapScale;
        float tall = minimap.heightPixels() * minimapScale;
        return at.x >= minimapX && at.x <= minimapX + wide
                && at.y >= minimapY && at.y <= minimapY + tall;
    }

    /** The game's marks on the minimap — see {@link Duke3D#minimapMarks}. */
    private java.util.function.Supplier<java.util.List<MinimapMark>> minimapMarks;
    private final java.util.List<Geometry> markShapes = new java.util.ArrayList<>();
    private final Map<String, com.jme3.texture.Texture> markPictures = new HashMap<>();

    void marks(java.util.function.Supplier<java.util.List<MinimapMark>> marks) {
        this.minimapMarks = marks;
    }

    /** The game's marks drawn over the dots, one shape each, kept from frame to frame and hidden when not asked. */
    void syncMarks() {
        var marks = minimapMarks == null ? java.util.List.<MinimapMark>of() : minimapMarks.get();
        marks = marks == null ? java.util.List.of() : marks;
        boolean lit = (int) (timer.getTimeInSeconds() * 4f) % 2 == 0;
        for (int at = 0; at < Math.max(marks.size(), markShapes.size()); at++) {
            if (at >= marks.size()) {
                markShapes.get(at).setCullHint(Spatial.CullHint.Always);
                continue;
            }
            var mark = marks.get(at);
            if (at >= markShapes.size()) {
                var shape = new Geometry("mm-mark", new Quad(MARK_SIZE, MARK_SIZE));
                shape.setMaterial(Materials.unshaded(assetManager, ColorRGBA.White));
                shape.getMaterial().getAdditionalRenderState().setBlendMode(
                        com.jme3.material.RenderState.BlendMode.Alpha);
                minimapNode.attachChild(shape);
                markShapes.add(shape);
            }
            var shape = markShapes.get(at);
            var material = shape.getMaterial();
            var picture = mark.picture() == null ? null : markPictures.computeIfAbsent(mark.picture(), path -> {
                try {
                    return assetManager.loadTexture(path);
                } catch (RuntimeException notThere) {
                    LOG.warning(() -> "a minimap mark names a picture that will not load: " + path);
                    return null;
                }
            });
            if (picture != null) {
                material.setTexture("ColorMap", picture);
            } else {
                material.clearParam("ColorMap");
            }
            material.setColor("Color", Materials.colour(new java.awt.Color(mark.colour())));
            var point = minimap.markAt(mark.x(), mark.y());
            // A diamond is the square turned an eighth; a picture stands upright.
            float turn = picture == null ? com.jme3.math.FastMath.QUARTER_PI : 0f;
            shape.setLocalRotation(new com.jme3.math.Quaternion().fromAngles(0f, 0f, turn));
            float half = MARK_SIZE / 2f;
            float offX = picture == null ? 0f : -half;
            float offY = picture == null ? -half * com.jme3.math.FastMath.sqrt(2f) : -half;
            shape.setLocalTranslation(point.x() + offX, point.y() + offY, 2);
            shape.setCullHint(!mark.blinks() || lit ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        }
    }

    private static final float MARK_SIZE = 7f;

    /** The minimap shown, while it belongs on the screen, or put away. */
    void show(boolean shown) {
        minimapNode.setCullHint(shown ? Spatial.CullHint.Never : Spatial.CullHint.Always);
    }
}
