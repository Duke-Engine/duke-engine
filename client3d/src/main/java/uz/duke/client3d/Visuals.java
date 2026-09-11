package uz.duke.client3d;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Unity-style asset binding: attach models, animations and sounds to unit
 * templates by name — no engine code, just configuration.
 *
 * <pre>{@code
 * var visuals = Visuals.create()
 *         .unit("Tank", u -> u
 *                 .model("Models/tank.gltf").scale(1.5f).facing(90)
 *                 .idle("Idle").walk("Drive").attack("Fire")
 *                 .fireSound("Sounds/cannon.ogg").dieSound("Sounds/boom.ogg"))
 *         .unit("Rifleman", u -> u.model("Models/soldier.gltf").walk("Walk"));
 * }</pre>
 *
 * <p>Every setting is optional. A unit with no model gets a clean primitive
 * (box for structures, capsule for units) in its player's colour, so a game is
 * playable before a single asset exists — add art when you have it.
 */
public final class Visuals {

    /**
     * One file animations are taken from.
     *
     * <p>{@code clipName} is what to call the file's single animation, or
     * {@code null} to take every clip in it under its own name.
     */
    record AnimationSource(String assetPath, String clipName) {
    }

    /** Per-template visual/audio configuration. All fields optional. */
    public static final class UnitVisual {
        String modelPath;
        String modelPart;
        String texturePath;
        String heldPath;
        String heldBone;
        float heldScale = 1f;
        /** Where this unit's animations come from, in the order they were named. */
        final java.util.List<AnimationSource> animations = new java.util.ArrayList<>();
        float scale = 1f;
        float yOffset;
        float facingDegrees; // extra yaw if the model's authored "forward" isn't +X
        String idleAnim;
        String walkAnim;
        String attackAnim;
        String hurtAnim;
        String dieAnim;
        String fireSound;
        String dieSound;
        java.awt.Color colour; // null = the owning player's colour
        java.awt.Color tint;   // multiplied over the model's own texture

        public UnitVisual model(String assetPath) {
            this.modelPath = assetPath;
            return this;
        }

        /**
         * One named piece out of a model file, rather than the whole thing.
         *
         * <p>Kits bundle props with the character carrying them — a bow, a shield,
         * the arrow on the string — and those are the props the game needs when it
         * comes to draw one on its own. Without this the only way to use the arrow
         * a character is holding is to load the character.
         *
         * <p>The name is the one inside the file, which is not always the name the
         * thing deserves: exporters mislabel, and a mesh called "Eyes" can turn out
         * to be an arrow. Naming it here rather than in code keeps that where
         * somebody can see it.
         */
        public UnitVisual modelPart(String assetPath, String partName) {
            this.modelPath = assetPath;
            this.modelPart = partName;
            return this;
        }

        /**
         * A second model carried on one of this one's bones — a bow, a sword, a
         * lantern.
         *
         * <p>Character kits ship weapons as separate files rather than as part of
         * the body, and they do it on purpose: one ranger and a rack of weapons is
         * every armed ranger there is, where a ranger-with-a-bow is one of them.
         * The rig has a bone for it — KayKit's is {@code handslot.l} — authored so
         * that a weapon hung there with no transform of its own lands in the hand
         * and stays in it through every clip, because the hand is what the clip
         * moves.
         *
         * <p>Which is why there is no rotation to set here. If a kit needed one,
         * the bone would not be doing its job.
         *
         * @param scale what to multiply the held model by, when it and the body
         *     were not authored at the same size; 1 for a kit that ships both
         */
        public UnitVisual holds(String assetPath, String boneName, float scale) {
            this.heldPath = assetPath;
            this.heldBone = boneName;
            this.heldScale = scale;
            return this;
        }

        /**
         * The colour map to draw this model with, overriding whatever the file
         * came with.
         *
         * <p>Two reasons, and the first is not optional. Model kits routinely ship
         * a base colour texture that jME's glTF loader does not bind, so the
         * creature arrives untextured and nobody finds out until they look at it.
         * The second is that a kit with two creatures and three colourways for
         * each has six creatures in it, if you can say which colourway you want —
         * and a dungeon needs more kinds of monster than a free kit ships models.
         */
        public UnitVisual texture(String assetPath) {
            this.texturePath = assetPath;
            return this;
        }

