package uz.duke.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.font.Rectangle;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.material.RenderState.FaceCullMode;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Quad;
import java.util.ArrayList;
import java.util.List;

/**
 * The screen a level puts in front of the player: three cards, one of which he
 * keeps.
 *
 * <p>The client draws it and knows nothing about what is on it. Which cards
 * there are, what they say and what taking one is worth is the game's, and it
 * arrives on the snapshot's status channel as finished words and an icon name —
 * exactly as {@link HeroPanel} receives the rest of the bar. What the client
 * supplies is the mechanism: the cards, the click, and holding the world still
 * while the player thinks.
 *
 * <p>What a click does is the game's too. It hands over a callback through
 * {@link Hotkeys}, and the callback posts a command; nothing here reaches into
 * the simulation, which is running on another thread and is not the render
 * thread's business.
 *
 * <p>Three equal cards, deliberately. Nothing is marked as recommended, nothing
 * is bigger than its neighbours: the choice is the player's and a client that
 * leaned on one would be making it for him.
 */
final class LevelUpOverlay {

    /** Dark enough that the cards are the only thing to look at. */
    private static final ColorRGBA VEIL = new ColorRGBA(0.05f, 0.05f, 0.07f, 0.55f);
    private static final ColorRGBA PANEL_TOP = rgb(0x2A241D);
    private static final ColorRGBA PANEL_LOW = rgb(0x191510);
    private static final ColorRGBA CARD_TOP = rgb(0x38312A);
    private static final ColorRGBA CARD_LOW = rgb(0x221D17);
    private static final ColorRGBA CARD_HOT = rgb(0x6A522A);
    private static final ColorRGBA EDGE = rgb(0x0A0806);
    private static final ColorRGBA INNER = rgb(0x514736);
    private static final ColorRGBA TORCH = rgb(0xE8A33D);
    private static final ColorRGBA BONE = rgb(0xD9CFBA);
    private static final ColorRGBA MUTE = rgb(0x9A907F);

    /** The width the design was drawn at, and the bounds it may be scaled within. */
    private static final float DESIGN_WIDTH = 760f;
    private static final float MIN_SCALE = 0.62f;
    private static final float MAX_SCALE = 1.25f;

    private static final float PAD = 26f;
    private static final float HEAD_HEIGHT = 70f;
    private static final float CARD_HEIGHT = 168f;
    private static final float CARD_GAP = 14f;

    private final AssetManager assets;
    private final BitmapFont font;
    private final Node root = new Node("level-up");
    private final Node panel = new Node("level-up-panel");
    private final List<Card> cards = new ArrayList<>();

    private Geometry veil;
    private BitmapText title;
    private BitmapText hint;

    private float screenWidth;
    private float screenHeight;
    private float scale = 1f;
    private float originX;
    private float originY;
    private float panelWidth = DESIGN_WIDTH;
    private float panelHeight;

    /** What the cards on screen say, so an unchanged offer is not rebuilt. */
    private String builtFor = "";
    private boolean showing;
    private int hovered = -1;

    /** One card: its own stone, an icon, a title and a line under it. */
    private static final class Card {
        private final Node node = new Node("card");
        private Geometry rim;
        private float x;
        private float width;
    }

    LevelUpOverlay(AssetManager assets, BitmapFont font, Node guiNode,
            float screenWidth, float screenHeight) {
        this.assets = assets;
        this.font = font;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        guiNode.attachChild(root);
        build();
        hide();
    }

    private void build() {
        veil = new Geometry("veil", new Quad(1f, 1f));
        veil.setMaterial(unshaded(VEIL));
        root.attachChild(veil);
        root.attachChild(panel);
    }

