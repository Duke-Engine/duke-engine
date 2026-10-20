package uz.dukeengine.client3d;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.logging.Logger;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.HeightMap;
import uz.dukeengine.core.pathfind.PathGrid;

/**
 * The ground as the map painted it: a mesh a surface, every cell lifted onto the relief, or, where the game asks for
 * it, blended from the paint a mesh a chunk — with the overlays laid over it, and the ground's own lights worked into
 * the corners.
 */
final class PaintedGround {

    /** How many surfaces one chunk of blended ground carries: two vectors of four weights a vertex. */
    static final int BLEND_SLOTS = 8;
    /** How a painted map's ground is blended from its paint; null for one surface a cell — see {@link #blendWith}. */
    private Visuals.PaintBlend blend;
    /** Whether blended ground cuts each cell two by two — see {@link Visuals#paintInQuarters}. */
    private boolean quarters;
    /** Whether a chunk with more surfaces than {@link #BLEND_SLOTS} has been said: once. */
    private boolean tooManySaid;

    private final Surfaces material;
    /** The ground's own lights, worked into the painted ground's corners; null where the things' light it. */
    private Visuals.GroundLight light;

    PaintedGround(Surfaces material) {
        this.material = material;
    }

    void light(Visuals.GroundLight light) {
        this.light = light;
    }

    /**
     * Blend a painted map's ground from its paint from the next build on, as the game asks ({@link
     * Visuals#paintBlend}); null for one surface a cell. A map that writes blends of its own is drawn from them
     * either way.
     */
    void blendWith(Visuals.PaintBlend blend) {
        this.blend = blend;
    }

    /** Cut each cell of blended ground two by two from the next build on — see {@link Visuals#paintInQuarters}. */
    void quarterWith(boolean quarters) {
        this.quarters = quarters;
    }

    boolean blended(GroundPaint paint) {
        return blend != null && paint != null && !paint.writesBlends();
    }

    /** The whole map {@code grid} laid on {@code into}: blended a mesh a chunk, else a mesh a surface. */
    void lay(PathGrid grid, GroundPaint paint, Node into) {
        if (blended(paint)) {
            blendWhole(grid, paint, into);
        } else {
            paintCells(grid, paint, 0, 0, grid.getWidth() - 1, grid.getHeight() - 1, into, 0f, 0f);
        }
    }

    /**
     * The cells from ({@code fromX}, {@code fromY}) to ({@code toX}, {@code toY}) laid on {@code into}, placed less
     * ({@code ox}, {@code oz}): blended where the game asks for it, else a mesh a surface.
     */
    void layCells(PathGrid grid, GroundPaint paint, int fromX, int fromY, int toX, int toY, Node into, float ox,
            float oz) {
        if (blended(paint)) {
            blendCells(grid, paint, fromX, fromY, toX, toY, into, ox, oz);
        } else {
            paintCells(grid, paint, fromX, fromY, toX, toY, into, ox, oz);
        }
    }

    /** Blended ground over the whole map, built whole: a mesh a chunk, as a world built round the camera has. */
    private void blendWhole(PathGrid grid, GroundPaint paint, Node into) {
        float cell = grid.getCellSize();
        for (int y0 = 0; y0 < grid.getHeight(); y0 += Chunks.CELLS) {
            for (int x0 = 0; x0 < grid.getWidth(); x0 += Chunks.CELLS) {
                var node = new Node("chunk");
                blendCells(grid, paint, x0, y0, Math.min(x0 + Chunks.CELLS, grid.getWidth()) - 1,
                        Math.min(y0 + Chunks.CELLS, grid.getHeight()) - 1, node, x0 * cell, y0 * cell);
                node.setLocalTranslation(x0 * cell, 0f, y0 * cell);
                into.attachChild(node);
            }
        }
    }

