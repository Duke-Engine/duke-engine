package uz.dukeengine.client3d;

import java.util.HashMap;
import uz.dukeengine.core.content.AnimationSet;
import uz.dukeengine.core.thing.Drawn;
import java.util.LinkedHashMap;
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
        /**
         * One thing hung on one of the unit's own bones.
         *
         * <p>A list rather than a field apiece, because a hand is not the only
         * place a character carries something and a character is not limited to
         * one hand. A knight is a sword AND a shield; an archer is a bow and the
         * arrows to go in it. Written as one field each, the second of every pair
         * was simply impossible.
         *
         * <p>Mutable and package-private on purpose: it is filled in by the
         * fluent calls below, where {@code holds} starts a new one and everything
         * after it describes the one just started.
         */
        static final class Carried {
            String path;
            String bone;
            float scale = 1f;
            float pitch;
            float yaw;
            float roll;
            float x;
            float y;
            float z;
        }

        final java.util.List<Carried> carried = new java.util.ArrayList<>();
        /** The name of the flight effect this unit wears, or null for a plain one. */
        String effect;
        /** How far forward of its middle the effect sits; see effectAt. */
        float effectForward;
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
        /** Clips it needs for something other than standing, walking and dying. */
        final java.util.List<String> otherAnims = new java.util.ArrayList<>();
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
            var one = new Carried();
            one.path = assetPath;
            one.bone = boneName;
            one.scale = scale;
            carried.add(one);
            return this;
        }

        /** The one being described, so heldTurn and heldAt settle the last holds. */
        private Carried last() {
            if (carried.isEmpty()) {
                carried.add(new Carried()); // turned before it was given anything
            }
            return carried.get(carried.size() - 1);
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
            var one = last();
            one.pitch = pitchDegrees;
            one.yaw = yawDegrees;
            one.roll = rollDegrees;
            return this;
        }

        /**
         * How far to shift what he carries off the bone it hangs on.
         *
         * <p>The same kind of number as {@link #heldTurn}: a fact about the art,
         * measured once and written down. The bone puts a weapon in the hand and
         * a hand is where a weapon goes, so most things want none of this — but
         * this rig has exactly two attachment points, both of them hands, and a
         * quiver goes on the BACK. Hung on the chest bone with no shift it sits
         * inside the man; shifted back and up it sits over his shoulder.
         */
        public UnitVisual heldAt(float x, float y, float z) {
            var one = last();
            one.x = x;
            one.y = y;
            one.z = z;
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

        /**
         * One more clip this creature wants loaded, beyond the five it is drawn
         * standing, walking, striking, flinching and falling with.
         *
         * <p>A clip has to be copied onto the model before anything can play it,
         * and what gets copied is what was asked for by name -- so a gesture
         * nobody has listed is simply not there when the moment comes. This is
         * where a game lists the ones its own rules will call for: a spell it
         * casts two-handed, a bow it shoulders, a door it opens.
         */
        public UnitVisual alsoAnimation(String animName) {
            if (animName != null && !animName.isBlank() && !otherAnims.contains(animName)) {
                otherAnims.add(animName);
            }
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
    /**
     * A template drawn as its own block says: its model, size, tint and facing, the clips it names, and the
     * library files of the animation set it links.
     *
     * <p>Here so that no game writes it again. Every game on this engine used to end up with the same twenty
     * lines handing its own record's look fields to this class one at a time — see {@link Drawn}, which is the
     * other half of the same seam. A clip the template leaves out falls back to the set's; a template with no
     * model at all is left to its {@code Geometry}, which is the client's own fallback.
     *
     * @param set the animation set {@link Drawn#animations()} names, or null when it names none
     */
    public Visuals draw(Drawn template, AnimationSet set) {
        if (!template.hasModel()) {
            return this;
        }
        return unit(template.name(), drawn -> {
            drawn.model(template.model()).scale(template.modelScale())
                    .tint(new java.awt.Color(template.tint())).facing(template.facing());
            if (set != null) {
                for (var library : set.libraries()) {
                    drawn.animationsFrom(library);
                }
            }
            drawn.idle(clipOf(template.idle(), set == null ? null : set.idle()));
            drawn.walk(clipOf(template.walk(), set == null ? null : set.walk()));
            drawn.attack(clipOf(template.attack(), set == null ? null : set.attack()));
            drawn.die(clipOf(template.death(), set == null ? null : set.death()));
            if (template.effect() != null && !template.effect().isBlank()) {
                drawn.effect(template.effect());
            }
        });
    }

    /** What the template said, or what the set it links says for that moment. */
    private static String clipOf(String own, String fromSet) {
        return own == null || own.isBlank() ? fromSet : own;
    }

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
     * <p>The client owns the <em>types</em> of layer — a trail, an aura, a burst on
     * landing — and the game owns every number in them. That division is
     * the same one the rest of this class keeps, and it is what lets a new burning
     * thing be a block of settings rather than a class.
     */
    public Visuals effect(String name, Consumer<EffectVisual> config) {
        config.accept(effects.computeIfAbsent(name, n -> new EffectVisual()));
        return this;
    }

    private final Map<String, Float> effectSeconds = new HashMap<>();
    private final Map<String, Float> effectReach = new HashMap<>();

    /**
     * How long the thing a recipe draws actually lasts, for a layer that says 0.
     *
     * <p>Set by the game from the skill rather than written into the effect, so
     * that a shield that lasts four seconds is drawn for four seconds and the two
     * cannot be tuned apart. Before this, the knight's guard said 4.0 in its effect
     * block and 120 frames in its skill block, and nothing connected them.
     */
    public Visuals effectSeconds(String recipeName, float seconds) {
        if (recipeName != null && seconds > 0f) {
            effectSeconds.put(recipeName, seconds);
        }
        return this;
    }

    /** How long the named recipe's skill lasts, or 0 if nothing said. */
    public float getEffectSeconds(String recipeName) {
        var seconds = recipeName == null ? null : effectSeconds.get(recipeName);
        return seconds == null ? 0f : seconds;
    }

    /**
     * How far the thing a recipe draws reaches, for a layer measured in reach.
     *
     * <p>Set by the game from the skill, for the same reason as the seconds: the
     * meteor's warning has to be exactly as wide as the blast, and a number written
     * twice is two numbers the moment somebody tunes one of them.
     */
    public Visuals effectReach(String recipeName, float radius) {
        if (recipeName != null && radius > 0f) {
            effectReach.put(recipeName, radius);
        }
        return this;
    }

    /** How far the named recipe's skill reaches, or 0 if nothing said. */
    public float getEffectReach(String recipeName) {
        var radius = recipeName == null ? null : effectReach.get(recipeName);
        return radius == null ? 0f : radius;
    }

    private int particleBudget;

    /**
     * How many particles may be burning at once, across every effect.
     *
     * <p>A ceiling rather than a target, like the lights: past it a new layer is
     * drawn thinner, and past that it is not drawn. 0 draws no layers at all.
     */
    public Visuals particleBudget(int particles) {
        this.particleBudget = Math.max(0, particles);
        return this;
    }

    public int getParticleBudget() {
        return particleBudget;
    }

    /** The recipe under that name, or {@code null} when the game named none. */
    public EffectVisual effectNamed(String name) {
        return name == null ? null : effects.get(name);
    }

    /**
     * What the client may spend on things in flight.
     *
     * <p>Ceilings rather than targets, and the reason they exist at all is that a
     * fight is not one arrow. Fifty in the air, each with a light of its own, is
     * fifty dynamic lights — and dynamic lights are the expensive kind. Past the
     * ceiling a shot simply flies darker, but the same shot going to the same place.
     *
     * @param lights    how many may burn at once. The terrain shader reads four;
     *     more than that still light the creatures, which is where jME's own
     *     lighting is doing the work
     * @param distance  how far from the camera a thing is still worth the trouble;
     *     zero for no limit
     */
    public record EffectBudget(int lights, float distance) {
    }

    private EffectBudget budget = new EffectBudget(4, 0f);

    public Visuals effectBudget(int lights, float distance) {
        this.budget = new EffectBudget(lights, distance);
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
     * What a thing looks like: the layers it is drawn from, the pieces of its wearer it
     * lights, and how hard it knocks the camera.
     *
     * <p>Every part may be left out, so a recipe that names only a knock is a recipe —
     * and a game that names no recipe at all draws exactly what it drew before any of
     * this existed.
     */
    public static final class EffectVisual {

        /** Words that name the pieces {@link #glow} lights; see there. */
        final java.util.List<String> glowParts = new java.util.ArrayList<>();
        java.awt.Color glowColour = java.awt.Color.WHITE;
        float shakeSeconds;
        float shakePower;
        /** The layers it is drawn from, in the order the file named them — see {@link EffectLayer}. */
        final java.util.List<EffectLayer> layers = new java.util.ArrayList<>();

        private EffectVisual() {
        }

        /** One more layer, drawn over the ones before it — by {@link LayeredEffects}. */
        public EffectVisual layer(EffectLayer layer) {
            if (layer != null) {
                layers.add(layer);
            }
            return this;
        }

        public java.util.List<EffectLayer> getLayers() {
            return java.util.List.copyOf(layers);
        }

        boolean hasLayers() {
            return !layers.isEmpty();
        }

        /**
         * Pieces of the wearer's model to light from inside — a skeleton's eye sockets, a
         * rune on a door, the coals in a brazier — by a word in their names.
         *
         * <p>The one thing an effect draws that is not a layer, because it is a material on a
         * model rather than something let out into the air — and the cheapest by a distance:
         * one material and no light, which is what makes it affordable on every creature in a
         * room. A word rather than the whole name, because a kit names the same piece
         * differently on every creature it ships — {@code Skeleton_Warrior_Eyes},
         * {@code Skeleton_Mage_Eyes} — and a game should be able to say "the eyes" once.
         */
        public EffectVisual glow(java.util.List<String> parts, java.awt.Color colour) {
            glowParts.clear();
            glowParts.addAll(parts);
            glowColour = colour == null ? java.awt.Color.WHITE : colour;
            return this;
        }

        public java.util.List<String> getGlowParts() {
            return java.util.List.copyOf(glowParts);
        }

        /**
         * How hard the camera is knocked, and for how long.
         *
         * <p>Small numbers. A shake is felt rather than seen, and one that can be
         * SEEN is one the player will ask you to turn off -- so this is a couple of
         * units for a couple of tenths, and zero for every skill that is not
         * supposed to land like a weight.
         */
        public EffectVisual shake(float seconds, float power) {
            this.shakeSeconds = seconds;
            this.shakePower = power;
            return this;
        }

        public float getShakePower() {
            return shakePower;
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

    private Sunlight sunlight = Sunlight.DEFAULT;

    /**
     * Where the light comes from and how much of it there is — see
     * {@link Sunlight}.
     *
     * <p>Not a detail of the art: how far off vertical the sun stands is what
     * decides whether the scene has any relief in it, because a sun straight
     * overhead meets a floor and the top of a wall at the same angle and shades
     * them the same. A game that never asks is lit exactly as the client always
     * lit it.
     */
    public Visuals sunlight(Sunlight sunlight) {
        this.sunlight = sunlight == null ? Sunlight.DEFAULT : sunlight;
        return this;
    }

    public Sunlight getSunlight() {
        return sunlight;
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
    public Visuals iconLook(IconLook look) {
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
    public Visuals statLook(StatLook look) {
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
    public Visuals panelLook(PanelLook look) {
        this.panelLook = look == null ? PanelLook.DEFAULTS : look;
        return this;
    }

    public PanelLook getPanelLook() {
        return panelLook;
    }

    /**
     * What the caster is seen doing, and for how long.
     *
     * <p>{@code seconds} is 0 for the clip's own length, or what it should be
     * stretched or hurried to take. A gesture that ends as the spell lands reads
     * as having caused it; the same gesture running a second and a half past
     * reads as somebody waving after the fact.
     */
    public record CastAnim(String clip, float seconds) {
    }

    private final Map<String, CastAnim> castAnims = new HashMap<>();

    /**
     * The gesture that goes with an effect recipe.
     *
     * <p>Keyed by the recipe rather than by the skill, because the recipe is what
     * the client is told about when something is cast -- and because two skills
     * that look the same should move the same. A recipe nobody registers is cast
     * exactly as it always was, which is with an effect and a caster who does not
     * move.
     */
    public Visuals castAnim(String look, String clipName, float seconds) {
        if (look != null && clipName != null && !clipName.isBlank()) {
            castAnims.put(look, new CastAnim(clipName, seconds));
        }
        return this;
    }

    public CastAnim getCastAnim(String look) {
        return look == null ? null : castAnims.get(look);
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

    private OrderMark orderMark = OrderMark.DEFAULTS;

    /**
     * How the flash that answers a click should look and move — see
     * {@link OrderMark}.
     *
     * <p>A game that never asks gets the client's own, rather than nothing: an
     * order that leaves no mark reads as a click that missed, so there is no
     * sensible "off" to default to.
     */
    public Visuals orderMark(OrderMark orderMark) {
        this.orderMark = orderMark == null ? OrderMark.DEFAULTS : orderMark;
        return this;
    }

    public OrderMark getOrderMark() {
        return orderMark;
    }

    private UnitBarLook unitBars = UnitBarLook.NONE;

    /**
     * What the bar over a creature's head is made of — see {@link UnitBarLook}.
     *
     * <p>Naming none means none is drawn, which is the right answer for the three
     * games that are not this one: a bar divided into lots the client invented
     * would be marks that mean nothing.
     */
    public Visuals unitBars(UnitBarLook look) {
        this.unitBars = look == null ? UnitBarLook.NONE : look;
        return this;
    }

    public UnitBarLook getUnitBars() {
        return unitBars;
    }

    private RangeLook rangeLook = RangeLook.DEFAULT;
    private final Map<Character, SkillRange> skillRanges = new LinkedHashMap<>();

    /**
     * How far each of the game's keys reaches, and what the player is aiming when
     * he presses it — see {@link SkillRange}.
     *
     * <p>The client keeps the press-then-click and knows nothing about skills, and
     * that was enough while all it had to do was forward the click. It is not
     * enough to draw one: "how far does this go" is a number, and the number is
     * the game's. A key the game says nothing about simply gets no ring, which is
     * what every game on this client had.
     */
    public Visuals skillRange(SkillRange range) {
        if (range != null) {
            skillRanges.put(range.key(), range);
        }
        return this;
    }

    public SkillRange getSkillRange(char key) {
        return skillRanges.get(key);
    }

    /** How a reach is drawn — one look for every skill in the game. */
    public Visuals rangeLook(RangeLook rangeLook) {
        this.rangeLook = rangeLook == null ? RangeLook.DEFAULT : rangeLook;
        return this;
    }

    public RangeLook getRangeLook() {
        return rangeLook;
    }

    private HitNumbers hitNumbers = HitNumbers.DEFAULTS;

    /**
     * How the numbers that come off a creature as it is hurt or healed should look
     * — see {@link HitNumbers}.
     *
     * <p>The client knows how to read a health bar's movement and throw a number
     * off it; how long it should stay, how far it should drift and what colour a
     * blow is are the game's, like the fog and the pointer.
     */
    public Visuals hitNumbers(HitNumbers hitNumbers) {
        this.hitNumbers = hitNumbers == null ? HitNumbers.DEFAULTS : hitNumbers;
        return this;
    }

    public HitNumbers getHitNumbers() {
        return hitNumbers;
    }

    /**
     * How a creature that is hit flashes -- see {@link HitFlash}.
     *
     * @param colour   what it flashes towards, 0xRRGGBB
     * @param seconds  the whole of it: there at once, and fading as a square
     * @param strength how far towards the colour, 0 to 1; 0 is no flash at all
     */
    public record HitFlashLook(int colour, float seconds, float strength) {
        public static final HitFlashLook NONE = new HitFlashLook(0xFFFFFF, 0f, 0f);
    }

    private HitFlashLook hitFlash = HitFlashLook.NONE;

    public Visuals hitFlash(HitFlashLook look) {
        this.hitFlash = look == null ? HitFlashLook.NONE : look;
        return this;
    }

    public HitFlashLook getHitFlash() {
        return hitFlash;
    }

    private float shakeScale = 1f;

    /**
     * How hard every knock of the camera is against what its effect asked for: 1 as
     * written, 0 for a camera that never moves.
     *
     * <p>One number rather than a ShakePower zeroed on every effect, because whether
     * the screen moves is a question about the player, not about any one skill.
     */
    public Visuals shakeScale(float scale) {
        this.shakeScale = Math.max(0f, scale);
        return this;
    }

    public float getShakeScale() {
        return shakeScale;
    }

    private float strikeWithin = 30f;

    /**
     * How near to where a shot was last drawn a blow must land, that same frame, for
     * the shot to have struck -- see {@link Landing#burstAt}.
     */
    public Visuals strikeWithin(float distance) {
        this.strikeWithin = Math.max(0f, distance);
        return this;
    }

    public float getStrikeWithin() {
        return strikeWithin;
    }

    // ---- the run's own moments ----

    /** A level gained. */
    public static final String LEVEL_UP = "LevelUp";
    /** The floor's boss, down. */
    public static final String BOSS_DOWN = "BossDown";
    /** The hero arriving on a floor: a run starting, or a stair taken down. */
    public static final String ARRIVED = "Arrived";

    /** Every moment a game may give a look to -- see {@link #moment}. */
    public static final java.util.Set<String> MOMENTS = java.util.Set.of(LEVEL_UP, BOSS_DOWN,
            ARRIVED);

    /**
     * What one of the run's moments looks like.
     *
     * @param effect the recipe it plays on the hero, by name
     * @param scale  how much bigger than the recipe is written; 1 as written
     */
    public record MomentLook(String effect, float scale) {
    }

    private final Map<String, MomentLook> moments = new java.util.HashMap<>();

    /**
     * Give one of the run's moments a look.
     *
     * <p>The client notices the moment -- a level, the boss down, a new floor -- and
     * the game says what it looks like, which is the division everything else here
     * keeps too. A moment given no look is not drawn.
     */
    public Visuals moment(String name, String effect, float scale) {
        if (name != null && effect != null && !effect.isBlank() && scale > 0f) {
            moments.put(name, new MomentLook(effect, scale));
        }
        return this;
    }

    /** The look given to a moment, or {@code null} for one given none. */
    public MomentLook getMoment(String name) {
        return name == null ? null : moments.get(name);
    }

    // ---- the portrait ----

    private final Map<String, PortraitLook> portraits = new LinkedHashMap<>();
    private PortraitLook everyPortrait;
    private int portraitFps = 24;

    /**
     * Draw this template <em>alive</em> in the hero panel's frame, rather than as
     * the silhouette that stands there otherwise — see {@link PortraitLook}.
     *
     * <p>Named by the creature's own template, and so repeatable. No art is named
     * here — the model, its scale and the libraries its clips come from are bound
     * once in {@link #unit}, under this same name, and the portrait takes them
     * from there.
     *
     * <p>An override rather than the way in: {@link #portraits} already gives a
     * face to everything the player can select. This is for the creature that
     * wants a different one — a hero who holds his bow ready rather than standing
     * about, and has a flourish for a new level.
     */
    public Visuals portrait(String templateName, PortraitLook look) {
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
    public Visuals portraits(PortraitLook look) {
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
    public Visuals portraitFps(int framesPerSecond) {
        this.portraitFps = Math.clamp(framesPerSecond, 1, 60);
        return this;
    }

    public int getPortraitFps() {
        return portraitFps;
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

    private MenuStyle menuStyle = MenuStyle.DEFAULTS;

    /**
     * How this game's menus are lettered — see { MenuStyle}.
     *
     * <p>The same bargain as everywhere else: the client knows how to draw a
     * menu and the game says what it should look like.
     */
    public Visuals menuStyle(MenuStyle style) {
        this.menuStyle = style == null ? MenuStyle.DEFAULTS : style;
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