        /**
         * Take this unit's animations from another file — a library built on the
         * same skeleton as the model.
         *
         * <p>Creature kits and animation libraries are sold separately and meet on
         * the standard humanoid rig, so one library moves every creature in a kit.
         * Only the clips named by {@link #idle}, {@link #walk} and {@link #attack}
         * are taken: a library holds dozens, and a unit needs three.
         */
        public UnitVisual animationsFrom(String assetPath) {
            animations.add(new AnimationSource(assetPath, null));
            return this;
        }

        /**
         * The same, for a file that holds exactly one animation: call it
         * {@code clipName} here, and name it with {@link #idle} and friends.
         *
         * <p>Animation sites hand their work out one movement per file, and every
         * one of those files carries the same exporter-generated clip name. The
         * file is then the only thing that says which is the run and which is the
         * punch, so the name has to be given on the way in. Call this once per
         * animation.
         */
        public UnitVisual animationFrom(String assetPath, String clipName) {
            animations.add(new AnimationSource(assetPath, clipName));
            return this;
        }

        /**
         * Draw this unit type in a colour of its own instead of its player's.
         *
         * <p>Player colour answers "whose is it?", which is the only question an
         * RTS asks of a shape. A game whose sides each field several kinds of
         * thing has a second question — "what is it?" — and with no models yet
         * there is nothing left to answer it with: every enemy is the same
         * capsule in the same colour, on screen and on the minimap alike.
         *
         * <p>Left unset, the player's colour is used exactly as before.
         */
        public UnitVisual colour(java.awt.Color colour) {
            this.colour = colour;
            return this;
        }

        /**
         * A wash of colour over the model's own texture.
         *
         * <p>Distinct from {@link #colour}, which replaces a shape's colour and is
         * what the minimap dot is drawn in. A tint multiplies whatever the skin
         * already is, so it separates two monsters sharing one texture without
         * flattening either into a single colour — and without changing what the
         * player reads on the minimap.
         */
        public UnitVisual tint(java.awt.Color tint) {
            this.tint = tint;
            return this;
        }

        public UnitVisual scale(float scale) {
            this.scale = scale;
            return this;
        }

        /** Raise (or sink) the model relative to the ground. */
        public UnitVisual yOffset(float yOffset) {
            this.yOffset = yOffset;
            return this;
        }

        /** Extra rotation when the model file doesn't face the engine's +X. */
        public UnitVisual facing(float degrees) {
            this.facingDegrees = degrees;
            return this;
        }

        public UnitVisual idle(String animName) {
            this.idleAnim = animName;
            return this;
        }

        public UnitVisual walk(String animName) {
            this.walkAnim = animName;
            return this;
        }

        /**
         * What it plays for one blow — <em>once</em> per blow, not on a loop.
         *
         * <p>A unit that has a target is "attacking" for the whole engagement,
         * reload and all, so a clip chosen from that state runs at its own tempo
         * and has nothing to do with when the weapon actually lets go. The client
         * plays this on the shot instead, which is a moment the game already
         * announces.
         */
        public UnitVisual attack(String animName) {
            this.attackAnim = animName;
            return this;
        }

        /**
         * What it plays when something takes health off it — a short flinch, over
         * whatever else it was doing.
         *
         * <p>Driven by the health in the snapshot rather than by whatever fired,
         * so a blow, an arrow arriving a moment after it was loosed, and a spell
         * with no shooter at all all read the same: it flinches when it is hurt.
         */
        public UnitVisual hurt(String animName) {
            this.hurtAnim = animName;
            return this;
        }

        /** What it plays as it dies, before the body is taken away. */
        public UnitVisual die(String animName) {
            this.dieAnim = animName;
            return this;
        }

        public UnitVisual fireSound(String assetPath) {
            this.fireSound = assetPath;
            return this;
        }

        public UnitVisual dieSound(String assetPath) {
            this.dieSound = assetPath;
            return this;
        }
    }

    // Linked, so the order a game declares its units in is the order anything
    // walking them sees -- which is what a loading bar advances through.
    private final Map<String, UnitVisual> units = new java.util.LinkedHashMap<>();
    private final UnitVisual defaults = new UnitVisual();
    private String assetRoot;
    private String discoveryTemplate;
    private Tileset tileset;

