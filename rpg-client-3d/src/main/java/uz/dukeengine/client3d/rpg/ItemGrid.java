package uz.dukeengine.client3d.rpg;

import static uz.dukeengine.client3d.rpg.PanelBrush.atDepth;
import static uz.dukeengine.client3d.rpg.PanelBrush.attach;
import static uz.dukeengine.client3d.rpg.PanelColours.withAlpha;
import static uz.dukeengine.client3d.rpg.PanelMeshes.gradient;
import static uz.dukeengine.client3d.rpg.PanelMeshes.strokes;
import static uz.dukeengine.client3d.rpg.PanelMetrics.BAND;
import static uz.dukeengine.client3d.rpg.PanelMetrics.HEADING_GAP;
import static uz.dukeengine.client3d.rpg.PanelMetrics.HEADING_SIZE;

import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.client3d.PanelSkin;

/** His bag: a heading with a grid of sockets under it, the same few whatever he carries. */
final class ItemGrid {

    private static final float ITEM_GAP = 5f;

    /** His bag: three across, two down. */
    private final float itemSlot;
    private final int itemColumns;
    private final int itemRows;

    /** One socket in the bag. Six of them, whatever he is carrying. */
    private static final class ItemSlot {
        private final Node node = new Node("item");
        private Geometry glyph;
        private Geometry empty;
        private BitmapText count;
    }

    final Node grid = new Node("items");
    private final Node headingNode = new Node("item-heading");
    private final List<ItemSlot> slots = new ArrayList<>();
    private final BitmapText itemsWord;
    /** What the grid was last filled with, so it is not rebuilt every frame. */
    private String builtFor = "";

    private final PanelBrush brush;
    private final PanelColours colours;

    ItemGrid(PanelBrush brush, PanelColours colours, PanelLook look, Node holder) {
        this.brush = brush;
        this.colours = colours;
        itemColumns = look.itemColumns();
        itemRows = look.itemRows();
        // A socket asked to be bigger than the band is tall is drawn as big as fits in it, with its heading over
        // it and, for a skill, its pips and its word under it: through the floor of the bar is not an option.
        itemSlot = Math.min(look.itemSlot(),
                (BAND - HEADING_SIZE - HEADING_GAP - (itemRows - 1) * ITEM_GAP) / itemRows);
        holder.attachChild(grid);
        holder.attachChild(headingNode);
        itemsWord = brush.heading(headingNode, width());
    }

    float width() {
        return itemColumns * itemSlot + (itemColumns - 1) * ITEM_GAP;
    }

    float height() {
        return itemRows * itemSlot + (itemRows - 1) * ITEM_GAP + HEADING_GAP + HEADING_SIZE;
    }

    float foot() {
        return (BAND - height()) / 2f;
    }

    /**
     * Six sockets, filled with what he is carrying and empty where he is not.
     *
     * <p>Always six, whether he has one thing or none. A grid that grew as he
     * picked things up would move everything to the right of it half a screen
     * every time he opened a chest, and an empty socket is information: it says
     * there is room, and it says how much of the floor he has left to search.
     *
     * <p>Nothing can be used or dropped. What he finds already works the moment he
     * finds it — the figures under the bars are worked out from this very bag — so
     * what the grid shows is what made him stronger, which is what finding a thing
     * in a dungeon means. A bag he can rummage in is a different game and would
     * start in the simulation, not here.
     */
    void show(List<PanelReading.ItemReading> reading, String word) {
        if (slots.isEmpty()) {
            for (int i = 0; i < itemColumns * itemRows; i++) {
                var slot = new ItemSlot();
                socket(slot, i);
                slots.add(slot);
                grid.attachChild(slot.node);
            }
        }
        itemsWord.setText(word);
        var signature = new StringBuilder();
        for (var item : reading) {
            signature.append(item.icon()).append(':').append(item.count()).append(',');
        }
        if (signature.toString().equals(builtFor)) {
            return;
        }
        builtFor = signature.toString();
        for (int i = 0; i < slots.size(); i++) {
            var slot = slots.get(i);
            var item = i < reading.size() ? reading.get(i) : null;
            fillSocket(slot, item, i);
        }
    }

