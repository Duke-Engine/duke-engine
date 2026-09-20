package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import org.junit.jupiter.api.Test;

/**
 * A frame drawn at any size keeps the corners it was carved with.
 *
 * <p>This is the one part of the panel's new look that is arithmetic rather than
 * taste, and the one part that fails invisibly: a corner stretched by a few per
 * cent looks like a corner, so the mistake is not seen until someone resizes the
 * window and wonders why the bar has gone soft. The numbers say it outright.
 *
 * <p>Everything here is float arithmetic with no graphics device in it, so a
 * frame at a size nobody has looked at can be asked what it would do.
 */
class NineSliceTest {

    /** A 48-pixel picture with a 16-pixel corner motif, which the pack really has. */
    private static final float TEXTURE = 48f;
    private static final float INSET = 16f;

    private static float[] positions(Mesh mesh) {
        var buffer = mesh.getFloatBuffer(VertexBuffer.Type.Position);
        var out = new float[buffer.limit()];
        buffer.rewind();
        buffer.get(out);
        return out;
    }

    private static float[] texCoords(Mesh mesh) {
        var buffer = mesh.getFloatBuffer(VertexBuffer.Type.TexCoord);
        var out = new float[buffer.limit()];
        buffer.rewind();
        buffer.get(out);
        return out;
    }

    /** The x of vertex {@code column} in the bottom row. */
    private static float x(float[] positions, int column) {
        return positions[column * 3];
    }

    /** The y of the first vertex in row {@code row}. */
    private static float y(float[] positions, int row) {
        return positions[row * 4 * 3 + 1];
    }

    @Test
    void theCornersAreTheSameSizeWhateverTheFrameIs() {
        var small = positions(NineSlice.frame(64f, 64f, TEXTURE, TEXTURE, INSET, 1f,
                NineSlice.Rect.WHOLE));
        var wide = positions(NineSlice.frame(640f, 200f, TEXTURE, TEXTURE, INSET, 1f,
                NineSlice.Rect.WHOLE));

        assertEquals(16f, x(small, 1), 0.001f, "the left corner is the inset, always");
        assertEquals(16f, x(wide, 1), 0.001f, "and ten times the width does not touch it");
        assertEquals(48f, x(small, 2), 0.001f, "nor the right one: 64 - 16");
        assertEquals(624f, x(wide, 2), 0.001f, "640 - 16");
        assertEquals(16f, y(small, 1), 0.001f);
        assertEquals(16f, y(wide, 1), 0.001f, "and a frame three times as short keeps them too");
    }

    @Test
    void onlyTheMiddleStretches() {
        var small = positions(NineSlice.frame(64f, 64f, TEXTURE, TEXTURE, INSET, 1f,
                NineSlice.Rect.WHOLE));
        var wide = positions(NineSlice.frame(640f, 64f, TEXTURE, TEXTURE, INSET, 1f,
                NineSlice.Rect.WHOLE));

        float smallMiddle = x(small, 2) - x(small, 1);
        float wideMiddle = x(wide, 2) - x(wide, 1);

        assertEquals(576f, wideMiddle - smallMiddle, 0.001f,
                "every pixel of the extra 576 should have gone to the middle");
    }

    /**
     * Which part of the picture each cut asks for does not move either.
     *
     * <p>The positions being right and the texture coordinates wrong is the shape
     * the bug actually takes: the mesh is the right size and the picture inside it
     * slides, which reads as a frame drawn slightly off.
     */
    @Test
    void theCutsThroughThePictureNeverMove() {
        var small = texCoords(NineSlice.frame(64f, 64f, TEXTURE, TEXTURE, INSET, 1f,
                NineSlice.Rect.WHOLE));
        var wide = texCoords(NineSlice.frame(640f, 640f, TEXTURE, TEXTURE, INSET, 1f,
                NineSlice.Rect.WHOLE));

        assertEquals(16f / 48f, small[1 * 2], 0.0001f, "the inset as a fraction of the file");
        assertEquals(16f / 48f, wide[1 * 2], 0.0001f);
        assertEquals(1f - 16f / 48f, small[2 * 2], 0.0001f);
        assertEquals(1f - 16f / 48f, wide[2 * 2], 0.0001f);
    }