    private Visuals() {
    }

    public static Visuals create() {
        return new Visuals();
    }

    /**
     * A directory to load assets from (in addition to the classpath). This is
     * how the Studio points the game at a project's assets folder; exported
     * games instead ship assets on the classpath and don't need it.
     */
    public Visuals assetRoot(String directory) {
        this.assetRoot = directory;
        return this;
    }

    public String getAssetRoot() {
        return assetRoot;
    }

    /** Configure the look and sound of one unit template. */
    public Visuals unit(String templateName, Consumer<UnitVisual> config) {
        var visual = units.computeIfAbsent(templateName, n -> new UnitVisual());
        config.accept(visual);
        return this;
    }

    /** The configuration for a template (empty defaults if none was set). */
    public UnitVisual of(String templateName) {
        return units.getOrDefault(templateName, defaults);
    }

    /**
     * Hide the map until the player has been there, and open it up around the
     * units built from {@code templateName} — a dungeon crawler's fog rather than
     * an RTS's.
     *
     * <p>An RTS shows the ground and hides what walks on it: the map is a briefing,
     * and the game is about what you cannot see moving across it. A crawler hides
     * the ground too, because there the map <em>is</em> the thing being discovered.
     * The engine only knows the first kind, so this is the client's answer to the
     * second, and games get it only by asking.
     *
     * <p>A template name rather than a distance on purpose. The radius is that
     * template's {@code VisionRange} — the very number the engine's own fog uses to
     * decide which creatures the player can see — so the ground opening up and the
     * monsters appearing are one setting in one file, and cannot be tuned apart.
     */
    public Visuals discoveredBy(String templateName) {
        this.discoveryTemplate = templateName;
        return this;
    }

    /** The template whose vision opens the map, or {@code null} for no discovery. */
    public String getDiscoveryTemplate() {
        return discoveryTemplate;
    }

    private Fog fog = Fog.DEFAULT;

    /**
     * How the dark behaves and what colour it is — see {@link Fog}.
     *
     * <p>Separate from {@link #discoveredBy} because they answer different
     * questions. That one says whose eyes open the map, which the client cannot
     * guess; this one says what the dark is worth, which the client has a
     * perfectly good default for and only a crawler wants to change.
     */
    public Visuals fog(Fog fog) {
        this.fog = fog == null ? Fog.DEFAULT : fog;
        return this;
    }

    public Fog getFog() {
        return fog;
    }

    private EdgeScroll edgeScroll = EdgeScroll.NONE;

    /**
     * Let the cursor shove the camera when it reaches the edge of the screen —
     * see {@link EdgeScroll}.
     *
     * <p>Asked for rather than assumed, because it is taste rather than
     * correctness: a game that never asks keeps the keys and nothing else, which
     * is what every game had.
     */
    public Visuals edgeScroll(EdgeScroll edgeScroll) {
        this.edgeScroll = edgeScroll == null ? EdgeScroll.NONE : edgeScroll;
        return this;
    }

    public EdgeScroll getEdgeScroll() {
        return edgeScroll;
    }

    /**
     * Build the ground from a modular kit rather than from coloured blocks.
     *
     * <p>The same bargain as {@link #discoveredBy}: the client knows how to lay a
     * kit out — floor on open ground, walls on the boundary, posts in the corners
     * — and the game says which kit. A game that never asks keeps the blocks,
     * which is the right picture for a map that is a battlefield rather than a
     * building.
     */
    public Visuals tiles(Tileset tileset) {
        this.tileset = tileset;
        return this;
    }

    /** The kit the ground is built from, or {@code null} for plain blocks. */
    public Tileset getTiles() {
        return tileset;
    }

    // ---- themes ----

    /**
     * One named way a world can look: a kit to build it from, a colour for the
     * dark, and whatever creatures are drawn differently while it lasts.
     *
     * <p>Everything in it is optional. A theme that names only a kit changes only
     * the floor; one that overrides one monster leaves every other creature
     * exactly as the game described it outside any theme.
     */
    public static final class Theme {