    /** Put a thing in a socket, or leave it showing that it is empty. */
    private void fillSocket(ItemSlot slot, PanelReading.ItemReading item, int index) {
        if (slot.glyph != null) {
            slot.glyph.removeFromParent();
            slot.glyph = null;
        }
        slot.empty.setCullHint(item == null ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        slot.count.setText(item != null && item.count() > 1 ? String.valueOf(item.count()) : "");
        if (item == null) {
            return;
        }
        slot.glyph = new Geometry("item-glyph", PanelGlyphs.of(item.icon(), itemSlot * 0.56f));
        slot.glyph.setMaterial(brush.lines(colours.itemColours[index % colours.itemColours.length]));
        attach(slot.node, slot.glyph, itemSlot / 2f, itemSlot / 2f, 4f);
    }

    /** One empty socket, cut like the skill slots but smaller. */
    private void socket(ItemSlot slot, int index) {
        attach(slot.node, brush.flat("drop", itemSlot + 3f, itemSlot + 3f, colours.drop), -1.5f, -3.5f, 0f);
        attach(slot.node, brush.flat("edge", itemSlot + 3f, itemSlot + 3f, colours.edge), -1.5f, -1.5f, 1f);
        var stone = brush.shaded("stone", gradient(itemSlot, itemSlot, colours.itemTop, colours.itemBottom));
        attach(slot.node, stone, 0f, 0f, 2f);
        // The dashed inner square that says a socket is empty rather than dark.
        slot.empty = new Geometry("empty", dashedBox(itemSlot - 14f));
        slot.empty.setMaterial(brush.lines(withAlpha(colours.stoneLit, 0.7f)));
        attach(slot.node, slot.empty, 7f, 7f, 3f);
        slot.count = brush.text(11f, colours.bone, 0f, -1f, itemSlot - 3f, BitmapFont.Align.Right);
        atDepth(slot.count, 5f);
        slot.node.attachChild(slot.count);
        var number = brush.text(10f, colours.itemNumber, 2f, itemSlot - 13f, itemSlot,
                BitmapFont.Align.Left);
        number.setText(String.valueOf(index + 1));
        atDepth(number, 5f);
        slot.node.attachChild(number);
        brush.framed(slot.node, PanelSkin.ITEM, -1.5f, -1.5f, itemSlot + 3f, itemSlot + 3f, 4.5f);
    }

    /** A square of short strokes — the mark of a socket with nothing in it. */
    private static Mesh dashedBox(float side) {
        var dashes = new ArrayList<float[]>();
        float step = side / 7f;
        for (int i = 0; i < 7; i += 2) {
            float from = i * step;
            float to = Math.min(side, from + step);
            dashes.add(new float[] {from, 0f, to, 0f});
            dashes.add(new float[] {from, side, to, side});
            dashes.add(new float[] {0f, from, 0f, to});
            dashes.add(new float[] {side, from, side, to});
        }
        return strokes(dashes.toArray(new float[0][]), 0f);
    }

    /** His bag: a heading with the grid under it, the pair centred in the band. */
    void place(float x) {
        float bagHeight = itemRows * itemSlot + (itemRows - 1) * ITEM_GAP;
        float bottom = (BAND - bagHeight - HEADING_SIZE - HEADING_GAP) / 2f;
        grid.setLocalTranslation(x, bottom, 0f);
        headingNode.setLocalTranslation(x, bottom + bagHeight + HEADING_GAP, 0f);
        for (int i = 0; i < slots.size(); i++) {
            // Filled across then down, which is the order they are read in and the
            // order the game sends them.
            float column = i % itemColumns;
            float row = i / itemColumns;
            slots.get(i).node.setLocalTranslation(column * (itemSlot + ITEM_GAP),
                    (itemRows - 1 - row) * (itemSlot + ITEM_GAP), 0f);
        }
    }
}
