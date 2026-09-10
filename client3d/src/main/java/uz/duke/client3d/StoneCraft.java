package uz.duke.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.font.Rectangle;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.material.RenderState.FaceCullMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.shape.Quad;
import com.jme3.util.BufferUtils;

/**
 * The few shapes everything carved out of stone is made of.
 *
 * <p>The hero's bar was drawn from these first — a lit gradient with a bright top
 * lip and a shadow at its foot reads as stone catching a room's light, and a
 * dozen of them read as one slab with things cut into it. The menus want exactly
 * the same vocabulary, and a second copy of it would be two panels that drift
 * apart over a year of small edits.
 *
 * <p>Nothing here knows what it is drawing. It makes a rectangle of a colour, a
 * rectangle shading between colours, a triangle, a line of text — and what those
 * add up to is the caller's business.
 *
 * <p>Everything is flat, depth-tested off, alpha blended: this is the screen, not
 * the world, and it is drawn in the order it is attached.
 */
final class StoneCraft {

    // ---- the palette a dungeon is lit by ----

    static final ColorRGBA STONE_DEEP = rgb(0x16130F);
    static final ColorRGBA STONE = rgb(0x2B2620);
    static final ColorRGBA STONE_LIT = rgb(0x3D362C);
    static final ColorRGBA STONE_EDGE = rgb(0x5A5042);
    static final ColorRGBA TORCH = rgb(0xE8A33D);
    static final ColorRGBA TORCH_HOT = rgb(0xFFD089);
    static final ColorRGBA BONE = rgb(0xD9CFBA);
    static final ColorRGBA BLOOD = rgb(0xA8322B);
    static final ColorRGBA MUTE = rgb(0x7A7062);

    private final AssetManager assets;
    private final BitmapFont font;

    StoneCraft(AssetManager assets, BitmapFont font) {
        this.assets = assets;
        this.font = font;
    }

    BitmapFont font() {
        return font;
    }

    // ---- materials ----

    Material unshaded(ColorRGBA colour) {
        var material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setColor("Color", linear(colour));
        material.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        material.getAdditionalRenderState().setFaceCullMode(FaceCullMode.Off);
        material.getAdditionalRenderState().setDepthTest(false);
        return material;
    }

    Material vertexColoured() {
        var material = unshaded(ColorRGBA.White);
        material.setBoolean("VertexColor", true);
        return material;
    }

    Material lines(ColorRGBA colour) {
        var material = unshaded(colour);
        material.getAdditionalRenderState().setLineWidth(2f);
        return material;
    }

    // ---- shapes ----

    /** A flat rectangle of one colour, which most of a carved panel is. */
    Geometry flat(String what, float width, float height, ColorRGBA colour) {
        var geometry = new Geometry(what, new Quad(width, height));
        geometry.setMaterial(unshaded(colour));
        return geometry;
    }

    /** A rectangle shading from the first colour at the top to the last at the foot. */
    Geometry shaded(String what, float width, float height, ColorRGBA... stops) {
        var geometry = new Geometry(what, gradient(width, height, stops));
        geometry.setMaterial(vertexColoured());
        return geometry;
    }

    /**
     * A slab of stone: shaded face, a lit lip along the top, a shadow at its foot.
     *
     * <p>The three together are what make it read as a thing catching light rather
     * than as a rectangle of paint. It is the panel's whole trick, and the menus'.
     */
    Node slab(String what, float width, float height) {
        var node = new Node(what);
        attach(node, shaded(what + "-face", width, height, STONE_LIT, STONE), 0f, 0f, 0f);
        attach(node, flat(what + "-lip", width, 2f, STONE_EDGE), 0f, height - 2f, 1f);
        attach(node, flat(what + "-foot", width, 2f, rgb(0x0A0806)), 0f, 0f, 1f);
        return node;
    }

