package uz.dukeengine.client3d.rpg;

import static uz.dukeengine.client3d.Shade.darker;
import static uz.dukeengine.client3d.Shade.linear;
import static uz.dukeengine.client3d.rpg.PanelBrush.atDepth;
import static uz.dukeengine.client3d.rpg.PanelBrush.attach;
import static uz.dukeengine.client3d.rpg.PanelColours.rgb;
import static uz.dukeengine.client3d.rpg.PanelColours.withAlpha;
import static uz.dukeengine.client3d.rpg.PanelMeshes.gradient;
import static uz.dukeengine.client3d.rpg.PanelMeshes.meshOf;
import static uz.dukeengine.client3d.rpg.PanelMetrics.BAND;
import static uz.dukeengine.client3d.rpg.PanelMetrics.BLANK;
import static uz.dukeengine.client3d.rpg.PanelMetrics.HEADING_GAP;
import static uz.dukeengine.client3d.rpg.PanelMetrics.HEADING_SIZE;
import static uz.dukeengine.client3d.rpg.PanelMetrics.hits;

import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.client3d.PanelSkin;

/**
 * The hero's skills: a heading and a row of carved slots, each with its picture, its cooldown shadow, its pips
 * and the badge that offers a point, built once for the keys the card names and dressed every frame.
 */
final class SkillRow {

    /**
     * The little bars under a slot: one per rank a skill can hold, lit up to what
     * is in it.
     *
     * <p>Bars rather than a number, because the question a player asks is "how
     * much of this is left to buy" and a row of four with two lit answers it
     * without being read. They hang under each slot rather than sitting in a row
     * of their own, so the ultimate's -- which is bigger and hangs lower -- keeps
     * its own pips beneath it.
     */
    private static final float PIP_WIDTH = 11f;
    private static final float PIP_HEIGHT = 5f;
    private static final float PIP_GAP = 3f;
    /** Between the foot of a slot and its pips, and between the pips and the word. */
    private static final float PIP_MARGIN = 4f;
    /** The line under the pips: "2 → 3", "3-daraja", "USTA", "yopiq". */
    private static final float RANK_TEXT = 11f;
    /** All that hangs under a slot: its pips, and the line under them. */
    private static final float UNDER_A_SLOT = PIP_MARGIN + PIP_HEIGHT + PIP_MARGIN + RANK_TEXT;

    /** The badge that says a point may go here: a small square over the corner. */
    private static final float BADGE = 22f;
    /** How far it hangs outside the slot, on both axes. */
    private static final float BADGE_OUT = 7f;
    private static final float SLOT_GAP = 9f;
    private static final float DROP_DEPTH = 3f;

    /**
     * How much stone shows round a skill's picture, in pixels a side.
     *
     * <p>★ A MARGIN, NOT A SHARE, and that is the whole change. It was
     * six-tenths of the socket, on the reasoning that a picture pressed to the
     * edges stops reading as something set into stone — which is true of a socket
     * with no edge of its own, and this one has three: a drop shadow, a dark rim
     * and a lit lip. They do the framing. What the share did instead was leave
     * eleven pixels of bare stone on every side of a thirty-eight pixel drawing,
     * so the art the game shipped was read at a third of the area it was drawn
     * for.
     *
     * <p>One pixel a side, so the picture all but fills the socket and the lip
     * still closes round it. In pixels rather than as a fraction because the two
     * sockets are different sizes and a fraction would give them different
     * margins for no reason anybody could name.
     */
    private static final float ICON_MARGIN = 1f;

    /** That margin as the fraction {@link #slotPicture} wants, for a socket this big. */
    private static float iconShare(float size) {
        return size <= ICON_MARGIN * 2f ? 1f : (size - ICON_MARGIN * 2f) / size;
    }

    /** How the design's own row is shaped, for a card that names no skills. */
    private final float[] blankRow;
    private final float skillSlot;
    private final float ultimateSlot;
    /** The key the design draws an ultimate for. Any other key gets an ordinary slot. */
    private final char ultimateKey;
    private final PanelLook look;
    /** Whether the game's pictures are white drawings or painted — see {@link IconLook}. */
    private final IconLook icons;