    /**
     * A frame smaller than two of its own corners is squeezed, not turned inside
     * out.
     *
     * <p>The panel really does ask for this: a 22-pixel power chip framed from a
     * 48-pixel picture would want 32 pixels of corner. Without the squeeze the
     * left corner is drawn past the right one and the frame comes out folded.
     */
    @Test
    void aFrameTooSmallForItsOwnCornersIsSqueezed() {
        var tiny = positions(NineSlice.frame(22f, 22f, TEXTURE, TEXTURE, INSET, 1f,
                NineSlice.Rect.WHOLE));

        assertEquals(11f, x(tiny, 1), 0.001f, "half of it, which is as much as there is");
        assertEquals(11f, x(tiny, 2), 0.001f, "and the two corners meet rather than cross");
        assertTrue(x(tiny, 1) <= x(tiny, 2), "never inside out");
    }

    /**
     * And squeezed by the same amount in both directions.
     *
     * <p>Shrinking each axis to its own limit would be the obvious thing and would
     * give a corner 16 wide and 5 tall — which is not the corner anybody drew, and
     * looks like a stretched one, which is the fault being avoided.
     */
    @Test
    void theSqueezeKeepsTheCornerSquare() {
        var flat = positions(NineSlice.frame(200f, 10f, TEXTURE, TEXTURE, INSET, 1f,
                NineSlice.Rect.WHOLE));

        assertEquals(5f, x(flat, 1), 0.001f, "the short side sets the limit");
        assertEquals(5f, y(flat, 1), 0.001f, "and the long side is held to it");
    }

    /** Scale is what lets one file frame a socket and a bar: it multiplies the inset. */
    @Test
    void scaleLaysTheSameCornerOnMoreHeavily() {
        assertEquals(16f, NineSlice.border(400f, 400f, INSET, 1f), 0.001f);
        assertEquals(32f, NineSlice.border(400f, 400f, INSET, 2f), 0.001f);
        assertEquals(8.8f, NineSlice.border(400f, 400f, INSET, 0.55f), 0.001f);
    }

    @Test
    void theMeshIsWholeAndInRange() {
        var mesh = NineSlice.frame(100f, 60f, TEXTURE, TEXTURE, INSET, 1f, NineSlice.Rect.WHOLE);

        assertEquals(16, mesh.getVertexCount(), "four by four");
        assertEquals(18, mesh.getTriangleCount(), "nine quads");
        var indices = mesh.getIndexBuffer();
        for (int i = 0; i < indices.size(); i++) {
            int vertex = indices.get(i);
            assertTrue(vertex >= 0 && vertex < 16, "index " + i + " points at " + vertex);
        }
    }

    /**
     * A sub-rectangle cuts the same frame out of part of a picture.
     *
     * <p>Nothing asks for this today — every piece is its own small file. It is a
     * parameter so that packing them into one sheet stays a change to what is
     * loaded rather than a change to this, and the way to keep that true is to
     * hold it still here.
     */
    @Test
    void aFrameCanBeCutFromPartOfALargerPicture() {
        var quarter = new NineSlice.Rect(0.5f, 0f, 1f, 0.5f);
        var uvs = texCoords(NineSlice.frame(64f, 64f, TEXTURE, TEXTURE, INSET, 1f, quarter));

        assertEquals(0.5f, uvs[0], 0.0001f, "starts where the sub-rectangle does");
        assertEquals(0.5f + 0.5f * 16f / 48f, uvs[1 * 2], 0.0001f,
                "and the inset is a fraction of the sub-rectangle, not of the sheet");
        assertEquals(1f, uvs[3 * 2], 0.0001f, "and ends where it ends");
    }

    /**
     * The divider stands up by turning its picture, not its geometry.
     *
     * <p>A right angle in floating point is a right angle to within a hundred
     * millionth, and a two-pixel rule sampled off the pixel grid by that much is
     * blurred where the error itself is invisible. Turning which corner of the
     * picture each corner of the quad asks for is exact — and this is what says it
     * really is a quarter turn and not a mirror.
     */
    @Test
    void theDividerIsTurnedThroughItsTextureCoordinates() {
        var mesh = NineSlice.quarterTurn(20f, 90f, NineSlice.Rect.WHOLE);
        var positions = positions(mesh);
        var uvs = texCoords(mesh);

        assertEquals(0f, positions[0], 0.001f, "a plain upright quad");
        assertEquals(20f, positions[1 * 3], 0.001f);
        assertEquals(90f, positions[2 * 3 + 1], 0.001f);

        // Bottom-left of the quad takes the picture's bottom-right: the device at
        // the end of the rule finishes at the foot of the standing divider.
        assertEquals(1f, uvs[0], 0.0001f);
        assertEquals(0f, uvs[1], 0.0001f);
        // Top-left takes the picture's bottom-left, the faded end.
        assertEquals(0f, uvs[2 * 2], 0.0001f);
        assertEquals(0f, uvs[2 * 2 + 1], 0.0001f);
    }
}