    /**
     * A pool of torchlight: a soft warm circle, brightest in the middle.
     *
     * <p>Made of rings rather than a texture because it is two colours and a
     * falloff, and a PNG for that is a file to ship, load and keep in memory for
     * something a hundred vertices say exactly as well.
     */
    Geometry glow(String what, float radius, ColorRGBA colour, float strength) {
        int segments = 28;
        var positions = new float[(segments + 2) * 3];
        var colours = new float[(segments + 2) * 4];
        var lit = linear(colour);
        colours[0] = lit.r;
        colours[1] = lit.g;
        colours[2] = lit.b;
        colours[3] = strength;
        for (int i = 0; i <= segments; i++) {
            float angle = FastMath.TWO_PI * i / segments;
            positions[(i + 1) * 3] = FastMath.cos(angle) * radius;
            positions[(i + 1) * 3 + 1] = FastMath.sin(angle) * radius;
            int at = (i + 1) * 4;
            colours[at] = lit.r;
            colours[at + 1] = lit.g;
            colours[at + 2] = lit.b;
            colours[at + 3] = 0f; // dark at the rim, so it has no edge
        }
        var indices = new int[segments * 3];
        for (int i = 0; i < segments; i++) {
            indices[i * 3 + 1] = i + 1;
            indices[i * 3 + 2] = i + 2;
        }
        var mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(positions));
        mesh.setBuffer(VertexBuffer.Type.Color, 4, BufferUtils.createFloatBuffer(colours));
        mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(indices));
        mesh.updateBound();
        var geometry = new Geometry(what, mesh);
        geometry.setMaterial(vertexColoured());
        return geometry;
    }

    /** A triangle pointing left or right — the mark beside a chosen line. */
    Geometry arrowhead(String what, float size, boolean pointingRight, ColorRGBA colour) {
        float[] points = pointingRight
                ? new float[] {0f, 0f, size, size / 2f, 0f, size}
                : new float[] {size, 0f, 0f, size / 2f, size, size};
        var geometry = new Geometry(what, polygon(points, size));
        geometry.setMaterial(unshaded(colour));
        return geometry;
    }

    /** A filled shape from points written in screen order — y downward. */
    static Mesh polygon(float[] points, float height) {
        int count = points.length / 2;
        var positions = new float[count * 3];
        for (int i = 0; i < count; i++) {
            positions[i * 3] = points[i * 2];
            positions[i * 3 + 1] = height - points[i * 2 + 1];
        }
        var indices = new int[(count - 2) * 3];
        for (int i = 0; i < count - 2; i++) {
            indices[i * 3] = 0;
            indices[i * 3 + 1] = i + 1;
            indices[i * 3 + 2] = i + 2;
        }
        var mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(positions));
        mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(indices));
        mesh.updateBound();
        return mesh;
    }

    private static Mesh gradient(float width, float height, ColorRGBA... rawStops) {
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

    // ---- text ----

    /**
     * A line of text in a box, so alignment does the placing.
     *
     * <p>{@code y} is the top of the line, counted the way a design is written,
     * and the box is deep enough that a descender is not clipped.
     */
    /**
     * How wide a line of this would be, with nothing boxing it in.
     *
     * <p>Needed because {@link com.jme3.font.BitmapText#getLineWidth} on text that
     * has been given a box reports the box, not the letters -- so a plaque sized
     * to fit its title comes out the width of the screen.
     */
    float widthOf(BitmapFont face, float size, String words) {
        var line = new BitmapText(face == null ? font : face);
        line.setSize(size);
        line.setText(words);
        return line.getLineWidth();
    }

    BitmapText text(BitmapFont face, float size, ColorRGBA colour, float x, float y,
            float width, BitmapFont.Align align) {
        var line = new BitmapText(face == null ? font : face);
        line.setSize(size);
        line.setColor(linear(colour));
        line.setBox(new Rectangle(x, y + size, width, size * 1.4f));
        line.setAlignment(align);
        return line;
    }

    // ---- odds and ends ----

    static void attach(Node parent, Spatial child, float x, float y, float z) {
        child.setLocalTranslation(x, y, z);
        parent.attachChild(child);
    }

    static ColorRGBA rgb(int hex) {
        return new ColorRGBA(((hex >> 16) & 0xFF) / 255f, ((hex >> 8) & 0xFF) / 255f,
                (hex & 0xFF) / 255f, 1f);
    }

    static ColorRGBA fade(ColorRGBA colour, float alpha) {
        return new ColorRGBA(colour.r, colour.g, colour.b, alpha);
    }

    /**
     * The same colour, ready for a shader.
     *
     * <p>jME's pipeline works in linear light while a palette is written in the
     * sRGB a designer picked it in, so a colour handed over raw comes out lighter
     * than the one chosen. Converted once, here, rather than by every caller
     * remembering to.
     */
    static ColorRGBA linear(ColorRGBA colour) {
        return new ColorRGBA(toLinear(colour.r), toLinear(colour.g), toLinear(colour.b),
                colour.a);
    }

    private static float toLinear(float channel) {
        return channel <= 0.04045f
                ? channel / 12.92f
                : (float) Math.pow((channel + 0.055) / 1.055, 2.4);
    }
}
