package uz.duke.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.font.Rectangle;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Quad;
import java.util.ArrayList;
import java.util.List;

/**
 * The card that hangs over a skill slot while the cursor is on it: what the
 * skill is called, what it does, and what one more point would change.
 *
 * <p><b>The last of those is the whole point of it.</b> A player asked to spend a
 * level on one of four skills, shown only their names, is not making a decision —
 * he is guessing and finding out afterwards. So every figure that moves with a
 * rank is shown twice, now and next, with the second in the colour of a gain.
 *
 * <p>Nothing here is written by this client. The name, the sentence, the row
 * labels and every number arrive finished on the status line, because the client
 * serves three other games and has no business knowing which language this one
 * speaks — or what a cooldown is. What it decides is where the card goes, how
 * tall it has to be, and which half of a row is green.
 *
 * <p>Built once and re-dressed, like everything else on the bar. It is a dozen
 * quads and a handful of {@link BitmapText}, and a card that rebuilt itself every
 * frame the cursor moved would be the most expensive thing on screen for the
 * least reason.
 */
final class SkillTip {

    /** Wide enough for a sentence at this size without becoming a paragraph. */
    private static final float WIDTH = 280f;
    private static final float PAD = 13f;
    /** How far above the slot it floats, so it never covers what it describes. */
    private static final float LIFT = 12f;

    private static final float NAME_SIZE = 15f;
    private static final float AT_SIZE = 12f;
    private static final float BLURB_SIZE = 13.5f;
    private static final float ROW_SIZE = 13f;
    private static final float FOOT_SIZE = 12.5f;
    private static final float ROW_STEP = 17f;
    private static final float GAP = 8f;

    private static final ColorRGBA EDGE = HeroPanel.rgb(0x0A0806);
    private static final ColorRGBA FACE_TOP = HeroPanel.rgb(0x2E2820);
    private static final ColorRGBA FACE_FOOT = HeroPanel.rgb(0x191510);
    private static final ColorRGBA RULE = HeroPanel.rgb(0x3A322A);
    private static final ColorRGBA NAME = HeroPanel.rgb(0xF0D48A);
    private static final ColorRGBA QUIET = HeroPanel.rgb(0x8A7F6C);
    private static final ColorRGBA BODY = HeroPanel.rgb(0xA69B87);
    private static final ColorRGBA VALUE = HeroPanel.rgb(0xDCD2BC);
    private static final ColorRGBA GAIN = HeroPanel.rgb(0x7FBF6A);
    private static final ColorRGBA FOOT = HeroPanel.rgb(0xC9A24B);
    private static final ColorRGBA FOOT_DEAD = HeroPanel.rgb(0x6E6555);

    /** As many rows as any skill in any game is likely to want. */
    private static final int MOST_ROWS = 6;

    private final Node node = new Node("tip");
    private final AssetManager assets;
    private final Geometry edge;
    private final Geometry face;
    private final Geometry rule;
    private final BitmapText name;
    private final BitmapText at;
    private final BitmapText blurb;
    private final BitmapText foot;
    private final List<BitmapText> labels = new ArrayList<>();
    private final List<BitmapText> values = new ArrayList<>();

    SkillTip(AssetManager assets, BitmapFont font, Node parent) {
        this.assets = assets;
        edge = plate(EDGE);
        face = plate(FACE_TOP);
        rule = plate(RULE);
        node.attachChild(edge);
        node.attachChild(face);
        node.attachChild(rule);
        name = line(font, NAME_SIZE, NAME, BitmapFont.Align.Left);
        at = line(font, AT_SIZE, QUIET, BitmapFont.Align.Left);
        blurb = line(font, BLURB_SIZE, BODY, BitmapFont.Align.Left);
        foot = line(font, FOOT_SIZE, FOOT, BitmapFont.Align.Left);
        for (int i = 0; i < MOST_ROWS; i++) {
            labels.add(line(font, ROW_SIZE, QUIET, BitmapFont.Align.Left));
            values.add(line(font, ROW_SIZE, VALUE, BitmapFont.Align.Right));
        }
        this.parent = parent;
    }

    private final Node parent;

    /**
     * Taken off the scene rather than merely hidden, which is not the usual
     * choice on this bar and is the right one here.
     *
     * <p>A {@link BitmapText} with nothing in it has no bound at all, and jME
     * throws rather than shrugging the first time anything asks a parent of one
     * how big it is. Culling leaves it on the graph and so leaves the question
     * askable; detaching does not. A card is one node and is on screen only
     * while the cursor rests on a socket, so there is nothing here worth the
     * trouble of keeping warm.
     */
    void hide() {
        node.removeFromParent();
    }

    boolean showing() {
        return node.getParent() != null;
    }

    /** For the tests: how many rows of figures are on screen. */
    int rowsShown() {
        int shown = 0;
        for (var label : labels) {
            if (label.getCullHint() != Spatial.CullHint.Always) {
                shown++;
            }
        }
        return shown;
    }

