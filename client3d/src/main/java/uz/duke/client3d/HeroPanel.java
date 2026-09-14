package uz.duke.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.font.Rectangle;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.material.RenderState.FaceCullMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Texture;
import com.jme3.util.BufferUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import uz.duke.core.GameConstants;

/**
 * The hero's own bar along the bottom of the screen: where he is, what he is,
 * what is left of him, and what he can cast.
 *
 * <p>Drawn rather than written. The rest of the client's HUD is a line of text,
 * which is right for an RTS where the interesting numbers are money and power and
 * a player reads them once a minute. A dungeon hero is read continuously and at a
 * glance — how much health is left, whether the ultimate is up — and a glance
 * cannot parse "Q ready  W 2s".
 *
 * <p>One slab, not a scattering of boxes. The minimap sits in a socket at the
 * left, the portrait and the vitals in the middle, the skills at the right, the
 * depth at the far end; the sections are separated by
 * a carved line rather than by empty screen. That is the Warcraft III arrangement,
 * and it is an arrangement rather than a decoration: everything a player looks at
 * between one click and the next is in one place, so his eyes travel a hand's
 * width instead of a screen's.
 *
 * <p>Like {@link Shell} and {@link Hotkeys}, the client keeps the mechanism and
 * the game says what goes in it. Everything here is driven by the snapshot's
 * status channel, which the engine carries and never reads; a game that puts
 * something else there, or nothing, gets the plain text HUD exactly as before.
 * The wording is the game's too — no English is written into this class, because
 * the labels are a dungeon's, not the engine's.
 *
 * <p>Laid out in the pixels the design was drawn at and then scaled to the window,
 * so the bar keeps its proportions on any screen instead of shrinking into a
 * corner of a wide one or overrunning a narrow one.
 *
 * <p>The look is stone carving: a slot is a lit top edge over a dark inner
 * shadow, sitting on its own drop. Three colours do all the work — torch for what
 * a skill is, blood for health, green for experience — so that the one thing that
 * changes colour, a skill going dead while it reloads, is the thing the eye
 * catches.
 */
final class HeroPanel {

    // ---- the palette, and the only place colour is decided ----

    private static final ColorRGBA STONE_DEEP = rgb(0x16130F);
    /**
     * ★ LIGHTER THAN IT WAS, and it is what the whole bar was missing.
     *
     * <p>The design's stone is a warm grey that catches light; this was a third
     * darker, and against it every gold edge, every gauge and every letter lost
     * the contrast it was chosen for — the bar read as one dark mass with things
     * faintly on it rather than as a slab with things set into it. Nothing was
     * wrong with any single colour. They were all slightly too far down.
     */
    private static final ColorRGBA STONE = rgb(0x332B22);
    private static final ColorRGBA STONE_LIT = rgb(0x4A4033);
    private static final ColorRGBA STONE_DEAD_LIT = rgb(0x231F1A);
    private static final ColorRGBA STONE_DEAD = rgb(0x191510);
    private static final ColorRGBA TORCH = rgb(0xE8A33D);
    private static final ColorRGBA GOLD = rgb(0xC9A24B);
    private static final ColorRGBA GOLD_HI = rgb(0xF0D48A);
    private static final ColorRGBA GAIN = rgb(0x7FBF6A);
    private static final ColorRGBA BONE = rgb(0xD9CFBA);
    private static final ColorRGBA BLOOD = rgb(0xA8322B);
    private static final ColorRGBA ARCANE = rgb(0x5F8C7B);

    /**
     * The mana bar's blue.
     *
     * <p>Blue because the other two are taken and because it is what a player
     * arrives already knowing: red is what is left of him, green is what he has
     * earned, blue is what he can spend. A bar the player has to learn the colour
     * of is a bar he reads by its position, and position is the one thing that
     * changes when a panel is laid out again.
     */
    private static final ColorRGBA MANA = rgb(0x3E6FA8);

    /** The same, lit — what the bar flashes when he asks for what he cannot pay. */
    private static final ColorRGBA MANA_DENIED = rgb(0xE06A5A);

    /**
     * What a picture is washed with in a slot he cannot pay for.
     *
     * <p>Blue rather than grey, so the reason is on the socket rather than only
     * in the corner: a grey slot is a slot that is off, and a blue one is a slot
     * that is waiting on the blue bar.
     */
    private static final ColorRGBA MANA_WASH = new ColorRGBA(0.62f, 0.78f, 1f, 1f);
    private static final ColorRGBA DEAD = rgb(0x4A443B);
    private static final ColorRGBA EDGE = rgb(0x100D0A);
    private static final ColorRGBA DROP = rgb(0x0A0806);
    private static final ColorRGBA GLYPH_COLD = rgb(0x6A6154);
    /** An empty pip; the lit ones take the gold the rest of the bar uses. */
    private static final ColorRGBA PIP_DARK = rgb(0x2A241D);

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
    private final ColorRGBA sel;
    private final ColorRGBA selHi;
    /** The cold stone an armed socket is cut from. */
    private static final ColorRGBA SEL_STONE_LIT = rgb(0x2E4A46);
    private static final ColorRGBA SEL_STONE = rgb(0x16302E);
    /** How long an arm of a corner bracket is, and how thick. */
    private static final float BRACKET = 11f;
    private static final float BRACKET_THICK = 2f;
    /** How far the brackets sit outside the socket. */
    private static final float BRACKET_OUT = 5f;
    private static final ColorRGBA LABEL = rgb(0x8B8171);
    private static final ColorRGBA LOCK_LABEL = rgb(0x6E6555);
    private static final ColorRGBA SLAB_TOP = rgb(0x4A4033);
    private static final ColorRGBA SLAB_HIGH = rgb(0x3A3127);
    private static final ColorRGBA SLAB_MID = rgb(0x2B241C);
    private static final ColorRGBA SLAB_LOW = rgb(0x221C16);
    private static final ColorRGBA SLAB_RIM = rgb(0x6B5C46);
    private static final ColorRGBA SOCKET_RIM = rgb(0x453D30);
    private static final ColorRGBA FLESH = rgb(0x6B5B45);

    // ---- the layout, in the pixels the design was drawn at ----

    /** The width the design was drawn for; everything is scaled from it. */
    private static final float DESIGN_WIDTH = 1300f;

    /** Below this the bar is unreadable, above it silly. Both are the design's. */
    private static final float MIN_SCALE = 0.55f;
    private static final float MAX_SCALE = 1.30f;

    private static final float PAD = 10f;
    /** The tallest thing in the bar — the minimap — and so the bar's own height. */
    private static final float BAND = 172f;
    private static final float SLAB_HEIGHT = BAND + PAD * 2f;

    private static final float MINIMAP = BAND;
    private static final float DIVIDER = 3f;
    private static final float DIVIDER_MARGIN = 10f;
    /**
     * The portrait, which is taller than it is wide.
     *
     * <p>A square read as a picture of a square thing. Head and shoulders are a
     * standing shape, and the badge that hangs under it wants the height.
     */
    private static final float PORTRAIT = 126f;
    /**
     * ★ SHORTER THAN IT WAS, because two bars moved in under it.
     *
     * <p>What is left of him now hangs off his own face rather than off the far
     * side of his name: portrait, health, mana, one block the width of the
     * portrait. They are the three things about the man himself, and they used to
     * be split between two columns for no reason but that the bars were wide.
     */
    private static final float PORTRAIT_HEIGHT = 128f;

    /**
     * The column the portrait stands in, which is wider than the portrait.
     *
     * <p>The design's: a frame of a hundred and twenty-six in a column of a
     * hundred and fifty, with the two gauges filling the column rather than the
     * frame. The bars being wider than the face is what makes them read as the
     * block's own rather than as part of the picture.
     */
    private static final float PORTRAIT_COLUMN = 150f;

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
    /** The level badge slung under the portrait, straddling its bottom edge. */
    private static final float BADGE_HEIGHT = 21f;
    private static final float BADGE_WIDTH = 86f;
    private static final float BADGE_DROP = 11f;
    private static final float PORTRAIT_GAP = 10f;
    /**
     * ★ WIDER THAN IT WAS, and the figures underneath are why.
     *
     * <p>Four of them across, each on ONE line — a drawing, a word, a number and
     * what is lent. Measured in the font this client actually ships: "Zarba 34
     * +6" is sixty-six pixels at the design's size and eighty-one with its
     * drawing and the gap, so four of them want three hundred and forty-five. The
     * design gets away with less because it is set in a condensed face and this
     * is not; the width is the honest way to pay for that, since shrinking the
     * lettering instead would make the one line nobody can enlarge unreadable.
     */
    static final float VITALS_WIDTH = 424f;

    /** The column of order buttons that stands beside the map. */
    private static final float ORDER_BUTTON = 38f;
    private static final float ORDER_GAP = 5f;
    private static final float ORDER_COLUMN_GAP = 7f;

    /** His bag: three across, two down. */
    private static final float ITEM_SLOT = 42f;
    private static final float ITEM_GAP = 5f;
    private static final int ITEM_COLUMNS = 3;
    private static final int ITEM_ROWS = 2;

    /** The gold heading over a block — "NARSALAR", "MAHORAT". */
    private static final float HEADING_SIZE = 12f;
    private static final float HEADING_GAP = 6f;

    private static final float BAR_HEIGHT = 17f;

    /**
     * The experience bar, which is now the widest thing on the band.
     *
     * <p>It was ten pixels of trim under two bars that mattered more. It is
     * thirty, alone across the column, because it is the only bar on the panel
     * that is about the WHOLE RUN: health and mana come back, and this never goes
     * backwards. It is also the only one with room for lettering at both ends,
     * which is what let the level badge under the portrait go — the level was
     * being said twice, and this is the place the eye already is.
     */
    static final float XP_HEIGHT = 30f;
    /** How far apart the slants across a filling bar are drawn. */
    private static final float XP_SLANT_PITCH = 14f;
    /** And how wide each of them is: the design's six in fourteen. */
    private static final float XP_SLANT_THICK = 6f;

    /**
     * Between the two, and nearer the health.
     *
     * <p>It is a thing he spends rather than a thing he accumulates, so it reads
     * with what is left of him rather than with what he has earned — and it wants
     * a figure on it, which the experience bar does not, so it cannot be as thin.
     */
    private static final float MANA_HEIGHT = 14f;
    private static final float NAME_HEIGHT = 20f;
    private static final float TITLE_HEIGHT = 15f;
    /**
     * Everything in the column above the block of figures: his name, what he is, the gap
     * under that, and the experience bar. The block has what is left of the band.
     */
    static final float ABOVE_THE_BLOCK = 27f + 18f + 12f + XP_HEIGHT;

    /** The ring of bright gold round his primary's socket. */
    static final float PRIMARY_FRAME = 2f;

    /** Between a socket and the word beside it. */
    static final float ICON_GAP = 6f;

    /** Between one column of the block and the next. */
    static final float COLUMN_GAP = 8f;

    /** Between the longest word in a column and the values lined up after it. */
    static final float WORD_GAP = 6f;

    /**
     * How tall the block is kept, whatever arrives in it: room for the rows the game sized
     * it for, and never more than the band has left under the experience bar.
     *
     * <p>Fixed rather than fitted to each card, because the name, the title and the bar
     * are centred in the band together with it — a block that grew for a card with a
     * fourth attribute would move the hero's name.
     */
    static float blockHeight(StatLook look) {
        float figures = rowsTall(look.figureRows(), look.figureIcon(), look.rowGap());
        float primary = look.primaryIcon() + PRIMARY_FRAME * 2f + 2f + look.primaryText();
        float attributes = rowsTall(look.attributeRows(), look.attributeIcon(), look.rowGap());
        return Math.min(Math.max(figures, Math.max(primary, attributes)),
                BAND - ABOVE_THE_BLOCK - look.gapUnderBar());
    }

    private static float rowsTall(int rows, float icon, float gap) {
        return rows <= 0 ? 0f : rows * icon + (rows - 1) * gap;
    }

    /**
     * How {@code count} rows go into a column this tall: the size each socket is drawn at,
     * and the step from one row down to the next.
     *
     * <p>The size the game asked for while they fit. Past that they close up — the gaps
     * first, then the sockets themselves — rather than run off the bottom of the bar: a
     * game that adds a fourth attribute gets four smaller rows, not a fourth one under
     * the stone.
     */
    static float[] rowsIn(int count, float icon, float gap, float height) {
        if (count <= 1 || rowsTall(count, icon, gap) <= height) {
            float size = Math.min(icon, height);
            return new float[] {size, size + gap};
        }
        if (count * icon <= height) {
            return new float[] {icon, icon + (height - count * icon) / (count - 1)};
        }
        float size = height / count;
        return new float[] {size, size};
    }

    /** The size a row's lettering is set in: what the game asked for, never taller than the row. */
    static float letteringFor(float wanted, float row) {
        return Math.min(wanted, Math.max(6f, row - 4f));
    }

    /**
     * How much of a row is kept for what is lent: room for "+12" at that size.
     *
     * <p>Kept whether anything is lent or not, so the value beside it does not shuffle
     * sideways the moment he picks up a sword. A column of numbers that moves is a column
     * nobody can compare down.
     */
    static float lentRoom(float lettering) {
        return lettering * 2f;
    }

    /** Where the list of attributes starts across the column. */
    static float attributeColumnAt(StatLook look) {
        return Math.min(look.figureColumn() + look.primaryColumn() + COLUMN_GAP,
                VITALS_WIDTH - 80f);
    }
    private static final float SLOT = 58f;
    private static final float ULT_SLOT = 64f;

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

    /** The badge that says a point may go here: a small square over the corner. */
    private static final float BADGE = 22f;
    /** How far it hangs outside the slot, on both axes. */
    private static final float BADGE_OUT = 7f;
    private static final float SLOT_GAP = 9f;
    private static final float DEPTH_WIDTH = 96f;
    /** The depth numeral, alone and with the bottom of the descent beside it. */
    private static final float DEPTH_LARGE = 34f;
    private static final float DEPTH_SMALL = 19f;
    private static final float DROP_DEPTH = 3f;
    /** The line above the bar, for something that has just happened. */
    private static final float NOTE_SIZE = 15f;

    /** The key the design draws an ultimate for. Any other key gets an ordinary slot. */
    private static final char ULTIMATE_KEY = 'R';

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

    /** That margin as the fraction {@link #picture} wants, for a socket this big. */
    private static float iconShare(float size) {
        return size <= ICON_MARGIN * 2f ? 1f : (size - ICON_MARGIN * 2f) / size;
    }

    /**
     * The same, for an order button and for a figure under the bars.
     *
     * <p>A shade more than a skill's, because both of those are small squares and
     * a picture that leaves a third of a twenty-two pixel socket empty is a
     * picture nobody can make out.
     */
    private static final float ORDER_ICON_SHARE = 0.68f;

    private static final Logger LOG = Logger.getLogger(HeroPanel.class.getName());

    /**
     * The key a socket carries when it belongs to nobody.
     *
     * <p>The bar's sockets are furniture and what goes in them is data — the same
     * arrangement his bag has always had, where six sockets are drawn whether he
     * is carrying six things or none. A row that appeared and vanished with the
     * selection would make the bar a different shape every time the player
     * clicked, which is the one thing a bar must not be.
     */
    private static final char BLANK = '\0';

    /** How the design's own row is shaped, for a card that names no skills. */
    private static final float[] BLANK_ROW = {SLOT, SLOT, SLOT, ULT_SLOT};

    /** Pictures the game named and the client could not find — warned about once each. */
    private final Set<String> missingIcons = new HashSet<>();

    /** Whether the game's pictures are white drawings or painted — see {@link IconLook}. */
    private final IconLook icons;

    /** What the panel's edges are painted with, if the game asked for anything. */
    private final PanelSkin skin;

    private final AssetManager assets;
    private final BitmapFont font;
    private final Node root = new Node("hero-panel");
    /** Everything but the slab: scaled and centred as one group. */
    private final Node slab = new Node("slab");
    private final Node contents = new Node("contents");

    private BitmapText name;
    private BitmapText health;
    private Geometry healthFill;
    private final Node manaBar = new Node("mana");
    private Geometry manaFill;
    private BitmapText manaCount;
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

