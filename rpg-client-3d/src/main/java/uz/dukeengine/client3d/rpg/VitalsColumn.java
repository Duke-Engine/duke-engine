package uz.dukeengine.client3d.rpg;

import static uz.dukeengine.client3d.rpg.PanelBrush.atDepth;
import static uz.dukeengine.client3d.rpg.PanelBrush.attach;
import static uz.dukeengine.client3d.rpg.PanelBrush.fillTo;
import static uz.dukeengine.client3d.rpg.PanelBrush.fraction;
import static uz.dukeengine.client3d.rpg.PanelColours.withAlpha;
import static uz.dukeengine.client3d.rpg.PanelMeshes.sideways;
import static uz.dukeengine.client3d.rpg.PanelMetrics.ABOVE_THE_BLOCK;
import static uz.dukeengine.client3d.rpg.PanelMetrics.BAND;
import static uz.dukeengine.client3d.rpg.PanelMetrics.VITALS_WIDTH;
import static uz.dukeengine.client3d.rpg.PanelMetrics.XP_HEIGHT;
import static uz.dukeengine.client3d.rpg.StatBlock.blockHeight;

import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import java.util.ArrayList;
import java.util.Locale;
import uz.dukeengine.client3d.PanelSkin;

/**
 * The column beside the portrait: his name, what he is, the experience bar across the width of it, and under
 * those the block of figures and attributes.
 */
final class VitalsColumn {

    /** How far apart the slants across a filling bar are drawn. */
    private static final float XP_SLANT_PITCH = 14f;
    /** And how wide each of them is: the design's six in fourteen. */
    private static final float XP_SLANT_THICK = 6f;

    private static final float TITLE_HEIGHT = 15f;

    final Node node = new Node("vitals");
    final StatBlock stats;

    /** The name, and what he is under it with the wash it sits on. */
    private final BitmapText name;
    private final BitmapText title;
    private final Node titleLine = new Node("title-line");
    /** The experience bar, kept together so it can go when nothing is levelling. */
    private final Node experienceBar = new Node("experience");
    private final Geometry experienceFill;

    /**
     * The slanted hatching across the filled part of the experience bar, and
     * how wide it was last cut for.
     *
     * <p>Rebuilt rather than scaled: scaling a slant shears it, and a bar whose
     * hatching gets steeper as it fills reads as a rendering fault. Cut again
     * only when the filled width has moved by a whole pixel, which for
     * experience is a few times a floor.
     */
    private final Geometry experienceSlant;
    private int slantedTo = -1;
    /** The two readings inside the experience bar: his level, and how far in. */
    private final BitmapText rank;
    private final BitmapText experienceCount;

    private final PanelBrush brush;
    private final PanelColours colours;

