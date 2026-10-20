package uz.dukeengine.client3d.rpg;

import static uz.dukeengine.client3d.rpg.PanelMeshes.gradient;
import static uz.dukeengine.client3d.rpg.PanelMeshes.sideways;
import static uz.dukeengine.client3d.Shade.linear;
import static uz.dukeengine.client3d.rpg.PanelColours.rgb;
import static uz.dukeengine.client3d.rpg.PanelColours.withAlpha;
import static uz.dukeengine.client3d.rpg.PanelMetrics.HEADING_SIZE;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.font.Rectangle;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.material.RenderState.FaceCullMode;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Texture;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import uz.dukeengine.client3d.Lettering;
import uz.dukeengine.client3d.NineSlice;
import uz.dukeengine.client3d.PanelSkin;
import uz.dukeengine.client3d.Shade;

/**
 * What the pieces of the hero's bar are cut and lettered with: flat and shaded rectangles, the materials they
 * are drawn in, lines of text in the faces the game named, the painted frames its skin gives, and the corner
 * marks of a socket waiting for a click.
 *
 * <p>One of them is shared by the whole bar, so a picture the game named and the client could not find is
 * warned about once, and a line with a shadow under it is written to together.
 */
final class PanelBrush {

    /** How long an arm of a corner bracket is, and how thick. */
    private static final float BRACKET = 11f;
    private static final float BRACKET_THICK = 2f;
    /** How far the brackets sit outside the socket. */
    private static final float BRACKET_OUT = 5f;
    /** Pictures the game named and the client could not find — warned about once each. */
    private final Set<String> missingIcons = new HashSet<>();

    /** What the panel's edges are painted with, if the game asked for anything. */
    final PanelSkin skin;

    private final AssetManager assets;
    private final BitmapFont font;
    /** The face his name is set in, which is the plain one unless a game said. */
    private final BitmapFont display;
    /** The faces the game named for the bar's lines and for its names and headings; null for the bitmap fonts. */
    private final Lettering lettering;
    private final Lettering titleLettering;
    /**
     * The scale a named face is baked for: the bar's own wherever a narrow window does not squeeze it, and known
     * before any line is made, where the bar's own is measured from the lines once they are.
     */
    private final float letteringScale;

    /**
     * The dark copy under a line of lettering, by the line itself.
     *
     * <p>Only the readings that sit ON something — inside a gauge, over a fill —
     * rather than on bare stone. Bone letters on a red bar are not dim, they are
     * hard to be SURE of, and the eye spends a fraction of a second on a number
     * meant to be taken in without one. Every reading in the design has this and
     * the panel had none.
     */
    private final Map<BitmapText, BitmapText> shadows = new IdentityHashMap<>();
    private final PanelColours colours;

    PanelBrush(AssetManager assets, BitmapFont font, BitmapFont display, PanelSkin skin, Lettering lettering,
            Lettering titleLettering, float letteringScale, PanelColours colours) {
        this.assets = assets;
        this.font = font;
        this.display = display;
        this.skin = skin;
        this.lettering = lettering;
        this.titleLettering = titleLettering;
        this.letteringScale = letteringScale;
        this.colours = colours;
    }

    /** The picture the game named, or null for one the client cannot find, which the log has said once. */
    Texture texture(String path) {
        return PanelSkin.texture(assets, path, missingIcons);
    }

    /**
     * A flat rectangle of one colour. Everything in the panel that is not a
     * gradient, a glyph or a shadow is one of these.
     */
    Geometry flat(String what, float width, float height, ColorRGBA colour) {
        var geometry = new Geometry(what, new Quad(width, height));
        geometry.setMaterial(unshaded(colour));
        return geometry;
    }

    Material unshaded(ColorRGBA colour) {
        var material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setColor("Color", linear(colour));
        material.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        material.getAdditionalRenderState().setFaceCullMode(FaceCullMode.Off);
        material.getAdditionalRenderState().setDepthTest(false);
        return material;
    }

    private Material vertexColoured() {
        var material = unshaded(ColorRGBA.White);
        material.setBoolean("VertexColor", true);
        return material;
    }

    /** A geometry drawn in the colours its mesh carries at each vertex. */
    Geometry shaded(String name, Mesh mesh) {
        var geometry = new Geometry(name, mesh);
        geometry.setMaterial(vertexColoured());
        return geometry;
    }

    Material lines(ColorRGBA colour) {
        var material = unshaded(colour);
        material.getAdditionalRenderState().setLineWidth(2f);
        return material;
    }