    /** How long the bar stays in the refusal colour. Long enough to catch, short
     * enough not to be mistaken for a state. */
    private static final float DENIAL_SECONDS = 0.4f;

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
                return slot.state == Reading.State.READY && !slot.affordable;
            }
        }
        return false;
    }

    /**
     * Say so: flash the bar and leave a noise to be taken.
     *
     * <p>The same answer the simulation's own refusal gets, raised from this side
     * because the cast never reaches the simulation any more. Arming is refused
     * here, so nothing is ever sent, so nothing can come back -- and a key that
     * does nothing and says nothing is a key the player thinks is broken.
     */
    void denyForMana(float seconds) {
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
    private Geometry experienceFill;
    private BitmapText depthNumber;
    private BitmapText depthWord;
    /** A line above the bar for something that just happened and will stop mattering. */
    private BitmapText note;

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
    private float screenWidth;
    private float scale = 1f;
    private boolean showing;

    /**
     * What the last status line said, so a second reader — the sounds — does not
     * have to parse it again.
     */
    private Reading reading;

    /** The face his name is set in, which is the plain one unless a game said. */
    private final BitmapFont display;

    /** The skill waiting for the player to click something, if any. */
    private Character armed;
    /** The slot the mouse is over, if any — it lights to say it can be clicked. */
    private Character hovered;
    /** Wall clock, for the armed slot's breathing — presentation only. */
    private float clock;

    HeroPanel(AssetManager assets, BitmapFont font, Node guiNode, float screenWidth,
            PanelSkin skin, RangeLook aiming) {
        this(assets, font, guiNode, screenWidth, skin, aiming, IconLook.DEFAULT);
    }

    HeroPanel(AssetManager assets, BitmapFont font, Node guiNode, float screenWidth,
            PanelSkin skin, RangeLook aiming, IconLook icons) {
        this(assets, font, null, guiNode, screenWidth, skin, aiming, icons, StatLook.DEFAULT);
    }

    /**
     * The same, told what face to set his NAME in.
     *
     * <p>One line of a panel, and it is the line that says which game this is: a
     * dungeon wants carved capitals over its hero and a skirmish game does not.
     * Everything else on the bar stays plain, because everything else is a number
     * or a word being read rather than a title being recognised.
     *
     * <p>Null falls back to the plain face, which is what the panel always did
     * and what the three other games this client serves will go on getting.
     */
    HeroPanel(AssetManager assets, BitmapFont font, BitmapFont display, Node guiNode,
            float screenWidth, PanelSkin skin, RangeLook aiming, IconLook icons,
            StatLook stats) {
        this.icons = icons == null ? IconLook.DEFAULT : icons;
        this.statLook = stats == null ? StatLook.DEFAULT : stats;
        this.assets = assets;
        this.font = font;
        this.display = display == null ? font : display;
        this.screenWidth = screenWidth;
        this.skin = skin == null ? PanelSkin.NONE : skin;
        // ★ ONE NUMBER, not two. The colour an armed slot is drawn in is the
        // colour the rings on the floor are drawn in, and they have to be the
        // same or the panel and the ground are speaking two languages about one
        // moment. Taken off the same RangeLook the rings use rather than kept
        // here, so the file moves both of them at once.
        var look = aiming == null ? RangeLook.DEFAULT : aiming;
        this.sel = rgb(look.allowColour());
        this.selHi = rgb(look.areaColour());
        guiNode.attachChild(root);
        // Beside the bar rather than inside it, and attached after, so it is drawn
        // over everything the bar is drawn under -- a card parented into the row
        // it describes would be clipped by the row.
        tip = new SkillTip(assets, font, guiNode);
        root.attachChild(slab);
        root.attachChild(contents);
        buildSlab();
        buildMinimapSocket();
        buildPortrait();
        buildPortraitVitals();
        buildVitals();
        buildHeadings();
        buildNote();
        buildDepth();
        // Placed now rather than when the first slot is carved: a hero with no
        // skills at all never carves one, and would sit in the screen's corner.
        layOut();
        hide();
    }

    /**
     * Show what the status line describes, or hide the panel if it describes
     * something else. Returns whether the panel took the line — the caller then
     * knows not to print it as text as well.
     */
    boolean show(String status, float seconds) {
        this.clock = seconds;
        var reading = Reading.parse(status);
        this.reading = reading;
        if (reading == null) {
            hide();
            return false;
        }
        showing = true;
        root.setCullHint(Spatial.CullHint.Inherit);
        say(name, reading.name);
        title.setText(spacedOut(reading.title.toUpperCase(java.util.Locale.ROOT)));
        say(rank, reading.rank);
        // No reading over an empty trough: "0 / 0" is a number, and a number is a
        // claim about somebody.
        say(health, reading.maxHealth <= 0f ? ""
                : Math.round(reading.health) + " / " + Math.round(reading.maxHealth));
        fillTo(healthFill, fraction(reading.health, reading.maxHealth));
        // A game that charges nothing for its skills sends no pool and gets no
        // bar. It leaves the space it would have taken: the column is laid out
        // once, in buildVitals, and nothing below it moves. Which is a gap rather
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
        boolean casts = reading.maxMana > 0f;
        manaBar.setCullHint(casts ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        if (casts) {
            say(manaCount, Math.round(reading.mana) + " / " + Math.round(reading.maxMana));
            fillTo(manaFill, fraction(reading.mana, reading.maxMana));
            // And it flashes when he asked for what he has not got. A flash
            // rather than a shake: the bar is two pixels from the one above it
            // and a bar that moves would read as the panel breaking.
            boolean denied = seconds < deniedUntil;
            manaFill.getMaterial().setColor("Color", linear(denied ? MANA_DENIED : MANA));
        }
        fillTo(experienceFill, fraction(reading.experience, reading.needed));
        // The figures at the far end of the bar, and the hatching across what is
        // filled. A game that gives him nothing to earn sends no total and gets a
        // bare bar rather than "0 / 0", which is a claim about somebody.
        say(experienceCount, reading.needed <= 0f ? ""
                : Math.round(reading.experience) + " / " + Math.round(reading.needed));
        slantTo(fraction(reading.experience, reading.needed));
        depthNumber.setText(reading.depth);
        // A floor out of four is three times the lettering of a floor, and the
        // stone it is carved into did not get any wider. Sized to what it has to
        // say — and only when that changes, because setting a size marks the text
        // for re-layout whether or not it moved.
        float wanted = reading.depth.length() > 4 ? DEPTH_SMALL : DEPTH_LARGE;
        if (depthNumber.getSize() != wanted) {
            depthNumber.setSize(wanted);
        }
        depthWord.setText(reading.depthWord);
        skillsWord.setText(reading.skillsWord);
        // Only while he has one. A counter reading nought is a thing to read and
        // dismiss every time the eye passes it; nothing there is nothing to read.
        pointsCount.setText(reading.points > 0
                ? reading.pointsWord + "  " + reading.points : "");
        note.setText(reading.note);
        showFace(reading.face, !reading.name.isBlank());
        showStatBlock(reading.stats, reading.attributes);
        showOrders(reading.orders, reading.ordersAreHis);
        showItems(reading.items, reading.itemsWord);
        tips = reading.tips;
        attributeTips = reading.attributeTips;
        showSkills(reading.skills, reading);
        showOnlyWhatTheCardHas(reading);
        return true;
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
     * <p>So a block with nothing to say is not drawn at all, and {@link #layOut}
     * closes the gap where it was. Which is also the honest rule for the four
     * other games this client draws: none of them sends skills or a bag either.
     */
    private void showOnlyWhatTheCardHas(Reading reading) {
        boolean named = !reading.title.isBlank();
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

    /** Take the bar out of the scene, so a fresh one can be built at a new size. */
    void destroy() {
        root.removeFromParent();
    }

    void hide() {
        showing = false;
        reading = null;
        root.setCullHint(Spatial.CullHint.Always);
    }

    /**
     * The status line as the panel read it.
     *
     * <p>Shared rather than parsed twice: the game's line carries a level, a
     * floor, and what he just picked up, and those are moments worth hearing as
     * well as worth drawing — see {@link GameSounds}. Two readers of one line
     * would drift the first time the line grew a field.
     */
    Reading reading() {
        return reading;
    }

    /** Whether the bar is on screen at all — nothing may be clicked while it is not. */
    boolean isShowing() {
        return showing;
    }

    /** Which skill is waiting to be pointed at something, so a rebuilt bar can be told. */
    Character armedKey() {
        return armed;
    }

    /**
     * Say which skill is waiting for the player to click something, or {@code null}
     * for none. Its slot lights up and breathes, which is the only thing on screen
     * saying why the next click will not do what a click usually does.
     */
    void arm(Character key) {
        this.armed = key;
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
                return slot.state == Reading.State.READY && slot.affordable;
            }
        }
        return true;
    }

    // ---- what the mouse is over ----

    /** Whether a screen point is on the bar at all, and so not on the world. */
    boolean contains(float screenX, float screenY) {
        return showing && screenY <= heightPixels();
    }

    /**
     * How tall the bar stands in window pixels, or 0 when it is hidden.
     *
     * <p>What the world's bottom edge actually is, as far as anything reaching for
     * it is concerned.
     */
    float heightPixels() {
        return showing ? SLAB_HEIGHT * scale : 0f;
    }

    /**
     * The skill slot under a screen point, or {@code null} for none.
     *
     * <p>The bar is drawn in design pixels and scaled to the window, so a cursor
     * has to come the other way — out of the window and into the design — before
     * it can be compared with anything.
     */
    /**
     * The upgrade badge under a screen point, or {@code null} for none.
     *
     * <p>Asked BEFORE {@link #slotAt}, because the badge hangs over the slot's own
     * corner and a click there means the badge rather than the skill. Only a slot
     * actually showing one answers — a badge that is hidden is not a target, and
     * the cursor passing over the corner of a slot with no point to spend must
     * arm the skill exactly as it always did.
     */
    /** Whether the panel is offering to put a point into that slot right now. */
    boolean canRaise(char key) {
        for (var slot : slots) {
            if (slot.key == key) {
                return slot.canRaise;
            }
        }
        return false;
    }

    Character badgeAt(float screenX, float screenY) {
        if (!showing) {
            return null;
        }
        for (var slot : slots) {
            if (slot.key != BLANK && slot.canRaise
                    && hits(screenX / scale, screenY / scale,
                            slot.badgeX, slot.badgeY, BADGE)) {
                return slot.key;
            }
        }
        return null;
    }

    Character slotAt(float screenX, float screenY) {
        if (!showing) {
            return null;
        }
        for (var slot : slots) {
            // An empty socket is furniture, not a control: it takes no click and
            // does not light under the cursor.
            if (slot.key != BLANK
                    && hits(screenX / scale, screenY / scale, slot.atX, slot.atY, slot.size)) {
                return slot.key;
            }
        }
        return orderAt(screenX, screenY);
    }

    /**
     * The order button under a point, if any.
     *
     * <p>Answered through the same door the skill sockets are, so a click on a
     * button is the same thing as its key being pressed and neither of them can
     * grow a rule the other does not have.
     */
    Character orderAt(float screenX, float screenY) {
        if (!ordersAreHis) {
            return null; // not his to command; the button is furniture, like an empty socket
        }
        if (!showing) {
            return null;
        }
        for (var button : orderButtons) {
            if (button.key != BLANK
                    && hits(screenX / scale, screenY / scale, button.atX, button.atY,
                            ORDER_BUTTON)) {
                return button.key;
            }
        }
        return null;
    }

    /**
     * Whether a point is inside a square, in the design's own pixels.
     *
     * <p>Its own method because it is the part that can be wrong without looking
     * wrong: a slot that answers to clicks an inch from where it is drawn is a
     * bug nobody sees until they try to cast something.
     */
    static boolean hits(float x, float y, float left, float bottom, float size) {
        return x >= left && x <= left + size && y >= bottom && y <= bottom + size;
    }

    /** Light the slot the mouse is resting on, or none. */
    void hover(Character key) {
        this.hovered = key;
        placeTip();
    }

    /**
     * The attribute under a point, by its place in the game's list, or null.
     *
     * <p>The whole row rather than only its drawing — the word and the number are what
     * the eye is on, and the hand follows the eye — and his primary's framed socket as
     * well, which is the same attribute drawn large. A figure has no card, so it is never
     * the answer.
     */
    Integer attributeAt(float screenX, float screenY) {
        if (!showing) {
            return null;
        }
        float x = screenX / scale - vitalsAtX;
        float y = screenY / scale - PAD;
        for (int i = 0; i < attributeSpots.size(); i++) {
            if (inside(x, y, attributeSpots.get(i))) {
                return i;
            }
        }
        if (primarySpot != null && inside(x, y, primarySpot)) {
            return primaryIndex;
        }
        return null;
    }

    /** Whether a point is in a box written as x, y, width, height. */
    private static boolean inside(float x, float y, float[] box) {
        return x >= box[0] && x <= box[0] + box[2] && y >= box[1] && y <= box[1] + box[3];
    }

    /** Rest the cursor on an attribute, or take it off one. */
    void hoverAttribute(Integer index) {
        this.hoveredAttribute = index;
        placeTip();
    }

    /**
     * Set a bar to a fraction of its trough, taking an empty one away entirely
     * rather than squashing it to nothing — a geometry scaled to zero is a
     * degenerate bound, and a hero at no health is exactly when it happens.
     */
    private static void fillTo(Geometry bar, float fraction) {
        bar.setCullHint(fraction <= 0f ? Spatial.CullHint.Always : Spatial.CullHint.Inherit);
        if (fraction > 0f) {
            bar.setLocalScale(fraction, 1f, 1f);
        }
    }

    /** The panel is anchored to the bottom of the window, so a resize moves it. */
    void resize(float width) {
        this.screenWidth = width;
        layOut();
    }

    // ---- the slab everything is cut out of ----

    private void buildSlab() {
        // Rebuilt on every resize, because it is the one piece whose width is the
        // window's rather than the design's.
        slab.detachAllChildren();
        var stone = new Geometry("slab",
                gradient(Math.max(1f, screenWidth / Math.max(scale, 0.0001f)), SLAB_HEIGHT,
                        SLAB_TOP, SLAB_HIGH, SLAB_MID, SLAB_LOW));
        stone.setMaterial(vertexColoured());
        attach(slab, stone, 0f, 0f, 0f);
        // The lit rim along the top: a slab of stone catching the room's light,
        // and the line that says the bar is a thing rather than a tint.
        attach(slab, flat("rim", Math.max(1f, screenWidth / Math.max(scale, 0.0001f)), 3f,
                SLAB_RIM), 0f, SLAB_HEIGHT - 3f, 1f);
    }

    /**
     * A carved line between two sections: dark in the middle, fading at both ends,
     * with a hairline of light down one side.
     *
     * <p>A groove rather than a border, because the sections are parts of one
     * stone. A drawn box around each would say they were four things sitting next
     * to each other, which is exactly what this bar is not.
     */
    private Node divider() {
        var node = new Node("divider");
        attach(node, flat("groove", DIVIDER, BAND, DROP), 0f, 0f, 1f);
        if (!paintDivider(node)) {
            attach(node, flat("catch", 1f, BAND, new ColorRGBA(1f, 1f, 1f, 0.05f)),
                    DIVIDER, 0f, 2f);
        }
        return node;
    }

    // ---- the minimap's socket ----

    private Node minimapSocket;

    /**
     * The hole the minimap is drawn into: a black recess with a stone lip.
     *
     * <p>Only the frame is here. What goes in it belongs to the client's minimap,
     * which knows about worlds and cameras and has no business being rebuilt every
     * time a hero's health changes — so this says where it goes and the app puts
     * it there.
     */
    private void buildMinimapSocket() {
        minimapSocket = new Node("minimap-socket");
        contents.attachChild(minimapSocket);
        attach(minimapSocket, flat("recess", MINIMAP + 4f, MINIMAP + 4f, DROP), -2f, -2f, 0f);
        attach(minimapSocket, flat("lip", MINIMAP + 2f, MINIMAP + 2f, SOCKET_RIM), -1f, -1f, 1f);
        attach(minimapSocket, flat("hole", MINIMAP, MINIMAP, rgb(0x0C0A08)), 0f, 0f, 2f);
        // Over the hole rather than under it: the map is drawn into the socket by
        // the client's own minimap, at a depth this panel does not own, and a rim
        // painted underneath would be a rim nobody ever sees.
        framed(minimapSocket, PanelSkin.MINIMAP, -3f, -3f, MINIMAP + 6f, MINIMAP + 6f, 3f);
    }

    /**
     * Where the minimap goes, in window pixels: {@code x, y, size}.
     *
     * <p>Handed out rather than drawn here so the two stay in step through a
     * resize without the minimap having to know the bar's arithmetic.
     */
    float[] minimapRect() {
        var at = minimapSocket.getLocalTranslation();
        return new float[] {
            (contents.getLocalTranslation().x + at.x) * scale,
            (contents.getLocalTranslation().y + at.y) * scale,
            MINIMAP * scale,
        };
    }

    // ---- the portrait ----

    private Node portrait;

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
    private void buildPortrait() {
        portrait = new Node("portrait");
        contents.attachChild(portrait);
        attach(portrait, flat("frame", PORTRAIT + 4f, PORTRAIT_HEIGHT + 4f, DROP), -2f, -2f, 0f);
        var face = new Geometry("face",
                gradient(PORTRAIT, PORTRAIT_HEIGHT, rgb(0x4A4034), rgb(0x241E17)));
        face.setMaterial(vertexColoured());
        attach(portrait, face, 0f, 0f, 1f);
        attach(portrait, flat("inner", PORTRAIT + 2f, 2f, rgb(0x5A4E3C)),
                -1f, PORTRAIT_HEIGHT, 2f);

        // Where the live creature lands, inset the way the design insets it. Built
        // empty and kept that way until a picture arrives: it is under the corner
        // brackets and over the lit recess, so what shows through the gaps is the
        // frame's own stone rather than a grey card.
        live = new Geometry("live", new Quad(PORTRAIT - PORTRAIT_INSET * 2f,
                PORTRAIT_HEIGHT - PORTRAIT_INSET * 2f));
        live.setMaterial(unshaded(ColorRGBA.White));
        live.setCullHint(Spatial.CullHint.Always);
        attach(portrait, live, PORTRAIT_INSET, PORTRAIT_INSET, 2.5f);

        // Head and shoulders. The figure is drawn in a square as it always was and
        // sits at the bottom of a taller frame, so the extra height is headroom
        // rather than a stretched man.
        var head = new Geometry("head", disc(17f, 16));
        head.setMaterial(unshaded(FLESH));
        attach(figure, head, FIGURE / 2f, PORTRAIT_HEIGHT - 44f, 3f);
        var body = new Geometry("body", polygon(shoulders(), FIGURE));
        body.setMaterial(unshaded(FLESH));
        attach(figure, body, 0f, 0f, 3f);

        // A bow held at his side: the one line saying which hero this is. The
        // limb bows out to the right and the string cuts straight back.
        var bow = new Geometry("bow", strokes(new float[][] {
            bowLimb(), {64, 26, 62, 62},
        }, FIGURE));
        bow.setMaterial(lines(TORCH));
        attach(figure, bow, 0f, PORTRAIT_HEIGHT - FIGURE, 4f);
        attach(portrait, figure, (PORTRAIT - FIGURE) / 2f, 0f, 0f);

        if (framed(portrait, PanelSkin.PORTRAIT, -3f, -3f,
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
            bracket.setMaterial(lines(new ColorRGBA(TORCH.r, TORCH.g, TORCH.b, 0.75f)));
            attach(portrait, bracket, corners[i][0], corners[i][1], 5f);
        }
    }

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
    private Geometry live;

    /** The picture currently hung in it, compared by identity — see {@link #live}. */
    private Texture livePicture;

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

    /** What stands in the frame instead of him, when something else is selected. */
    private Geometry faceGlyph;

    /** The level, on a plate slung across the bottom edge of the portrait. */

    /** Draw the figure the card names, or his own silhouette when it names none. */
    private void showFace(String named, boolean anybody) {
        if (livePicture != null) {
            // Somebody live is in the frame, so neither drawing belongs in it. The
            // frame, its brackets and the badge stay: those are the furniture.
            figure.setCullHint(Spatial.CullHint.Always);
            if (faceGlyph != null) {
                faceGlyph.removeFromParent();
                faceGlyph = null;
                facedWith = "";
            }
            return;
        }
        if (!anybody) {
            // Nobody is selected: an empty frame, and no figure in it. The frame
            // stays because it is furniture; the face is what the card carries.
            figure.setCullHint(Spatial.CullHint.Always);
            if (faceGlyph != null) {
                faceGlyph.removeFromParent();
                faceGlyph = null;
                facedWith = "";
            }
            return;
        }
        boolean his = named == null || named.isBlank();
        figure.setCullHint(his ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        if (his) {
            if (faceGlyph != null) {
                faceGlyph.removeFromParent();
                faceGlyph = null;
                facedWith = "";
            }
            return;
        }
        if (named.equals(facedWith)) {
            return;
        }
        facedWith = named;
        if (faceGlyph != null) {
            faceGlyph.removeFromParent();
        }
        faceGlyph = new Geometry("face-glyph", Glyphs.of(named, PORTRAIT * 0.62f));
        faceGlyph.setMaterial(lines(BONE));
        attach(portrait, faceGlyph, PORTRAIT / 2f, PORTRAIT_HEIGHT / 2f, 4f);
    }

    /** What the frame is currently showing, so the glyph is not rebuilt every frame. */
    private String facedWith = "";

    /**
     * What level he is, hung under his picture rather than written beside his
     * name.
     *
     * <p>It belongs to the figure, not to the line of text: a level is what he is
     * rather than something he has, and on the portrait it is where the eye
     * already is. Straddling the frame's edge rather than sitting under it is what
     * makes it read as fixed to the picture instead of as a caption.
     */

    // ---- the orders beside the map ----

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
    private static final List<Reading.OrderReading> BLANK_ORDERS = List.of(
            new Reading.OrderReading(BLANK, "", "", false),
            new Reading.OrderReading(BLANK, "", "", false),
            new Reading.OrderReading(BLANK, "", "", false),
            new Reading.OrderReading(BLANK, "", "", false));

    private final Node orderColumn = new Node("orders");
    private final List<OrderButton> orderButtons = new ArrayList<>();
    /** The orders the column was built for; null to begin with, as above. */
    private String ordersBuiltFor;

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
    private void showOrders(List<Reading.OrderReading> reading, boolean his) {
        ordersAreHis = his;
        var signature = new StringBuilder();
        for (var order : reading) {
            signature.append(order.key()).append(order.icon()).append(',');
        }
        if (!signature.toString().equals(ordersBuiltFor)) {
            ordersBuiltFor = signature.toString();
            for (var button : orderButtons) {
                button.node.removeFromParent();
            }
            orderButtons.clear();
            var drawn = reading.isEmpty() ? BLANK_ORDERS : reading;
            for (var order : drawn) {
                var button = new OrderButton(order.key());
                cut(button, order);
                orderButtons.add(button);
                orderColumn.attachChild(button.node);
            }
            layOut();
        }
        for (int i = 0; i < orderButtons.size() && i < reading.size(); i++) {
            var button = orderButtons.get(i);
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
            boolean waiting = his && Character.valueOf(button.key).equals(armed);
            boolean reaching = waiting || (his && Character.valueOf(button.key).equals(hovered));
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
                float breath = 0.72f + 0.28f * FastMath.sin(clock * FastMath.TWO_PI * 1.1f);
                button.lit.getMaterial().setColor("Color",
                        linear(new ColorRGBA(sel.r, sel.g, sel.b, breath)));
            } else if (marked) {
                button.lit.getMaterial().setColor("Color", linear(sel));
            } else {
                button.lit.getMaterial().setColor("Color", linear(TORCH));
            }
            // Dim when it is not his, and dim whether or not it is lit: a skeleton
            // walking still shows its walk, because reading what something across
            // the room is doing is worth as much as reading his own — it is only
            // the offer to change it that goes away.
            button.glyph.getMaterial().setColor("Color",
                    linear(marked ? selHi : his ? (on ? GOLD_HI : GOLD) : (doing ? GOLD : DEAD)));
        }
    }

    /**
     * Put the card over the slot or the figure the cursor is on, or take it away.
     *
     * <p>Only over a SKILL or an ATTRIBUTE: the order buttons beside them are four
     * words a player learns once, and a card explaining "attack" every time his hand
     * passed the column would be the panel talking for the sake of it. An attribute
     * is the other way round — what one point of it is worth is exactly the thing
     * nobody can see by looking at it.
     */
    private void placeTip() {
        if (tip == null) {
            return;
        }
        if (hovered != null && tips.containsKey(hovered)) {
            for (var slot : slots) {
                if (slot.key == hovered) {
                    // Above the socket, not over it: a card that covered the thing it
                    // describes would hide the pips the moment they became the reason
                    // to read it.
                    tip.show(tips.get(hovered), slot.atX, slot.atY + slot.size + TIP_LIFT,
                            scale, screenWidth);
                    return;
                }
            }
        }
        if (hoveredAttribute != null && attributeTips.containsKey(hoveredAttribute)) {
            // Over the whole bar, not over the row: the block sits low in a column
            // with a name, a title and a lettered bar above it, and a card opened just
            // over a row lay across all three and under their words. From the middle
            // column, so it stands over his primary and the list together.
            tip.show(attributeTips.get(hoveredAttribute), vitalsAtX + statLook.figureColumn(),
                    SLAB_HEIGHT + TIP_LIFT, scale, screenWidth);
            return;
        }
        tip.hide();
    }

    /** How far the card floats over the slot it belongs to. */
    private static final float TIP_LIFT = 12f;

    /** Whether a card is on screen. For the tests. */
    boolean tipShowing() {
        return tip != null && tip.showing();
    }

    /** How tall the card came out. For the tests. */
    float tipHeight() {
        return tip == null ? 0f : tip.heightDrawn();
    }

    /**
     * Whether that order button is wearing the mark of the one he is in — the cold
     * stone and the corner brackets an armed skill wears. For the tests.
     */
    boolean orderIsMarked(char key) {
        for (var button : orderButtons) {
            if (button.key == key) {
                return button.selStone.getCullHint() != Spatial.CullHint.Always
                        && button.brackets.getCullHint() != Spatial.CullHint.Always;
            }
        }
        return false;
    }

    /** Whether that key is one of the order buttons rather than a skill. */
    boolean isAnOrder(char key) {
        for (var button : orderButtons) {
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
    boolean ordersAreHis() {
        return ordersAreHis;
    }

    private boolean ordersAreHis;

    /** One button cut out of the stone, the same way a skill socket is. */
    private void cut(OrderButton button, Reading.OrderReading order) {
        float size = ORDER_BUTTON;
        attach(button.node, flat("drop", size + 3f, size + 3f, DROP), -1.5f, -1.5f - 2f, 0f);
        // The lit ring, shown while the order is on or waiting to be pointed at
        // something. Under the edge, so it reads as stone catching light.
        button.lit = flat("order-lit", size + 6f, size + 6f, TORCH);
        attach(button.node, button.lit, -3f, -3f, 1f);
        button.lit.setCullHint(Spatial.CullHint.Always);
        attach(button.node, flat("edge", size + 3f, size + 3f, EDGE), -1.5f, -1.5f, 2f);
        var stone = new Geometry("stone", gradient(size, size, STONE_LIT, STONE));
        stone.setMaterial(vertexColoured());
        attach(button.node, stone, 0f, 0f, 3f);

        // The same cold treatment the skill sockets get. An order that waits for
        // a click -- attack something, guard somewhere -- is in exactly the state
        // an aimed skill is in, and the player should not have to learn two
        // pictures for one idea.
        button.selStone = new Geometry("stone-armed",
                gradient(size, size, SEL_STONE_LIT, SEL_STONE));
        button.selStone.setMaterial(vertexColoured());
        attach(button.node, button.selStone, 0f, 0f, 3.2f);
        button.selStone.setCullHint(Spatial.CullHint.Always);
        button.brackets = reticle(size);
        attach(button.node, button.brackets, 0f, 0f, 39f);
        button.brackets.setCullHint(Spatial.CullHint.Always);
        boolean blank = button.key == BLANK && order.icon().isBlank();
        // A picture from the game's own file, like the skills beside it. A
        // missing one falls back to nothing rather than losing the button: the
        // key in the corner is what the button is for teaching anyway.
        var drawn = blank ? null : picture(order.icon(), size, ORDER_ICON_SHARE, GOLD);
        if (drawn == null) {
            button.glyph = new Geometry("order-glyph", new Mesh());
            button.glyph.setMaterial(lines(DEAD));
            attach(button.node, button.glyph, size / 2f, size / 2f, 4f);
        } else {
            button.glyph = drawn;
            float inset = size * (1f - ORDER_ICON_SHARE) / 2f;
            attach(button.node, button.glyph, inset, inset, 4f);
        }
        // The key in the corner, because the button's whole job is to teach it.
        var key = text(10f, LABEL, 0f, -1f, size - 2f, BitmapFont.Align.Right);
        key.setText(button.key == BLANK ? "" : String.valueOf(button.key));
        key.setLocalTranslation(0f, key.getLocalTranslation().y, 5f);
        button.node.attachChild(key);
        framed(button.node, PanelSkin.BUTTON, -1.5f, -1.5f, size + 3f, size + 3f, 4.5f);
    }

    // ---- his bag ----

    /** One socket in the bag. Six of them, whatever he is carrying. */
    private static final class ItemSlot {
        private final Node node = new Node("item");
        private Geometry glyph;
        private Geometry empty;
        private BitmapText count;
    }

    private final Node itemGrid = new Node("items");
    private final List<ItemSlot> itemSlots = new ArrayList<>();
    private BitmapText itemsWord;
    /** What the grid was last filled with, so it is not rebuilt every frame. */
    private String itemsBuiltFor = "";

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
    private void showItems(List<Reading.ItemReading> reading, String word) {
        if (itemSlots.isEmpty()) {
            for (int i = 0; i < ITEM_COLUMNS * ITEM_ROWS; i++) {
                var slot = new ItemSlot();
                socket(slot, i);
                itemSlots.add(slot);
                itemGrid.attachChild(slot.node);
            }
        }
        if (itemsWord != null) {
            itemsWord.setText(word);
        }
        var signature = new StringBuilder();
        for (var item : reading) {
            signature.append(item.icon()).append(':').append(item.count()).append(',');
        }
        if (signature.toString().equals(itemsBuiltFor)) {
            return;
        }
        itemsBuiltFor = signature.toString();
        for (int i = 0; i < itemSlots.size(); i++) {
            var slot = itemSlots.get(i);
            var item = i < reading.size() ? reading.get(i) : null;
            fillSocket(slot, item, i);
        }
    }

    /** Put a thing in a socket, or leave it showing that it is empty. */
    private void fillSocket(ItemSlot slot, Reading.ItemReading item, int index) {
        if (slot.glyph != null) {
            slot.glyph.removeFromParent();
            slot.glyph = null;
        }
        slot.empty.setCullHint(item == null ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        slot.count.setText(item != null && item.count() > 1 ? String.valueOf(item.count()) : "");
        if (item == null) {
            return;
        }
        slot.glyph = new Geometry("item-glyph", Glyphs.of(item.icon(), ITEM_SLOT * 0.56f));
        slot.glyph.setMaterial(lines(ITEM_COLOURS[index % ITEM_COLOURS.length]));
        attach(slot.node, slot.glyph, ITEM_SLOT / 2f, ITEM_SLOT / 2f, 4f);
    }

    /**
     * What each socket's drawing is coloured.
     *
     * <p>By socket rather than by what is in it, which sounds backwards and is not:
     * the client has no idea what a flask does, and colouring by the picture would
     * mean it deciding that flasks are green. Six settled colours give the grid the
     * look the design has — a row of different things — without the client claiming
     * to know what any of them is.
     */
    private static final ColorRGBA[] ITEM_COLOURS = {
        rgb(0xC4564A), rgb(0x5F8C7B), rgb(0xC9A24B), rgb(0xE8A33D), rgb(0x8FA8C4), rgb(0xB08CC4),
    };

    /** One empty socket, cut like the skill slots but smaller. */
    private void socket(ItemSlot slot, int index) {
        attach(slot.node, flat("drop", ITEM_SLOT + 3f, ITEM_SLOT + 3f, DROP), -1.5f, -3.5f, 0f);
        attach(slot.node, flat("edge", ITEM_SLOT + 3f, ITEM_SLOT + 3f, EDGE), -1.5f, -1.5f, 1f);
        var stone = new Geometry("stone",
                gradient(ITEM_SLOT, ITEM_SLOT, rgb(0x3E3529), rgb(0x201A14)));
        stone.setMaterial(vertexColoured());
        attach(slot.node, stone, 0f, 0f, 2f);
        // The dashed inner square that says a socket is empty rather than dark.
        slot.empty = new Geometry("empty", dashedBox(ITEM_SLOT - 14f));
        slot.empty.setMaterial(lines(new ColorRGBA(STONE_LIT.r, STONE_LIT.g, STONE_LIT.b, 0.7f)));
        attach(slot.node, slot.empty, 7f, 7f, 3f);
        slot.count = text(11f, BONE, 0f, -1f, ITEM_SLOT - 3f, BitmapFont.Align.Right);
        slot.count.setLocalTranslation(0f, slot.count.getLocalTranslation().y, 5f);
        slot.node.attachChild(slot.count);
        var number = text(10f, rgb(0x7A7062), 2f, ITEM_SLOT - 13f, ITEM_SLOT,
                BitmapFont.Align.Left);
        number.setText(String.valueOf(index + 1));
        number.setLocalTranslation(number.getLocalTranslation().x,
                number.getLocalTranslation().y, 5f);
        slot.node.attachChild(number);
        framed(slot.node, PanelSkin.ITEM, -1.5f, -1.5f, ITEM_SLOT + 3f, ITEM_SLOT + 3f, 4.5f);
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

    // ---- the vitals column ----

    private Node vitals;

    /** What he is, under his name, and the wash it sits on. */
    private BitmapText title;
    private final Node titleLine = new Node("title-line");
    /** The experience bar, kept together so it can go when nothing is levelling. */
    private final Node experienceBar = new Node("experience");
    /** The two gauges under the portrait, which travel with it. */
    private final Node portraitBars = new Node("portrait-bars");

    /**
     * The slanted hatching across the filled part of the experience bar, and
     * how wide it was last cut for.
     *
     * <p>Rebuilt rather than scaled: scaling a slant shears it, and a bar whose
     * hatching gets steeper as it fills reads as a rendering fault. Cut again
     * only when the filled width has moved by a whole pixel, which for
     * experience is a few times a floor.
     */
    private Geometry experienceSlant;
    private int slantedTo = -1;
    /** The two readings inside the experience bar: his level, and how far in. */
    private BitmapText rank;
    private BitmapText experienceCount;

    private void buildVitals() {
        vitals = new Node("vitals");
        contents.attachChild(vitals);

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
        this.statsTop = experienceY - statLook.gapUnderBar();
        vitals.attachChild(statBlock);

        name = carved(22f, BONE, 0f, nameY, VITALS_WIDTH, BitmapFont.Align.Center);
        shadowed(vitals, name,
                carved(22f, DROP, 1f, nameY - 1f, VITALS_WIDTH, BitmapFont.Align.Center), 1f);
        // A wash behind the title, which is what stops a second centred line from
        // reading as a second name.
        var wash = new Geometry("title-wash",
                sideways(VITALS_WIDTH, TITLE_HEIGHT + 3f, new ColorRGBA(GOLD.r, GOLD.g,
                        GOLD.b, 0.14f)));
        wash.setMaterial(vertexColoured());
        attach(titleLine, wash, 0f, titleY - 2f, 0f);
        title = text(13f, GOLD, 0f, titleY, VITALS_WIDTH, BitmapFont.Align.Center);
        title.setLocalTranslation(0f, title.getLocalTranslation().y, 1f);
        titleLine.attachChild(title);
        vitals.attachChild(titleLine);

        // attachChild rather than attach: the trough is placed where it is built, and
        // attach would put it back at nought -- which drew it along the bottom of the
        // column, a dark band behind whatever stood there, instead of under its fill.
        experienceBar.attachChild(trough(0f, experienceY, VITALS_WIDTH, XP_HEIGHT));
        experienceFill = fill("xp-fill", VITALS_WIDTH - 2f, XP_HEIGHT - 2f, ARCANE);
        experienceFill.setLocalTranslation(1f, experienceY + 1f, 1f);
        experienceBar.attachChild(experienceFill);
        // Over the fill and under the lettering. Faint on purpose: it is there to
        // say "this is filling up" rather than to be looked at, and a bar that
        // reads as a barber's pole is a bar nobody reads the number off.
        experienceSlant = new Geometry("xp-slant", new Mesh());
        experienceSlant.setMaterial(unshaded(new ColorRGBA(1f, 1f, 1f, 0.10f)));
        experienceSlant.setLocalTranslation(1f, experienceY + 1f, 2f);
        experienceBar.attachChild(experienceSlant);
        // Both readings INSIDE the bar, which is the whole reason it is thirty
        // pixels tall. His level at the near end and how far through it he is at
        // the far one -- the two halves of the same sentence, read left to right.
        float lettering = experienceY + (XP_HEIGHT - 15f) / 2f;
        rank = carved(15f, GOLD_HI, 11f, lettering, VITALS_WIDTH, BitmapFont.Align.Left);
        shadowed(experienceBar, rank,
                carved(15f, DROP, 12f, lettering - 1f, VITALS_WIDTH, BitmapFont.Align.Left), 3f);
        experienceCount = text(13f, BONE, 0f, lettering + 1f, VITALS_WIDTH - 11f,
                BitmapFont.Align.Right);
        shadowed(experienceBar, experienceCount,
                text(13f, DROP, 1f, lettering, VITALS_WIDTH - 11f, BitmapFont.Align.Right), 3f);
        vitals.attachChild(experienceBar);

        // The bezel last and highest: a bar fills from under its own rim, and the
        // reading rides over both. A gauge is the one place the picture is asked
        // for a plain square -- the rim IS the ornament at this size.
        framed(experienceBar, PanelSkin.GAUGE, -1f, experienceY - 1f,
                VITALS_WIDTH + 2f, XP_HEIGHT + 2f, 1.5f);
    }

    /**
     * Health and mana, under his own face and as wide as it.
     *
     * <p>Its own node rather than part of the portrait's, because the portrait is
     * a frame with a live creature turning inside it and these are two gauges: one
     * is rebuilt when the face changes and the other never is.
     */
    private void buildPortraitVitals() {
        float healthY = BAND - PORTRAIT_HEIGHT - 6f - BAR_HEIGHT;
        float manaY = healthY - 3f - MANA_HEIGHT;

        portraitBars.attachChild(trough(0f, healthY, PORTRAIT_COLUMN, BAR_HEIGHT));
        healthFill = fill("hp-fill", PORTRAIT_COLUMN - 2f, BAR_HEIGHT - 2f, BLOOD);
        healthFill.setLocalTranslation(1f, healthY + 1f, 1f);
        portraitBars.attachChild(healthFill);
        health = text(12f, BONE, 0f, healthY + 3f, PORTRAIT_COLUMN,
                BitmapFont.Align.Center);
        shadowed(portraitBars, health, text(12f, DROP, 1f, healthY + 2f, PORTRAIT_COLUMN,
                BitmapFont.Align.Center), 2f);

        manaBar.attachChild(trough(0f, manaY, PORTRAIT_COLUMN, MANA_HEIGHT));
        manaFill = fill("mana-fill", PORTRAIT_COLUMN - 2f, MANA_HEIGHT - 2f, MANA);
        manaFill.setLocalTranslation(1f, manaY + 1f, 1f);
        manaBar.attachChild(manaFill);
        manaCount = text(10f, BONE, 0f, manaY + 2f, PORTRAIT_COLUMN,
                BitmapFont.Align.Center);
        shadowed(manaBar, manaCount, text(10f, DROP, 1f, manaY + 1f, PORTRAIT_COLUMN,
                BitmapFont.Align.Center), 2f);
        portraitBars.attachChild(manaBar);

        framed(portraitBars, PanelSkin.GAUGE, -1f, healthY - 1f,
                PORTRAIT_COLUMN + 2f, BAR_HEIGHT + 2f, 1.5f);
        framed(manaBar, PanelSkin.GAUGE, -1f, manaY - 1f,
                PORTRAIT_COLUMN + 2f, MANA_HEIGHT + 2f, 1.5f);
        contents.attachChild(portraitBars);
    }

    // ---- the block under the experience bar ----

    /** How the block is drawn — see {@link StatLook}. */
    private final StatLook statLook;

    /** Where the block's top edge is, worked out with the rest of the column. */
    private float statsTop;

    /** Everything in the block, taken down and built again when what it holds changes shape. */
    private final Node statBlock = new Node("stat-block");
    /** The lettering on each figure's row down the left, in the order the game sends them. */
    private final List<StatLine> figureLines = new ArrayList<>();
    /** And on each attribute's row down the right. */
    private final List<StatLine> attributeLines = new ArrayList<>();
    /** The value under his primary, or null while no primary is drawn. */
    private BitmapText primaryValue;
    /** The pictures and words the block was built for; a different set means rebuilding. */
    private String statsBuiltFor;
    /** Each attribute's row inside the column, as x, y, width, height — for the cursor. */
    private final List<float[]> attributeSpots = new ArrayList<>();
    /** His primary's framed socket, the same way; null while there is none. */
    private float[] primarySpot;
    /** Which attribute is drawn large in the middle, or -1. */
    private int primaryIndex = -1;
    /** The card over each attribute that has one, by its place in the game's list. */
    private Map<Integer, SkillTip.Reading> attributeTips = Map.of();
    /** The attribute the cursor is resting on, if any. */
    private Integer hoveredAttribute;
    /** Where the column starts across the bar, in design pixels. */
    private float vitalsAtX;

    /** The lettering on one row: its word, its value, and what was lent to it. */
    private record StatLine(BitmapText word, BitmapText value, BitmapText lent) {
    }

    /**
     * The block under the experience bar, laid out the way Warcraft III lays out a hero.
     *
     * <p>Three columns. Down the left, the figures a fight is read by — what he hits for
     * and what he shrugs off — in the biggest sockets of the two lists. In the middle, his
     * primary attribute, large and framed, with its value under it: the one attribute
     * that is also his blow. Down the right, every attribute the game sends, in the game's
     * order, his primary in the brighter gold and the rest dimmer.
     *
     * <p><b>The green is the point of both lists.</b> A figure that only ever goes up says
     * nothing about whether the thing he just picked up was worth picking up; the
     * difference does, and it is the only number on the panel that answers "was that any
     * good".
     *
     * <p>Built to fit whatever arrives rather than a fixed three, for the same reason the
     * skill row is: what a game counts is the game's business. A fourth attribute is a
     * fourth row, and the rows close up to keep the block inside the band.
     */
    private void showStatBlock(List<Reading.Stat> figures, List<Reading.Stat> attributes) {
        var signature = new StringBuilder();
        for (var figure : figures) {
            signature.append(figure.icon()).append(',').append(figure.word()).append(';');
        }
        signature.append('|');
        for (var attribute : attributes) {
            signature.append(attribute.icon()).append(attribute.primary() ? '*' : ',')
                    .append(attribute.word()).append(';');
        }
        if (!signature.toString().equals(statsBuiltFor)) {
            statsBuiltFor = signature.toString();
            buildStatBlock(figures, attributes);
        }
        for (int i = 0; i < figures.size(); i++) {
            write(figureLines.get(i), figures.get(i));
        }
        for (int i = 0; i < attributes.size(); i++) {
            write(attributeLines.get(i), attributes.get(i));
        }
        if (primaryValue != null && primaryIndex >= 0 && primaryIndex < attributes.size()) {
            primaryValue.setText(attributes.get(primaryIndex).value());
        }
    }

    private static void write(StatLine line, Reading.Stat stat) {
        line.word().setText(stat.word());
        line.value().setText(stat.value());
        line.lent().setText(stat.bonus());
    }

    private void buildStatBlock(List<Reading.Stat> figures, List<Reading.Stat> attributes) {
        statBlock.detachAllChildren();
        figureLines.clear();
        attributeLines.clear();
        attributeSpots.clear();
        primarySpot = null;
        primaryIndex = -1;
        primaryValue = null;
        var look = statLook;
        float height = blockHeight(look);

        // ★ LEFT: the figures, in the sockets a fight is read by.
        column(figures, 0f, look.figureColumn() - COLUMN_GAP, look.figureIcon(),
                look.figureText(), height, figureLines, null,
                stat -> rgb(look.labelColour()), stat -> rgb(look.valueColour()),
                stat -> rgb(look.figureTint()));

        // ★ MIDDLE: his primary, large, in a ring of bright gold, with its value under it.
        for (int i = 0; i < attributes.size(); i++) {
            if (attributes.get(i).primary()) {
                primaryIndex = i;
                break;
            }
        }
        if (primaryIndex >= 0) {
            var primary = attributes.get(primaryIndex);
            float size = look.primaryIcon();
            float framedSize = size + PRIMARY_FRAME * 2f;
            float drawn = framedSize + 2f + look.primaryText();
            float x = look.figureColumn() + (look.primaryColumn() - size) / 2f;
            float y = statsTop - (height - drawn) / 2f - PRIMARY_FRAME - size;
            attach(statBlock, flat("primary-frame", framedSize, framedSize,
                    rgb(look.frameColour())), x - PRIMARY_FRAME, y - PRIMARY_FRAME, -0.5f);
            socket(x, y, size, primary.icon(), primary.word(), rgb(look.primaryTint()));
            primaryValue = text(look.primaryText(), rgb(look.primaryColour()),
                    look.figureColumn(), y - PRIMARY_FRAME - 2f - look.primaryText(),
                    look.primaryColumn(), BitmapFont.Align.Center);
            statBlock.attachChild(primaryValue);
            primarySpot = new float[] {x - PRIMARY_FRAME, y - PRIMARY_FRAME, framedSize,
                framedSize};
        }

        // ★ RIGHT: every attribute, his primary in the brighter gold and the rest dimmer.
        float from = attributeColumnAt(look);
        column(attributes, from, VITALS_WIDTH - from, look.attributeIcon(),
                look.attributeText(), height, attributeLines, attributeSpots,
                stat -> rgb(stat.primary() ? look.primaryColour() : look.attributeColour()),
                stat -> rgb(stat.primary() ? look.primaryColour() : look.attributeColour()),
                stat -> rgb(stat.primary() ? look.primaryTint() : look.attributeTint()));
    }

    /**
     * One column of rows — a socket, a word, a value and what was lent — centred down the
     * block, and closed up when there are more rows than the block was sized for.
     *
     * <p>The values stand in a line of their own just after the longest word, rather than
     * against the column's far edge: a value a hundred pixels from its own word is a value
     * nobody reads as belonging to it. And the word and the value are given separate room
     * rather than one box each end of — they used to share one, and "Tezlik" and its
     * figure came out as "Tezli29", both exactly where they had been put.
     */
    private void column(List<Reading.Stat> stats, float x, float width, float icon,
            float lettering, float height, List<StatLine> lines, List<float[]> spots,
            java.util.function.Function<Reading.Stat, ColorRGBA> wordColour,
            java.util.function.Function<Reading.Stat, ColorRGBA> valueColour,
            java.util.function.Function<Reading.Stat, ColorRGBA> tint) {
        if (stats.isEmpty()) {
            return;
        }
        var rows = rowsIn(stats.size(), icon, statLook.rowGap(), height);
        float size = rows[0];
        float step = rows[1];
        float tall = (stats.size() - 1) * step + size;
        float top = statsTop - (height - tall) / 2f;
        float letters = letteringFor(lettering, size);
        float widest = 0f;
        for (var stat : stats) {
            widest = Math.max(widest, widthOf(stat.word(), letters));
        }
        float room = width - size - ICON_GAP;
        float valueEnd = Math.min(room - lentRoom(letters),
                widest + WORD_GAP + widthOf("000", letters));
        for (int i = 0; i < stats.size(); i++) {
            var stat = stats.get(i);
            float y = top - size - i * step;
            socket(x, y, size, stat.icon(), stat.word(), tint.apply(stat));
            float from = x + size + ICON_GAP;
            float baseline = y + (size - letters) / 2f;
            var word = text(letters, wordColour.apply(stat), from, baseline, room,
                    BitmapFont.Align.Left);
            var value = text(letters, valueColour.apply(stat), from, baseline, valueEnd,
                    BitmapFont.Align.Right);
            var lent = text(letters, rgb(statLook.gainColour()), from + valueEnd + 3f, baseline,
                    Math.max(1f, room - valueEnd - 3f), BitmapFont.Align.Left);
            statBlock.attachChild(word);
            statBlock.attachChild(value);
            statBlock.attachChild(lent);
            lines.add(new StatLine(word, value, lent));
            if (spots != null) {
                spots.add(new float[] {x, y, width, size});
            }
        }
    }

    /** How wide a string comes out in the panel's lettering at this size. */
    private float widthOf(String words, float size) {
        var line = new BitmapText(font);
        line.setSize(size);
        line.setText(words == null ? "" : words);
        return line.getLineWidth();
    }

    /**
     * A picture in a socket of its own: the stone, the rim, and the drawing.
     *
     * <p>The picture is the game's, named beside the row it belongs to. When it names one
     * the client cannot find — or none — the socket carries the first letter of the row's
     * word instead, so it still says which one it is: a missing file costs a line in the
     * log, never the row.
     */
    private void socket(float x, float y, float size, String icon, String word,
            ColorRGBA tint) {
        var box = new Node("stat-box");
        attach(box, flat("stat-edge", size, size, EDGE), 0f, 0f, 0f);
        var stone = new Geometry("stat-stone", gradient(size - 2f, size - 2f, STONE_LIT, STONE));
        stone.setMaterial(vertexColoured());
        attach(box, stone, 1f, 1f, 1f);
        float share = statLook.iconShare();
        var drawn = picture(icon, size, share, tint);
        if (drawn != null) {
            float inset = size * (1f - share) / 2f;
            attach(box, drawn, inset, inset, 2f);
        } else {
            float lettering = size * 0.6f;
            var letter = text(lettering, GOLD, 0f, (size - lettering) / 2f, size,
                    BitmapFont.Align.Center);
            letter.setName("stat-letter");
            letter.setText(initial(word));
            letter.setLocalTranslation(0f, 0f, 2f);
            box.attachChild(letter);
        }
        framed(box, PanelSkin.CHIP, 0f, 0f, size, size, 2.5f);
        attach(statBlock, box, x, y, 0f);
    }

    /** The first letter of a word, for a socket whose picture is not there. */
    static String initial(String word) {
        if (word == null || word.isBlank()) {
            return "?";
        }
        int first = word.strip().codePointAt(0);
        return new String(Character.toChars(Character.toUpperCase(first)));
    }

    /** A sunken trough for a bar to sit in. */
    private Geometry trough(float x, float y, float width, float height) {
        var geometry = flat("trough", width, height, STONE_DEEP);
        geometry.setLocalTranslation(x, y, 0f);
        return geometry;
    }

    /**
     * The lit part of a bar, drawn at full width and scaled down horizontally.
     * The gradient runs top to bottom, so scaling across it costs nothing.
     */
    private Geometry fill(String name, float width, float height, ColorRGBA colour) {
        var geometry = new Geometry(name, gradient(width, height, Shade.gauge(colour)));
        geometry.setMaterial(vertexColoured());
        return geometry;
    }

    // ---- the depth marker ----

    private Node depth;

    /**
     * The line just above the bar, for a thing that has happened.
     *
     * <p>Not the banner: the banner interrupts and sits in the middle of the
     * screen, which is right for "you died" and wrong for "you picked up a sword".
     * This sits on the edge of what he is already looking at, and goes away on its
     * own — the game decides when by simply not sending it any more.
     */
    private void buildNote() {
        note = text(NOTE_SIZE, TORCH, 0f, SLAB_HEIGHT + 6f, DESIGN_WIDTH,
                BitmapFont.Align.Center);
        note.setLocalTranslation(0f, note.getLocalTranslation().y, 2f);
        root.attachChild(note);
    }

    private void buildDepth() {
        depth = new Node("depth");
        contents.attachChild(depth);
        depthWord = text(11f, LABEL, 0f, BAND / 2f - 20f, DEPTH_WIDTH, BitmapFont.Align.Center);
        depthNumber = text(DEPTH_LARGE, TORCH, 0f, BAND / 2f - 12f, DEPTH_WIDTH,
                BitmapFont.Align.Center);
        depth.attachChild(depthWord);
        depth.attachChild(depthNumber);
    }

    // ---- the skill row ----

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
        private Geometry pipRow;
        /** The corner badge, and the glow behind it that breathes. */
        private Node badge;
        private Geometry badgeGlow;
        private BitmapText rankWord;
        /** Where the badge sits, so a click on it can be told from one on the slot. */
        private float badgeX;
        private float badgeY;
        private boolean canRaise;
        private float sweptTo = -1f;
        private Reading.State state = Reading.State.READY;

        private Slot(char key, float size) {
            this.key = key;
            this.size = size;
        }
    }

    private final Node skillRow = new Node("skills");

    /**
     * The card over whichever slot the cursor is on.
     *
     * <p>Outside the bar's own node and drawn after it, because it hangs ABOVE
     * the bar: parented into the row it describes, it would be clipped by
     * everything the row is drawn under.
     */
    private SkillTip tip;
    /** The cards the last line described, by key. */
    private Map<Character, SkillTip.Reading> tips = Map.of();

    private void showSkills(List<Reading.SkillReading> reading, Reading card) {
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
    private static Reading.RankReading rankFor(Reading card, char key) {
        for (var rank : card.ranks()) {
            if (rank != null && rank.key() == key) {
                return rank;
            }
        }
        return null;
    }

    private void buildSlots(List<Reading.SkillReading> reading) {
        for (var slot : slots) {
            slot.node.removeFromParent();
        }
        slots.clear();
        if (skillRow.getParent() == null) {
            contents.attachChild(skillRow);
        }
        if (reading.isEmpty()) {
            for (float size : BLANK_ROW) {
                var slot = new Slot(BLANK, size);
                carve(slot, "");
                slots.add(slot);
                skillRow.attachChild(slot.node);
            }
        }
        for (var skill : reading) {
            var slot = new Slot(skill.key(), skill.key() == ULTIMATE_KEY ? ULT_SLOT : SLOT);
            carve(slot, skill.icon());
            slots.add(slot);
            skillRow.attachChild(slot.node);
        }
        layOut();
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
        slot.brackets = reticle(slot.size);
        attach(slot.node, slot.brackets, 0f, 0f, 39f);
        slot.brackets.setCullHint(Spatial.CullHint.Always);
    }

    /** The four marks themselves, at whatever size they are framing. */
    private Node reticle(float size) {
        var brackets = new Node("reticle");
        float out = BRACKET_OUT;
        float far = size + out - BRACKET;
        // x, y of each corner, then which way its arms run from there.
        float[][] corners = {
            {-out, far, 1f, 1f},        // top left
            {far, far, -1f, 1f},        // top right
            {-out, -out, 1f, -1f},      // bottom left
            {far, -out, -1f, -1f},      // bottom right
        };
        for (var corner : corners) {
            float x = corner[0];
            float y = corner[1];
            boolean leftward = corner[2] < 0f;
            boolean downward = corner[3] < 0f;
            // The arm that runs along the top or bottom edge...
            attach(brackets, flat("brk", BRACKET, BRACKET_THICK, selHi),
                    x, downward ? y : y + BRACKET - BRACKET_THICK, 0f);
            // ...and the one that runs down the side.
            attach(brackets, flat("brk", BRACKET_THICK, BRACKET, selHi),
                    leftward ? x + BRACKET - BRACKET_THICK : x, y, 0f);
        }
        return brackets;
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
        slot.badgeGlow = flat("badge-glow", BADGE + 6f, BADGE + 6f, TORCH);
        attach(slot.badge, slot.badgeGlow, -3f, -3f, 0f);
        attach(slot.badge, flat("badge-edge", BADGE, BADGE, rgb(0x0C0A08)), 0f, 0f, 1f);
        attach(slot.badge, flat("badge-face", BADGE - 4f, BADGE - 4f, rgb(0x4A3A18)), 2f, 2f, 2f);
        var plus = text(14f, GOLD_HI, 0f, 4f, BADGE, BitmapFont.Align.Center);
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
            var pip = flat("pip", PIP_WIDTH, PIP_HEIGHT, PIP_DARK);
            attach(slot.node, pip, x + i * (PIP_WIDTH + PIP_GAP), y, 6f);
            slot.pips.add(pip);
        }
    }

    /** Take a painted rim down to its dead shade, when there is one to take down. */
    private void dimTheRim(Slot slot) {
        var painted = skin.piece(PanelSkin.SLOT);
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
        slot.rankWord = text(RANK_TEXT, LABEL, 0f,
                -PIP_MARGIN - PIP_HEIGHT - PIP_MARGIN - RANK_TEXT, size,
                BitmapFont.Align.Center);
        attach(slot.node, slot.rankWord, 0f, 0f, 6f);
        badgeFor(slot);
        bracketsFor(slot);
        attach(slot.node, flat("drop", size + 4f, size + 4f, DROP), -2f, -2f - DROP_DEPTH, 0f);
        // A torch-coloured lip around the socket, shown only while this is the
        // skill the next click belongs to. Under the edge, so it reads as the
        // stone catching light rather than as a box drawn on top of it.
        slot.ring = flat("armed", size + 8f, size + 8f, TORCH);
        attach(slot.node, slot.ring, -4f, -4f, 1f);
        slot.ring.setCullHint(Spatial.CullHint.Always);
        attach(slot.node, flat("edge", size + 4f, size + 4f, EDGE), -2f, -2f, 2f);

        var stone = new Geometry("stone", gradient(size, size, STONE_LIT, STONE));
        stone.setMaterial(vertexColoured());
        attach(slot.node, stone, 0f, 0f, 3f);

        slot.deadStone = new Geometry("stone-dead",
                gradient(size, size, STONE_DEAD_LIT, STONE_DEAD));
        slot.deadStone.setMaterial(vertexColoured());
        attach(slot.node, slot.deadStone, 0f, 0f, 4f);

        // The cold stone of an armed socket, over the warm one and under
        // everything that means anything -- the rim, the glyph and the shading
        // all sit above it, so arming changes the stone without hiding the skill.
        slot.selStone = new Geometry("stone-armed",
                gradient(size, size, SEL_STONE_LIT, SEL_STONE));
        slot.selStone.setMaterial(vertexColoured());
        attach(slot.node, slot.selStone, 0f, 0f, 4.2f);
        slot.selStone.setCullHint(Spatial.CullHint.Always);

        // The carved illusion: light catches the top lip, shadow pools at the foot.
        attach(slot.node, flat("lip", size, 2f, new ColorRGBA(1f, 1f, 1f, 0.07f)),
                0f, size - 2f, 5f);
        attach(slot.node, flat("pool", size, 6f, new ColorRGBA(0f, 0f, 0f, 0.45f)),
                0f, 0f, 5f);
        // And the painted rim over the top of the shading -- the whole difference
        // between a socket that is shaded and one that is framed. Above the
        // stone and below the glyph, so it never covers what the slot is for.
        slot.rim = paint(slot.node, PanelSkin.SLOT, -2f, -2f, size + 4f, size + 4f, 5.7f);

        // The wash under the cursor. Over the stone but under everything that
        // means something, so hovering brightens the slot without hiding its state.
        slot.warm = flat("warm", size, size, new ColorRGBA(1f, 0.85f, 0.6f, 0.12f));
        attach(slot.node, slot.warm, 0f, 0f, 5.5f);
        slot.warm.setCullHint(Spatial.CullHint.Always);

        slot.glyph = picture(icon, size);
        if (slot.glyph == null && slot.key == BLANK) {
            // An empty socket. Not a slot with a picture missing: a slot with
            // nothing in it, because nothing is selected. The stone goes dead so
            // it does not read as a skill that is merely waiting.
            slot.glyph = new Geometry("glyph", new Mesh());
            slot.glyph.setMaterial(lines(DEAD));
            attach(slot.node, slot.glyph, size / 2f, size / 2f, 6f);
            slot.deadStone.setCullHint(Spatial.CullHint.Inherit);
            dimTheRim(slot);
        } else if (slot.glyph == null) {
            slot.glyph = new Geometry("glyph", Glyphs.of(String.valueOf(slot.key), size * 0.53f));
            slot.glyph.setMaterial(lines(TORCH));
            attach(slot.node, slot.glyph, size / 2f, size / 2f, 6f);
        } else {
            // A quad grows from its own corner, so it is placed rather than centred.
            attach(slot.node, slot.glyph, ICON_MARGIN, ICON_MARGIN, 6f);
        }

        slot.sweep = new Geometry("sweep", new Mesh());
        slot.sweep.setMaterial(unshaded(new ColorRGBA(0.031f, 0.024f, 0.02f, 0.82f)));
        attach(slot.node, slot.sweep, 0f, 0f, 7f);

        slot.seconds = text(17f, BONE, 0f, size / 2f - 6f, size, BitmapFont.Align.Center);
        slot.seconds.setLocalTranslation(0f, slot.seconds.getLocalTranslation().y, 8f);
        slot.node.attachChild(slot.seconds);

        slot.locked = text(12f, LOCK_LABEL, 0f, size / 2f - 4f, size, BitmapFont.Align.Center);
        slot.locked.setLocalTranslation(0f, slot.locked.getLocalTranslation().y, 8f);
        slot.node.attachChild(slot.locked);

        var key = text(14f, BONE, 0f, 1f, size - 3f, BitmapFont.Align.Right);
        key.setText(slot.key == BLANK ? "" : String.valueOf(slot.key));
        key.setLocalTranslation(0f, key.getLocalTranslation().y, 8f);
        slot.node.attachChild(key);

        // What it costs, in the corner opposite the key. Small, because it is a
        // number he checks rather than reads -- and in the mana bar's own blue,
        // so the two are obviously about the same thing without a word between
        // them. It turns when he cannot pay, which is the whole point of it.
        slot.cost = text(11f, MANA, 3f, 1f, size - 3f, BitmapFont.Align.Left);
        slot.cost.setLocalTranslation(slot.cost.getLocalTranslation().x,
                slot.cost.getLocalTranslation().y, 8f);
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
    private Geometry picture(String icon, float size) {
        return picture(icon, size, iconShare(size),
                icons.paintedSkills() ? IconLook.AS_PAINTED : TORCH);
    }

    /**
     * A picture to lay in a socket, at {@code share} of its width and starting in
     * {@code colour}.
     *
     * <p>Shared by the skills, the four order buttons and the figures under the
     * bars, which all used to draw their own way: the skills from a file, the
     * other two from line drawings held in this class under names their own side
     * of the wire had spelt out in Java. One path, so that a game which wants to
     * change what an order looks like changes a line in its own file rather than
     * a mesh in the client.
     */
    private Geometry picture(String icon, float size, float share, ColorRGBA colour) {
        var texture = iconTexture(assets, icon, missingIcons);
        if (texture == null) {
            return null;
        }
        float side = size * share;
        var quad = new Geometry("icon", new Quad(side, side));
        var material = unshaded(colour);
        material.setTexture("ColorMap", texture);
        quad.setMaterial(material);
        return quad;
    }

    /**
     * Load an icon, or give back {@code null} — for no name, or for a name nothing
     * answers to.
     *
     * <p>Static and handed everything it needs so that the fallback can be held
     * still by a test: what happens when the file is not there is the part of this
     * nobody exercises by playing.
     */
    static Texture iconTexture(AssetManager assets, String icon, Set<String> missing) {
        if (assets == null || icon == null || icon.isBlank()) {
            return null;
        }
        try {
            return assets.loadTexture(icon);
        } catch (RuntimeException e) {
            if (missing.add(icon)) {
                LOG.warning(() -> "panel picture not found: " + icon + " (" + e.getMessage()
                        + ") — drawing that part as it was drawn before there were any");
            }
            return null;
        }
    }

    // ---- the painted edges ----

    /**
     * Lay a painted frame over something, and say whether it took.
     *
     * <p>The caller keeps its own carved rim and draws it only when this says no,
     * so a game that named no skin — or named a file that is not there — gets the
     * panel exactly as it was. That is the same bargain {@link #picture} strikes
     * for a skill's icon, and it is struck the same way for the same reason: the
     * game names its own art and the client cannot check the spelling, so being
     * wrong has to cost a line in the log rather than the panel.
     *
     * <p>The picture is white and the colour comes from the piece, which is what
     * lets one file be a gold rim here and a bone one there.
     */
    private boolean framed(Node node, String piece, float x, float y,
            float width, float height, float z) {
        return paint(node, piece, x, y, width, height, z) != null;
    }

    /** The same, handing back the frame for anything that has to re-colour it later. */
    private Geometry paint(Node node, String piece, float x, float y,
            float width, float height, float z) {
        var painted = skin.piece(piece);
        if (painted == null) {
            return null;
        }
        var texture = iconTexture(assets, painted.texture(), missingIcons);
        if (texture == null) {
            return null;
        }
        // Clamped so the stretched middle cannot reach round and sample the far
        // edge, which shows up as a ghost of the opposite corner.
        texture.setWrap(Texture.WrapMode.EdgeClamp);
        var image = texture.getImage();
        var mesh = piece.equals(PanelSkin.DIVIDER)
                ? NineSlice.quarterTurn(width, height, NineSlice.Rect.WHOLE)
                : NineSlice.frame(width, height, image.getWidth(), image.getHeight(),
                        painted.inset(), painted.scale(), NineSlice.Rect.WHOLE);
        var geometry = new Geometry("frame-" + piece, mesh);
        var material = unshaded(rgb(painted.tint().getRGB()));
        material.setTexture("ColorMap", texture);
        geometry.setMaterial(material);
        attach(node, geometry, x, y, z);
        return geometry;
    }

    /**
     * How big a divider's picture is when drawn, in the design's own pixels.
     *
     * <p>Asked of the piece rather than measured from the file, because the file
     * is not there to measure in a game that named no skin — and because the
     * length is a decision: the ornament is a fixed shape, so making it fit the
     * band is choosing how heavily to lay it on rather than stretching it.
     */
    private static final float DIVIDER_TEXELS_LONG = 96f;
    private static final float DIVIDER_TEXELS_WIDE = 22f;

    /**
     * Two ornaments standing on end, meeting in the middle of the band.
     *
     * <p>The picture is a rule that fades at one end and finishes in a device at
     * the other, drawn lying down. Stood up and mirrored about the middle it
     * becomes a carved line with a device at each end and nothing to see where the
     * two faded ends meet — which is why a gap there costs nothing and stretching
     * the ornament to close it would cost the ornament.
     */
    private boolean paintDivider(Node node) {
        var painted = skin.piece(PanelSkin.DIVIDER);
        if (painted == null) {
            return false;
        }
        float length = DIVIDER_TEXELS_LONG * painted.scale();
        float thickness = DIVIDER_TEXELS_WIDE * painted.scale();
        // Wider than the groove it stands in, and centred on it: the margin
        // either side of a divider is there so the carving has somewhere to go.
        float across = (DIVIDER - thickness) * 0.5f;
        if (!framed(node, PanelSkin.DIVIDER, across, 0f, thickness, length, 3f)) {
            return false;
        }
        // The same piece turned over about the top of the band, so the two devices
        // point away from each other and the two faded ends are the ones that meet.
        var mirrored = new Node("divider-mirrored");
        framed(mirrored, PanelSkin.DIVIDER, across, 0f, thickness, length, 3f);
        mirrored.setLocalScale(1f, -1f, 1f);
        mirrored.setLocalTranslation(0f, BAND, 0f);
        node.attachChild(mirrored);
        return true;
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
     * reason {@link #iconTexture} is: this is arithmetic about colour with no
     * window in it, and a test can hold it still without one.
     */
    static ColorRGBA skillColour(IconLook icons, boolean locked, boolean cooling) {
        if (icons.paintedSkills()) {
            return locked ? IconLook.PAINTED_DEAD
                    : cooling ? IconLook.PAINTED_COLD : IconLook.AS_PAINTED;
        }
        return locked ? DEAD.mult(new ColorRGBA(1f, 1f, 1f, 0.5f))
                : cooling ? GLYPH_COLD : TORCH;
    }

    /**
     * What this one costs, and whether he can pay it.
     *
     * <p>Set before the slot is dressed, because being unable to pay changes how
     * the whole socket is drawn and {@link #dress} is what draws it.
     */
    private void priceUp(Slot slot, Reading card) {
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

    private void dress(Slot slot, Reading.SkillReading skill) {
        slot.state = skill.state;
        boolean locked = skill.state == Reading.State.LOCKED;
        boolean cooling = skill.state == Reading.State.COOLING;
        // Three ways a slot can be unusable and they must not look alike. Locked
        // is a skill he has not bought; cooling is one that is coming back and
        // says when; BROKE is one that is ready and waiting on the bar above.
        // Dressed as cooling would say "wait" about something no amount of
        // waiting for THIS slot will fix.
        boolean broke = !locked && !cooling && !slot.affordable;
        slot.deadStone.setCullHint(locked || broke
                ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        slot.glyph.getMaterial().setColor("Color",
                linear(broke ? IconLook.PAINTED_COLD.mult(MANA_WASH)
                        : skillColour(icons, locked, cooling)));
        if (slot.cost != null) {
            // The price is the one thing on a slot he cannot pay for that goes
            // BRIGHTER. Everything else about the socket dims, so the number is
            // what the eye lands on, and the number is the answer.
            slot.cost.setColor(linear(broke ? MANA_DENIED : MANA));
        }
        // A painted rim goes dead with the rest of the socket. Left at full
        // strength it was the one bright thing on a slot he cannot cast, which
        // says the opposite of what the slot means — the whole reason the stone
        // behind it darkens.
        if (slot.rim != null) {
            var gold = rgb(skin.piece(PanelSkin.SLOT).tint().getRGB());
            slot.rim.getMaterial().setColor("Color",
                    linear(locked ? darker(gold, 0.6f) : cooling ? darker(gold, 0.3f) : gold));
        }
        slot.locked.setText(locked ? skill.label : "");
        slot.seconds.setText(cooling ? skill.label : "");
        sweepTo(slot, cooling ? skill.left : 0f);
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
    private void dressRank(Slot slot, Reading.RankReading rank) {
        pipsFor(slot, rank.max());
        slot.canRaise = rank.canRaise();
        boolean full = rank.rank() >= rank.max();
        for (int i = 0; i < slot.pips.size(); i++) {
            boolean lit = i < rank.rank();
            slot.pips.get(i).getMaterial().setColor("Color",
                    linear(lit ? full ? GOLD_HI : GOLD : PIP_DARK));
        }
        // The words are the game's and arrive finished; the colour is the
        // client's, because it is a fact about a state it can already see. Armed
        // wins over all of them: while he is choosing a target, the whole column
        // is the one cold thing on the bar.
        boolean picked = armed != null && armed == slot.key;
        slot.rankWord.setColor(linear(
                picked ? selHi : rank.canRaise() ? GAIN : full ? TORCH : LABEL));
        slot.rankWord.setText(rank.word());
        slot.badge.setCullHint(rank.canRaise()
                ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        if (rank.canRaise()) {
            // Breathing, so a player with a point does not forget he has one. The
            // armed lip breathes at 0.9; this is a little quicker, because it is
            // asking for something rather than waiting.
            float breath = 0.55f + 0.45f * FastMath.sin(clock * FastMath.TWO_PI * 0.7f);
            slot.badgeGlow.getMaterial().setColor("Color",
                    linear(new ColorRGBA(TORCH.r, TORCH.g, TORCH.b, breath * 0.5f)));
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
        boolean waiting = armed != null && armed == slot.key;
        var show = waiting ? Spatial.CullHint.Inherit : Spatial.CullHint.Always;
        slot.ring.setCullHint(show);
        slot.selStone.setCullHint(show);
        slot.brackets.setCullHint(show);
        slot.warm.setCullHint(hovered != null && hovered == slot.key
                && slot.state == Reading.State.READY
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
            slot.glyph.getMaterial().setColor("Color", linear(selHi));
        }
        // Quicker than the old gold lip breathed, because this one is asking for
        // something: a click has to come before anything else can happen.
        float breath = 0.72f + 0.28f * FastMath.sin(clock * FastMath.TWO_PI * 1.1f);
        slot.ring.getMaterial().setColor("Color",
                linear(new ColorRGBA(sel.r, sel.g, sel.b, breath)));
    }

    /** Rebuild the cooldown shadow, but only when it has actually moved. */
    private void sweepTo(Slot slot, float remaining) {
        if (Math.abs(remaining - slot.sweptTo) < 0.002f) {
            return;
        }
        slot.sweptTo = remaining;
        slot.sweep.setMesh(sweep(slot.size, remaining));
    }

    /** The gold heading over a block, on a wash that fades out at both ends. */
    private BitmapText heading(Node block, float width) {
        var wash = new Geometry("heading-wash",
                sideways(width, HEADING_SIZE + 5f, new ColorRGBA(GOLD.r, GOLD.g, GOLD.b, 0.16f)));
        wash.setMaterial(vertexColoured());
        attach(block, wash, 0f, -3f, 0f);
        var word = text(HEADING_SIZE, GOLD, 0f, 0f, width, BitmapFont.Align.Center);
        word.setLocalTranslation(0f, word.getLocalTranslation().y, 1f);
        block.attachChild(word);
        return word;
    }

    private BitmapText skillsWord;
    /** "NUQTA 2" beside it: what the four slots are competing for. */
    private BitmapText pointsCount;
    private final Node skillHeading = new Node("skill-heading");
    private final Node itemHeading = new Node("item-heading");

    /** The two gold headings the design puts over the bag and the skill row. */
    private void buildHeadings() {
        contents.attachChild(itemGrid);
        contents.attachChild(orderColumn);
        contents.attachChild(itemHeading);
        contents.attachChild(skillHeading);
        itemsWord = heading(itemHeading, ITEM_COLUMNS * ITEM_SLOT + (ITEM_COLUMNS - 1) * ITEM_GAP);
        float skillWidth = SLOT * 3f + ULT_SLOT + SLOT_GAP * 3f;
        skillsWord = heading(skillHeading, skillWidth);
        // At the far end of the same line, which is where the design puts it: the
        // heading names the block and this says what the block is waiting for.
        pointsCount = text(HEADING_SIZE, GOLD_HI, 0f, 0f, skillWidth,
                BitmapFont.Align.Right);
        skillHeading.attachChild(pointsCount);
    }

    // ---- placing the whole thing ----

    /** Where one block of the bar goes, once it is known what is left of it. */
    private interface Placing {
        void at(float x, float left);
    }

    /** One block: how wide it is, and how to put it down. */
    private record Block(float width, Placing place) {
    }

    /**
     * Put the bar together out of whatever blocks this card has.
     *
     * <p>A list rather than a chain of ifs, because the blocks are independently
     * optional — a creature's card has no bag and no skills, and with nothing
     * selected at all there is no portrait either. Four optional blocks is
     * sixteen arrangements, and the only way to write sixteen arrangements once
     * is to describe each block and let the loop space them.
     *
     * <p>A groove goes between every neighbouring pair and nowhere else, which is
     * what makes the bar close up rather than leave a hole where something used
     * to be.
     */
    private void layOut() {
        var blocks = new ArrayList<Block>();
        float mapBlock = MINIMAP + (orderButtons.isEmpty() ? 0f
                : ORDER_COLUMN_GAP + ORDER_BUTTON);
        blocks.add(new Block(mapBlock, (x, left) -> {
            minimapSocket.setLocalTranslation(x, 0f, 0f);
            placeOrders(x + MINIMAP + ORDER_COLUMN_GAP, left);
        }));
        blocks.add(new Block(PORTRAIT_COLUMN + PORTRAIT_GAP + VITALS_WIDTH, (x, left) -> {
            // The frame hangs from the top of the band and is CENTRED in its
            // column; the two gauges fill the column under it, and the identity
            // and the experience bar fill the height beside it.
            portrait.setLocalTranslation(x + (PORTRAIT_COLUMN - PORTRAIT) / 2f,
                    BAND - PORTRAIT_HEIGHT, 0f);
            portraitBars.setLocalTranslation(x, 0f, 0f);
            vitals.setLocalTranslation(x + PORTRAIT_COLUMN + PORTRAIT_GAP, 0f, 0f);
            vitalsAtX = left + x + PORTRAIT_COLUMN + PORTRAIT_GAP;
        }));
        blocks.add(new Block(ITEM_COLUMNS * ITEM_SLOT + (ITEM_COLUMNS - 1) * ITEM_GAP,
                (x, left) -> placeBag(x)));
        blocks.add(new Block(skillRowWidth(), this::placeSkills));
        blocks.add(new Block(DEPTH_WIDTH, (x, left) -> depth.setLocalTranslation(x, 0f, 0f)));

        float gap = DIVIDER + DIVIDER_MARGIN * 2f;
        float total = gap * (blocks.size() - 1);
        for (var block : blocks) {
            total += block.width();
        }

        // Measured first, and the scale settled from it: see scaleFor.
        this.scale = scaleFor(total);
        root.setLocalScale(scale);
        buildSlab();
        // Centred on the window rather than on the design, so it sits over the
        // middle of the bar however wide the window is.
        note.setBox(new Rectangle(0f, SLAB_HEIGHT + 6f + NOTE_SIZE,
                screenWidth / scale, NOTE_SIZE * 1.4f));

        // The bar spans the window; its contents are centred on it, so a wide
        // screen puts empty stone at both ends rather than all of it at one.
        float left = Math.max(PAD, (screenWidth / scale - total) / 2f);
        contents.setLocalTranslation(left, PAD, 1f);

        float x = 0f;
        for (int i = 0; i < blocks.size(); i++) {
            if (i > 0) {
                x += DIVIDER_MARGIN;
                placeDivider(i - 1, x);
                x += DIVIDER + DIVIDER_MARGIN;
            }
            blocks.get(i).place().at(x, left);
            x += blocks.get(i).width();
        }
        for (int spare = blocks.size() - 1; spare < dividers.size(); spare++) {
            hideDivider(spare); // grooves the last card needed and this one does not
        }
    }

    /**
     * How much the design is shrunk to reach this window, in the design's own
     * pixels — and never so little that the bar runs off the edge.
     *
     * <p>Two answers, and the smaller wins. The first is the design's: a share of
     * the width it was drawn at, held between a size below which it is unreadable
     * and one above which it is silly. That one is about <em>legibility</em>, and
     * it knows nothing about how wide the card in hand happens to be.
     *
     * <p>The second is arithmetic: what actually fits. It has to be asked because
     * the clamp cannot be told the answer in advance — a card carries whatever
     * blocks the game sent, so the bar is a different width for a hero and for a
     * skeleton, and any fixed floor is a number somebody has to re-check every
     * time anything on the bar changes size. It was re-checked exactly once, and
     * the portrait growing to the size the design draws it at was enough to push
     * the far end of a narrow window off the screen by two pixels.
     *
     * <p>So the floor is kept for what it is good at and the overrun is made
     * impossible rather than unlikely.
     */
    private float scaleFor(float total) {
        float legible = Math.clamp(screenWidth / DESIGN_WIDTH, MIN_SCALE, MAX_SCALE);
        float fits = (screenWidth - PAD * 2f) / Math.max(1f, total);
        return Math.min(legible, fits);
    }

    /** As wide as the sockets come to, and never narrower than the design's four. */
    private float skillRowWidth() {
        float wide = 0f;
        for (int i = 0; i < slots.size(); i++) {
            wide += slots.get(i).size + (i == 0 ? 0f : SLOT_GAP);
        }
        return Math.max(wide, SLOT * 3f + ULT_SLOT + SLOT_GAP * 3f);
    }

    /** His bag: a heading with the grid under it, the pair centred in the band. */
    private void placeBag(float x) {
        float bagHeight = ITEM_ROWS * ITEM_SLOT + (ITEM_ROWS - 1) * ITEM_GAP;
        float bottom = (BAND - bagHeight - HEADING_SIZE - HEADING_GAP) / 2f;
        itemGrid.setLocalTranslation(x, bottom, 0f);
        itemHeading.setLocalTranslation(x, bottom + bagHeight + HEADING_GAP, 0f);
        for (int i = 0; i < itemSlots.size(); i++) {
            // Filled across then down, which is the order they are read in and the
            // order the game sends them.
            float column = i % ITEM_COLUMNS;
            float row = i / ITEM_COLUMNS;
            itemSlots.get(i).node.setLocalTranslation(column * (ITEM_SLOT + ITEM_GAP),
                    (ITEM_ROWS - 1 - row) * (ITEM_SLOT + ITEM_GAP), 0f);
        }
    }

    /** Skills: a heading, and the row of sockets under it. */
    private void placeSkills(float x, float left) {
        // The pips and their word hang under each slot, so the column is that much
        // taller than the slots are. Measured off the deepest, which is the
        // ultimate's -- it is the one whose foot sits lowest.
        float below = PIP_MARGIN + PIP_HEIGHT + PIP_MARGIN + RANK_TEXT;
        float columnHeight = HEADING_SIZE + HEADING_GAP + ULT_SLOT + below;
        float bottom = (BAND - columnHeight) / 2f;
        float rowY = bottom + below;
        skillRow.setLocalTranslation(x, rowY, 0f);
        skillHeading.setLocalTranslation(x, rowY + ULT_SLOT + HEADING_GAP, 0f);
        float slotX = 0f;
        for (var slot : slots) {
            // ★ CENTRED, not top-aligned. It hung from a common top, so an
            // ultimate was bigger only by reaching further DOWN -- which reads as
            // a socket that has slipped rather than as a larger one. The design
            // stands it proud at both ends, and that is what makes it the one the
            // eye finds without looking for it.
            float lift = (ULT_SLOT - slot.size) / 2f;
            slot.node.setLocalTranslation(slotX, lift, 0f);
            slot.atX = left + x + slotX;
            slot.atY = PAD + rowY + lift;
            // Its own corner, in the same design pixels the slot is measured in,
            // so a click on the badge can be told from one on the slot under it.
            slot.badgeX = slot.atX + slot.size - BADGE + BADGE_OUT;
            slot.badgeY = slot.atY + slot.size - BADGE + BADGE_OUT;
            slotX += slot.size + SLOT_GAP;
        }
    }

    /** The order buttons, stacked beside the map and centred against it. */
    private void placeOrders(float x, float left) {
        if (orderButtons.isEmpty()) {
            return;
        }
        float height = orderButtons.size() * ORDER_BUTTON
                + (orderButtons.size() - 1) * ORDER_GAP;
        float top = (BAND + height) / 2f;
        orderColumn.setLocalTranslation(x, 0f, 0f);
        for (int i = 0; i < orderButtons.size(); i++) {
            var button = orderButtons.get(i);
            float y = top - (i + 1) * ORDER_BUTTON - i * ORDER_GAP;
            button.node.setLocalTranslation(0f, y, 0f);
            button.atX = left + x;
            button.atY = PAD + y;
        }
    }

    private final List<Node> dividers = new ArrayList<>();

    /** Take a groove off the bar, for a card whose blocks do not need dividing. */
    private void hideDivider(int index) {
        if (index < dividers.size()) {
            dividers.get(index).setCullHint(Spatial.CullHint.Always);
        }
    }

    private void placeDivider(int index, float x) {
        while (dividers.size() <= index) {
            var line = divider();
            dividers.add(line);
            contents.attachChild(line);
        }
        dividers.get(index).setCullHint(Spatial.CullHint.Inherit);
        dividers.get(index).setLocalTranslation(x, 0f, 0f);
    }

    // ---- meshes ----

    /**
     * A flat rectangle of one colour. Everything in the panel that is not a
     * gradient, a glyph or a shadow is one of these.
     */
    private Geometry flat(String what, float width, float height, ColorRGBA colour) {
        var geometry = new Geometry(what, new Quad(width, height));
        geometry.setMaterial(unshaded(colour));
        return geometry;
    }

    /**
     * A band of colour that fades out at both ends, running across rather than
     * down.
     *
     * <p>Behind the headings — a title, "NARSALAR", "MAHORAT". A ruled box round a
     * heading would say the heading is a thing; a wash that has no edges says only
     * "this is the top of something", which is what a heading is for. Both ends
     * fade so that it belongs to the column rather than to the stone beside it.
     */
    private static Mesh sideways(float width, float height, ColorRGBA colour) {
        var clear = new ColorRGBA(colour.r, colour.g, colour.b, 0f);
        var stops = new ColorRGBA[] {clear, colour, colour, clear};
        float[] at = {0f, width * 0.25f, width * 0.75f, width};
        var positions = new float[stops.length * 2 * 3];
        var colours = new float[stops.length * 2 * 4];
        var indices = new int[(stops.length - 1) * 6];
        for (int column = 0; column < stops.length; column++) {
            for (int side = 0; side < 2; side++) {
                int vertex = column * 2 + side;
                positions[vertex * 3] = at[column];
                positions[vertex * 3 + 1] = side == 0 ? 0f : height;
                var linear = linear(stops[column]);
                colours[vertex * 4] = linear.r;
                colours[vertex * 4 + 1] = linear.g;
                colours[vertex * 4 + 2] = linear.b;
                colours[vertex * 4 + 3] = linear.a;
            }
            if (column < stops.length - 1) {
                int base = column * 6;
                int corner = column * 2;
                indices[base] = corner;
                indices[base + 1] = corner + 2;
                indices[base + 2] = corner + 3;
                indices[base + 3] = corner;
                indices[base + 4] = corner + 3;
                indices[base + 5] = corner + 1;
            }
        }
        var mesh = meshOf(positions, indices);
        mesh.setBuffer(VertexBuffer.Type.Color, 4, BufferUtils.createFloatBuffer(colours));
        return mesh;
    }

    /**
     * A rectangle shading evenly from the first colour at the top to the last at
     * the bottom, as a strip of vertex-coloured quads.
     */
    private static Mesh gradient(float width, float height, ColorRGBA... rawStops) {
        var stops = new ColorRGBA[rawStops.length];
        for (int i = 0; i < rawStops.length; i++) {
            stops[i] = linear(rawStops[i]);
        }
        int rows = stops.length;
        var positions = new float[rows * 2 * 3];
        var colours = new float[rows * 2 * 4];
        var indices = new int[(rows - 1) * 6];
        for (int row = 0; row < rows; row++) {
            float y = height * (1f - row / (float) (rows - 1));
            for (int side = 0; side < 2; side++) {
                int vertex = row * 2 + side;
                positions[vertex * 3] = side == 0 ? 0f : width;
                positions[vertex * 3 + 1] = y;
                positions[vertex * 3 + 2] = 0f;
                var colour = stops[row];
                colours[vertex * 4] = colour.r;
                colours[vertex * 4 + 1] = colour.g;
                colours[vertex * 4 + 2] = colour.b;
                colours[vertex * 4 + 3] = colour.a;
            }
            if (row < rows - 1) {
                int base = row * 2;
                int at = row * 6;
                indices[at] = base;
                indices[at + 1] = base + 1;
                indices[at + 2] = base + 3;
                indices[at + 3] = base;
                indices[at + 4] = base + 3;
                indices[at + 5] = base + 2;
            }
        }
        var mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(positions));
        mesh.setBuffer(VertexBuffer.Type.Color, 4, BufferUtils.createFloatBuffer(colours));
        mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(indices));
        mesh.updateBound();
        return mesh;
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

    /** Open line paths, in the same screen-order coordinates. */
    private static Mesh strokes(float[][] paths, float height) {
        int segments = 0;
        for (var path : paths) {
            segments += path.length / 2 - 1;
        }
        var positions = new float[segments * 2 * 3];
        int at = 0;
        for (var path : paths) {
            for (int i = 0; i + 3 < path.length; i += 2) {
                positions[at] = path[i];
                positions[at + 1] = height - path[i + 1];
                positions[at + 3] = path[i + 2];
                positions[at + 4] = height - path[i + 3];
                at += 6;
            }
        }
        var mesh = new Mesh();
        mesh.setMode(Mesh.Mode.Lines);
        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(positions));
        mesh.updateBound();
        return mesh;
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

    private static Mesh meshOf(float[] positions, int[] indices) {
        var mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(positions));
        mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(indices));
        mesh.updateBound();
        return mesh;
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

    // ---- materials & text ----

    private Material unshaded(ColorRGBA colour) {
        var material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setColor("Color", linear(colour));
        material.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        material.getAdditionalRenderState().setFaceCullMode(FaceCullMode.Off);
        material.getAdditionalRenderState().setDepthTest(false);
        return material;
    }

    private Material vertexColoured() {
        var material = unshaded(ColorRGBA.White);
        material.setBoolean("VertexColor", true);
        return material;
    }

    private Material lines(ColorRGBA colour) {
        var material = unshaded(colour);
        material.getAdditionalRenderState().setLineWidth(2f);
        return material;
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
     * <p>Only the title. It is the one line on the bar that is a LABEL rather
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

    /**
     * The dark copy under a line of lettering, by the line itself.
     *
     * <p>Only the readings that sit ON something — inside a gauge, over a fill —
     * rather than on bare stone. Bone letters on a red bar are not dim, they are
     * hard to be SURE of, and the eye spends a fraction of a second on a number
     * meant to be taken in without one. Every reading in the design has this and
     * the panel had none.
     */
    private final java.util.Map<BitmapText, BitmapText> shadows =
            new java.util.IdentityHashMap<>();

    /**
     * Attach a line with its shadow, and remember the pair.
     *
     * <p>The shadow is a second {@code BitmapText} because jME has no other kind:
     * a bitmap font is drawn as it was baked, and what is not in the glyph cannot
     * be added to it. Remembered here rather than returned so that nothing which
     * writes to the panel has to know there are two of anything — see {@link #say}.
     */
    private void shadowed(Node parent, BitmapText line, BitmapText under, float depth) {
        under.setLocalTranslation(under.getLocalTranslation().x,
                under.getLocalTranslation().y, depth - 0.1f);
        parent.attachChild(under);
        line.setLocalTranslation(line.getLocalTranslation().x,
                line.getLocalTranslation().y, depth);
        parent.attachChild(line);
        shadows.put(line, under);
    }

    /** Write to a line and to whatever is underneath it. */
    private void say(BitmapText line, String words) {
        line.setText(words);
        var under = shadows.get(line);
        if (under != null) {
            under.setText(words);
        }
    }

    /** The same, in the face a game named for its own lettering. */
    private BitmapText carved(float size, ColorRGBA colour, float x, float y, float width,
            BitmapFont.Align align) {
        var line = new BitmapText(display);
        line.setSize(size);
        line.setColor(linear(colour));
        line.setBox(new Rectangle(x, y + size, width, size * 1.4f));
        line.setAlignment(align);
        return line;
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
        var points = new java.util.ArrayList<Float>();
        var order = new java.util.ArrayList<Integer>();
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
     * A line of text in a box, so alignment does the placing. The size is the
     * design's pixel size; the font's own is whatever the client shipped.
     */
    private BitmapText text(float size, ColorRGBA colour, float x, float y, float width,
            BitmapFont.Align align) {
        var line = new BitmapText(font);
        line.setSize(size);
        line.setColor(linear(colour));
        line.setBox(new Rectangle(x, y + size, width, size * 1.4f));
        line.setAlignment(align);
        return line;
    }

    private static void attach(Node parent, Spatial child, float x, float y, float z) {
        child.setLocalTranslation(x, y, z);
        parent.attachChild(child);
    }

    private static float fraction(float part, float whole) {
        return whole <= 0f ? 0f : Math.max(0f, Math.min(1f, part / whole));
    }

    private static ColorRGBA linear(ColorRGBA colour) {
        return Shade.linear(colour);
    }

    static ColorRGBA rgb(int hex) {
        return new ColorRGBA(((hex >> 16) & 0xFF) / 255f, ((hex >> 8) & 0xFF) / 255f,
                (hex & 0xFF) / 255f, 1f);
    }

    private static ColorRGBA lighter(ColorRGBA colour, float towardsWhite) {
        return Shade.lighter(colour, towardsWhite);
    }

    private static ColorRGBA darker(ColorRGBA colour, float towardsBlack) {
        return Shade.darker(colour, towardsBlack);
    }

    // ---- what the status line says ----

    /**
     * The status line, read. Nothing here knows what a skill does — only what the
     * slot has to look like — which is the whole reason the game sends finished
     * words and the client sends none.
     */
    record Reading(String name, String title, String face, String rank,
            float health, float maxHealth,
            float experience, float needed, String depth, String depthWord,
            String skillsWord, String itemsWord, String note,
            List<Stat> stats, List<SkillReading> skills,
            List<ItemReading> items, List<OrderReading> orders, boolean ordersAreHis,
            List<RankReading> ranks, int points, String pointsWord,
            Map<Character, SkillTip.Reading> tips,
            float mana, float maxMana, int refusedForManaAt, List<CostReading> costs,
            List<Stat> attributes, Map<Integer, SkillTip.Reading> attributeTips) {

        /**
         * What a slot costs to cast, and whether he can pay it.
         *
         * <p>Its own field for the reason the rank beside it is: the slot's own
         * fields are positional and already end in two optional ones, so a fourth
         * thing inside them would be a fourth shape to get wrong. A game that
         * charges nothing sends none of these and the slots are drawn as they
         * always were.
         */
        record CostReading(char key, int cost, boolean affordable) {
        }

        /**
         * What is in a slot, what fits in it, and whether the next point may go
         * there.
         *
         * <p>Its own field on the line rather than three more on the slot's,
         * which already has three shapes. Nothing here knows what a rank DOES —
         * only what the pips under the socket have to look like.
         */
        record RankReading(char key, int rank, int max, boolean canRaise, String word) {
        }

        enum State { READY, COOLING, LOCKED }

        /**
         * One slot: its key, the picture to draw in it, its state, the words on it,
         * and how much shadow is left.
         *
         * <p>{@code icon} is a path to an image, given by the game, and empty when
         * the game named none — which is not a failure: a slot with no picture is
         * drawn with the letter of its key, as every slot was before there were any
         * pictures. The client never invents one, because it has three other games
         * to serve and no idea where any of them keeps its art.
         */
        record SkillReading(char key, String icon, State state, String label, float left) {
        }

        /**
         * One figure under the bars: what it is called, what it is, and how much
         * of that he borrowed.
         *
         * <p>{@code bonus} is empty when nothing is lent. It is a finished word
         * rather than a number because the sign is part of it and the panel is not
         * in the business of deciding whether a thing that went down is good news.
         *
         * <p>{@code icon} is a path to a picture, and empty for a game that names
         * none. It comes down the wire with the figure rather than being chosen
         * here by which figure this is, which is what the panel used to do — and
         * which quietly decided that a game's third statistic is a lightning bolt.
         *
         * <p>{@code primary} marks the one attribute that is also his blow, and is
         * drawn in gold. A game with no such idea never sends the mark.
         */
        record Stat(String word, String value, String bonus, String icon, boolean primary) {

            Stat(String word, String value, String bonus, String icon) {
                this(word, value, bonus, icon, false);
            }
        }

        /** One socket in his bag: which drawing, and how many of it he carries. */
        record ItemReading(String icon, int count) {
        }

        /**
         * One button beside the map: its key, its drawing, its word, and whether
         * it is a standing order that is currently on.
         */
        record OrderReading(char key, String icon, String word, boolean on) {
        }

        static Reading parse(String status) {
            if (status == null || !status.startsWith("name=")) {
                return null;
            }
            String name = "";
            String title = "";
            String face = "";
            String rank = "";
            String depth = "";
            String depthWord = "";
            String skillsWord = "";
            String itemsWord = "";
            String note = "";
            var health = new float[] {0f, 0f};
            var experience = new float[] {0f, 0f};
            var skills = new ArrayList<SkillReading>();
            var stats = new ArrayList<Stat>();
            var items = new ArrayList<ItemReading>();
            var orders = new ArrayList<OrderReading>();
            var ordersAreHis = new boolean[] {false};
            var ranks = new ArrayList<RankReading>();
            var costs = new ArrayList<CostReading>();
            var tips = new java.util.LinkedHashMap<Character, SkillTip.Reading>();
            var attributes = new ArrayList<Stat>();
            var attributeTips = new java.util.LinkedHashMap<Integer, SkillTip.Reading>();
            float[] mana = null;
            var refusedAt = new int[] {0};
            int points = 0;
            String pointsWord = "";
            for (var field : status.split("\\|")) {
                int split = field.indexOf('=');
                if (split < 0) {
                    return null;
                }
                var value = field.substring(split + 1);
                switch (field.substring(0, split)) {
                    case "name" -> name = value;
                    case "title" -> title = value;
                    case "face" -> face = value;
                    case "rank" -> rank = value;
                    case "depth" -> depth = value;
                    case "depthWord" -> depthWord = value;
                    case "skWord" -> skillsWord = value;
                    case "itWord" -> itemsWord = value;
                    case "it" -> items.add(item(value));
                    case "cmd" -> orders.add(order(value));
                    // Whether the player may press them at all. One field for the
                    // four of them, because it is a fact about whose creature is
                    // selected rather than about any one button.
                    case "cmds" -> ordersAreHis[0] = "mine".equals(value);
                    case "note" -> note = value;
                    case "hp" -> health = pair(value);
                    case "xp" -> experience = pair(value);
                    case "skill" -> skills.add(skill(value));
                    case "mana" -> mana = pair(value);
                    case "noMana" -> refusedAt[0] = whole(value);
                    case "cost" -> costs.add(cost(value));
                    case "stat" -> stats.add(stat(value));
                    // An attribute: the four fields a figure has, and a mark on his
                    // primary. A field of its own because the block draws the two apart
                    // -- figures down the left, attributes down the right.
                    case "attr" -> attributes.add(stat(value));
                    // "rank" above is the HERO's -- "7-daraja". This is a slot's,
                    // which is a different thing on a different row, so it gets a
                    // name of its own rather than a cleverness.
                    case "srank" -> ranks.add(skillRank(value));
                    // The card over a slot, in five kinds of field. Five rather
                    // than one long one because they are five different shapes,
                    // and packing them into a single string would want an escape
                    // scheme for a saving nobody asked for.
                    case "tipName" -> tip(tips, value, Reading::tipNamed);
                    case "tipAt" -> tip(tips, value, Reading::tipAt);
                    case "tipText" -> tip(tips, value, Reading::tipBlurb);
                    case "tipFoot" -> tip(tips, value, Reading::tipFoot);
                    case "tipRow" -> tip(tips, value, Reading::tipRow);
                    // The same card over an attribute, in the same five kinds of field,
                    // keyed by the attribute's place in the game's list: an attribute
                    // has no key he presses.
                    case "atTipName" -> attributeTip(attributeTips, value, Reading::tipNamed);
                    case "atTipAt" -> attributeTip(attributeTips, value, Reading::tipAt);
                    case "atTipText" -> attributeTip(attributeTips, value, Reading::tipBlurb);
                    case "atTipRow" -> attributeTip(attributeTips, value, Reading::tipRow);
                    case "atTipFoot" -> attributeTip(attributeTips, value, Reading::tipFoot);
                    case "pts" -> {
                        var halves = value.split(",", 2);
                        points = Integer.parseInt(halves[0]);
                        pointsWord = halves.length > 1 ? halves[1] : "";
                    }
                    // How the floor is drawn. Nothing on the panel, but the line
                    // is one line: a field this panel has no picture for still has
                    // to be a field it recognises, or it would refuse the whole
                    // thing as somebody else's.
                    // What a skill looked like going off. Nothing on the panel, but
                    // the line is one line: see "look" above.
                    case "cast" -> { }
                    case "look" -> { }
                    // The floor, and what is over everybody else's head. Nothing
                    // on the panel either, and read by UnitBars off the same
                    // line: see "look" above. The panel has to KNOW them all the
                    // same, since one field it does not recognise is the whole
                    // line refused as somebody else's game.
                    case "deep" -> { }
                    case "boss" -> { }
                    case "hero" -> { }
                    case "who" -> { }
                    default -> {
                        return null; // a field this client does not know: not ours
                    }
                }
                if (health == null || experience == null || skills.contains(null)
                        || costs.contains(null)
                        || stats.contains(null) || attributes.contains(null)
                        || items.contains(null) || orders.contains(null)) {
                    return null;
                }
            }
            return new Reading(name, title, face, rank, health[0], health[1],
                    experience[0], experience[1], depth, depthWord, skillsWord,
                    itemsWord, note, List.copyOf(stats), List.copyOf(skills),
                    List.copyOf(items), List.copyOf(orders),
                    ordersAreHis[0], List.copyOf(ranks),
                    points, pointsWord, withRaising(tips, ranks),
                    mana == null ? 0f : mana[0], mana == null ? 0f : mana[1],
                    refusedAt[0], List.copyOf(costs), List.copyOf(attributes),
                    withFootLit(attributeTips));
        }

        /**
         * An attribute's footer is neither an offer nor a refusal — it says how fast he
         * is now — so it is drawn in the card's gold whenever there is one.
         */
        private static Map<Integer, SkillTip.Reading> withFootLit(
                Map<Integer, SkillTip.Reading> tips) {
            var out = new java.util.LinkedHashMap<Integer, SkillTip.Reading>();
            for (var entry : tips.entrySet()) {
                var was = entry.getValue();
                out.put(entry.getKey(), new SkillTip.Reading(was.name(), was.at(), was.blurb(),
                        was.rows(), was.foot(), !was.foot().isEmpty()));
            }
            return Map.copyOf(out);
        }

        private static SkillTip.Reading tipNamed(SkillTip.Reading was, String rest) {
            return new SkillTip.Reading(rest, was.at(), was.blurb(), was.rows(), was.foot(),
                    was.canRaise());
        }

        private static SkillTip.Reading tipAt(SkillTip.Reading was, String rest) {
            return new SkillTip.Reading(was.name(), rest, was.blurb(), was.rows(), was.foot(),
                    was.canRaise());
        }

        private static SkillTip.Reading tipBlurb(SkillTip.Reading was, String rest) {
            return new SkillTip.Reading(was.name(), was.at(), rest, was.rows(), was.foot(),
                    was.canRaise());
        }

        private static SkillTip.Reading tipFoot(SkillTip.Reading was, String rest) {
            return new SkillTip.Reading(was.name(), was.at(), was.blurb(), was.rows(), rest,
                    was.canRaise());
        }

        private static SkillTip.Reading tipRow(SkillTip.Reading was, String rest) {
            var parts = rest.split(",", 3);
            if (parts.length < 3) {
                return was;
            }
            var rows = new ArrayList<>(was.rows());
            rows.add(new SkillTip.Reading.Row(parts[0], parts[1], parts[2]));
            return new SkillTip.Reading(was.name(), was.at(), was.blurb(), List.copyOf(rows),
                    was.foot(), was.canRaise());
        }

        /** An attribute's card field: {@code <place>,<the rest>}. */
        private static void attributeTip(Map<Integer, SkillTip.Reading> tips, String value,
                java.util.function.BiFunction<SkillTip.Reading, String, SkillTip.Reading> change) {
            var halves = value.split(",", 2);
            if (halves.length < 2) {
                return;
            }
            int place;
            try {
                place = Integer.parseInt(halves[0].trim());
            } catch (NumberFormatException notAPlace) {
                return;
            }
            tips.put(place, change.apply(tips.getOrDefault(place, SkillTip.Reading.NONE),
                    halves[1]));
        }

        /**
         * {@code Q,22,yes} — the key, what it costs at its present rank, and
         * whether the pool has that much in it right now.
         */
        private static CostReading cost(String value) {
            var parts = value.split(",");
            if (parts.length < 3 || parts[0].isEmpty()) {
                return null;
            }
            try {
                return new CostReading(parts[0].charAt(0), Integer.parseInt(parts[1].trim()),
                        "yes".equals(parts[2].trim()));
            } catch (NumberFormatException notANumber) {
                return null;
            }
        }

        /** A single whole number, or 0 for one that will not parse. */
        private static int whole(String value) {
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException notANumber) {
                return 0;
            }
        }

        private static float[] pair(String value) {
            var halves = value.split("/");
            if (halves.length != 2) {
                return null;
            }
            try {
                return new float[] {Float.parseFloat(halves[0]), Float.parseFloat(halves[1])};
            } catch (NumberFormatException e) {
                return null;
            }
        }

        /**
         * {@code Q,icon,ready} — {@code W,icon,cool,72,165} — {@code R,icon,lock,5-daraja}.
         *
         * <p>The icon comes second because it belongs to the slot rather than to
         * the state: a skill's picture does not change when it goes on cooldown,
         * and putting it before the state keeps the three states the same shape as
         * each other. It may be empty.
         */
        private static SkillReading skill(String value) {
            var parts = value.split(",", -1);
            if (parts.length < 3 || parts[0].length() != 1) {
                return null;
            }
            char key = parts[0].charAt(0);
            String icon = parts[1];
            try {
                return switch (parts[2]) {
                    case "ready" -> new SkillReading(key, icon, State.READY, "", 0f);
                    // An ordinary skill nobody has bought says "lock" and stops
                    // there: it waits for a POINT rather than for a level, so
                    // there is no level to name. Only an ultimate has one.
                    case "lock" -> parts.length < 4
                            ? new SkillReading(key, icon, State.LOCKED, "", 0f)
                            : new SkillReading(key, icon, State.LOCKED, parts[3], 1f);
                    case "cool" -> parts.length < 5 ? null : cooling(key, icon,
                            Integer.parseInt(parts[3]), Integer.parseInt(parts[4]));
                    default -> null;
                };
            } catch (NumberFormatException e) {
                return null;
            }
        }

        /** {@code Zarba,34} — a word and whatever the game counts under it. */
        private static Stat stat(String value) {
            var parts = value.split(",", -1);
            return parts.length < 2 ? null
                    : new Stat(parts[0], parts[1], parts.length > 2 ? parts[2] : "",
                            parts.length > 3 ? parts[3] : "",
                            parts.length > 4 && "primary".equals(parts[4]));
        }

        /** {@code flask,3} — which drawing is in the socket, and how many of it. */
        private static ItemReading item(String value) {
            var parts = value.split(",");
            if (parts.length < 2) {
                return null;
            }
            try {
                return new ItemReading(parts[0], Integer.parseInt(parts[1]));
            } catch (NumberFormatException e) {
                return null;
            }
        }

        /** {@code A,march,Yur,off} — key, drawing, word, and whether it is lit. */
        private static OrderReading order(String value) {
            var parts = value.split(",", -1);
            if (parts.length < 4 || parts[0].length() != 1) {
                return null;
            }
            return new OrderReading(parts[0].charAt(0), parts[1], parts[2],
                    "on".equals(parts[3]));
        }

        /**
         * Frames on the wire, seconds on the screen. The simulation counts frames
         * and must, but a player reading a number off a slot thinks in seconds —
         * and the conversion is the engine's own constant, not a second copy of it.
         */
        /**
         * Change the card for one key, whatever the field was carrying.
         *
         * <p>Every tip field is {@code <key>,<the rest>}, so the key comes off
         * the front and what is left belongs to whichever part of the card the
         * field was. A key with no card yet gets an empty one to build on, since
         * the fields arrive in whatever order the game wrote them.
         */
        private static void tip(Map<Character, SkillTip.Reading> tips, String value,
                java.util.function.BiFunction<SkillTip.Reading, String, SkillTip.Reading> change) {
            var halves = value.split(",", 2);
            if (halves.length < 2 || halves[0].isEmpty()) {
                return;
            }
            char key = halves[0].charAt(0);
            tips.put(key, change.apply(tips.getOrDefault(key, SkillTip.Reading.NONE), halves[1]));
        }

        /**
         * Whether each card's footer is an offer or a refusal.
         *
         * <p>Known from the slot's own rank field rather than said twice: the
         * card and the badge are answering one question, and two fields that
         * could disagree about it would eventually disagree about it.
         */
        private static Map<Character, SkillTip.Reading> withRaising(
                Map<Character, SkillTip.Reading> tips, List<RankReading> ranks) {
            var out = new java.util.LinkedHashMap<Character, SkillTip.Reading>();
            for (var entry : tips.entrySet()) {
                boolean raising = ranks.stream().anyMatch(rank -> rank != null
                        && rank.key() == entry.getKey() && rank.canRaise());
                var was = entry.getValue();
                out.put(entry.getKey(), new SkillTip.Reading(was.name(), was.at(), was.blurb(),
                        was.rows(), was.foot(), raising));
            }
            return Map.copyOf(out);
        }

        /** {@code <key>,<rank>,<max>,up|no} — what the pips and the badge need. */
        private static RankReading skillRank(String value) {
            var parts = value.split(",");
            if (parts.length < 5 || parts[0].isEmpty()) {
                return null;
            }
            return new RankReading(parts[0].charAt(0), Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]), "up".equals(parts[3]), parts[4]);
        }

        private static SkillReading cooling(char key, String icon, int left, int total) {
            float seconds = left / (float) GameConstants.LOGICFRAMES_PER_SECOND;
            var label = seconds >= 10f
                    ? String.valueOf(Math.round(seconds))
                    : String.format(java.util.Locale.ROOT, "%.1f", seconds);
            return new SkillReading(key, icon, State.COOLING, label,
                    total <= 0 ? 0f : left / (float) total);
        }
    }

    /**
     * The marks cut into the slots and the cards, as line drawings in a 24-by-24
     * square.
     *
     * <p>Drawn here rather than loaded, because an icon that is four straight lines
     * is smaller as four straight lines than as a file, and because a glyph that
     * is drawn takes the panel's colours without a second copy of the palette
     * living in an image.
     *
     * <p>They are pictures of what the thing is rather than of what it hits: an
     * arrow leaving, a burst around a centre, a stride, a star. The names are a
     * small vocabulary a game picks from, the same way it picks the words — so a
     * dungeon says "clock" and gets a clock without this class knowing what a
     * cooldown is.
     */
    private static final class Glyphs {

        private Glyphs() {
        }

        /** An arrow flying up and to the right — the shot that goes further. */
        private static final float[][] SHOT = {
            {4, 20, 20, 4}, {14, 4, 20, 4, 20, 10}, {9, 15, 6, 18},
        };

        /** A ring with rays off it — something going off where he stands. */
        private static final float[][] BURST = ring();

        /** A stride: a long step and the ground ahead of it. */
        private static final float[][] DASH = {
            {3, 18, 8, 18, 11, 12, 14, 21, 17, 6}, {18, 6, 21, 6},
        };

        /** A star, for the one that is not like the others. */
        private static final float[][] STAR = {
            {12, 2, 14.6f, 8.4f, 21, 11, 14.6f, 13.6f, 12, 20, 9.4f, 13.6f, 3, 11, 9.4f, 8.4f,
                12, 2},
        };

        /** A dial with a hand — time, and so a cooldown. */
        private static final float[][] CLOCK = clock();

        /** A heart — health coming back. */
        private static final float[][] HEART = {
            {12, 21, 5, 15, 3, 11, 4, 7, 8, 5.5f, 12, 8, 16, 5.5f, 20, 7, 21, 11, 19, 15, 12, 21},
        };

        /** A boot in stride — moving faster. */
        private static final float[][] BOOT = {
            {8, 3, 8, 14, 5, 17, 5, 20, 18, 20, 18, 17, 13, 14, 13, 3, 8, 3},
        };

        /** A plus — more of something. */
        private static final float[][] PLUS = {
            {12, 5, 12, 19}, {5, 12, 19, 12},
        };

        /** A doubling mark — the same thing again. */
        private static final float[][] TIMES = {
            {5, 6, 12, 13}, {12, 6, 5, 13}, {14, 18, 16, 16, 16, 20, 20, 20},
        };

        /** A skull: two sockets, a nose and a jaw. Something that is not his. */
        private static final float[][] SKULL = {
            {4, 10, 4, 16, 7, 19, 7, 21, 17, 21, 17, 19, 20, 16, 20, 10,
                17, 4, 12, 2, 7, 4, 4, 10},
            circle(9f, 12f, 2.4f, 10), circle(15f, 12f, 2.4f, 10),
            {12, 15, 10.6f, 17.5f, 13.4f, 17.5f, 12, 15},
            {9.5f, 19, 9.5f, 21}, {12, 19, 12, 21}, {14.5f, 19, 14.5f, 21},
        };

        /** A blade on the diagonal, with its guard — what he hits for. */
        private static final float[][] BLADE = {
            {4, 20, 20, 4}, {14, 4, 20, 4, 20, 10}, {6, 14, 10, 18}, {4, 16, 8, 20},
        };

        /** A shield — what gets through, and the order to stand behind one. */
        private static final float[][] SHIELD = {
            {12, 3, 20, 6, 20, 12, 12, 21, 4, 12, 4, 6, 12, 3},
        };

        /** A bolt — how fast he moves. */
        private static final float[][] BOLT = {
            {13, 2, 4, 14, 11, 14, 10, 22, 19, 10, 12, 10, 13, 2},
        };

        /** A flask — something drunk, or eaten, or otherwise used up. */
        private static final float[][] FLASK = {
            {9, 3, 9, 9, 5, 18, 6, 21, 18, 21, 19, 18, 15, 9, 15, 3},
            {8, 3, 16, 3}, {6.5f, 15, 17.5f, 15},
        };

        /** Banded armour — the heavier of the two things that stop a blow. */
        private static final float[][] PLATE = {
            {12, 3, 20, 6, 20, 12, 12, 21, 4, 12, 4, 6, 12, 3},
            {5, 10, 19, 10}, {5.5f, 14, 18.5f, 14},
        };

        /** An arrow coming down onto a line — "walk to there". */
        private static final float[][] MARCH = {
            {12, 3, 12, 15}, {7, 11, 12, 16, 17, 11}, {5, 20, 19, 20},
        };

        /** A filled-looking square — "stop". */
        private static final float[][] HALT = {
            {6, 6, 18, 6, 18, 18, 6, 18, 6, 6},
            {8.5f, 8.5f, 15.5f, 8.5f, 15.5f, 15.5f, 8.5f, 15.5f, 8.5f, 8.5f},
        };

        /** A cut stone — the sort of thing found in a chest. */
        private static final float[][] GEM = {
            {12, 3, 20, 10, 12, 21, 4, 10, 12, 3}, {4, 10, 20, 10}, {12, 3, 8, 10, 12, 21},
            {12, 3, 16, 10, 12, 21},
        };

        /** Anything the design did not draw: a plain lozenge, so nothing is blank. */
        private static final float[][] PLAIN = {
            {12, 4, 19, 12, 12, 20, 5, 12, 12, 4},
        };

        private static float[][] ring() {
            var paths = new float[9][];
            paths[0] = circle(12f, 12f, 4f, 16);
            float[][] rays = {
                {12, 3, 12, 6}, {12, 18, 12, 21}, {3, 12, 6, 12}, {18, 12, 21, 12},
                {5.6f, 5.6f, 7.7f, 7.7f}, {16.3f, 16.3f, 18.4f, 18.4f},
                {18.4f, 5.6f, 16.3f, 7.7f}, {7.7f, 16.3f, 5.6f, 18.4f},
            };
            System.arraycopy(rays, 0, paths, 1, rays.length);
            return paths;
        }

        private static float[][] clock() {
            return new float[][] {circle(12f, 12f, 9f, 20), {12, 7, 12, 12, 15, 14}};
        }

        private static float[] circle(float x, float y, float radius, int steps) {
            var points = new float[(steps + 1) * 2];
            for (int i = 0; i <= steps; i++) {
                float angle = FastMath.TWO_PI * i / steps;
                points[i * 2] = x + radius * FastMath.cos(angle);
                points[i * 2 + 1] = y + radius * FastMath.sin(angle);
            }
            return points;
        }

        /**
         * One glyph as a line mesh, centred on the origin and scaled to {@code size}.
         * The drawings are in screen order — y downwards, as a designer writes
         * them — and the flip to the client's upward y happens once, here.
         */
        private static Mesh of(String named, float size) {
            var paths = switch (named) {
                case "Q", "shot" -> SHOT;
                case "W", "burst" -> BURST;
                case "E", "dash" -> DASH;
                case "R", "star" -> STAR;
                case "clock" -> CLOCK;
                case "heart" -> HEART;
                case "boot" -> BOOT;
                case "plus" -> PLUS;
                case "times" -> TIMES;
                case "skull" -> SKULL;
                case "blade" -> BLADE;
                case "shield" -> SHIELD;
                case "bolt" -> BOLT;
                case "flask" -> FLASK;
                case "plate" -> PLATE;
                case "march" -> MARCH;
                case "halt" -> HALT;
                case "gem" -> GEM;
                default -> PLAIN;
            };
            int segments = 0;
            for (var path : paths) {
                segments += path.length / 2 - 1;
            }
            var positions = new float[segments * 2 * 3];
            float scale = size / 24f;
            int at = 0;
            for (var path : paths) {
                for (int i = 0; i + 3 < path.length; i += 2) {
                    at = put(positions, at, path[i], path[i + 1], scale);
                    at = put(positions, at, path[i + 2], path[i + 3], scale);
                }
            }
            var mesh = new Mesh();
            mesh.setMode(Mesh.Mode.Lines);
            mesh.setBuffer(VertexBuffer.Type.Position, 3,
                    BufferUtils.createFloatBuffer(positions));
            mesh.updateBound();
            return mesh;
        }

        private static int put(float[] positions, int at, float x, float y, float scale) {
            positions[at] = (x - 12f) * scale;
            positions[at + 1] = (12f - y) * scale;
            positions[at + 2] = 0f;
            return at + 3;
        }
    }

    /** The glyph vocabulary, for anything else in the client that draws one. */
    static Mesh glyph(String named, float size) {
        return Glyphs.of(named, size);
    }
}