    VitalsColumn(PanelBrush brush, PanelColours colours, StatLook statLook, Node holder) {
        this.brush = brush;
        this.colours = colours;
        holder.attachChild(node);

        // Down the band: who he is, what he is, and the one bar that only ever
        // goes forward. What is LEFT of him is not here any more -- health and
        // mana moved under his own portrait, where they read as facts about the
        // man rather than as two more rows of the column.
        // ★ CENTRED IN THE BAND, not hung from the top of it. The three rows come
        // to less than the band is tall, and hanging them from the top left the
        // slack in one strip along the bottom -- which reads as a column that has
        // run out rather than as one that is arranged. The design centres it, and
        // the portrait beside it is centred too, so the two blocks agree.
        float stack = ABOVE_THE_BLOCK + statLook.gapUnderBar() + blockHeight(statLook);
        float top = BAND - (BAND - stack) / 2f;
        float nameY = top - 27f;
        float titleY = nameY - 18f;
        float experienceY = titleY - 12f - XP_HEIGHT;
        float statsTop = experienceY - statLook.gapUnderBar();
        stats = new StatBlock(brush, colours, statLook, statsTop);
        node.attachChild(stats.node);

        name = brush.carved(22f, colours.bone, 0f, nameY, VITALS_WIDTH, BitmapFont.Align.Center);
        brush.shadowed(node, name,
                brush.carved(22f, colours.drop, 1f, nameY - 1f, VITALS_WIDTH, BitmapFont.Align.Center), 1f);
        // A wash behind the title, which is what stops a second centred line from
        // reading as a second name.
        var wash = brush.shaded("title-wash", sideways(VITALS_WIDTH, TITLE_HEIGHT + 3f,
                withAlpha(colours.gold, 0.14f)));
        attach(titleLine, wash, 0f, titleY - 2f, 0f);
        title = brush.text(13f, colours.gold, 0f, titleY, VITALS_WIDTH, BitmapFont.Align.Center);
        atDepth(title, 1f);
        titleLine.attachChild(title);
        node.attachChild(titleLine);

        // attachChild rather than attach: the trough is placed where it is built, and
        // attach would put it back at nought -- which drew it along the bottom of the
        // column, a dark band behind whatever stood there, instead of under its fill.
        experienceBar.attachChild(brush.trough(0f, experienceY, VITALS_WIDTH, XP_HEIGHT));
        experienceFill = brush.fill("xp-fill", VITALS_WIDTH - 2f, XP_HEIGHT - 2f, colours.arcane);
        experienceFill.setLocalTranslation(1f, experienceY + 1f, 1f);
        experienceBar.attachChild(experienceFill);
        // Over the fill and under the lettering. Faint on purpose: it is there to
        // say "this is filling up" rather than to be looked at, and a bar that
        // reads as a barber's pole is a bar nobody reads the number off.
        experienceSlant = new Geometry("xp-slant", new Mesh());
        experienceSlant.setMaterial(brush.unshaded(new ColorRGBA(1f, 1f, 1f, 0.10f)));
        experienceSlant.setLocalTranslation(1f, experienceY + 1f, 2f);
        experienceBar.attachChild(experienceSlant);
        // Both readings INSIDE the bar, which is the whole reason it is thirty
        // pixels tall. His level at the near end and how far through it he is at
        // the far one -- the two halves of the same sentence, read left to right.
        float lettering = experienceY + (XP_HEIGHT - 15f) / 2f;
        rank = brush.carved(15f, colours.goldHi, 11f, lettering, VITALS_WIDTH, BitmapFont.Align.Left);
        brush.shadowed(experienceBar, rank,
                brush.carved(15f, colours.drop, 12f, lettering - 1f, VITALS_WIDTH, BitmapFont.Align.Left), 3f);
        experienceCount = brush.text(13f, colours.bone, 0f, lettering + 1f, VITALS_WIDTH - 11f,
                BitmapFont.Align.Right);
        brush.shadowed(experienceBar, experienceCount,
                brush.text(13f, colours.drop, 1f, lettering, VITALS_WIDTH - 11f, BitmapFont.Align.Right), 3f);
        node.attachChild(experienceBar);

        // The bezel last and highest: a bar fills from under its own rim, and the
        // reading rides over both. A gauge is the one place the picture is asked
        // for a plain square -- the rim IS the ornament at this size.
        brush.framed(experienceBar, PanelSkin.GAUGE, -1f, experienceY - 1f,
                VITALS_WIDTH + 2f, XP_HEIGHT + 2f, 1.5f);
    }

    /** Where the column stands on the screen, for the cursor over its attributes. */
    void place(float originX, float originY) {
        stats.place(originX, originY);
    }

    /** Take in what the card says of who he is, how far he has come and what he carries. */
    void show(PanelReading reading) {
        brush.say(name, reading.name());
        title.setText(spacedOut(reading.title().toUpperCase(Locale.ROOT)));
        brush.say(rank, reading.rank());
        fillTo(experienceFill, fraction(reading.experience(), reading.needed()));
        // The figures at the far end of the bar, and the hatching across what is
        // filled. A game that gives him nothing to earn sends no total and gets a
        // bare bar rather than "0 / 0", which is a claim about somebody.
        brush.say(experienceCount, reading.needed() <= 0f ? ""
                : Math.round(reading.experience()) + " / " + Math.round(reading.needed()));
        slantTo(fraction(reading.experience(), reading.needed()));
        stats.show(reading.stats(), reading.attributes());
        showOnlyWhatTheCardHas(reading);
    }