    /**
     * The ground of the cells from ({@code fromX}, {@code fromY}) to ({@code toX}, {@code toY}) blended from the paint,
     * as Warcraft III blends its tiles: one mesh, each cell's corners carrying the weight of every surface the chunk
     * shows there ({@link uz.dukeengine.core.map.PaintWeights}), the material laying each surface across the world at
     * its coverage and blending them by the weights. The surfaces are the chunk's in palette order, at most {@link
     * #BLEND_SLOTS}: a chunk where more meet draws the first of them, and says so once. A map's picture corners are a
     * cell's own blend's, and a blended map has none. Lit smoothly: each corner by the relief round it ({@link
     * #smoothNormal}), and, cut in quarters, each cell's middle and the middles of its edges too — every corner on the
     * relief's own triangles, each quarter cut along the cell's own diagonal.
     */
    private void blendCells(PathGrid grid, GroundPaint paint, int fromX, int fromY, int toX, int toY, Node into,
            float ox, float oz) {
        int width = grid.getWidth();
        int height = grid.getHeight();
        float cell = grid.getCellSize();
        var keys = paint.keys();
        int across = toX - fromX + 2;
        var weights = new float[across * (toY - fromY + 2)][];
        var shown = new boolean[keys.size()];
        for (int cy = fromY; cy <= toY + 1; cy++) {
            for (int cx = fromX; cx <= toX + 1; cx++) {
                var at = paint.weightsAt(width, height, cx, cy);
                weights[(cy - fromY) * across + cx - fromX] = at;
                for (int surface = 0; surface < at.length; surface++) {
                    shown[surface] |= at[surface] > 0f;
                }
            }
        }
        var slots = new ArrayList<Integer>();
        for (int surface = 0; surface < shown.length; surface++) {
            if (!shown[surface]) {
                continue;
            }
            if (slots.size() == BLEND_SLOTS) {
                if (!tooManySaid) {
                    tooManySaid = true;
                    Logger.getLogger(PaintedGround.class.getName()).warning(() -> "more than "
                            + BLEND_SLOTS + " surfaces meet in one chunk of 16 cells; the first " + BLEND_SLOTS
                            + " of the palette are drawn there");
                }
                break;
            }
            slots.add(surface);
        }
        if (slots.isEmpty()) {
            return;
        }
        int steps = quarters ? 2 : 1;
        int quads = (toX - fromX + 1) * (toY - fromY + 1) * steps * steps;
        var positions = BufferUtils.createFloatBuffer(quads * 12);
        var normals = BufferUtils.createFloatBuffer(quads * 12);
        var first = BufferUtils.createFloatBuffer(quads * 16);
        var second = BufferUtils.createFloatBuffer(quads * 16);
        var indices = BufferUtils.createIntBuffer(quads * 6);
        var lights = light == null ? null : BufferUtils.createFloatBuffer(quads * 16);
        float[][] round = {{0f, 0f}, {1f, 0f}, {1f, 1f}, {0f, 1f}};
        int vertex = 0;
        for (int cy = fromY; cy <= toY; cy++) {
            for (int cx = fromX; cx <= toX; cx++) {
                var w00 = weights[(cy - fromY) * across + cx - fromX];
                var w10 = weights[(cy - fromY) * across + cx + 1 - fromX];
                var w11 = weights[(cy + 1 - fromY) * across + cx + 1 - fromX];
                var w01 = weights[(cy + 1 - fromY) * across + cx - fromX];
                var split = cellTriangles(drawnDiagonal(grid, paint, cx, cy));
                for (int qy = 0; qy < steps; qy++) {
                    for (int qx = 0; qx < steps; qx++) {
                        for (var corner : round) {
                            float u = (qx + corner[0]) / steps;
                            float v = (qy + corner[1]) / steps;
                            float x = (cx + u) * cell;
                            float z = (cy + v) * cell;
                            positions.put(x - ox).put(groundAt(grid, x, z)).put(z - oz);
                            var normal = smoothNormal(grid, x, z);
                            normals.put(normal.x).put(normal.y).put(normal.z);
                            for (int slot = 0; slot < BLEND_SLOTS; slot++) {
                                float at = 0f;
                                if (slot < slots.size()) {
                                    int s = slots.get(slot);
                                    at = (w00[s] * (1f - u) + w10[s] * u) * (1f - v)
                                            + (w01[s] * (1f - u) + w11[s] * u) * v;
                                }
                                (slot < 4 ? first : second).put(at);
                            }
                            if (lights != null) {
                                var lit = light.at(normal);
                                lights.put(lit.r).put(lit.g).put(lit.b).put(1f);
                            }
                        }
                        for (int corner : split) {
                            indices.put(vertex + corner);
                        }
                        vertex += 4;
                    }
                }
            }
        }
        var mesh = meshOf(positions, normals, indices);
        mesh.setBuffer(VertexBuffer.Type.TexCoord2, 4, first);
        mesh.setBuffer(VertexBuffer.Type.TexCoord3, 4, second);
        if (lights != null) {
            mesh.setBuffer(VertexBuffer.Type.Color, 4, lights);
        }
        mesh.updateBound();
        var surfaces = new ArrayList<GroundPaint.Surface>();
        var spans = new float[slots.size()];
        for (int slot = 0; slot < slots.size(); slot++) {
            var key = keys.get(slots.get(slot));
            surfaces.add(paint.surfaceOf(key));
            spans[slot] = paint.coverage(key) * cell;
        }
        var ground = new Geometry("blended", mesh);
        ground.setMaterial(material.blended(surfaces, spans));
        litByCorners(ground);
        into.attachChild(ground);
    }