    /**
     * Put an offer on screen, or take one away.
     *
     * <p>Rebuilt only when the words change, so a frame in which nothing about the
     * offer moved costs nothing — and the offer is on screen for as long as the
     * player takes to read it.
     */
    void show(HeroPanel.Reading.Offer offer) {
        if (offer == null) {
            hide();
            return;
        }
        var signature = new StringBuilder().append(offer.id());
        for (var card : offer.cards()) {
            signature.append('|').append(card.icon()).append(card.name())
                    .append(card.description());
        }
        if (!signature.toString().equals(builtFor)) {
            builtFor = signature.toString();
            rebuild(offer);
        }
        showing = true;
        root.setCullHint(Spatial.CullHint.Inherit);
    }

    void hide() {
        showing = false;
        hovered = -1;
        root.setCullHint(Spatial.CullHint.Always);
    }

    boolean isShowing() {
        return showing;
    }

    private void rebuild(HeroPanel.Reading.Offer offer) {
        panel.detachAllChildren();
        cards.clear();

        int count = Math.max(1, offer.cards().size());
        float cardWidth = (DESIGN_WIDTH - PAD * 2f - CARD_GAP * (count - 1)) / count;
        panelWidth = DESIGN_WIDTH;
        panelHeight = PAD * 2f + HEAD_HEIGHT + CARD_HEIGHT;

        attach(panel, flat("edge", panelWidth + 4f, panelHeight + 4f, EDGE), -2f, -2f, 0f);
        attach(panel, flat("inner", panelWidth + 2f, panelHeight + 2f, INNER), -1f, -1f, 1f);
        attach(panel, flat("top", panelWidth, panelHeight / 2f, PANEL_TOP),
                0f, panelHeight / 2f, 2f);
        attach(panel, flat("low", panelWidth, panelHeight / 2f, PANEL_LOW), 0f, 0f, 2f);

        title = text(24f, TORCH, 0f, panelHeight - PAD - 26f, panelWidth,
                BitmapFont.Align.Center);
        title.setText(offer.title());
        title.setLocalTranslation(0f, title.getLocalTranslation().y, 3f);
        panel.attachChild(title);

        this.hint = text(14f, MUTE, 0f, panelHeight - PAD - 48f, panelWidth,
                BitmapFont.Align.Center);
        this.hint.setText(offer.hint());
        this.hint.setLocalTranslation(0f, this.hint.getLocalTranslation().y, 3f);
        panel.attachChild(this.hint);

        for (int i = 0; i < offer.cards().size(); i++) {
            var reading = offer.cards().get(i);
            var card = new Card();
            card.x = PAD + i * (cardWidth + CARD_GAP);
            card.width = cardWidth;
            attach(card.node, flat("card-edge", cardWidth + 4f, CARD_HEIGHT + 4f, EDGE),
                    -2f, -2f, 0f);
            // The lit rim is what a card under the cursor gains, and nothing else
            // about it changes: three equal cards must stay three equal cards.
            card.rim = flat("card-rim", cardWidth + 4f, CARD_HEIGHT + 4f, CARD_HOT);
            attach(card.node, card.rim, -2f, -2f, 1f);
            card.rim.setCullHint(Spatial.CullHint.Always);
            attach(card.node, flat("card-top", cardWidth, CARD_HEIGHT * 0.55f, CARD_TOP),
                    0f, CARD_HEIGHT * 0.45f, 2f);
            attach(card.node, flat("card-low", cardWidth, CARD_HEIGHT * 0.45f, CARD_LOW),
                    0f, 0f, 2f);
            attach(card.node, flat("card-lip", cardWidth, 2f,
                    new ColorRGBA(1f, 1f, 1f, 0.06f)), 0f, CARD_HEIGHT - 2f, 3f);

            var glyph = new Geometry("card-glyph", HeroPanel.glyph(reading.icon(), 38f));
            glyph.setMaterial(lines());
            attach(card.node, glyph, cardWidth / 2f, CARD_HEIGHT - 46f, 4f);

            var cardName = text(16f, BONE, 0f, CARD_HEIGHT - 92f, cardWidth,
                    BitmapFont.Align.Center);
            cardName.setText(reading.name());
            cardName.setLocalTranslation(0f, cardName.getLocalTranslation().y, 4f);
            card.node.attachChild(cardName);

            var description = text(13.5f, MUTE, 8f, CARD_HEIGHT - 122f, cardWidth - 16f,
                    BitmapFont.Align.Center);
            description.setText(reading.description());
            description.setLocalTranslation(0f, description.getLocalTranslation().y, 4f);
            card.node.attachChild(description);

            // The number that picks it from the keyboard, in the corner where a
            // shortcut belongs.
            var number = text(13f, TORCH, 0f, 6f, cardWidth - 8f, BitmapFont.Align.Right);
            number.setText(String.valueOf(i + 1));
            number.setLocalTranslation(0f, number.getLocalTranslation().y, 4f);
            card.node.attachChild(number);

            card.node.setLocalTranslation(card.x, PAD, 3f);
            panel.attachChild(card.node);
            cards.add(card);
        }
        layOut();
    }