    /**
     * One carved slot. The stone is built once; what changes every frame is the
     * glyph's colour, the shadow sweeping round as a cooldown runs off, and the
     * text over it.
     */
    private static final class Slot {
        private final char key;
        private final float size;
        private final Node node = new Node("slot");
        /**
         * Where it sits in the design, measured from the bar's own corner.
         *
         * <p>Recorded rather than read back off the scene graph: a slot's own
         * translation is relative to the row it is in, which is relative to the
         * block that is centred, and adding those up by hand at click time is how
         * a click lands one column to the left of where it looked.
         */
        private float atX;
        private float atY;
        private Geometry deadStone;
        /** The cold stone and the four corner marks, shown only while it is armed. */
        private Geometry selStone;
        private Node brackets;
        private Geometry glyph;
        private Geometry sweep;
        private Geometry ring;
        private Geometry warm;
        /** The painted rim, when the game named one, so it can go dead with the rest. */
        private Geometry rim;
        private BitmapText seconds;
        private BitmapText locked;
        /** What it costs to cast, or empty for a game that charges nothing. */
        private BitmapText cost;
        /** Whether the pool has that much in it — a different dimming from a cooldown. */
        private boolean affordable = true;
        /** One per rank the skill can hold; the lit ones are what is in it. */
        private final List<Geometry> pips = new ArrayList<>();
        /** The corner badge, and the glow behind it that breathes. */
        private Node badge;
        private Geometry badgeGlow;
        private BitmapText rankWord;
        /** Where the badge sits, so a click on it can be told from one on the slot. */
        private float badgeX;
        private float badgeY;
        private boolean canRaise;
        private float sweptTo = -1f;
        private PanelReading.State state = PanelReading.State.READY;
        /** The key's letter in the corner, gone from a passive: no key casts it. */
        private BitmapText keyText;

        private Slot(char key, float size) {
            this.key = key;
            this.size = size;
        }
    }

    private final List<Slot> slots = new ArrayList<>();
    /**
     * The keys the slots were built for; a different set means rebuilding them.
     *
     * <p>Null rather than empty to begin with, and that is not tidiness: a card
     * naming no skills has an empty signature, so starting at the empty string
     * would make the first such card look like no change at all and the row would
     * never be built — no sockets, and the block missing from the bar.
     */
    private String builtFor;
    private final Node row = new Node("skills");
    private final Node headingNode = new Node("skill-heading");
    private final BitmapText skillsWord;
    /** "NUQTA 2" beside it: what the four slots are competing for. */
    private final BitmapText pointsCount;
    private final Node holder;

    private final PanelBrush brush;
    private final PanelColours colours;
    private final PanelFocus focus;
    private final Runnable relayout;

    SkillRow(PanelBrush brush, PanelColours colours, PanelFocus focus, PanelLook look, IconLook icons, Node holder,
            Runnable relayout) {
        this.brush = brush;
        this.colours = colours;
        this.focus = focus;
        this.look = look;
        this.icons = icons;
        this.holder = holder;
        this.relayout = relayout;
        float skillRoom = BAND - HEADING_SIZE - HEADING_GAP - UNDER_A_SLOT;
        skillSlot = Math.min(look.skillSlot(), skillRoom);
        ultimateSlot = Math.min(look.ultimateSlot(), skillRoom);
        ultimateKey = look.ultimateKey();
        blankRow = new float[] {skillSlot, skillSlot, skillSlot, ultimateSlot};
        holder.attachChild(headingNode);
        float skillWidth = skillSlot * 3f + ultimateSlot + SLOT_GAP * 3f;
        skillsWord = brush.heading(headingNode, skillWidth);
        // At the far end of the same line, which is where the design puts it: the
        // heading names the block and this says what the block is waiting for.
        pointsCount = brush.text(HEADING_SIZE, colours.goldHi, 0f, 0f, skillWidth,
                BitmapFont.Align.Right);
        headingNode.attachChild(pointsCount);
    }

    /** Take in what the card says of the skills: the heading, the points to spend, and every slot. */
    void show(PanelReading reading) {
        skillsWord.setText(reading.skillsWord());
        // Only while he has one. A counter reading nought is a thing to read and
        // dismiss every time the eye passes it; nothing there is nothing to read.
        pointsCount.setText(reading.points() > 0
                ? reading.pointsWord() + "  " + reading.points() : "");
        showSkills(reading.skills(), reading);
    }