    /**
     * A cell's two triangles, by its corners — 0 at (x0, z0), then round by (x1, z0), (x1, z1) and (x0, z1) — cut
     * along {@code diagonal} and each wound so its face looks up.
     */
    static int[] cellTriangles(HeightMap.Diagonal diagonal) {
        return switch (diagonal) {
            case MAIN -> new int[] {0, 3, 2, 0, 2, 1};
            case ANTI -> new int[] {0, 3, 1, 1, 3, 2};
        };
    }

    /**
     * The ground as the map painted it: one mesh a surface, every cell of it lifted onto the relief.
     *
     * <p><b>One geometry a palette entry, not a cell.</b> A converted Command &amp; Conquer map is sixty
     * thousand cells and a dozen pictures. Gathered, that is a dozen meshes and a dozen materials; a
     * geometry a cell would be sixty thousand of each, and a scene graph that size is not slow to draw so
     * much as slow to walk, which the fog does every frame.
     *
     * <p><b>The texture coordinates run across the world, not across a cell.</b> This is the whole
     * difference between ground and a chequerboard: a picture laid 0..1 inside every cell shows the whole
     * of itself sixty thousand times and its own edges draw the grid. So {@code u} is the world position
     * divided by how much ground one copy covers — which the map says, per entry, because one game's road
     * repeats every two cells and its grass every ten, and a number in the engine could only ever be wrong
     * for one of them.
     *
     * <p>Corners are not shared between cells. Two cells of the same surface meet at the same position with
     * the same texture coordinate, so nothing shows; and not sharing is what lets each cell carry the flat
     * normal of its own slope, which is the look this client's art is drawn for.
     */
    private void paintCells(PathGrid grid, GroundPaint paint, int fromX, int fromY, int toX, int toY, Node into,
            float ox, float oz) {
        float cell = grid.getCellSize();
        int width = grid.getWidth();
        for (var patch : paint.patches(width, grid.getHeight(), fromX, fromY, toX, toY)) {
            float span = patch.coverage() * cell;
            var positions = BufferUtils.createFloatBuffer(patch.cells().length * 12);
            var normals = BufferUtils.createFloatBuffer(patch.cells().length * 12);
            var uvs = BufferUtils.createFloatBuffer(patch.cells().length * 8);
            var indices = BufferUtils.createIntBuffer(patch.cells().length * 6);
            var lights = light == null ? null : BufferUtils.createFloatBuffer(patch.cells().length * 16);
            int vertex = 0;
            for (var at : patch.cells()) {
                int cx = at % width;
                int cy = at / width;
                var split = cellTriangles(drawnDiagonal(grid, paint, cx, cy));
                float x0 = cx * cell;
                float x1 = x0 + cell;
                float z0 = cy * cell;
                float z1 = z0 + cell;
                float h00 = groundAt(grid, x0, z0);
                float h10 = groundAt(grid, x1, z0);
                float h11 = groundAt(grid, x1, z1);
                float h01 = groundAt(grid, x0, z1);
                // The slope of this cell, from the two diagonals of its own corners: flat where the ground
                // is flat, and tilted into the sun where it is not.
                var normal = slopeOf(x0, x1, z0, z1, h00, h10, h01);
                positions.put(x0 - ox).put(h00).put(z0 - oz).put(x1 - ox).put(h10).put(z0 - oz)
                        .put(x1 - ox).put(h11).put(z1 - oz).put(x0 - ox).put(h01).put(z1 - oz);
                for (int corner = 0; corner < 4; corner++) {
                    normals.put(normal.x).put(normal.y).put(normal.z);
                }
                if (lights != null) {
                    for (var place : new float[][] {{x0, z0}, {x1, z0}, {x1, z1}, {x0, z1}}) {
                        var lit = light.at(smoothNormal(grid, place[0], place[1]));
                        lights.put(lit.r).put(lit.g).put(lit.b).put(1f);
                    }
                }
                var laid = paint.cornersOf(cx, cy);
                if (laid != null) {
                    uvs.put(laid); // up a cliff as the map lays it, not stretched straight down
                } else {
                    uvs.put(x0 / span).put(z0 / span).put(x1 / span).put(z0 / span)
                            .put(x1 / span).put(z1 / span).put(x0 / span).put(z1 / span);
                }
                // Cut along the diagonal HeightMap.fixedAt cuts along. It was once cut along the other: on a
                // slope the two triangles then describe a different surface from the one the pathfinder walks,
                // and a unit stood a little in the air or a little in the ground on every tilted cell. A cell the
                // map turns is cut along the other, and a unit on it stands so, as in the reference.
                for (int corner : split) {
                    indices.put(vertex + corner);
                }
                vertex += 4;
            }
            var mesh = meshOf(positions, normals, indices);
            mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, uvs);
            if (lights != null) {
                mesh.setBuffer(VertexBuffer.Type.Color, 4, lights);
            }
            mesh.updateBound();
            var ground = new Geometry("painted", mesh);
            ground.setMaterial(material.of(patch.surface().colour(), patch.surface().texture()));
            litByCorners(ground);
            into.attachChild(ground);
        }
        for (var overlay : paint.overlays(width, grid.getHeight(), fromX, fromY, toX, toY)) {
            layOver(grid, paint, overlay, into, ox, oz);
        }
    }

    /** A mesh of triangles with their normals, to be given what else its look reads. */
    private static Mesh meshOf(FloatBuffer positions, FloatBuffer normals,
            IntBuffer indices) {
        var mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, positions);
        mesh.setBuffer(VertexBuffer.Type.Normal, 3, normals);
        mesh.setBuffer(VertexBuffer.Type.Index, 3, indices);
        return mesh;
    }

    /**
     * The slope of a cell, from the two diagonals of its own corners: flat where the ground is flat, and tilted into
     * the sun where it is not.
     */
    private static Vector3f slopeOf(float x0, float x1, float z0, float z1, float h00, float h10, float h01) {
        return new Vector3f(x1 - x0, h10 - h00, 0f)
                .cross(new Vector3f(0f, h01 - h00, z1 - z0)).negateLocal().normalizeLocal();
    }

    /** A painted mesh carrying the ground's own light at its corners told to be drawn by it, where its material can. */
    private void litByCorners(Geometry geometry) {
        var look = geometry.getMaterial();
        if (light != null && look != null && look.getMaterialDef().getMaterialParam("VertexLight") != null) {
            look.setBoolean("VertexLight", true);
        }
    }

    /**
     * How the ground leans at a place, smoothed from the heights a cell either side — the reference's normal for its
     * terrain lights, where a cell's own flat slope draws the facets its lit corners would blur.
     */
    static Vector3f smoothNormal(PathGrid grid, float x, float z) {
        float cell = grid.getCellSize();
        float across = groundAt(grid, x + cell, z) - groundAt(grid, x - cell, z);
        float down = groundAt(grid, x, z + cell) - groundAt(grid, x, z - cell);
        return new Vector3f(-across / (2f * cell), 1f, -down / (2f * cell)).normalizeLocal();
    }

    /**
     * The diagonal a cell of the painted ground is drawn cut along: its relief's, or the other where the map turns it
     * ({@link uz.dukeengine.core.data.Flipped}). Drawing only: heights, slopes and clicks keep the relief's.
     */
    static HeightMap.Diagonal drawnDiagonal(PathGrid grid, GroundPaint paint, int cx, int cy) {
        var relief = grid.getRelief() == null ? HeightMap.Diagonal.MAIN : grid.getRelief().diagonal();
        if (paint == null || !paint.flipped(cx, cy)) {
            return relief;
        }
        return relief == HeightMap.Diagonal.MAIN ? HeightMap.Diagonal.ANTI : HeightMap.Diagonal.MAIN;
    }

    /**
     * A picture laid over the ground where kinds of it meet, faded in by the shape each cell names.
     *
     * <p>Four triangles a cell, meeting at its middle, because that is what makes every one of the sixteen
     * fades exact — see {@link FadeShape}. It costs nothing in the fit: the middle lies on the diagonal the
     * ground under it is cut along, at the height the ground has there, so all four triangles lie in the two
     * the ground is made of and the overlay cannot sink into it or float above it.
     *
     * <p>Drawn after the ground and blended over it. Within a layer the order never matters — a cell has one
     * overlay a layer, so no two of a layer's meshes cover the same place. Between layers it is everything:
     * where three kinds of ground meet, a cell carries one picture a layer on exactly the same triangles, and
     * the later layer has to land on top. So each mesh is marked with its layer ({@link OverlayOrder#LAYER})
     * and the transparent bucket is drawn in layer order by {@link OverlayOrder} — a fixed order, not the
     * camera's distance to a mesh that spans the map, and not a depth test two identical surfaces would
     * fight over.
     *
     * <p>Laid across the world at its own coverage, like the ground's pictures, so an overlay lines up with
     * the same picture drawn as ground next door rather than starting again in every cell.
     */
    private void layOver(PathGrid grid, GroundPaint paint, GroundPaint.OverlayPatch overlay, Node into,
            float ox, float oz) {
        float cell = grid.getCellSize();
        int width = grid.getWidth();
        float span = overlay.coverage() * cell;
        int count = overlay.cells().length;
        var positions = BufferUtils.createFloatBuffer(count * 5 * 3);
        var normals = BufferUtils.createFloatBuffer(count * 5 * 3);
        var colours = BufferUtils.createFloatBuffer(count * 5 * 4);
        var uvs = BufferUtils.createFloatBuffer(count * 5 * 2);
        var indices = BufferUtils.createIntBuffer(count * 12);
        int vertex = 0;
        for (int i = 0; i < count; i++) {
            int at = overlay.cells()[i];
            float x0 = at % width * cell;
            float z0 = at / width * cell;
            float x1 = x0 + cell;
            float z1 = z0 + cell;
            float xm = x0 + cell / 2f;
            float zm = z0 + cell / 2f;
            float h00 = groundAt(grid, x0, z0);
            float h10 = groundAt(grid, x1, z0);
            float h11 = groundAt(grid, x1, z1);
            float h01 = groundAt(grid, x0, z1);
            var normal = slopeOf(x0, x1, z0, z1, h00, h10, h01);
            // The middle on the diagonal the ground under it is drawn cut along, so it lies on that ground.
            float hm = drawnDiagonal(grid, paint, at % width, at / width) == HeightMap.Diagonal.MAIN
                    ? (h00 + h11) / 2f : (h10 + h01) / 2f;
            positions.put(x0 - ox).put(h00).put(z0 - oz).put(x1 - ox).put(h10).put(z0 - oz).put(x1 - ox).put(h11)
                    .put(z1 - oz).put(x0 - ox).put(h01).put(z1 - oz).put(xm - ox).put(hm).put(zm - oz);
            uvs.put(x0 / span).put(z0 / span).put(x1 / span).put(z0 / span).put(x1 / span).put(z1 / span)
                    .put(x0 / span).put(z1 / span).put(xm / span).put(zm / span);
            // White, so the picture is what is seen; the fade is the alpha alone — and the ground's own light, where
            // it has one, at each corner and the middle.
            float[][] places = {{x0, z0}, {x1, z0}, {x1, z1}, {x0, z1}, {xm, zm}};
            var strengths = FadeShape.strengths(overlay.shapes()[i]);
            for (int corner = 0; corner < strengths.length; corner++) {
                var lit = light == null ? ColorRGBA.White
                        : light.at(smoothNormal(grid, places[corner][0], places[corner][1]));
                colours.put(lit.r).put(lit.g).put(lit.b).put(strengths[corner]);
                normals.put(normal.x).put(normal.y).put(normal.z);
            }
            // Corner, middle, next corner, round the cell: each wound so its face looks up.
            for (int corner = 0; corner < 4; corner++) {
                indices.put(vertex + corner).put(vertex + 4).put(vertex + (corner + 1) % 4);
            }
            vertex += 5;
        }
        var mesh = meshOf(positions, normals, indices);
        mesh.setBuffer(VertexBuffer.Type.Color, 4, colours);
        mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, uvs);
        mesh.updateBound();
        var laid = new Geometry("overlay", mesh);
        laid.setMaterial(material.overlay(overlay.surface().colour(), overlay.surface().texture()));
        litByCorners(laid);
        laid.setQueueBucket(RenderQueue.Bucket.Transparent);
        laid.setUserData(OverlayOrder.LAYER, overlay.layer());
        into.attachChild(laid);
    }

    /**
     * How high the painted ground stands at a place: the relief, and zero where the map has none.
     *
     * <p>The same number the pathfinder reads, asked the same way, so the ground a unit walks on and the
     * ground drawn under it cannot drift apart. Two cells sharing a corner ask about the same world
     * position and get the same answer, so the meshes meet without a crack even though they share no vertex.
     *
     * <p>Storeys are not in it. A map built in storeys is a map built from a kit — see {@link TerrainScene#rebuild} —
     * and a painted map is the flat outdoor sort, where a storey is a thing it does not have.
     */
    static float groundAt(PathGrid grid, float x, float z) {
        return grid.reliefHeight(new Coord3D(x, z, 0f));
    }
}
