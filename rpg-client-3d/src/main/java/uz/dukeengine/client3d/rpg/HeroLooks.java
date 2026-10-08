package uz.dukeengine.client3d.rpg;

import java.util.LinkedHashMap;
import java.util.Map;
import uz.dukeengine.client3d.Visuals;

/**
 * How the hero's bar and its portrait are drawn: its icons, its block of figures, its blocks and colours, and the live
 * creature in its frame. Held among the game's {@link Visuals} ({@link #of}), where they were before the bar was the
 * RPG's: a game writes {@code HeroLooks.of(visuals).statLook(…)} where it wrote {@code visuals.statLook(…)}.
 */
public final class HeroLooks {

    private HeroLooks() {
    }

    /** The hero bar's looks among {@code visuals}, made with every default the first time they are asked for. */
    public static HeroLooks of(Visuals visuals) {
        return visuals.part(HeroLooks.class, HeroLooks::new);
    }

    private IconLook iconLook = IconLook.DEFAULT;

    /**
     * Whether the game's icons are white drawings to be tinted, or pictures
     * already painted — see {@link IconLook}.
     *
     * <p>A game that never says gets what the panel always did, which is to
     * colour them: the drawings it was built for were white, and tinting them is
     * how one file serves a slot that is ready, one reloading and one locked.
     */
    public HeroLooks iconLook(IconLook look) {
        this.iconLook = look == null ? IconLook.DEFAULT : look;
        return this;
    }

    public IconLook getIconLook() {
        return iconLook;
    }

    private StatLook statLook = StatLook.DEFAULT;

    /**
     * How the block of figures and attributes under the experience bar is drawn — see
     * {@link StatLook}. A game that never says gets its default sizes and colours.
     */
    public HeroLooks statLook(StatLook look) {
        this.statLook = look == null ? StatLook.DEFAULT : look;
        return this;
    }

    public StatLook getStatLook() {
        return statLook;
    }

    private PanelLook panelLook = PanelLook.DEFAULTS;

    /**
     * Which blocks the hero's bar has, in what order, how big its sockets are and every colour it is painted
     * in — see {@link PanelLook}. A game that never says gets the bar as it was designed.
     */
    public HeroLooks panelLook(PanelLook look) {
        this.panelLook = look == null ? PanelLook.DEFAULTS : look;
        return this;
    }

    public PanelLook getPanelLook() {
        return panelLook;
    }

    private final Map<String, PortraitLook> portraits = new LinkedHashMap<>();
    private PortraitLook everyPortrait;
    private int portraitFps = 24;

    /**
     * Draw this template <em>alive</em> in the hero panel's frame, rather than as
     * the silhouette that stands there otherwise — see {@link PortraitLook}.
     *
     * <p>Named by the creature's own template, and so repeatable. No art is named
     * here — the model, its scale and the libraries its clips come from are bound
     * once in {@code Visuals.unit}, under this same name, and the portrait takes them
     * from there.
     *
     * <p>An override rather than the way in: {@link #portraits} already gives a
     * face to everything the player can select. This is for the creature that
     * wants a different one — a hero who holds his bow ready rather than standing
     * about, and has a flourish for a new level.
     */
    public HeroLooks portrait(String templateName, PortraitLook look) {
        if (templateName != null && look != null) {
            portraits.put(templateName, look);
        }
        return this;
    }

    /**
     * One portrait for everything the player can select, without naming any of
     * them.
     *
     * <p>The whole of what a game has to do to give every monster in it a face.
     * Nothing about a portrait is per-creature except where the camera stands, and
     * that is already written as fractions of whatever it is looking at — so one
     * block frames a skeleton, a hero and whatever is added next, each by its own
     * measured height. The clips need not be named either: a creature's own idle
     * and death are already bound on it.
     *
     * <p>Naming none leaves every frame to the drawing, which is what every game
     * on this client had.
     */
    public HeroLooks portraits(PortraitLook look) {
        this.everyPortrait = look;
        return this;
    }

    /**
     * How that template is drawn in the frame — its own, or the one every
     * selectable creature gets, or {@code null} for the silhouette.
     */
    public PortraitLook getPortrait(String templateName) {
        var own = templateName == null ? null : portraits.get(templateName);
        return own != null ? own : everyPortrait;
    }

    /**
     * How many times a second the portrait is worth redrawing.
     *
     * <p>A ceiling rather than a target, and the whole of what a live portrait
     * costs. A hundred and fifty pixels of one creature redrawn on every frame of
     * a game that is drawing a floor of the dungeon is work nobody can see; at a
     * third of that it still breathes, and the two frames in between cost
     * literally nothing, because a viewport that is switched off is skipped before
     * anything in it is touched.
     */
    public HeroLooks portraitFps(int framesPerSecond) {
        this.portraitFps = Math.clamp(framesPerSecond, 1, 60);
        return this;
    }

    public int getPortraitFps() {
        return portraitFps;
    }
}