    private void showSkills(List<PanelReading.SkillReading> reading, PanelReading card) {
        // The picture is part of what a slot IS, not part of what it is doing, so
        // a changed icon rebuilds the row exactly as a changed key does. Which is
        // also what lets an icon be swapped in the file and seen without a build.
        var carving = new StringBuilder();
        for (var skill : reading) {
            carving.append(skill.key()).append(skill.icon()).append(';');
        }
        if (!carving.toString().equals(builtFor)) {
            buildSlots(reading);
            builtFor = carving.toString();
        }
        for (int i = 0; i < slots.size() && i < reading.size(); i++) {
            priceUp(slots.get(i), card);
            dress(slots.get(i), reading.get(i));
            var rank = rankFor(card, reading.get(i).key());
            if (rank != null) {
                dressRank(slots.get(i), rank);
            }
        }
    }

    /** What the card says about one slot's ranks, or null if it says nothing. */
    private static PanelReading.RankReading rankFor(PanelReading card, char key) {
        for (var rank : card.ranks()) {
            if (rank != null && rank.key() == key) {
                return rank;
            }
        }
        return null;
    }

    private void buildSlots(List<PanelReading.SkillReading> reading) {
        for (var slot : slots) {
            slot.node.removeFromParent();
        }
        slots.clear();
        if (row.getParent() == null) {
            holder.attachChild(row);
        }
        if (reading.isEmpty()) {
            for (float size : blankRow) {
                var slot = new Slot(BLANK, size);
                carve(slot, "");
                slots.add(slot);
                row.attachChild(slot.node);
            }
        }
        for (var skill : reading) {
            var slot = new Slot(skill.key(), skill.key() == ultimateKey ? ultimateSlot : skillSlot);
            carve(slot, skill.icon());
            slots.add(slot);
            row.attachChild(slot.node);
        }
        relayout.run();
    }

    /**
     * Four corner marks outside an armed socket: a reticle, which is the shape
     * every game uses for "point at something".
     *
     * <p>Two arms apiece rather than a drawn L, because a quad is what this panel
     * is made of and eight of them cost nothing. Outside the socket, like the
     * badge, so they frame the skill rather than sitting on it.
     */
    private void bracketsFor(Slot slot) {
        slot.brackets = brush.reticle(slot.size);
        attach(slot.node, slot.brackets, 0f, 0f, 39f);
        slot.brackets.setCullHint(Spatial.CullHint.Always);
    }

    /**
     * The corner badge: a small square with a plus in it, over the slot's top
     * right, shown only while a point may go into this one.
     *
     * <p>Outside the slot rather than on it, and deliberately overhanging both
     * edges: a mark drawn inside the socket competes with the picture of the
     * skill, and this has to be findable in the corner of an eye while the
     * player is looking at the dungeon.
     */
    private void badgeFor(Slot slot) {
        slot.badge = new Node("badge");
        float at = slot.size - BADGE + BADGE_OUT;
        slot.badgeGlow = brush.flat("badge-glow", BADGE + 6f, BADGE + 6f, colours.torch);
        attach(slot.badge, slot.badgeGlow, -3f, -3f, 0f);
        attach(slot.badge, brush.flat("badge-edge", BADGE, BADGE, colours.hole), 0f, 0f, 1f);
        attach(slot.badge, brush.flat("badge-face", BADGE - 4f, BADGE - 4f, colours.badge), 2f, 2f, 2f);
        var plus = brush.text(14f, colours.goldHi, 0f, 4f, BADGE, BitmapFont.Align.Center);
        plus.setText("+");
        attach(slot.badge, plus, 0f, 0f, 3f);
        attach(slot.node, slot.badge, at, at, 40f);
        slot.badge.setCullHint(Spatial.CullHint.Always);
    }

    /**
     * The pips, the word under them and the corner badge.
     *
     * <p>Built once with the slot and then only dressed, like everything else on
     * this bar: a row that rebuilt its own furniture every frame is how a panel
     * becomes the expensive thing on screen.
     *
     * <p>How many pips is the skill's own ceiling and is not known until the game
     * says so, so this is called from {@link #dress} the first time a slot learns
     * what it holds rather than from {@link #carve}.
     */
    private void pipsFor(Slot slot, int max) {
        if (slot.pips.size() == max) {
            return;
        }
        for (var pip : slot.pips) {
            pip.removeFromParent();
        }
        slot.pips.clear();
        float width = max * PIP_WIDTH + (max - 1) * PIP_GAP;
        float x = (slot.size - width) / 2f;
        float y = -PIP_MARGIN - PIP_HEIGHT;
        for (int i = 0; i < max; i++) {
            var pip = brush.flat("pip", PIP_WIDTH, PIP_HEIGHT, colours.pipDark);
            attach(slot.node, pip, x + i * (PIP_WIDTH + PIP_GAP), y, 6f);
            slot.pips.add(pip);
        }
    }