    /**
     * Draw the card for one slot, with its bottom-left corner at {@code x, y}.
     *
     * <p>Laid out from the bottom up, because that is the end that is pinned: the
     * card sits above the slot and grows upward, so a skill with four rows and one
     * with none both start at the same place and the one that has more to say is
     * simply taller. Laying it out downward from a fixed top would have the card
     * move every time the cursor crossed a slot with a longer sentence.
     */
    void show(Reading tip, float x, float y, float scale, float screenWidth) {
        if (tip == null || tip.name().isEmpty()) {
            hide();
            return;
        }
        if (node.getParent() == null) {
            parent.attachChild(node);
        }
        // Measured before it is placed: the sentence wraps to however many lines
        // it wraps to, and the card is whatever that comes to.
        blurb.setBox(new Rectangle(0f, 0f, WIDTH - PAD * 2f, Float.MAX_VALUE));
        blurb.setText(tip.blurb());
        float blurbHeight = tip.blurb().isEmpty() ? 0f : blurb.getHeight() + GAP;

        int rows = Math.min(tip.rows().size(), MOST_ROWS);
        float footHeight = tip.foot().isEmpty() ? 0f : FOOT_SIZE + GAP + 1f;
        float height = PAD + NAME_SIZE + AT_SIZE + 2f + blurbHeight
                + rows * ROW_STEP + footHeight + PAD;

        // Kept on screen: a card over the rightmost slot would otherwise hang
        // half of itself off the edge of the window, which is where the slot the
        // player is most likely to be reaching for happens to live.
        float left = Math.min(x, screenWidth / scale - WIDTH - 4f);
        left = Math.max(4f, left);
        node.setLocalTranslation(left * scale, y * scale, 0f);
        node.setLocalScale(scale);

        edge.setLocalScale(WIDTH + 4f, height + 4f, 1f);
        edge.setLocalTranslation(-2f, -2f, 0f);
        face.setLocalScale(WIDTH, height, 1f);
        face.setLocalTranslation(0f, 0f, 1f);

        float top = height - PAD;
        place(name, PAD, top - NAME_SIZE, WIDTH - PAD * 2f);
        name.setText(tip.name());
        top -= NAME_SIZE + 1f;
        place(at, PAD, top - AT_SIZE, WIDTH - PAD * 2f);
        at.setText(tip.at());
        top -= AT_SIZE + GAP;

        if (blurbHeight > 0f) {
            blurb.setBox(new Rectangle(PAD, top, WIDTH - PAD * 2f, blurb.getHeight()));
            blurb.setLocalTranslation(0f, 0f, 2f);
            blurb.setCullHint(Spatial.CullHint.Inherit);
            top -= blurbHeight;
        } else {
            blurb.setCullHint(Spatial.CullHint.Always);
        }

        for (int i = 0; i < MOST_ROWS; i++) {
            var label = labels.get(i);
            var value = values.get(i);
            if (i >= rows) {
                label.setCullHint(Spatial.CullHint.Always);
                value.setCullHint(Spatial.CullHint.Always);
                continue;
            }
            var row = tip.rows().get(i);
            label.setCullHint(Spatial.CullHint.Inherit);
            value.setCullHint(Spatial.CullHint.Inherit);
            place(label, PAD, top - ROW_SIZE, WIDTH - PAD * 2f);
            label.setText(row.label());
            place(value, PAD, top - ROW_SIZE, WIDTH - PAD * 2f);
            // The two halves in one line, because jME's right alignment puts the
            // whole string against the edge and the green half has to be the
            // rightmost part of it. One colour per line is the price; the arrow
            // carries the meaning, and the row is read as "now, then".
            value.setColor(HeroPanel.linear(row.next().isEmpty() ? VALUE : GAIN));
            value.setText(row.next().isEmpty() ? row.now()
                    : row.now().isEmpty() ? row.next() : row.now() + "  →  " + row.next());
            top -= ROW_STEP;
        }

        if (footHeight > 0f) {
            rule.setCullHint(Spatial.CullHint.Inherit);
            rule.setLocalScale(WIDTH - PAD * 2f, 1f, 1f);
            rule.setLocalTranslation(PAD, top - GAP / 2f, 2f);
            place(foot, PAD, top - GAP - FOOT_SIZE, WIDTH - PAD * 2f);
            foot.setText(tip.foot());
            foot.setColor(HeroPanel.linear(tip.canRaise() ? FOOT : FOOT_DEAD));
            foot.setCullHint(Spatial.CullHint.Inherit);
        } else {
            rule.setCullHint(Spatial.CullHint.Always);
            foot.setCullHint(Spatial.CullHint.Always);
        }
    }

    private void place(BitmapText text, float x, float y, float width) {
        text.setBox(new Rectangle(x, y + text.getSize(), width, text.getSize() * 1.4f));
        text.setLocalTranslation(0f, 0f, 2f);
    }

    private BitmapText line(BitmapFont font, float size, ColorRGBA colour,
            BitmapFont.Align align) {
        var text = new BitmapText(font);
        text.setSize(size);
        text.setColor(HeroPanel.linear(colour));
        // A box before the alignment, and both before it is attached. An empty
        // BitmapText with no box has no bound at all, and attaching one to a node
        // is enough to make jME throw the next time anything measures the node.
        text.setBox(new Rectangle(0f, size, WIDTH, size * 1.4f));
        text.setAlignment(align);
        node.attachChild(text);
        return text;
    }

    /** A flat quad of one colour, scaled to whatever it has to cover. */
    private Geometry plate(ColorRGBA colour) {
        var geometry = new Geometry("tip-plate", new Quad(1f, 1f));
        var material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setColor("Color", HeroPanel.linear(colour));
        material.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        geometry.setMaterial(material);
        return geometry;
    }

    /**
     * One slot's card, as the status line describes it.
     *
     * @param rows  the figures, in the order the game listed them
     * @param canRaise whether the footer is an offer or a refusal, which is the
     *     only thing the client decides about the footer — the words are the
     *     game's either way
     */
    record Reading(String name, String at, String blurb, List<Row> rows, String foot,
            boolean canRaise) {

        /** One line of figures: what it is called, what it is, what it becomes. */
        record Row(String label, String now, String next) {
        }

        static final Reading NONE = new Reading("", "", "", List.of(), "", false);
    }
}
