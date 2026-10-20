package uz.dukeengine.client3d.rpg;

import static uz.dukeengine.client3d.rpg.PanelMetrics.BAND;

import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.scene.Node;

/** How deep he is: a word over a numeral, at the far end of the bar. */
final class DepthMarker {

    static final float WIDTH = 96f;
    /** The word and the numeral under it, which is all the depth block is. */
    static final float HEIGHT = 46f;
    /** The depth numeral, alone and with the bottom of the descent beside it. */
    private static final float LARGE = 34f;
    private static final float SMALL = 19f;

    final Node node = new Node("depth");
    private final BitmapText number;
    /** The same, in the smaller lettering a long number is carved in. */
    private final BitmapText numberSmall;
    private final BitmapText word;

    DepthMarker(PanelBrush brush, PanelColours colours, Node holder) {
        holder.attachChild(node);
        word = brush.text(11f, colours.label, 0f, BAND / 2f - 20f, WIDTH, BitmapFont.Align.Center);
        number = brush.text(LARGE, colours.torch, 0f, BAND / 2f - 12f, WIDTH, BitmapFont.Align.Center);
        // Its box's top where the large one's is.
        numberSmall = brush.text(SMALL, colours.torch, 0f, BAND / 2f - 12f + LARGE - SMALL,
                WIDTH, BitmapFont.Align.Center);
        node.attachChild(word);
        node.attachChild(number);
        node.attachChild(numberSmall);
    }

    void show(String depth, String depthWord) {
        // A floor out of four is three times the lettering of a floor, and the
        // stone it is carved into did not get any wider: a long one in the smaller
        // line. Two lines rather than one resized, so each is drawn at its own pixels.
        boolean longNumber = depth.length() > 4;
        number.setText(longNumber ? "" : depth);
        numberSmall.setText(longNumber ? depth : "");
        word.setText(depthWord);
    }
}