    /**
     * Hide every part of the bar the card says nothing about.
     *
     * <p>The panel was written for one card — his — and every block on it always
     * had something in it. A creature's card is shorter on purpose: a skeleton has
     * no experience the player is earning, no skills, no bag and no level. Drawn
     * as empty sockets and a bar at zero, those blocks would not read as "this
     * creature has none of that" but as "the panel has broken".
     *
     * <p>So a block with nothing to say is not drawn at all, and the bar's layout
     * closes the gap where it was. Which is also the honest rule for the four
     * other games this client draws: none of them sends skills or a bag either.
     */
    private void showOnlyWhatTheCardHas(PanelReading reading) {
        boolean named = !reading.title().isBlank();
        // One now, and it is an ornament hung off the furniture rather than part
        // of it: a line of italics under a name. It moves nothing else when it
        // goes.
        //
        // ★ THE EXPERIENCE BAR USED TO BE HANDLED HERE TOO, AND IT WAS A BUG.
        // Two lines owned that one cull hint: fillTo hides a bar with nothing in
        // it, and this showed it again whenever the card had a level at all. The
        // hidden fill was never scaled -- fillTo does not bother scaling what it
        // is hiding -- so what came back was the LAST width it had, which for a
        // fresh hero is the full one. A hero at nought experience was drawn with
        // a full bar. It survived a thin bar of trim for a long time and became
        // obvious the moment the bar was thirty pixels tall and the only thing in
        // the middle of the panel.
        //
        // fillTo owns it alone now, and it is right for the other case too: a
        // skeleton sends no total, so the fraction is nought and the fill is
        // hidden without anybody here saying so.
        titleLine.setCullHint(named ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
    }

    /**
     * Cut the hatching again for a bar this full.
     *
     * <p>Only when it has moved a whole pixel. Rebuilding a mesh is what scaling
     * one would have avoided — but scaling a slant shears it, and hatching that
     * gets steeper as the bar fills reads as something being drawn wrong rather
     * than as the bar filling.
     */
    private void slantTo(float fraction) {
        int wide = Math.round(Math.clamp(fraction, 0f, 1f) * (VITALS_WIDTH - 2f));
        if (wide == slantedTo) {
            return;
        }
        slantedTo = wide;
        experienceSlant.setMesh(slants(wide, XP_HEIGHT - 2f, XP_SLANT_PITCH,
                XP_SLANT_THICK));
        experienceSlant.setCullHint(wide <= 0
                ? Spatial.CullHint.Always : Spatial.CullHint.Inherit);
    }

    /**
     * Parallel slants across a box, each leaning by its own height.
     *
     * <p>Clipped to the box by hand — there is no scissor in this layer — so each
     * slant is a quadrilateral rather than a parallelogram wherever it runs off
     * an end.
     */
    private static Mesh slants(float width, float height, float pitch, float thick) {
        if (width <= 0f) {
            return new Mesh();
        }
        var points = new ArrayList<Float>();
        var order = new ArrayList<Integer>();
        for (float foot = -height; foot < width; foot += pitch) {
            float bottomFrom = Math.clamp(foot, 0f, width);
            float bottomTo = Math.clamp(foot + thick, 0f, width);
            float topFrom = Math.clamp(foot + height, 0f, width);
            float topTo = Math.clamp(foot + height + thick, 0f, width);
            if (bottomTo <= bottomFrom && topTo <= topFrom) {
                continue;
            }
            int corner = points.size() / 3;
            for (float[] at : new float[][] {{bottomFrom, 0f}, {bottomTo, 0f},
                {topTo, height}, {topFrom, height}}) {
                points.add(at[0]);
                points.add(at[1]);
                points.add(0f);
            }
            for (int step : new int[] {0, 1, 2, 0, 2, 3}) {
                order.add(corner + step);
            }
        }
        var mesh = new Mesh();
        var positions = new float[points.size()];
        for (int at = 0; at < positions.length; at++) {
            positions[at] = points.get(at);
        }
        var indices = new int[order.size()];
        for (int at = 0; at < indices.length; at++) {
            indices[at] = order.get(at);
        }
        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(positions));
        mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(indices));
        mesh.updateBound();
        return mesh;
    }

    /**
     * The same words, set with air between the letters.
     *
     * <p>What a designer calls tracking, and jME has none: a {@code BitmapText}
     * advances by whatever the baked font says and there is no way to ask it for
     * more. So the air is put in by hand, which is the oldest trick there is and
     * the only one available here.
     *
     * <p>Set in capitals for the same reason, and by the same hand: a label is
     * read as a shape rather than word by word, and capitals are the shape. It is
     * the one place the client touches a word the game sent — everywhere else
     * the game's own casing stands, because everywhere else the words are being
     * read.
     *
     * <p>Only the title. It is the one line on the bar that is a labelColour rather
     * than something being read -- what he does, under what he is called -- and
     * letting it breathe is what stops a second centred line from competing with
     * the name above it. Doing the same to a name or a number would make both
     * harder to read, which is the opposite of the point.
     *
     * <p>Word gaps widen by themselves and that is the point: the space the
     * words already had is still there, with an inserted one either side of it,
     * so a gap between words comes out three times a gap between letters. Without
     * that "O'q ustasi" would read as one long string with no seam in it.
     */
    static String spacedOut(String words) {
        if (words == null || words.isEmpty()) {
            return "";
        }
        var spaced = new StringBuilder(words.length() * 2);
        for (int at = 0; at < words.length(); at++) {
            if (at > 0) {
                spaced.append(' ');
            }
            spaced.append(words.charAt(at));
        }
        return spaced.toString();
    }
}
