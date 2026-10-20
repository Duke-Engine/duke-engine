package uz.dukeengine.client3d.rpg;

import static uz.dukeengine.client3d.Shade.linear;
import static uz.dukeengine.client3d.rpg.PanelBrush.atDepth;
import static uz.dukeengine.client3d.rpg.PanelBrush.attach;
import static uz.dukeengine.client3d.rpg.PanelColours.withAlpha;
import static uz.dukeengine.client3d.rpg.PanelMeshes.gradient;
import static uz.dukeengine.client3d.rpg.PanelMetrics.BAND;
import static uz.dukeengine.client3d.rpg.PanelMetrics.BLANK;
import static uz.dukeengine.client3d.rpg.PanelMetrics.hits;

import com.jme3.font.BitmapFont;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.client3d.PanelSkin;

/** The column of order buttons that stands beside the map. */
final class OrderColumn {

    /** How big a button is, and the gaps between them and between the column and the map. */
    private static final float ORDER_BUTTON = 38f;
    private static final float ORDER_GAP = 5f;
    static final float ORDER_COLUMN_GAP = 7f;

    /**
     * How much of an order button its picture fills.
     *
     * <p>A shade more than a skill's, because the button is a small square and a picture that leaves a
     * third of a twenty-two pixel socket empty is a picture nobody can make out.
     */
    private static final float ORDER_ICON_SHARE = 0.68f;

    /** One button: the order it gives, and the parts of it that change. */
    private static final class OrderButton {
        private final char key;
        private final Node node = new Node("order");
        private Geometry lit;
        private Geometry glyph;
        /** The cold stone and the corner marks, while it is waiting to be pointed. */
        private Geometry selStone;
        private Node brackets;
        private float atX;
        private float atY;

        private OrderButton(char key) {
            this.key = key;
        }
    }

    /**
     * The column drawn empty when the card offers no orders.
     *
     * <p>Four of them because the design has four. Furniture, like the sockets
     * beside them and the six in his bag: a column that came and went with the
     * selection would move the whole bar every time the player clicked.
     */
    private static final List<PanelReading.OrderReading> BLANK_ORDERS = List.of(
            new PanelReading.OrderReading(BLANK, "", "", false),
            new PanelReading.OrderReading(BLANK, "", "", false),
            new PanelReading.OrderReading(BLANK, "", "", false),
            new PanelReading.OrderReading(BLANK, "", "", false));

    final Node node = new Node("orders");
    private final List<OrderButton> buttons = new ArrayList<>();
    /** The orders the column was built for; null to begin with, as above. */
    private String builtFor;
    private boolean ordersAreHis;

    private final PanelBrush brush;
    private final PanelColours colours;
    private final PanelFocus focus;
    private final Runnable relayout;

    OrderColumn(PanelBrush brush, PanelColours colours, PanelFocus focus, Node holder, Runnable relayout) {
        this.brush = brush;
        this.colours = colours;
        this.focus = focus;
        this.relayout = relayout;
        holder.attachChild(node);
    }

    /** How much of the block the column takes beside the map: nothing while it has no buttons. */
    float width() {
        return buttons.isEmpty() ? 0f : ORDER_COLUMN_GAP + ORDER_BUTTON;
    }