        private Tileset tileset;
        private Integer fogTint;
        private final Map<String, UnitVisual> units = new java.util.LinkedHashMap<>();

        private Theme() {
        }

        public Theme tiles(Tileset tileset) {
            this.tileset = tileset;
            return this;
        }

        /** What the dark is coloured while this theme lasts, packed {@code 0xRRGGBB}. */
        public Theme fogTint(int packedRgb) {
            this.fogTint = packedRgb;
            return this;
        }

        /**
         * How one creature is drawn while this theme lasts.
         *
         * <p>A whole replacement, not a patch: a themed creature is described from
         * nothing, so a theme that gives it a model has to give it that model's
         * scale and clips too. Patching would mean a half-described creature
         * wearing one kit's animation names on another kit's skeleton.
         */
        public Theme unit(String templateName, Consumer<UnitVisual> config) {
            var visual = units.computeIfAbsent(templateName, n -> new UnitVisual());
            config.accept(visual);
            return this;
        }

        Tileset getTiles() {
            return tileset;
        }

        Integer getFogTint() {
            return fogTint;
        }

        /** How this theme draws a creature, or {@code null} if it has no opinion. */
        UnitVisual of(String templateName) {
            return units.get(templateName);
        }
    }

    private final Map<String, Theme> themes = new java.util.LinkedHashMap<>();

    /**
     * Register a named theme. Which one is current is the <em>game's</em> to say,
     * frame by frame, through the snapshot's status channel — see
     * {@code DukeRtsApp}. The client only ever asks "which of these, now".
     *
     * <p>Kept out of {@link #tiles} and {@link #fog} on purpose: those are what a
     * game looks like, full stop, and most games have exactly one answer. A theme
     * is for a game whose answer changes as it is played.
     */
    public Visuals theme(String name, Consumer<Theme> config) {
        var theme = themes.computeIfAbsent(name, n -> new Theme());
        config.accept(theme);
        return this;
    }

    /** The theme of that name, or {@code null} — including for a null name. */
    public Theme getTheme(String name) {
        return name == null ? null : themes.get(name);
    }

    /** Whether this game has any themes at all; most do not. */
    public boolean hasThemes() {
        return !themes.isEmpty();
    }

    // ---- menus ----

    private MenuStyle menuStyle = MenuStyle.PLAIN;

    /**
     * How this game's menus are lettered — see { MenuStyle}.
     *
     * <p>The same bargain as everywhere else: the client knows how to draw a
     * menu and the game says what it should look like.
     */
    public Visuals menuStyle(MenuStyle style) {
        this.menuStyle = style == null ? MenuStyle.PLAIN : style;
        return this;
    }

    public MenuStyle getMenuStyle() {
        return menuStyle;
    }

    // ---- noise ----

    private SoundBank sounds = SoundBank.silent();

    /**
     * What this game sounds like — see {@link SoundBank}.
     *
     * <p>The same bargain as {@link #tiles}: the client raises the moments,
     * because it is the one drawing them, and the game says what each one sounds
     * like, because it is the one that knows. A game that never asks is silent,
     * which is what every game here was.
     */
    public Visuals sounds(SoundBank bank) {
        this.sounds = bank == null ? SoundBank.silent() : bank;
        return this;
    }

    public SoundBank getSounds() {
        return sounds;
    }

    // ---- what all of this adds up to ----

    /**
     * Every look this game can draw: the ones it named, and the ones its themes
     * name on top of them.
     *
     * <p>For {@link Preload}, which needs to know what will be asked for before it
     * is. A themed look is included even though nothing will wear it for another
     * ten floors — that is exactly the one whose file is not read yet when the
     * floor changes.
     */
    java.util.List<UnitVisual> allLooks() {
        var all = new java.util.ArrayList<>(units.values());
        for (var theme : themes.values()) {
            all.addAll(theme.units.values());
        }
        return all;
    }

    /** Every kit a world may be built from: the game's own, and its themes'. */
    java.util.List<Tileset> allKits() {
        var all = new java.util.ArrayList<Tileset>();
        if (tileset != null) {
            all.add(tileset);
        }
        for (var theme : themes.values()) {
            if (theme.tileset != null) {
                all.add(theme.tileset);
            }
        }
        return all;
    }
}
