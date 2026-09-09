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

    /** Per-template visual/audio configuration. All fields optional. */
    public static final class UnitVisual {
        String modelPath;
        String texturePath;
        String animationLibrary;
        float scale = 1f;
        float yOffset;
        float facingDegrees; // extra yaw if the model's authored "forward" isn't +X
        String idleAnim;
        String walkAnim;
        String attackAnim;
        String fireSound;
        String dieSound;
        java.awt.Color colour; // null = the owning player's colour
        java.awt.Color tint;   // multiplied over the model's own texture

        public UnitVisual model(String assetPath) {
            this.modelPath = assetPath;
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
         * are taken: a library holds dozens, and a monster needs three.
         */
        public UnitVisual animationsFrom(String assetPath) {
            this.animationLibrary = assetPath;
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

        public UnitVisual attack(String animName) {
            this.attackAnim = animName;
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

    private final Map<String, UnitVisual> units = new HashMap<>();
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
}