    void resize(float width, float height) {
        this.screenWidth = width;
        this.screenHeight = height;
        layOut();
    }

    private void layOut() {
        this.scale = Math.clamp(screenWidth / (DESIGN_WIDTH * 1.7f), MIN_SCALE, MAX_SCALE);
        veil.setLocalScale(Math.max(1f, screenWidth), Math.max(1f, screenHeight), 1f);
        veil.setLocalTranslation(0f, 0f, 0f);
        panel.setLocalScale(scale);
        this.originX = (screenWidth - panelWidth * scale) / 2f;
        this.originY = (screenHeight - panelHeight * scale) / 2f;
        panel.setLocalTranslation(originX, originY, 1f);
    }

    /** Which card a screen point is on, or -1 for none. */
    int cardAt(float screenX, float screenY) {
        if (!showing) {
            return -1;
        }
        float x = (screenX - originX) / scale;
        float y = (screenY - originY) / scale;
        for (int i = 0; i < cards.size(); i++) {
            var card = cards.get(i);
            if (x >= card.x && x <= card.x + card.width && y >= PAD && y <= PAD + CARD_HEIGHT) {
                return i;
            }
        }
        return -1;
    }

    /** Light the card the cursor is resting on. */
    void hover(int index) {
        if (index == hovered) {
            return;
        }
        hovered = index;
        for (int i = 0; i < cards.size(); i++) {
            cards.get(i).rim.setCullHint(
                    i == index ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        }
    }

    // ---- drawing helpers ----

    private Geometry flat(String what, float width, float height, ColorRGBA colour) {
        var geometry = new Geometry(what, new Quad(width, height));
        geometry.setMaterial(unshaded(colour));
        return geometry;
    }

    private Material unshaded(ColorRGBA colour) {
        var material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        // The same conversion the bar does, and for the same reason -- see
        // HeroPanel#linear.
        material.setColor("Color", HeroPanel.linear(colour));
        material.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        material.getAdditionalRenderState().setFaceCullMode(FaceCullMode.Off);
        material.getAdditionalRenderState().setDepthTest(false);
        return material;
    }

    private Material lines() {
        var material = unshaded(TORCH);
        material.getAdditionalRenderState().setLineWidth(2f);
        return material;
    }

    private BitmapText text(float size, ColorRGBA colour, float x, float y, float width,
            BitmapFont.Align align) {
        var line = new BitmapText(font);
        line.setSize(size);
        line.setColor(HeroPanel.linear(colour));
        line.setBox(new Rectangle(x, y + size, width, size * 2.6f));
        line.setAlignment(align);
        return line;
    }

    private static void attach(Node parent, Spatial child, float x, float y, float z) {
        child.setLocalTranslation(x, y, z);
        parent.attachChild(child);
    }

    private static ColorRGBA rgb(int hex) {
        return new ColorRGBA(((hex >> 16) & 0xFF) / 255f, ((hex >> 8) & 0xFF) / 255f,
                (hex & 0xFF) / 255f, 1f);
    }
}
