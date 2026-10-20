package uz.dukeengine.client3d.rpg;

import static uz.dukeengine.client3d.Shade.linear;
import static uz.dukeengine.client3d.rpg.PanelBrush.fillTo;
import static uz.dukeengine.client3d.rpg.PanelBrush.fraction;
import static uz.dukeengine.client3d.rpg.PanelMetrics.BAND;
import static uz.dukeengine.client3d.rpg.PanelMetrics.PORTRAIT_COLUMN;
import static uz.dukeengine.client3d.rpg.PanelMetrics.PORTRAIT_HEIGHT;

import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import uz.dukeengine.client3d.PanelSkin;

/**
 * Health and mana, the two gauges that hang under his portrait, and the refusal that flashes the mana when he
 * asks for what he cannot pay.
 */
final class Gauges {

    private static final float BAR_HEIGHT = 17f;
    /**
     * Between the two, and nearer the health.
     *
     * <p>It is a thing he spends rather than a thing he accumulates, so it reads
     * with what is left of him rather than with what he has earned — and it wants
     * a figure on it, which the experience bar does not, so it cannot be as thin.
     */
    private static final float MANA_HEIGHT = 14f;

    /** How long the bar stays in the refusal colour. Long enough to catch, short
     * enough not to be mistaken for a state. */
    private static final float DENIAL_SECONDS = 0.4f;

    /** One gauge: the fill that is scaled to what is left, and the reading over it. */
    private record Bar(Geometry fill, BitmapText reading) {
    }

    /** The two gauges under the portrait, which travel with it. */
    final Node node = new Node("portrait-bars");
    private final Node manaNode = new Node("mana");
    private final Bar health;
    private final Bar mana;
    /** The last refusal the bar has been shown, so one refusal flashes once. */
    private int refusalShown;
    /** Until when the bar is drawn in the refusal colour. */
    private float deniedUntil;
    /**
     * Whether a refusal is waiting to be sounded.
     *
     * <p>The panel sees it — it is the thing that reads the line — and the app
     * owns the noises, so the flag is picked up rather than acted on here. Set
     * once per refusal and cleared by whoever takes it.
     */
    private boolean refusalToSound;

    private final PanelBrush brush;
    private final PanelColours colours;

    /**
     * Health and mana, under his own face and as wide as it.
     *
     * <p>Its own node rather than part of the portrait's, because the portrait is
     * a frame with a live creature turning inside it and these are two gauges: one
     * is rebuilt when the face changes and the other never is.
     */
    Gauges(PanelBrush brush, PanelColours colours, Node holder) {
        this.brush = brush;
        this.colours = colours;
        float healthY = BAND - PORTRAIT_HEIGHT - 6f - BAR_HEIGHT;
        float manaY = healthY - 3f - MANA_HEIGHT;

        health = bar(node, "hp-fill", healthY, BAR_HEIGHT, colours.blood, 12f, 3f, 2f);
        mana = bar(manaNode, "mana-fill", manaY, MANA_HEIGHT, colours.mana, 10f, 2f, 1f);
        node.attachChild(manaNode);

        brush.framed(node, PanelSkin.GAUGE, -1f, healthY - 1f,
                PORTRAIT_COLUMN + 2f, BAR_HEIGHT + 2f, 1.5f);
        brush.framed(manaNode, PanelSkin.GAUGE, -1f, manaY - 1f,
                PORTRAIT_COLUMN + 2f, MANA_HEIGHT + 2f, 1.5f);
        holder.attachChild(node);
    }

    /** A trough, its fill and the reading over it with its shadow, hung from {@code parent}. */
    private Bar bar(Node parent, String fillName, float y, float height, ColorRGBA colour, float lettering,
            float readingLift, float shadowLift) {
        parent.attachChild(brush.trough(0f, y, PORTRAIT_COLUMN, height));
        var fill = brush.fill(fillName, PORTRAIT_COLUMN - 2f, height - 2f, colour);
        fill.setLocalTranslation(1f, y + 1f, 1f);
        parent.attachChild(fill);
        var reading = brush.text(lettering, colours.bone, 0f, y + readingLift, PORTRAIT_COLUMN,
                BitmapFont.Align.Center);
        brush.shadowed(parent, reading, brush.text(lettering, colours.drop, 1f, y + shadowLift, PORTRAIT_COLUMN,
                BitmapFont.Align.Center), 2f);
        return new Bar(fill, reading);
    }

    /** Take what the card says of his health and mana, and flash the mana if a refusal has just come. */
    void show(PanelReading reading, float seconds) {
        // No reading over an empty trough: "0 / 0" is a number, and a number is a
        // claim about somebody.
        brush.say(health.reading(), reading.maxHealth() <= 0f ? ""
                : Math.round(reading.health()) + " / " + Math.round(reading.maxHealth()));
        fillTo(health.fill(), fraction(reading.health(), reading.maxHealth()));
        // A game that charges nothing for its skills sends no pool and gets no
        // bar. It leaves the space it would have taken: the column is laid out
        // once, when it is built, and nothing below it moves. Which is a gap rather
        // than a hole -- the troughs are what carry the eye down the band, and one
        // of them missing reads as a panel with room to spare.
        // A refusal he has not been shown yet. Compared against the last one
        // rather than acted on every frame, because the line is rebuilt and sent
        // whether or not anything happened -- without this the bar would be
        // shaking continuously for as long as nothing else went on.
        if (reading.refusedForManaAt() > refusalShown) {
            refusalShown = reading.refusedForManaAt();
            deniedUntil = seconds + DENIAL_SECONDS;
            refusalToSound = true;
        }
        boolean casts = reading.maxMana() > 0f;
        manaNode.setCullHint(casts ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        if (casts) {
            brush.say(mana.reading(), Math.round(reading.mana()) + " / " + Math.round(reading.maxMana()));
            fillTo(mana.fill(), fraction(reading.mana(), reading.maxMana()));
            // And it flashes when he asked for what he has not got. A flash
            // rather than a shake: the bar is two pixels from the one above it
            // and a bar that moves would read as the panel breaking.
            boolean denied = seconds < deniedUntil;
            mana.fill().getMaterial().setColor("Color", linear(denied ? colours.manaDenied : colours.mana));
        }
    }

    /**
     * Say so: flash the bar and leave a noise to be taken.
     *
     * <p>The same answer the simulation's own refusal gets, raised from this side
     * because the cast never reaches the simulation any more. Arming is refused
     * here, so nothing is ever sent, so nothing can come back -- and a key that
     * does nothing and says nothing is a key the player thinks is broken.
     */
    void deny(float seconds) {
        deniedUntil = seconds + DENIAL_SECONDS;
        refusalToSound = true;
    }

    /**
     * Take the refusal, if there is one waiting.
     *
     * <p>Asked by the app each frame so that it can make the noise: the panel
     * knows WHEN because it reads the line, and the app knows WHAT because it
     * owns the game's sounds.
     */
    boolean takeRefusal() {
        boolean waiting = refusalToSound;
        refusalToSound = false;
        return waiting;
    }
}
