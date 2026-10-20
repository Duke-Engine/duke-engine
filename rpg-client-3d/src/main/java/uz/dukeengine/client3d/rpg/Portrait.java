package uz.dukeengine.client3d.rpg;

import static uz.dukeengine.client3d.rpg.PanelBrush.attach;
import static uz.dukeengine.client3d.rpg.PanelColours.withAlpha;
import static uz.dukeengine.client3d.rpg.PanelMeshes.gradient;
import static uz.dukeengine.client3d.rpg.PanelMeshes.meshOf;
import static uz.dukeengine.client3d.rpg.PanelMeshes.strokes;
import static uz.dukeengine.client3d.rpg.PanelMetrics.PORTRAIT;
import static uz.dukeengine.client3d.rpg.PanelMetrics.PORTRAIT_HEIGHT;

import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Texture;
import uz.dukeengine.client3d.PanelSkin;

/** The frame at the left of the hero's block: his silhouette, what else the card names, or the live creature. */
final class Portrait {

    /**
     * The square the drawn figure was drawn in, which is not the frame any more.
     *
     * <p>The frame grew to the design's when it learned to hold a live creature;
     * the silhouette behind it is a hand-plotted set of coordinates and would come
     * out stretched if it were simply scaled up. So it keeps its own box and is
     * centred in the wider one — which is what a fallback should do anyway: look
     * exactly as it always did.
     */
    private static final float FIGURE = 112f;

    /** How far in from the frame the live picture sits, as the design draws it. */
    private static final float PORTRAIT_INSET = 6f;

    final Node node = new Node("portrait");

    /**
     * The archer's silhouette, kept together so it can step aside.
     *
     * <p>The portrait is his; when the player picks out something that is not, the
     * frame stays and the figure in it has to change or the panel is lying about
     * what is selected. The game says what to put there instead — see
     * {@code face} on the status line — and the client draws it from the same
     * vocabulary of line glyphs everything else on the bar is drawn from.
     */
    private final Node figure = new Node("figure");

    /** The socket the live picture is drawn in, empty until one arrives. */
    private final Geometry live;

    /** The picture currently hung in it, compared by identity — see {@link #live}. */
    private Texture livePicture;

    /** What stands in the frame instead of him, when something else is selected. */
    private Geometry faceGlyph;

    /** What the frame is currently showing, so the glyph is not rebuilt every frame. */
    private String facedWith = "";

    private final PanelBrush brush;
    private final PanelColours colours;

    /**
     * A framed portrait, with a live creature in it where the game asked for one
     * and a figure cut into the stone where it did not.
     *
     * <p>It was the drawing alone, and the note here said why: a second camera
     * drawing a live hero into a texture was a real thing to want and a real thing
     * to pay for, and what the frame was <em>for</em> was telling the player which
     * corner of the screen is his. That was true and it was not the whole of it.
     * The frame is also the one place a player looks between clicks, and a person
     * who breathes, stands ready and falls over answers a question a drawing
     * cannot — see {@link HeroPortrait}, which pays for it at a third of the frame
     * rate and nothing at all while the game is not running.
     *
     * <p>Both are built. The drawing is what a game that named no portrait gets,
     * what a creature nobody described one for gets, and what is left standing in
     * the frame if the render target cannot be had.
     */
    Portrait(PanelBrush brush, PanelColours colours, Node holder) {
        this.brush = brush;
        this.colours = colours;
        holder.attachChild(node);
        attach(node, brush.flat("frame", PORTRAIT + 4f, PORTRAIT_HEIGHT + 4f, colours.drop), -2f, -2f, 0f);
        var face = brush.shaded("face",
                gradient(PORTRAIT, PORTRAIT_HEIGHT, colours.portraitTop, colours.portraitBottom));
        attach(node, face, 0f, 0f, 1f);
        attach(node, brush.flat("inner", PORTRAIT + 2f, 2f, colours.portraitRim),
                -1f, PORTRAIT_HEIGHT, 2f);

        // Where the live creature lands, inset the way the design insets it. Built
        // empty and kept that way until a picture arrives: it is under the corner
        // brackets and over the lit recess, so what shows through the gaps is the
        // frame's own stone rather than a grey card.
        live = new Geometry("live", new Quad(PORTRAIT - PORTRAIT_INSET * 2f,
                PORTRAIT_HEIGHT - PORTRAIT_INSET * 2f));
        live.setMaterial(brush.unshaded(ColorRGBA.White));
        live.setCullHint(Spatial.CullHint.Always);
        attach(node, live, PORTRAIT_INSET, PORTRAIT_INSET, 2.5f);

        // Head and shoulders. The figure is drawn in a square as it always was and
        // sits at the bottom of a taller frame, so the extra height is headroom
        // rather than a stretched man.
        var head = new Geometry("head", disc(17f, 16));
        head.setMaterial(brush.unshaded(colours.flesh));
        attach(figure, head, FIGURE / 2f, PORTRAIT_HEIGHT - 44f, 3f);
        var body = new Geometry("body", polygon(shoulders(), FIGURE));
        body.setMaterial(brush.unshaded(colours.flesh));
        attach(figure, body, 0f, 0f, 3f);

        // A bow held at his side: the one line saying which hero this is. The
        // limb bows out to the right and the string cuts straight back.
        var bow = new Geometry("bow", strokes(new float[][] {
            bowLimb(), {64, 26, 62, 62},
        }, FIGURE));
        bow.setMaterial(brush.lines(colours.torch));
        attach(figure, bow, 0f, PORTRAIT_HEIGHT - FIGURE, 4f);
        attach(node, figure, (PORTRAIT - FIGURE) / 2f, 0f, 0f);

        if (brush.framed(node, PanelSkin.PORTRAIT, -3f, -3f,
                PORTRAIT + 6f, PORTRAIT_HEIGHT + 6f, 5f)) {
            return; // a painted frame has corners of its own; two sets would fight
        }
        // Torch-coloured corner brackets, the mark of a framed thing.
        float[][] corners = {{3, 3}, {PORTRAIT - 12, 3}, {3, PORTRAIT_HEIGHT - 12},
            {PORTRAIT - 12, PORTRAIT_HEIGHT - 12}};
        for (int i = 0; i < corners.length; i++) {
            boolean left = i % 2 == 0;
            boolean low = i < 2;
            var bracket = new Geometry("corner", strokes(new float[][] {
                left ? new float[] {0, low ? 9 : 0, 0, low ? 0 : 9, 9, low ? 0 : 9}
                     : new float[] {9, low ? 9 : 0, 9, low ? 0 : 9, 0, low ? 0 : 9},
            }, 0f));
            bracket.setMaterial(brush.lines(withAlpha(colours.torch, 0.75f)));
            attach(node, bracket, corners[i][0], corners[i][1], 5f);
        }
    }

