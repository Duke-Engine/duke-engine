package uz.dukeengine.client3d.rpg;

import com.jme3.math.ColorRGBA;
import uz.dukeengine.client3d.RangeLook;

/**
 * The palette of the hero's bar, and the only place colour is decided. Every colour is read off the game's
 * {@link PanelLook}.
 */
final class PanelColours {

    final ColorRGBA stoneDeep;
    /**
     * ★ LIGHTER THAN IT WAS, and it is what the whole bar was missing.
     *
     * <p>The design's stone is a warm grey that catches light; this was a third
     * darker, and against it every gold edge, every gauge and every letter lost
     * the contrast it was chosen for — the bar read as one dark mass with things
     * faintly on it rather than as a slab with things set into it. Nothing was
     * wrong with any single colour. They were all slightly too far down.
     */
    final ColorRGBA stone;
    final ColorRGBA stoneLit;
    final ColorRGBA stoneDeadLit;
    final ColorRGBA stoneDead;
    final ColorRGBA torch;
    final ColorRGBA gold;
    final ColorRGBA goldHi;
    final ColorRGBA gain;
    final ColorRGBA bone;
    final ColorRGBA blood;
    final ColorRGBA arcane;

    /**
     * The mana bar's blue.
     *
     * <p>Blue because the other two are taken and because it is what a player
     * arrives already knowing: red is what is left of him, green is what he has
     * earned, blue is what he can spend. A bar the player has to learn the colour
     * of is a bar he reads by its position, and position is the one thing that
     * changes when a panel is laid out again.
     */
    final ColorRGBA mana;

    /** The same, lit — what the bar flashes when he asks for what he cannot pay. */
    final ColorRGBA manaDenied;

    /**
     * What a picture is washed with in a slot he cannot pay for.
     *
     * <p>Blue rather than grey, so the reason is on the socket rather than only
     * in the corner: a grey slot is a slot that is off, and a blue one is a slot
     * that is waiting on the blue bar.
     */
    final ColorRGBA manaWash;
    final ColorRGBA dead;
    final ColorRGBA edge;
    final ColorRGBA drop;
    final ColorRGBA glyphCold;
    /** An empty pip; the lit ones take the gold the rest of the bar uses. */
    final ColorRGBA pipDark;

    /**
     * The colour of "this one is waiting for a target", and it is deliberately
     * the one colour on the bar that is COLD.
     *
     * <p>Gold was the obvious pick and the wrong one: gold is the bar's own
     * colour — the headings, the rims, the pips are all gold — so an armed slot
     * drawn in it says "slightly more gold than usual", which in a fight nobody
     * notices. Everything down here is stone, torch and gold, all of them warm.
     * A cold colour has nothing to blend into.
     *
     * <p>It is also the colour the rings on the FLOOR use while he is aiming, so
     * the panel and the ground say the same thing in the same word: cyan means
     * "you are choosing where this goes".
     */
    final ColorRGBA sel;
    final ColorRGBA selHi;
    /** The cold stone an armed socket is cut from. */
    final ColorRGBA selStoneLit;
    final ColorRGBA selStone;
    final ColorRGBA label;
    final ColorRGBA lockLabel;
    final ColorRGBA slabTop;
    final ColorRGBA slabHigh;
    final ColorRGBA slabMid;
    final ColorRGBA slabLow;
    final ColorRGBA slabRim;
    final ColorRGBA socketRim;
    final ColorRGBA flesh;
    final ColorRGBA hole;
    final ColorRGBA portraitTop;
    final ColorRGBA portraitBottom;
    final ColorRGBA portraitRim;
    final ColorRGBA itemTop;
    final ColorRGBA itemBottom;
    final ColorRGBA itemNumber;
    final ColorRGBA badge;
    /**
     * What each socket's drawing is coloured.
     *
     * <p>By socket rather than by what is in it, which sounds backwards and is not:
     * the client has no idea what a flask does, and colouring by the picture would
     * mean it deciding that flasks are green. Six settled colours give the grid the
     * look the design has — a row of different things — without the client claiming
     * to know what any of them is.
     */
    final ColorRGBA[] itemColours;

    PanelColours(PanelLook look, RangeLook aiming) {
        // ★ ONE NUMBER, not two. The colour an armed slot is drawn in is the
        // colour the rings on the floor are drawn in, and they have to be the
        // same or the panel and the ground are speaking two languages about one
        // moment. Taken off the same RangeLook the rings use rather than kept
        // here, so the file moves both of them at once.
        var aim = aiming == null ? RangeLook.DEFAULT : aiming;
        sel = rgb(aim.allowColour());
        selHi = rgb(aim.areaColour());
        slabTop = rgb(look.slabTopColour());
        slabHigh = rgb(look.slabHighColour());
        slabMid = rgb(look.slabMidColour());
        slabLow = rgb(look.slabLowColour());
        slabRim = rgb(look.slabRimColour());
        stoneDeep = rgb(look.stoneDeepColour());
        stone = rgb(look.stoneColour());
        stoneLit = rgb(look.stoneLitColour());
        stoneDead = rgb(look.stoneDeadColour());
        stoneDeadLit = rgb(look.stoneDeadLitColour());
        socketRim = rgb(look.socketRimColour());
        hole = rgb(look.holeColour());
        edge = rgb(look.edgeColour());
        drop = rgb(look.dropColour());
        portraitTop = rgb(look.portraitTopColour());
        portraitBottom = rgb(look.portraitBottomColour());
        portraitRim = rgb(look.portraitRimColour());
        itemTop = rgb(look.itemTopColour());
        itemBottom = rgb(look.itemBottomColour());
        torch = rgb(look.torchColour());
        gold = rgb(look.goldColour());
        goldHi = rgb(look.goldHiColour());
        gain = rgb(look.gainColour());
        bone = rgb(look.boneColour());
        blood = rgb(look.bloodColour());
        arcane = rgb(look.arcaneColour());
        mana = rgb(look.manaColour());
        manaDenied = rgb(look.manaDeniedColour());
        manaWash = rgb(look.manaWashColour());
        dead = rgb(look.deadColour());
        glyphCold = rgb(look.glyphColdColour());
        pipDark = rgb(look.pipDarkColour());
        selStone = rgb(look.selStoneColour());
        selStoneLit = rgb(look.selStoneLitColour());
        label = rgb(look.labelColour());
        lockLabel = rgb(look.lockLabelColour());
        itemNumber = rgb(look.itemNumberColour());
        badge = rgb(look.badgeColour());
        flesh = rgb(look.fleshColour());
        itemColours = look.itemColours().stream().map(PanelColours::rgb).toArray(ColorRGBA[]::new);
    }

    /** The same colour, as clear as {@code alpha} says. */
    static ColorRGBA withAlpha(ColorRGBA colour, float alpha) {
        return new ColorRGBA(colour.r, colour.g, colour.b, alpha);
    }

    static ColorRGBA rgb(int hex) {
        return new ColorRGBA(((hex >> 16) & 0xFF) / 255f, ((hex >> 8) & 0xFF) / 255f,
                (hex & 0xFF) / 255f, 1f);
    }
}