    /**
     * The four orders, in a column beside the map.
     *
     * <p>Every one of them can already be given with the mouse, and three of them
     * are what the mouse does. They are drawn anyway, because a right-click is not
     * a thing a player can be shown: the buttons are how he finds out that walking
     * somewhere, attacking something, stopping and standing your ground are four
     * separate orders rather than one and a half.
     *
     * <p>Which orders exist is the game's, like everything else on this bar — they
     * arrive down the status line with their keys, their drawings and their words.
     * A game that names none gets no column and the space back.
     */
    void show(List<PanelReading.OrderReading> reading, boolean his) {
        ordersAreHis = his;
        var signature = new StringBuilder();
        for (var order : reading) {
            signature.append(order.key()).append(order.icon()).append(',');
        }
        if (!signature.toString().equals(builtFor)) {
            builtFor = signature.toString();
            for (var button : buttons) {
                button.node.removeFromParent();
            }
            buttons.clear();
            var drawn = reading.isEmpty() ? BLANK_ORDERS : reading;
            for (var order : drawn) {
                var button = new OrderButton(order.key());
                cut(button, order);
                buttons.add(button);
                node.attachChild(button.node);
            }
            relayout.run();
        }
        for (int i = 0; i < buttons.size() && i < reading.size(); i++) {
            var button = buttons.get(i);
            // What the creature is doing, and — only when it is one of his —
            // whatever the player's hand is on. Hovering something he cannot
            // command must not light it: the light is how a button says "press
            // me", and one that cannot be pressed must not say it.
            boolean doing = reading.get(i).on();
            // ★ Waiting for a click is not the same as being hovered, and it used
            // to be drawn as though it were. One is "this is what the next click
            // means" and the other is "your hand is here" -- so the first goes
            // cold, with the reticle, exactly as an armed skill does, and the
            // second stays the warm light it always was.
            boolean waiting = his && Character.valueOf(button.key).equals(focus.armed);
            boolean reaching = waiting || (his && Character.valueOf(button.key).equals(focus.hovered));
            boolean on = doing || reaching;
            button.lit.setCullHint(on ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
            // ★ THE ONE HE IS IN WEARS WHAT AN ARMED SKILL WEARS: the cold stone
            // and the corner marks. A warm light was all it had, and a warm light
            // is also what a button under the cursor gets -- so the state he was
            // actually in was being said in the same voice as "your hand is here",
            // which is to say it was not being said at all.
            //
            // Armed keeps the two apart by BREATHING. The look is deliberately the
            // same, because the two are the same kind of fact -- this is what he is
            // doing, this is what the next click will do -- and the pulse is the
            // difference between is and about to.
            boolean marked = waiting || (his && doing);
            var show = marked ? Spatial.CullHint.Inherit : Spatial.CullHint.Always;
            button.selStone.setCullHint(show);
            button.brackets.setCullHint(show);
            if (waiting) {
                float breath = focus.breath(0.72f, 0.28f, 1.1f);
                button.lit.getMaterial().setColor("Color",
                        linear(withAlpha(colours.sel, breath)));
            } else if (marked) {
                button.lit.getMaterial().setColor("Color", linear(colours.sel));
            } else {
                button.lit.getMaterial().setColor("Color", linear(colours.torch));
            }
            // Dim when it is not his, and dim whether or not it is lit: a skeleton
            // walking still shows its walk, because reading what something across
            // the room is doing is worth as much as reading his own — it is only
            // the offer to change it that goes away.
            button.glyph.getMaterial().setColor("Color",
                    linear(marked ? colours.selHi
                            : his ? (on ? colours.goldHi : colours.gold) : (doing ? colours.gold : colours.dead)));
        }
    }

    /**
     * The order button under a point, if any.
     *
     * <p>Answered through the same door the skill sockets are, so a click on a
     * button is the same thing as its key being pressed and neither of them can
     * grow a rule the other does not have.
     */
    Character orderAt(float x, float y) {
        if (!ordersAreHis) {
            return null; // not his to command; the button is furniture, like an empty socket
        }
        for (var button : buttons) {
            if (button.key != BLANK
                    && hits(x, y, button.atX, button.atY,
                            ORDER_BUTTON)) {
                return button.key;
            }
        }
        return null;
    }

    /**
     * Whether that order button is wearing the mark of the one he is in — the cold
     * stone and the corner brackets an armed skill wears. For the tests.
     */
    boolean orderIsMarked(char key) {
        for (var button : buttons) {
            if (button.key == key) {
                return button.selStone.getCullHint() != Spatial.CullHint.Always
                        && button.brackets.getCullHint() != Spatial.CullHint.Always;
            }
        }
        return false;
    }

    /** Whether that key is one of the order buttons rather than a skill. */
    boolean isAnOrder(char key) {
        for (var button : buttons) {
            if (button.key == key) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the buttons may be pressed — that is, whether what is selected is
     * the player's to command.
     *
     * <p>Read back out by the client so that a key press is refused for the same
     * reason the button is drawn dim. One answer, from the game, rather than the
     * client working it out a second time and the two disagreeing on the frame the
     * selection changes.
     */
    boolean areHis() {
        return ordersAreHis;
    }

    /** One button cut out of the stone, the same way a skill socket is. */
    private void cut(OrderButton button, PanelReading.OrderReading order) {
        float size = ORDER_BUTTON;
        attach(button.node, brush.flat("drop", size + 3f, size + 3f, colours.drop), -1.5f, -1.5f - 2f, 0f);
        // The lit ring, shown while the order is on or waiting to be pointed at
        // something. Under the edge, so it reads as stone catching light.
        button.lit = brush.flat("order-lit", size + 6f, size + 6f, colours.torch);
        attach(button.node, button.lit, -3f, -3f, 1f);
        button.lit.setCullHint(Spatial.CullHint.Always);
        attach(button.node, brush.flat("edge", size + 3f, size + 3f, colours.edge), -1.5f, -1.5f, 2f);
        var stone = brush.shaded("stone", gradient(size, size, colours.stoneLit, colours.stone));
        attach(button.node, stone, 0f, 0f, 3f);

        // The same cold treatment the skill sockets get. An order that waits for
        // a click -- attack something, guard somewhere -- is in exactly the state
        // an aimed skill is in, and the player should not have to learn two
        // pictures for one idea.
        button.selStone = brush.shaded("stone-armed",
                gradient(size, size, colours.selStoneLit, colours.selStone));
        attach(button.node, button.selStone, 0f, 0f, 3.2f);
        button.selStone.setCullHint(Spatial.CullHint.Always);
        button.brackets = brush.reticle(size);
        attach(button.node, button.brackets, 0f, 0f, 39f);
        button.brackets.setCullHint(Spatial.CullHint.Always);
        boolean blank = button.key == BLANK && order.icon().isBlank();
        // A picture from the game's own file, like the skills beside it. A
        // missing one falls back to nothing rather than losing the button: the
        // key in the corner is what the button is for teaching anyway.
        var drawn = blank ? null : brush.picture(order.icon(), size, ORDER_ICON_SHARE, colours.gold);
        if (drawn == null) {
            button.glyph = new Geometry("order-glyph", new Mesh());
            button.glyph.setMaterial(brush.lines(colours.dead));
            attach(button.node, button.glyph, size / 2f, size / 2f, 4f);
        } else {
            button.glyph = drawn;
            float inset = size * (1f - ORDER_ICON_SHARE) / 2f;
            attach(button.node, button.glyph, inset, inset, 4f);
        }
        // The key in the corner, because the button's whole job is to teach it.
        var key = brush.text(10f, colours.label, 0f, -1f, size - 2f, BitmapFont.Align.Right);
        key.setText(button.key == BLANK ? "" : String.valueOf(button.key));
        atDepth(key, 5f);
        button.node.attachChild(key);
        brush.framed(button.node, PanelSkin.BUTTON, -1.5f, -1.5f, size + 3f, size + 3f, 4.5f);
    }

    /** The order buttons, stacked beside the map and centred against it. */
    void place(float x, float originX, float originY) {
        if (buttons.isEmpty()) {
            return;
        }
        float height = buttons.size() * ORDER_BUTTON
                + (buttons.size() - 1) * ORDER_GAP;
        float top = (BAND + height) / 2f;
        node.setLocalTranslation(x, 0f, 0f);
        for (int i = 0; i < buttons.size(); i++) {
            var button = buttons.get(i);
            float y = top - (i + 1) * ORDER_BUTTON - i * ORDER_GAP;
            button.node.setLocalTranslation(0f, y, 0f);
            button.atX = originX;
            button.atY = originY + y;
        }
    }
}