    /**
     * Attach a line with its shadow, and remember the pair.
     *
     * <p>The shadow is a second {@code BitmapText} because jME has no other kind:
     * a bitmap font is drawn as it was baked, and what is not in the glyph cannot
     * be added to it. Remembered here rather than returned so that nothing which
     * writes to the panel has to know there are two of anything — see {@link #say}.
     */
    void shadowed(Node parent, BitmapText line, BitmapText under, float depth) {
        atDepth(under, depth - 0.1f);
        parent.attachChild(under);
        atDepth(line, depth);
        parent.attachChild(line);
        shadows.put(line, under);
    }

    /** Write to a line and to whatever is underneath it. */
    void say(BitmapText line, String words) {
        line.setText(words);
        var under = shadows.get(line);
        if (under != null) {
            under.setText(words);
        }
    }

    /** The same, in the face a game named for its own lettering. */
    BitmapText carved(float size, ColorRGBA colour, float x, float y, float width,
            BitmapFont.Align align) {
        return lettered(of(titleLettering, display, size), size, colour, x, y, width, align);
    }

    /** A sunken trough for a bar to sit in. */
    Geometry trough(float x, float y, float width, float height) {
        var geometry = flat("trough", width, height, colours.stoneDeep);
        geometry.setLocalTranslation(x, y, 0f);
        return geometry;
    }

    /**
     * The lit part of a bar, drawn at full width and scaled down horizontally.
     * The gradient runs top to bottom, so scaling across it costs nothing.
     */
    Geometry fill(String name, float width, float height, ColorRGBA colour) {
        return shaded(name, gradient(width, height, Shade.gauge(colour)));
    }

    /** The gold heading over a block, on a wash that fades out at both ends. */
    BitmapText heading(Node block, float width) {
        var wash = shaded("heading-wash", sideways(width, HEADING_SIZE + 5f,
                withAlpha(colours.gold, 0.16f)));
        attach(block, wash, 0f, -3f, 0f);
        var word = text(HEADING_SIZE, colours.gold, 0f, 0f, width, BitmapFont.Align.Center);
        atDepth(word, 1f);
        block.attachChild(word);
        return word;
    }

    /** The four marks themselves, at whatever size they are framing. */
    Node reticle(float size) {
        var brackets = new Node("reticle");
        float out = BRACKET_OUT;
        float far = size + out - BRACKET;
        // x, y of each corner, then which way its arms run from there.
        float[][] corners = {
            {-out, far, 1f, 1f},        // top left
            {far, far, -1f, 1f},        // top right
            {-out, -out, 1f, -1f},      // bottom left
            {far, -out, -1f, -1f},      // bottom right
        };
        for (var corner : corners) {
            float x = corner[0];
            float y = corner[1];
            boolean leftward = corner[2] < 0f;
            boolean downward = corner[3] < 0f;
            // The arm that runs along the top or bottom edge...
            attach(brackets, flat("brk", BRACKET, BRACKET_THICK, colours.selHi),
                    x, downward ? y : y + BRACKET - BRACKET_THICK, 0f);
            // ...and the one that runs down the side.
            attach(brackets, flat("brk", BRACKET_THICK, BRACKET, colours.selHi),
                    leftward ? x + BRACKET - BRACKET_THICK : x, y, 0f);
        }
        return brackets;
    }

    /**
     * A picture to lay in a socket, at {@code share} of its width and starting in
     * {@code colour}.
     *
     * <p>Shared by the skills, the four order buttons and the figures under the
     * bars, which all used to draw their own way: the skills from a file, the
     * other two from line drawings held in this class under names their own side
     * of the wire had spelt out in Java. One path, so that a game which wants to
     * change what an order looks like changes a line in its own file rather than
     * a mesh in the client.
     */
    Geometry picture(String icon, float size, float share, ColorRGBA colour) {
        var texture = texture(icon);
        if (texture == null) {
            return null;
        }
        float side = size * share;
        var quad = new Geometry("icon", new Quad(side, side));
        var material = unshaded(colour);
        material.setTexture("ColorMap", texture);
        quad.setMaterial(material);
        return quad;
    }

    /**
     * Lay a painted frame over something, and say whether it took.
     *
     * <p>The caller keeps its own carved rim and draws it only when this says no,
     * so a game that named no skin — or named a file that is not there — gets the
     * panel exactly as it was. That is the same bargain {@link #picture} strikes
     * for a skill's icon, and it is struck the same way for the same reason: the
     * game names its own art and the client cannot check the spelling, so being
     * wrong has to cost a line in the log rather than the panel.
     *
     * <p>The picture is white and the colour comes from the piece, which is what
     * lets one file be a gold rim here and a bone one there.
     */
    boolean framed(Node node, String piece, float x, float y,
            float width, float height, float z) {
        return paint(node, piece, x, y, width, height, z) != null;
    }