    /** Take a painted rim down to its dead shade, when there is one to take down. */
    private void dimTheRim(Slot slot) {
        var painted = brush.skin.piece(PanelSkin.SLOT);
        if (slot.rim != null && painted != null) {
            slot.rim.getMaterial().setColor("Color",
                    linear(darker(rgb(painted.tint().getRGB()), 0.6f)));
        }
    }

    /** Cut one slot out of the stone: drop, edge, face, lit lip, inner shadow. */
    private void carve(Slot slot, String icon) {
        float size = slot.size;
        // Under the pips, which are under the slot. Built here and filled in by
        // dressRank, which is the only thing that knows what is in the slot.
        slot.rankWord = brush.text(RANK_TEXT, colours.label, 0f,
                -PIP_MARGIN - PIP_HEIGHT - PIP_MARGIN - RANK_TEXT, size,
                BitmapFont.Align.Center);
        attach(slot.node, slot.rankWord, 0f, 0f, 6f);
        badgeFor(slot);
        bracketsFor(slot);
        attach(slot.node, brush.flat("drop", size + 4f, size + 4f, colours.drop), -2f, -2f - DROP_DEPTH, 0f);
        // A torch-coloured lip around the socket, shown only while this is the
        // skill the next click belongs to. Under the edge, so it reads as the
        // stone catching light rather than as a box drawn on top of it.
        slot.ring = brush.flat("armed", size + 8f, size + 8f, colours.torch);
        attach(slot.node, slot.ring, -4f, -4f, 1f);
        slot.ring.setCullHint(Spatial.CullHint.Always);
        attach(slot.node, brush.flat("edge", size + 4f, size + 4f, colours.edge), -2f, -2f, 2f);

        var stone = brush.shaded("stone", gradient(size, size, colours.stoneLit, colours.stone));
        attach(slot.node, stone, 0f, 0f, 3f);

        slot.deadStone = brush.shaded("stone-dead",
                gradient(size, size, colours.stoneDeadLit, colours.stoneDead));
        attach(slot.node, slot.deadStone, 0f, 0f, 4f);

        // The cold stone of an armed socket, over the warm one and under
        // everything that means anything -- the rim, the glyph and the shading
        // all sit above it, so arming changes the stone without hiding the skill.
        slot.selStone = brush.shaded("stone-armed",
                gradient(size, size, colours.selStoneLit, colours.selStone));
        attach(slot.node, slot.selStone, 0f, 0f, 4.2f);
        slot.selStone.setCullHint(Spatial.CullHint.Always);

        // The carved illusion: light catches the top lip, shadow pools at the foot.
        attach(slot.node, brush.flat("lip", size, 2f, new ColorRGBA(1f, 1f, 1f, 0.07f)),
                0f, size - 2f, 5f);
        attach(slot.node, brush.flat("pool", size, 6f, new ColorRGBA(0f, 0f, 0f, 0.45f)),
                0f, 0f, 5f);
        // And the painted rim over the top of the shading -- the whole difference
        // between a socket that is shaded and one that is framed. Above the
        // stone and below the glyph, so it never covers what the slot is for.
        slot.rim = brush.paint(slot.node, PanelSkin.SLOT, -2f, -2f, size + 4f, size + 4f, 5.7f);

        // The wash under the cursor. Over the stone but under everything that
        // means something, so hovering brightens the slot without hiding its state.
        slot.warm = brush.flat("warm", size, size, new ColorRGBA(1f, 0.85f, 0.6f, 0.12f));
        attach(slot.node, slot.warm, 0f, 0f, 5.5f);
        slot.warm.setCullHint(Spatial.CullHint.Always);

        slot.glyph = slotPicture(icon, size);
        if (slot.glyph == null && slot.key == BLANK) {
            // An empty socket. Not a slot with a picture missing: a slot with
            // nothing in it, because nothing is selected. The stone goes dead so
            // it does not read as a skill that is merely waiting.
            slot.glyph = new Geometry("glyph", new Mesh());
            slot.glyph.setMaterial(brush.lines(colours.dead));
            attach(slot.node, slot.glyph, size / 2f, size / 2f, 6f);
            slot.deadStone.setCullHint(Spatial.CullHint.Inherit);
            dimTheRim(slot);
        } else if (slot.glyph == null) {
            slot.glyph = new Geometry("glyph", PanelGlyphs.of(String.valueOf(slot.key), size * 0.53f));
            slot.glyph.setMaterial(brush.lines(colours.torch));
            attach(slot.node, slot.glyph, size / 2f, size / 2f, 6f);
        } else {
            // A quad grows from its own corner, so it is placed rather than centred.
            attach(slot.node, slot.glyph, ICON_MARGIN, ICON_MARGIN, 6f);
        }

        slot.sweep = new Geometry("sweep", new Mesh());
        slot.sweep.setMaterial(brush.unshaded(new ColorRGBA(0.031f, 0.024f, 0.02f, 0.82f)));
        attach(slot.node, slot.sweep, 0f, 0f, 7f);

        slot.seconds = brush.text(17f, colours.bone, 0f, size / 2f - 6f, size, BitmapFont.Align.Center);
        atDepth(slot.seconds, 8f);
        slot.node.attachChild(slot.seconds);

        slot.locked = brush.text(12f, colours.lockLabel, 0f, size / 2f - 4f, size, BitmapFont.Align.Center);
        atDepth(slot.locked, 8f);
        slot.node.attachChild(slot.locked);

        var key = brush.text(14f, colours.bone, 0f, 1f, size - 3f, BitmapFont.Align.Right);
        key.setText(slot.key == BLANK ? "" : String.valueOf(slot.key));
        atDepth(key, 8f);
        slot.node.attachChild(key);
        slot.keyText = key;

        // What it costs, in the corner opposite the key. Small, because it is a
        // number he checks rather than reads -- and in the mana bar's own blue,
        // so the two are obviously about the same thing without a word between
        // them. It turns when he cannot pay, which is the whole point of it.
        slot.cost = brush.text(11f, colours.mana, 3f, 1f, size - 3f, BitmapFont.Align.Left);
        atDepth(slot.cost, 8f);
        slot.node.attachChild(slot.cost);
    }

