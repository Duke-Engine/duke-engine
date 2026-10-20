package uz.dukeengine.client3d.rpg;

import static uz.dukeengine.client3d.Shade.linear;

import com.jme3.math.ColorRGBA;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;

/** The meshes the bar is cut from: shaded rectangles, washes and open strokes, in the design's own pixels. */
final class PanelMeshes {

    private PanelMeshes() {
    }

    /**
     * A band of colour that fades out at both ends, running across rather than
     * down.
     *
     * <p>Behind the headings — a title, "NARSALAR", "MAHORAT". A ruled box round a
     * heading would say the heading is a thing; a wash that has no edges says only
     * "this is the top of something", which is what a heading is for. Both ends
     * fade so that it belongs to the column rather than to the stone beside it.
     */
    static Mesh sideways(float width, float height, ColorRGBA colour) {
        var clear = new ColorRGBA(colour.r, colour.g, colour.b, 0f);
        var stops = new ColorRGBA[] {clear, colour, colour, clear};
        float[] at = {0f, width * 0.25f, width * 0.75f, width};
        var positions = new float[stops.length * 2 * 3];
        var colours = new float[stops.length * 2 * 4];
        var indices = new int[(stops.length - 1) * 6];
        for (int column = 0; column < stops.length; column++) {
            for (int side = 0; side < 2; side++) {
                int vertex = column * 2 + side;
                positions[vertex * 3] = at[column];
                positions[vertex * 3 + 1] = side == 0 ? 0f : height;
                var linear = linear(stops[column]);
                colours[vertex * 4] = linear.r;
                colours[vertex * 4 + 1] = linear.g;
                colours[vertex * 4 + 2] = linear.b;
                colours[vertex * 4 + 3] = linear.a;
            }
            if (column < stops.length - 1) {
                int base = column * 6;
                int corner = column * 2;
                indices[base] = corner;
                indices[base + 1] = corner + 2;
                indices[base + 2] = corner + 3;
                indices[base + 3] = corner;
                indices[base + 4] = corner + 3;
                indices[base + 5] = corner + 1;
            }
        }
        var mesh = meshOf(positions, indices);
        mesh.setBuffer(VertexBuffer.Type.Color, 4, BufferUtils.createFloatBuffer(colours));
        return mesh;
    }

    /**
     * A rectangle shading evenly from the first colour at the top to the last at
     * the bottom, as a strip of vertex-coloured quads.
     */
    static Mesh gradient(float width, float height, ColorRGBA... rawStops) {
        var stops = new ColorRGBA[rawStops.length];
        for (int i = 0; i < rawStops.length; i++) {
            stops[i] = linear(rawStops[i]);
        }
        int rows = stops.length;
        var positions = new float[rows * 2 * 3];
        var colours = new float[rows * 2 * 4];
        var indices = new int[(rows - 1) * 6];
        for (int row = 0; row < rows; row++) {
            float y = height * (1f - row / (float) (rows - 1));
            for (int side = 0; side < 2; side++) {
                int vertex = row * 2 + side;
                positions[vertex * 3] = side == 0 ? 0f : width;
                positions[vertex * 3 + 1] = y;
                positions[vertex * 3 + 2] = 0f;
                var colour = stops[row];
                colours[vertex * 4] = colour.r;
                colours[vertex * 4 + 1] = colour.g;
                colours[vertex * 4 + 2] = colour.b;
                colours[vertex * 4 + 3] = colour.a;
            }
            if (row < rows - 1) {
                int base = row * 2;
                int at = row * 6;
                indices[at] = base;
                indices[at + 1] = base + 1;
                indices[at + 2] = base + 3;
                indices[at + 3] = base;
                indices[at + 4] = base + 3;
                indices[at + 5] = base + 2;
            }
        }
        var mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(positions));
        mesh.setBuffer(VertexBuffer.Type.Color, 4, BufferUtils.createFloatBuffer(colours));
        mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(indices));
        mesh.updateBound();
        return mesh;
    }

    /** Open line paths, in the same screen-order coordinates. */
    static Mesh strokes(float[][] paths, float height) {
        int segments = 0;
        for (var path : paths) {
            segments += path.length / 2 - 1;
        }
        var positions = new float[segments * 2 * 3];
        int at = 0;
        for (var path : paths) {
            for (int i = 0; i + 3 < path.length; i += 2) {
                positions[at] = path[i];
                positions[at + 1] = height - path[i + 1];
                positions[at + 3] = path[i + 2];
                positions[at + 4] = height - path[i + 3];
                at += 6;
            }
        }
        var mesh = new Mesh();
        mesh.setMode(Mesh.Mode.Lines);
        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(positions));
        mesh.updateBound();
        return mesh;
    }

    static Mesh meshOf(float[] positions, int[] indices) {
        var mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(positions));
        mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(indices));
        mesh.updateBound();
        return mesh;
    }
}