    /** The same, handing back the frame for anything that has to re-colour it later. */
    Geometry paint(Node node, String piece, float x, float y,
            float width, float height, float z) {
        var painted = skin.piece(piece);
        if (painted == null) {
            return null;
        }
        var texture = texture(painted.texture());
        if (texture == null) {
            return null;
        }
        // Clamped so the stretched middle cannot reach round and sample the far
        // edge, which shows up as a ghost of the opposite corner.
        texture.setWrap(Texture.WrapMode.EdgeClamp);
        var image = texture.getImage();
        var mesh = piece.equals(PanelSkin.DIVIDER)
                ? NineSlice.quarterTurn(width, height, NineSlice.Rect.WHOLE)
                : NineSlice.frame(width, height, image.getWidth(), image.getHeight(),
                        painted.inset(), painted.scale(), NineSlice.Rect.WHOLE);
        var geometry = new Geometry("frame-" + piece, mesh);
        var material = unshaded(rgb(painted.tint().getRGB()));
        material.setTexture("ColorMap", texture);
        geometry.setMaterial(material);
        attach(node, geometry, x, y, z);
        return geometry;
    }

    /** How wide a string comes out in the panel's lettering at this size. */
    float widthOf(String words, float size) {
        var letters = of(lettering, font, size);
        var line = new BitmapText(letters.font());
        line.setSize(letters.size());
        line.setText(words == null ? "" : words);
        return line.getLineWidth() / letters.perDesign();
    }

    /**
     * Set a bar to a fraction of its trough, taking an empty one away entirely
     * rather than squashing it to nothing — a geometry scaled to zero is a
     * degenerate bound, and a hero at no health is exactly when it happens.
     */
    static void fillTo(Geometry bar, float fraction) {
        bar.setCullHint(fraction <= 0f ? Spatial.CullHint.Always : Spatial.CullHint.Inherit);
        if (fraction > 0f) {
            bar.setLocalScale(fraction, 1f, 1f);
        }
    }

    /**
     * A line of text in a box, so alignment does the placing. The size is the
     * design's pixel size; the font's own is whatever the client shipped.
     */
    BitmapText text(float size, ColorRGBA colour, float x, float y, float width,
            BitmapFont.Align align) {
        return lettered(of(lettering, font, size), size, colour, x, y, width, align);
    }

    BitmapText lettered(Letters letters, float size, ColorRGBA colour, float x, float y, float width,
            BitmapFont.Align align) {
        var line = new BitmapText(letters.font());
        line.setLocalScale(1f / letters.perDesign());
        line.setSize(letters.size());
        line.setColor(linear(colour));
        box(line, x, y + size, width, size * 1.4f);
        line.setAlignment(align);
        return line;
    }

    /**
     * What a line of a design size is drawn from: a named face baked at the pixel size the line lands at, drawn at
     * its own size and scaled back against the bar's, one pixel of it to one of the screen; or the bitmap font, at the
     * design's size, scaled with the bar.
     *
     * @param perDesign how many of the line's own pixels make one of the design's
     */
    record Letters(BitmapFont font, float size, float perDesign) {
    }

    Letters of(Lettering face, BitmapFont bitmap, float size) {
        return letters(face, bitmap, size, letteringScale);
    }

    static Letters letters(Lettering face, BitmapFont bitmap, float size, float scale) {
        var baked = face == null ? null : face.at(Math.round(size * scale));
        return baked == null ? new Letters(bitmap, size, 1f)
                : new Letters(baked, baked.getCharSet().getRenderedSize(), scale);
    }

    /** A line's box, in the design's pixels whatever pixels the line is drawn in. */
    static void box(BitmapText line, float x, float y, float width, float height) {
        float perDesign = 1f / line.getLocalScale().x;
        line.setBox(new Rectangle(x * perDesign, y * perDesign, width * perDesign, height * perDesign));
    }

    /** A line brought to a depth of its own, so it is drawn over what it is written on. */
    static void atDepth(BitmapText line, float z) {
        var at = line.getLocalTranslation();
        line.setLocalTranslation(at.x, at.y, z);
    }

    static void attach(Node parent, Spatial child, float x, float y, float z) {
        child.setLocalTranslation(x, y, z);
        parent.attachChild(child);
    }

    static float fraction(float part, float whole) {
        return whole <= 0f ? 0f : Math.clamp(part / whole, 0f, 1f);
    }
}