    /**
     * Hang a live creature in the frame, or take the last one down.
     *
     * <p>The panel does not own it, look at it, or know what is in it: somebody
     * else keeps the little scene and the camera — see {@link HeroPortrait} — and
     * this is only the wall it is hung on. Which is what lets the bar be thrown
     * away and rebuilt on every resize without the render target going with it.
     *
     * <p>Compared by identity rather than by value because the same picture
     * arrives every frame: it is one texture being drawn into, not a new one each
     * time, so anything but identity would rebuild the socket sixty times a second.
     *
     * <p>Called <em>before</em> {@link #show}, which is what decides between this
     * and the drawing.
     */
    void live(Texture picture) {
        if (picture == livePicture) {
            return;
        }
        livePicture = picture;
        if (picture == null) {
            live.setCullHint(Spatial.CullHint.Always);
            return;
        }
        live.getMaterial().setTexture("ColorMap", picture);
        live.setCullHint(Spatial.CullHint.Inherit);
    }

    /** Draw the figure the card names, or his own silhouette when it names none. */
    void show(String named, boolean anybody) {
        if (livePicture != null) {
            // Somebody live is in the frame, so neither drawing belongs in it. The
            // frame, its brackets and the badge stay: those are the furniture.
            figure.setCullHint(Spatial.CullHint.Always);
            dropFaceGlyph();
            return;
        }
        if (!anybody) {
            // Nobody is selected: an empty frame, and no figure in it. The frame
            // stays because it is furniture; the face is what the card carries.
            figure.setCullHint(Spatial.CullHint.Always);
            dropFaceGlyph();
            return;
        }
        boolean his = named == null || named.isBlank();
        figure.setCullHint(his ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        if (his) {
            dropFaceGlyph();
            return;
        }
        if (named.equals(facedWith)) {
            return;
        }
        dropFaceGlyph();
        facedWith = named;
        faceGlyph = new Geometry("face-glyph", PanelGlyphs.of(named, PORTRAIT * 0.62f));
        faceGlyph.setMaterial(brush.lines(colours.bone));
        attach(node, faceGlyph, PORTRAIT / 2f, PORTRAIT_HEIGHT / 2f, 4f);
    }

    private void dropFaceGlyph() {
        if (faceGlyph != null) {
            faceGlyph.removeFromParent();
            faceGlyph = null;
            facedWith = "";
        }
    }

    /** A filled circle, centred on its own origin — the portrait's head. */
    private static Mesh disc(float radius, int segments) {
        var positions = new float[(segments + 2) * 3];
        for (int i = 0; i <= segments; i++) {
            float angle = FastMath.TWO_PI * i / segments;
            positions[(i + 1) * 3] = FastMath.cos(angle) * radius;
            positions[(i + 1) * 3 + 1] = FastMath.sin(angle) * radius;
        }
        var indices = new int[segments * 3];
        for (int i = 0; i < segments; i++) {
            indices[i * 3 + 1] = i + 1;
            indices[i * 3 + 2] = i + 2;
        }
        return meshOf(positions, indices);
    }

    /**
     * A filled shape from a list of points written in screen order — y downward,
     * as a designer writes them — flipped once here.
     */
    private static Mesh polygon(float[] points, float height) {
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
        return meshOf(positions, indices);
    }

    /**
     * Shoulders: a dome rather than a peak, so the figure reads as a person seen
     * from the front rather than as a roof.
     */
    private static float[] shoulders() {
        int steps = 12;
        var points = new float[(steps + 3) * 2];
        points[0] = 28f;
        points[1] = 96f;
        for (int i = 0; i <= steps; i++) {
            float angle = FastMath.PI * i / steps;
            points[(i + 1) * 2] = 48f - FastMath.cos(angle) * 20f;
            points[(i + 1) * 2 + 1] = 96f - 14f - FastMath.sin(angle) * 10f;
        }
        points[(steps + 2) * 2] = 68f;
        points[(steps + 2) * 2 + 1] = 96f;
        return points;
    }

    /** The bow's limb: a shallow curve from shoulder height down past his hip. */
    private static float[] bowLimb() {
        int steps = 10;
        var points = new float[(steps + 1) * 2];
        for (int i = 0; i <= steps; i++) {
            float along = i / (float) steps;
            points[i * 2] = 64f + FastMath.sin(FastMath.PI * along) * 8f;
            points[i * 2 + 1] = 26f + 36f * along;
        }
        return points;
    }
}
