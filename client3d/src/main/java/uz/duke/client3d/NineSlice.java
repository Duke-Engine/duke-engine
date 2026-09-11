package uz.duke.client3d;

import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;

/**
 * One small picture of a frame, drawn at any size without the corners melting.
 *
 * <p>A frame stretched as a single quad is ruined by the stretching: a 48-pixel
 * square blown up to the width of the screen turns its corner carving into a
 * smear and its two-pixel edge into a band. So the picture is cut into nine —
 * four corners, four edges, one middle — and only the parts that may be stretched
 * are. Corners are drawn at a fixed size, the top and bottom edges stretch
 * sideways only, the sides stretch up and down only, and the middle takes what is
 * left. One picture then serves a 42-pixel item socket and an 1180-pixel bar.
 *
 * <p>jMonkeyEngine has nothing for this. {@code com.jme3.ui.Picture} is a plain
 * quad and {@code Common/MatDefs/Gui} is a plain textured shader, so the mesh is
 * built here: sixteen vertices in a four-by-four grid, nine quads, eighteen
 * triangles, one draw call.
 *
 * <p><b>The frame only, never the field.</b> What makes a panel read as carved
 * stone rather than as a coloured box is a painted edge over a shaded surface —
 * so the pictures used here are the ones with a transparent middle, and what
 * shows through is the panel's own gradient. A picture with an opaque middle
 * still works; it simply covers what is under it, which is what such a picture
 * is for.
 *
 * <p>Everything is arithmetic on floats, with no graphics device anywhere near
 * it, so what a frame does at a size nobody has looked at can be asked of it in a
 * test rather than found in a screenshot.
 */
final class NineSlice {

    /**
     * Which part of a texture a piece is cut from, in texture coordinates.
     *
     * <p>The whole of it, today. It is a parameter rather than an assumption
     * because packing the frames into one sheet is a change to what is loaded and
     * not to anything here — and a mesh builder that had hard-coded nought to one
     * would have to be found and reopened to allow it.
     */
    record Rect(float u0, float v0, float u1, float v1) {

        static final Rect WHOLE = new Rect(0f, 0f, 1f, 1f);
    }

    private NineSlice() {
    }

    /**
     * How wide the unstretched border is, in the units the frame is drawn in.
     *
     * <p>{@code inset} is read off the picture in texels — the corner motif is so
     * many pixels across — and {@code scale} says how many drawn units one texel
     * becomes. A bar the width of the screen wants a heavier border than a
     * 42-pixel socket does, from the same file, and that is the number that says
     * so.
     *
     * <p><b>Shrunk to fit, and squarely.</b> Two borders wider than the thing
     * being framed would have the left corner drawn past the right one, which
     * comes out as a frame turned inside out. Both axes are shrunk by the same
     * factor rather than each to its own limit, because a corner that is 16 wide
     * and 6 tall is a corner nobody drew.
     */
    static float border(float width, float height, float inset, float scale) {
        float border = Math.max(0f, inset * scale);
        float room = Math.min(width, height) * 0.5f;
        return border <= room ? border : Math.max(0f, room);
    }

    /** The four cuts across one axis: the two borders and the two outer edges. */
    static float[] stops(float length, float border) {
        return new float[] {0f, border, Math.max(border, length - border), length};
    }

    /**
     * The frame, as a mesh to be drawn at {@code (0, 0)} with y upwards — the way
     * everything else in the panel is drawn.
     *
     * @param texWidth  how many texels wide {@code uv} is, so an inset measured in
     *                  texels can be turned into a texture coordinate
     */
    static Mesh frame(float width, float height, float texWidth, float texHeight,
            float inset, float scale, Rect uv) {
        float border = border(width, height, inset, scale);
        var xs = stops(width, border);
        var ys = stops(height, border);
        // The texture is cut at the inset itself, never at the shrunken border:
        // squeezing a corner is meant to lose room, not to show a different part
        // of the picture.
        float du = texWidth <= 0f ? 0f : (uv.u1() - uv.u0()) * inset / texWidth;
        float dv = texHeight <= 0f ? 0f : (uv.v1() - uv.v0()) * inset / texHeight;
        var us = new float[] {uv.u0(), uv.u0() + du, uv.u1() - du, uv.u1()};
        // v runs up: jME puts a loaded image's bottom row at v = 0, and y is up
        // here too, so the two agree without a flip.
        var vs = new float[] {uv.v0(), uv.v0() + dv, uv.v1() - dv, uv.v1()};
        return grid(xs, ys, us, vs);
    }

    /**
     * A plain quad wearing its texture turned a quarter turn: what was the
     * picture's right-hand end comes out at the bottom.
     *
     * <p>For the one picture in the set that is not a frame: the divider, which is
     * drawn lying down and wanted standing up. Turning the geometry would do it
     * too, and would land the picture on half-pixels — a right angle in floating
     * point is a right angle to within a hundred-millionth, and the blur that
     * comes of sampling a sharp two-pixel line off its grid is visible where the
     * error is not. Rotating which corner of the picture each corner of the quad
     * asks for costs nothing and is exact.
     */
    static Mesh quarterTurn(float width, float height, Rect uv) {
        var xs = new float[] {0f, width};
        var ys = new float[] {0f, height};
        // Bottom-left of the quad takes the picture's bottom-right, and round.
        var corners = new float[][] {
            {uv.u1(), uv.v0()}, {uv.u1(), uv.v1()},
            {uv.u0(), uv.v0()}, {uv.u0(), uv.v1()},
        };
        var positions = new float[4 * 3];
        var texCoords = new float[4 * 2];
        for (int row = 0; row < 2; row++) {
            for (int column = 0; column < 2; column++) {
                int vertex = row * 2 + column;
                positions[vertex * 3] = xs[column];
                positions[vertex * 3 + 1] = ys[row];
                texCoords[vertex * 2] = corners[vertex][0];
                texCoords[vertex * 2 + 1] = corners[vertex][1];
            }
        }
        return meshOf(positions, texCoords, new int[] {0, 1, 3, 0, 3, 2});
    }

    /** Sixteen vertices, nine quads, eighteen triangles, wound anticlockwise. */
    private static Mesh grid(float[] xs, float[] ys, float[] us, float[] vs) {
        var positions = new float[16 * 3];
        var texCoords = new float[16 * 2];
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 4; column++) {
                int vertex = row * 4 + column;
                positions[vertex * 3] = xs[column];
                positions[vertex * 3 + 1] = ys[row];
                positions[vertex * 3 + 2] = 0f;
                texCoords[vertex * 2] = us[column];
                texCoords[vertex * 2 + 1] = vs[row];
            }
        }
        var indices = new int[9 * 6];
        int at = 0;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                int bottomLeft = row * 4 + column;
                int bottomRight = bottomLeft + 1;
                int topLeft = bottomLeft + 4;
                int topRight = topLeft + 1;
                indices[at++] = bottomLeft;
                indices[at++] = bottomRight;
                indices[at++] = topRight;
                indices[at++] = bottomLeft;
                indices[at++] = topRight;
                indices[at++] = topLeft;
            }
        }
        return meshOf(positions, texCoords, indices);
    }

    private static Mesh meshOf(float[] positions, float[] texCoords, int[] indices) {
        var mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(positions));
        mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, BufferUtils.createFloatBuffer(texCoords));
        mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(indices));
        mesh.updateBound();
        return mesh;
    }
}