    /**
     * The game's own picture for a slot, or {@code null} to fall back to the letter
     * of the key.
     *
     * <p>The image is white and is <em>tinted</em> as it is drawn, exactly as the
     * letter was: one file then serves a slot that is ready, one that is reloading
     * and one that is still locked, and {@link #dress} goes on setting a colour
     * without caring which of the two it has.
     *
     * <p>A picture that will not load is not a reason to lose the panel. The game
     * names its own art and the client cannot check the spelling — so a missing one
     * is said once, in the log, and the slot falls back to what it looked like
     * before there were any pictures.
     */
    private Geometry slotPicture(String icon, float size) {
        return brush.picture(icon, size, iconShare(size),
                icons.paintedSkills() ? IconLook.AS_PAINTED : colours.torch);
    }

    /**
     * What colour a skill's picture is drawn in, for the three states it can be
     * in when nothing is armed.
     *
     * <p>Two answers, and which one depends on what kind of picture the game
     * ships — see {@link IconLook}. A white drawing becomes whatever it is
     * multiplied by, so the state is a hue: torch, cold, dead. A painted one is
     * already a colour and multiplying by a second is how a blue frost burst
     * comes out gold, so the state is a brightness instead and the picture stays
     * the picture.
     *
     * <p>Static and handed its look rather than reading the field, for the same
     * reason {@link PanelSkin#texture} is: this is arithmetic about colour with no
     * window in it, and a test can hold it still without one.
     */
    static ColorRGBA skillColour(IconLook icons, PanelLook look, boolean locked, boolean cooling) {
        if (icons.paintedSkills()) {
            return locked ? IconLook.PAINTED_DEAD
                    : cooling ? IconLook.PAINTED_COLD : IconLook.AS_PAINTED;
        }
        return locked ? rgb(look.deadColour()).mult(new ColorRGBA(1f, 1f, 1f, 0.5f))
                : rgb(cooling ? look.glyphColdColour() : look.torchColour());
    }

    /**
     * What this one costs, and whether he can pay it.
     *
     * <p>Set before the slot is dressed, because being unable to pay changes how
     * the whole socket is drawn and {@link #dress} is what draws it.
     */
    private void priceUp(Slot slot, PanelReading card) {
        if (slot.cost == null) {
            return;
        }
        slot.affordable = true;
        if (card == null) {
            slot.cost.setText("");
            return;
        }
        for (var price : card.costs()) {
            if (price.key() == slot.key) {
                slot.cost.setText(String.valueOf(price.cost()));
                slot.affordable = price.affordable();
                return;
            }
        }
        slot.cost.setText("");
    }

