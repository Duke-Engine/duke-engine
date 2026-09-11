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
        /** The name of the flight effect this unit wears, or null for a plain one. */
        String effect;
        /** How far forward of its middle the effect sits; see effectAt. */
        float effectForward;
        float heldScale = 1f;
        float heldPitch;
        float heldYaw;
        float heldRoll;
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
         * that a weapon hung there lands in the hand and stays in it through every
         * clip, because the hand is what the clip moves.
         *
         * @param scale what to multiply the held model by, when it and the body
         *     were not authored at the same size; 1 for a kit that ships both
         * @see #heldTurn
         */
        public UnitVisual holds(String assetPath, String boneName, float scale) {
            this.heldPath = assetPath;
            this.heldBone = boneName;
            this.heldScale = scale;
            return this;
        }

        /**
         * How far to turn what he carries, in degrees, before it goes on the bone.
         *
         * <p>The bone gets a weapon into the hand and does not settle which way
         * round it is, because that is between the bone and the <em>model</em> and
         * a kit does not always lay every model out the same way. KayKit's bow is
         * the case in point: every other weapon in the pack runs along its own
         * {@code +Y} and the bow runs along {@code +Z}, so the bone that points a
         * sword's blade out of the fist points the bow's length straight up, which
         * is right — and then hands it over with the string facing away from the
         * archer and the grip against his knuckles, which is not.
         *
         * <p>So this is the same kind of number as a wall's shift or a unit's
         * facing: a fact about the art, measured once and written down where it
         * can be seen.
         */
        public UnitVisual heldTurn(float pitchDegrees, float yawDegrees, float rollDegrees) {
            this.heldPitch = pitchDegrees;
            this.heldYaw = yawDegrees;
            this.heldRoll = rollDegrees;
            return this;
        }

        /**
         * What this thing looks like in flight, by the name of a recipe declared
         * with {@link Visuals#effect}.
         *
         * <p>For projectiles. A creature may name one too and nothing stops it,
         * but a burning skeleton is a longer conversation than a burning arrow.
         */
        public UnitVisual effect(String recipeName) {
            this.effect = recipeName;
            return this;
        }

        /**
         * Where on this thing the effect sits: how far forward of its middle, and
         * the height is {@link #yOffset} because that is where the thing itself is
         * drawn.
         *
         * <p>Both halves were wrong before either was set. A trail hung on a unit's
         * root comes out of the <em>ground under it</em>, because the root is where
         * the unit stands and the model is lifted off it — so a burning arrow flew
         * at chest height with its fire dragging along the floor. And a trail at
         * the middle of a twelve-unit shaft is fire coming out of the middle of an
         * arrow, where it belongs at the head.
         */
        public UnitVisual effectAt(float forward) {
            this.effectForward = forward;
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
    private final Map<String, EffectVisual> effects = new java.util.LinkedHashMap<>();
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

    /**
     * One named recipe for what a thing in flight looks like.
     *
     * <p>Named rather than written on the unit, because a recipe is shared: an
     * arrow and the drawn shot the hero looses are the same fire at two sizes, and
     * a game with six kinds of burning thing has two or three kinds of burning.
     * Units point at one by name with {@link UnitVisual#effect}.
     *
     * <p>The client owns the <em>kinds</em> of effect — a trail, a glowing body, a
     * burst on landing — and the game owns every number in them. That division is
     * the same one the rest of this class keeps, and it is what lets a new burning
     * thing be a block of settings rather than a class.
     */
    public Visuals effect(String name, Consumer<EffectVisual> config) {
        config.accept(effects.computeIfAbsent(name, n -> new EffectVisual()));
        return this;
    }

    /** The recipe under that name, or {@code null} when the game named none. */
    public EffectVisual effectNamed(String name) {
        return name == null ? null : effects.get(name);
    }

    /**
     * What the client may spend on things in flight.
     *
     * <p>Every one of these is a ceiling rather than a target, and the reason they
     * exist at all is that a fight is not one arrow. Fifty in the air, each with a
     * hundred sparks and a light of its own, is five thousand particles and fifty
     * dynamic lights — and dynamic lights are the expensive kind. Past the ceiling
     * a shot simply flies plainer: no light, or no trail, but the same shot going
     * to the same place.
     *
     * @param lights    how many may burn at once. The terrain shader reads four;
     *     more than that still light the creatures, which is where jME's own
     *     lighting is doing the work
     * @param perEffect how many trails one recipe may have alight
     * @param bursts    how many impacts may be burning at once
     * @param distance  how far from the camera a thing is still worth the trouble;
     *     zero for no limit
     */
    public record EffectBudget(int lights, int perEffect, int bursts, float distance) {
    }

    private EffectBudget budget = new EffectBudget(4, 8, 8, 0f);

    public Visuals effectBudget(int lights, int perEffect, int bursts, float distance) {
        this.budget = new EffectBudget(lights, perEffect, bursts, distance);
        return this;
    }

    public EffectBudget getEffectBudget() {
        return budget;
    }

    /** Every recipe, in the order the game declared them. */
    public java.util.Collection<EffectVisual> allEffects() {
        return java.util.List.copyOf(effects.values());
    }

    /**
     * What a thing in flight looks like: what it trails, what it is made of, and
     * what it leaves where it lands.
     *
     * <p>Every field has a harmless default, so a recipe that names only a colour
     * is a recipe — and a game that names no recipe at all draws exactly what it
     * drew before any of this existed.
     */
    public static final class EffectVisual {

        /**
         * The effects this client knows how to draw.
         *
         * <p>Public because they are half of a contract: the client owns the kinds
         * and the game owns which of them a thing uses, and a game with no way to
         * ask what the kinds are would be guessing at strings.
         */
        public static final String FLAME_TRAIL = "FLAME_TRAIL";
        public static final String GLOW_ORB = "GLOW_ORB";
        public static final String IMPACT_BURST = "IMPACT_BURST";
        /**
         * Named pieces of a model, lit from inside.
         *
         * <p>The one of these that is not about something in flight, and the reason
         * the whole arrangement was worth generalising: a skeleton's eye sockets, a
         * rune on a door, the coals in a brazier. It costs one material and no
         * light at all, which is what makes it affordable on every creature in a
         * room — a torch each would be over the budget before the second one.
         */
        public static final String GLOW_PARTS = "GLOW_PARTS";

        /** All of them, for a game that wants to check a settings file against it. */
        public static java.util.Set<String> allKinds() {
            return java.util.Set.of(FLAME_TRAIL, GLOW_ORB, IMPACT_BURST, GLOW_PARTS);
        }

        /**
         * Which of the client's effects this recipe uses, by name.
         *
         * <p>Strings rather than an enum because the enum is the client's and the
         * settings file is the game's: a name the client does not know is a line
         * in a file rather than a compile error, and it is ignored with a warning
         * instead of stopping the game.
         */
        final java.util.Set<String> kinds = new java.util.LinkedHashSet<>();
        /** Words that name the pieces GLOW_PARTS lights; see {@link #part}. */
        final java.util.List<String> parts = new java.util.ArrayList<>();
        java.awt.Color colour = java.awt.Color.WHITE;
        java.awt.Color fade;
        java.awt.Color lightColour;
        float lightPower;
        float lightRadius;
        int particles;
        float particleSize = 1f;
        float particleLife = 0.4f;
        float spread;
        float orbSize;
        int burstParticles;
        float burstSize = 1f;
        float burstSeconds = 0.3f;

        private EffectVisual() {
        }

        public EffectVisual kind(String name) {
            kinds.add(name);
            return this;
        }

        /** What it burns, and what that colour dies down to. */
        public EffectVisual colours(java.awt.Color colour, java.awt.Color fade) {
            this.colour = colour;
            this.fade = fade;
            return this;
        }

        /**
         * The light it carries: its colour, how hard it burns, and how far it
         * reaches. A power of zero means it carries none, which is what everything
         * further off than the client is willing to light ends up with anyway.
         */
        public EffectVisual light(java.awt.Color colour, float power, float radius) {
            this.lightColour = colour;
            this.lightPower = power;
            this.lightRadius = radius;
            return this;
        }

        /**
         * The trail: how many sparks are alive at once, how big, how long they
         * last, and how far they wander from the line of flight.
         */
        public EffectVisual particles(int count, float size, float life, float spread) {
            this.particles = count;
            this.particleSize = size;
            this.particleLife = life;
            this.spread = spread;
            return this;
        }

        /** How wide the glowing body is, for a projectile that has no model. */
        public EffectVisual orb(float size) {
            this.orbSize = size;
            return this;
        }

        /**
         * A piece of the model to light from inside, by a word in its name.
         *
         * <p>A word rather than the whole name, because a kit names the same piece
         * differently on every creature it ships — {@code Skeleton_Warrior_Eyes},
         * {@code Skeleton_Mage_Eyes} — and a game should be able to say "the eyes"
         * once for all of them.
         */
        public EffectVisual part(String nameContains) {
            parts.add(nameContains);
            return this;
        }

        /** What it leaves where it lands. */
        public EffectVisual burst(int count, float size, float seconds) {
            this.burstParticles = count;
            this.burstSize = size;
            this.burstSeconds = seconds;
            return this;
        }

        boolean has(String kind) {
            return kinds.contains(kind);
        }
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

    private PanelSkin panelSkin = PanelSkin.NONE;

    /**
     * What the hero panel's edges are painted with — see {@link PanelSkin}.
     *
     * <p>The same bargain again, and the same one {@link #menuStyle} strikes about
     * lettering: the client knows where a skill socket goes and how big it is, and
     * the game says what its rim is painted with. Naming nothing leaves the panel
     * carved out of flat colour, which is what it was.
     */
    public Visuals panelSkin(PanelSkin skin) {
        this.panelSkin = skin == null ? PanelSkin.NONE : skin;
        return this;
    }

    public PanelSkin getPanelSkin() {
        return panelSkin;
    }

    private final java.util.Map<String, Cursors.Look> pointers = new java.util.LinkedHashMap<>();

    /**
     * What the mouse pointer looks like in one situation — see {@link Cursors}.
     *
     * <p>The situations are the client's, because what is under the pointer is a
     * fact about the screen; the pictures are the game's, like every other piece
     * of its art. Naming none leaves the system arrow, which is what every game
     * had.
     *
     * @param hotX how far from the left of the picture the tip is
     * @param hotY how far from the top of it — read the way anyone reads a file
     */
    public Visuals pointer(String situation, String assetPath, int hotX, int hotY) {
        return pointer(situation, assetPath, hotX, hotY, 0xFFFFFF);
    }

    /** The same, painted: the drawings are white, so this is what colours them. */
    public Visuals pointer(String situation, String assetPath, int hotX, int hotY, int tint) {
        if (situation != null && assetPath != null && !assetPath.isBlank()) {
            pointers.put(situation, new Cursors.Look(assetPath, hotX, hotY, tint));
        }
        return this;
    }

    java.util.Map<String, Cursors.Look> getPointers() {
        return java.util.Map.copyOf(pointers);
    }

    /** The pointer pictures, for {@link Preload}: read before the window needs them. */
    public java.util.List<String> pointerImages() {
        return pointers.values().stream().map(Cursors.Look::image).toList();
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