    private void dress(Slot slot, PanelReading.SkillReading skill) {
        slot.state = skill.state();
        boolean passive = skill.state() == PanelReading.State.PASSIVE;
        if (slot.keyText != null) {
            slot.keyText.setText(passive || slot.key == BLANK ? "" : String.valueOf(focus.letterOf(slot.key)));
        }
        boolean locked = skill.state() == PanelReading.State.LOCKED;
        boolean cooling = skill.state() == PanelReading.State.COOLING;
        // Three ways a slot can be unusable and they must not look alike. Locked
        // is a skill he has not bought; cooling is one that is coming back and
        // says when; BROKE is one that is ready and waiting on the bar above.
        // Dressed as cooling would say "wait" about something no amount of
        // waiting for THIS slot will fix.
        boolean broke = !locked && !cooling && !slot.affordable;
        slot.deadStone.setCullHint(locked || broke
                ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        slot.glyph.getMaterial().setColor("Color",
                linear(broke ? IconLook.PAINTED_COLD.mult(colours.manaWash)
                        : skillColour(icons, look, locked, cooling)));
        if (slot.cost != null) {
            // The price is the one thing on a slot he cannot pay for that goes
            // BRIGHTER. Everything else about the socket dims, so the number is
            // what the eye lands on, and the number is the answer.
            slot.cost.setColor(linear(broke ? colours.manaDenied : colours.mana));
        }
        // A painted rim goes dead with the rest of the socket. Left at full
        // strength it was the one bright thing on a slot he cannot cast, which
        // says the opposite of what the slot means — the whole reason the stone
        // behind it darkens.
        if (slot.rim != null) {
            var gold = rgb(brush.skin.piece(PanelSkin.SLOT).tint().getRGB());
            slot.rim.getMaterial().setColor("Color",
                    linear(locked ? darker(gold, 0.6f) : cooling || passive ? darker(gold, 0.3f) : gold));
        }
        slot.locked.setText(locked ? skill.label() : "");
        slot.seconds.setText(cooling ? skill.label() : "");
        sweepTo(slot, cooling ? skill.left() : 0f);
        light(slot);
    }

    /**
     * What the player has put into this one, and whether the next point may go
     * here: the pips, the word under them, and the badge over the corner.
     *
     * <p>The four states the design names, and each says a different thing:
     * a rank he can raise says what it would BECOME ("2 → 3") in the colour of a
     * gain; one he cannot afford says only what it is; a full one says so in the
     * torch colour with its pips brightened; and one he has never bought says
     * nothing, because the badge beside it is the whole story.
     */
    private void dressRank(Slot slot, PanelReading.RankReading rank) {
        pipsFor(slot, rank.max());
        slot.canRaise = rank.canRaise();
        boolean full = rank.rank() >= rank.max();
        for (int i = 0; i < slot.pips.size(); i++) {
            boolean lit = i < rank.rank();
            slot.pips.get(i).getMaterial().setColor("Color",
                    linear(lit ? full ? colours.goldHi : colours.gold : colours.pipDark));
        }
        // The words are the game's and arrive finished; the colour is the
        // client's, because it is a fact about a state it can already see. Armed
        // wins over all of them: while he is choosing a target, the whole column
        // is the one cold thing on the bar.
        boolean picked = focus.armed != null && focus.armed == slot.key;
        slot.rankWord.setColor(linear(
                picked ? colours.selHi : rank.canRaise() ? colours.gain : full ? colours.torch : colours.label));
        slot.rankWord.setText(rank.word());
        slot.badge.setCullHint(rank.canRaise()
                ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        if (rank.canRaise()) {
            // Breathing, so a player with a point does not forget he has one. The
            // armed lip breathes at 0.9; this is a little quicker, because it is
            // asking for something rather than waiting.
            float breath = focus.breath(0.55f, 0.45f, 0.7f);
            slot.badgeGlow.getMaterial().setColor("Color",
                    linear(withAlpha(colours.torch, breath * 0.5f)));
        }
    }

    /**
     * The armed slot's lip, breathing so it cannot be mistaken for the ordinary
     * ready glow. Slow — twice a second, between two thirds and full — because the
     * point is "this one is waiting", not "look at me".
     *
     * <p>The hover wash is separate and quieter: it says a slot can be clicked,
     * which every ready one can, so it must not shout.
     */
    private void light(Slot slot) {
        boolean waiting = focus.armed != null && focus.armed == slot.key;
        var show = waiting ? Spatial.CullHint.Inherit : Spatial.CullHint.Always;
        slot.ring.setCullHint(show);
        slot.selStone.setCullHint(show);
        slot.brackets.setCullHint(show);
        slot.warm.setCullHint(focus.hovered != null && focus.hovered == slot.key
                && slot.state == PanelReading.State.READY
                ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        if (!waiting) {
            return;
        }
        // The picture goes cold with the stone under it. Set here rather than in
        // dress, which runs first and knows nothing about what is armed -- and
        // which would otherwise have the one slot that matters drawn in the same
        // torch colour as the three that do not.
        //
        // A painted picture is left alone: the rim, the stone and the brackets
        // round it have all gone cold already, and washing a fire arrow in cyan
        // would make the one socket that matters the one you cannot read.
        if (!icons.paintedSkills()) {
            slot.glyph.getMaterial().setColor("Color", linear(colours.selHi));
        }
        // Quicker than the old gold lip breathed, because this one is asking for
        // something: a click has to come before anything else can happen.
        float breath = focus.breath(0.72f, 0.28f, 1.1f);
        slot.ring.getMaterial().setColor("Color",
                linear(withAlpha(colours.sel, breath)));
    }

    /** Rebuild the cooldown shadow, but only when it has actually moved. */
    private void sweepTo(Slot slot, float remaining) {
        if (Math.abs(remaining - slot.sweptTo) < 0.002f) {
            return;
        }
        slot.sweptTo = remaining;
        slot.sweep.setMesh(sweep(slot.size, remaining));
    }

    /**
     * The shadow still lying over a slot: a wedge from twelve o'clock, clockwise,
     * covering the part of the cooldown that has not run off yet.
     *
     * <p>Cut to the slot's square rather than to a circle, because a round shadow
     * inside a square socket reads as a coin sitting on the stone rather than as
     * the stone itself being uncovered. The corners fall on segment boundaries, so
     * they come out sharp.
     */
    private static Mesh sweep(float size, float remaining) {
        if (remaining <= 0f) {
            return new Mesh();
        }
        int segments = Math.max(1, Math.round(48 * Math.min(1f, remaining)));
        float step = FastMath.TWO_PI * Math.min(1f, remaining) / segments;
        float half = size / 2f;
        var positions = new float[(segments + 2) * 3];
        positions[0] = half;
        positions[1] = half;
        for (int i = 0; i <= segments; i++) {
            float angle = i * step;
            float dx = FastMath.sin(angle);
            float dy = FastMath.cos(angle);
            float reach = half / Math.max(Math.abs(dx), Math.abs(dy));
            positions[(i + 1) * 3] = half + dx * reach;
            positions[(i + 1) * 3 + 1] = half + dy * reach;
        }
        var indices = new int[segments * 3];
        for (int i = 0; i < segments; i++) {
            indices[i * 3] = 0;
            indices[i * 3 + 1] = i + 1;
            indices[i * 3 + 2] = i + 2;
        }
        return meshOf(positions, indices);
    }

    /** As wide as the sockets come to, and never narrower than the design's four. */
    float width() {
        float wide = 0f;
        for (int i = 0; i < slots.size(); i++) {
            wide += slots.get(i).size + (i == 0 ? 0f : SLOT_GAP);
        }
        return Math.max(wide, skillSlot * 3f + ultimateSlot + SLOT_GAP * 3f);
    }

    float columnHeight() {
        return HEADING_SIZE + HEADING_GAP + Math.max(skillSlot, ultimateSlot) + UNDER_A_SLOT;
    }

    float foot() {
        return (BAND - columnHeight()) / 2f;
    }

    /** Skills: a heading, and the row of sockets under it. */
    void place(float x, float originX, float originY) {
        // The pips and their word hang under each slot, so the column is that much
        // taller than the slots are. Measured off the deepest, which is the
        // tallest socket's -- the ultimate's, as the design draws it -- whose foot
        // sits lowest.
        float tallest = Math.max(skillSlot, ultimateSlot);
        float columnHeight = HEADING_SIZE + HEADING_GAP + tallest + UNDER_A_SLOT;
        float bottom = (BAND - columnHeight) / 2f;
        float rowY = bottom + UNDER_A_SLOT;
        row.setLocalTranslation(x, rowY, 0f);
        headingNode.setLocalTranslation(x, rowY + tallest + HEADING_GAP, 0f);
        float slotX = 0f;
        for (var slot : slots) {
            // ★ CENTRED, not top-aligned. It hung from a common top, so an
            // ultimate was bigger only by reaching further DOWN -- which reads as
            // a socket that has slipped rather than as a larger one. The design
            // stands it proud at both ends, and that is what makes it the one the
            // eye finds without looking for it.
            float lift = (tallest - slot.size) / 2f;
            slot.node.setLocalTranslation(slotX, lift, 0f);
            slot.atX = originX + slotX;
            slot.atY = originY + rowY + lift;
            // Its own corner, in the same design pixels the slot is measured in,
            // so a click on the badge can be told from one on the slot under it.
            slot.badgeX = slot.atX + slot.size - BADGE + BADGE_OUT;
            slot.badgeY = slot.atY + slot.size - BADGE + BADGE_OUT;
            slotX += slot.size + SLOT_GAP;
        }
    }

    /**
     * Whether this key is a skill the panel is showing as unaffordable.
     *
     * <p>Asked so that a refusal to ARM can be told apart from a refusal to arm
     * for any other reason -- a slot still reloading, or one he has not bought.
     * Those two say why on the socket already; being broke says it on a bar at
     * the other end of the panel, which is not where he is looking.
     */
    boolean refusedForMana(char key) {
        for (var slot : slots) {
            if (slot.key == key) {
                return slot.state == PanelReading.State.READY && !slot.affordable;
            }
        }
        return false;
    }

    /**
     * Whether a skill looks castable — ready, not waiting for a level, and paid
     * for.
     *
     * <p>Used to refuse to arm something that would only be refused a moment later
     * by the simulation, which is the real judge. With no panel on screen there is
     * no opinion to give, and the answer is yes.
     *
     * <p><b>Affordability belongs here too.</b> It was checked only where the cast
     * is actually made, which is one step too late to matter: arming is what
     * changes the cursor and throws the reach out onto the floor, so a skill he
     * could not pay for still <em>aimed</em> — and then swallowed the click. The
     * whole of what "cannot afford" means is that nothing happens, and a cursor
     * that changes is something happening.
     */
    boolean readyToCast(char key) {
        for (var slot : slots) {
            if (slot.key == key) {
                return slot.state == PanelReading.State.READY && slot.affordable;
            }
        }
        return true;
    }

    /** Whether the panel is offering to put a point into that slot right now. */
    boolean canRaise(char key) {
        for (var slot : slots) {
            if (slot.key == key) {
                return slot.canRaise;
            }
        }
        return false;
    }

    /**
     * The upgrade badge under a screen point, or {@code null} for none.
     *
     * <p>Asked BEFORE {@link #slotAt}, because the badge hangs over the slot's own
     * corner and a click there means the badge rather than the skill. Only a slot
     * actually showing one answers — a badge that is hidden is not a target, and
     * the cursor passing over the corner of a slot with no point to spend must
     * arm the skill exactly as it always did.
     */
    Character badgeAt(float x, float y) {
        for (var slot : slots) {
            if (slot.key != BLANK && slot.canRaise
                    && hits(x, y, slot.badgeX, slot.badgeY, BADGE)) {
                return slot.key;
            }
        }
        return null;
    }

    /** Whether a click on the slot or its key does anything: a passive's does not, though its card still shows. */
    boolean pressable(char key) {
        for (var slot : slots) {
            if (slot.key == key) {
                return slot.state != PanelReading.State.PASSIVE;
            }
        }
        return true; // an order button, which is no skill slot
    }

    /** The letter drawn in the slot's corner: its key, or nothing for a passive. */
    String keyLetter(char key) {
        for (var slot : slots) {
            if (slot.key == key && slot.keyText != null) {
                return slot.keyText.getText();
            }
        }
        return "";
    }

    /** The skill slot under a point in the bar's design pixels, or {@code null} for none. */
    Character slotAt(float x, float y) {
        for (var slot : slots) {
            // An empty socket is furniture, not a control: it takes no click and
            // does not light under the cursor.
            if (slot.key != BLANK && hits(x, y, slot.atX, slot.atY, slot.size)) {
                return slot.key;
            }
        }
        return null;
    }

    /** Where a slot stands and how big it is, as x, y and size in design pixels, or null for a key with none. */
    float[] boxOf(char key) {
        for (var slot : slots) {
            if (slot.key == key) {
                return new float[] {slot.atX, slot.atY, slot.size};
            }
        }
        return null;
    }
}
