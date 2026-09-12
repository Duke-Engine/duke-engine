package uz.duke.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.scene.Node;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import uz.duke.core.math.Coord3D;

/**
 * What a skill looks like when it goes off: a ring across the floor, a mark left
 * standing on it, and a knock to the camera.
 *
 * <p><b>Drawing only, and late.</b> Nothing here is ever asked before anything is
 * decided — the simulation says a skill was cast, at what, and where, and this
 * hangs a picture on it. Every number it uses may be as random as it likes; none
 * of it is read back, and a client that drew nothing at all would play the same
 * game.
 *
 * <p><b>Why a ring and not more fire.</b> {@link ProjectileEffects} already draws
 * a burst of sparks and a flash of light, and that is what an arrow landing looks
 * like — but it is also what every other arrow landing looks like, and twelve
 * skills that all say "something happened here" in the same voice are twelve
 * skills the player cannot tell apart at a glance. A ring has three things sparks
 * do not: a SIZE, which is the only drawing in the game that shows how far the
 * skill actually reached; a SPEED, which is what separates a nova from a
 * whirlwind; and a colour that survives being small and far away. So the fire
 * stays where it was and this is added beside it, layered rather than instead.
 *
 * <p><b>The curve is the whole of the feel.</b> A ring that opens at a constant
 * speed reads as a circle being resized, because nothing in the world moves like
 * that; one that leaps and then slows reads as an impact. That is one line of
 * arithmetic — the elapsed fraction raised to a power out of the file — and it is
 * the difference between an effect and a widget. The fade is late and sharp for
 * the same reason: things stop being bright long after they stop growing.
 *
 * <p><b>Pooled and capped like everything else that burns.</b> Rings are built
 * once and hidden rather than detached, because a {@link GroundRing} owns a
 * vertex buffer and a pair of materials and a fight is thirty casts. Past the cap
 * the next skill draws no ring at all — it still has its fire, its light and its
 * damage, so what is lost is a decoration rather than a skill. Far-off ones are
 * refused before they are started, on the same judgement the trails are: a ring
 * two rooms away is a pixel and the budget it takes belongs to the one under the
 * player's nose.
 */
final class SkillEffects {

    /** How far off a ring stops being worth drawing. Shared with the trails. */
    private final float tooFar;

    /** How many rings may be open at once, and the pool they come from. */
    private final int cap;

    private final AssetManager assets;
    private final Node root;
    private final Visuals visuals;
    private final RangeLook look;

    /**
     * The fire, which is somebody else's and stays somebody else's.
     *
     * <p>A cast that only drew a ring would be a diagram. What makes it land is
     * the three of them at once -- sparks, a flash of light, and the ring opening
     * through both -- and the first two are exactly what {@link ProjectileEffects}
     * already does for an arrow arriving, pooled and capped and distance-culled.
     * Borrowing it is the difference between layering an effect and writing a
     * second particle system.
     */
    private final ProjectileEffects fire;

    private final List<GroundRing> spare = new ArrayList<>();
    private final List<Ring> open = new ArrayList<>();
    private int madeSoFar;

    /**
     * How much of the camera's knock is left, and what it was worth at the start.
     *
     * <p>One shake for the whole client rather than one per effect: two impacts in
     * the same tenth of a second are one event as far as a pair of eyes is
     * concerned, and adding their shakes together is how a pleasant thump becomes
     * an unplayable earthquake. A second knock therefore takes the LOUDER of the
     * two rather than the sum.
     */
    private float shakeLeft;
    private float shakeFor;
    private float shakePower;

    /** Deterministic-looking noise that never asks the clock. Client-side only. */
    private final java.util.Random wobble = new java.util.Random();

    SkillEffects(AssetManager assets, Node root, Visuals visuals, ProjectileEffects fire,
            RangeLook look, int cap, float tooFar) {
        this.assets = assets;
        this.root = root;
        this.visuals = visuals;
        this.fire = fire;
        this.look = look == null ? RangeLook.DEFAULT : look;
        this.cap = Math.max(0, cap);
        this.tooFar = tooFar;
    }

    /** One ring in progress: where it is, what it is drawn from, how far through. */
    private static final class Ring {
        private final GroundRing drawn;
        private final Coord3D at;
        private final float from;
        private final float to;
        private final float seconds;
        private final float ease;
        private final float edge;
        private final float wash;
        private final int colour;
        /** Zero for a ring that opens; a standing mark holds its size instead. */
        private final boolean standing;
        private float gone;

        Ring(GroundRing drawn, Coord3D at, float from, float to, float seconds, float ease,
                float edge, float wash, int colour, boolean standing) {
            this.drawn = drawn;
            this.at = at;
            this.from = from;
            this.to = to;
            this.seconds = Math.max(0.01f, seconds);
            this.ease = Math.max(0.05f, ease);
            this.edge = edge;
            this.wash = wash;
            this.colour = colour;
            this.standing = standing;
        }

        /** How far through it is, 0 to 1. */
        float through() {
            return Math.min(1f, gone / seconds);
        }

        /**
         * How wide it is now.
         *
         * <p>A standing mark does not grow: it is a place, and a place that
         * swells is a place that is lying about how big it is. What it does
         * instead is breathe, which the alpha below takes care of.
         */
        float radius() {
            if (standing) {
                return to;
            }
            return widthAt(from, to, through(), ease);
        }

        /**
         * How hard it is drawn now.
         *
         * <p>Full until most of the way through and then gone quickly, rather
         * than dimming evenly from the first frame. A ring that starts fading
         * immediately never looks bright; one that holds and then drops looks
         * like something that was there and then was not.
         *
         * <p>A standing mark pulses instead, because it has to go on saying
         * "still here, still coming" for a second and a half and a steady disc
         * stops being read after the first half of that.
         */
        float alphaNow() {
            float through = through();
            if (standing) {
                float breath = 0.65f + 0.35f
                        * (float) StrictMath.sin(gone * 9f);
                // And harder the nearer it is to landing, which is the warning
                // the player is actually being given.
                return breath * (0.55f + 0.45f * through);
            }
            return through < 0.6f ? 1f : 1f - (through - 0.6f) / 0.4f;
        }

        boolean done() {
            return gone >= seconds;
        }
    }

    /**
     * How wide a ring is, a given fraction of the way through.
     *
     * <p>Pulled out where it can be read and tested, because it is the one line
     * in the file that decides whether any of this looks like anything. The
     * fraction is raised to 1/ease before the width is taken from it, so an ease
     * above 1 spends most of the ring's size in the first part of its time --
     * which is what a thing that was struck does, and what nothing else does.
     *
     * <p>At ease = 1 it is a straight line, which is the shape to compare
     * against: a circle being resized at a constant speed, and the reason this
     * exists at all.
     */
    static float widthAt(float from, float to, float through, float ease) {
        float eased = (float) StrictMath.pow(Math.clamp(through, 0f, 1f),
                1f / Math.max(0.05f, ease));
        return from + (to - from) * eased;
    }

    /**
     * One place a cast asked to be drawn: what to draw, when it was cast, where,
     * and how wide.
     */
    record Cast(String look, int frame, Coord3D at, float radius) {
    }

    /**
     * Every cast in a status line that has not been drawn yet.
     *
     * <p>The game's own channel to its own client, read here rather than in the
     * app so that it can be tested without a window. The format is
     * {@code cast=<recipe>,<frame>,<x>,<y>,<radius>}, repeatable, mixed in among
     * whatever else the line carries.
     *
     * <p><b>One cast is not always one place.</b> A blink sends the spot he left
     * and the spot he arrived at, both stamped with the same frame — so the
     * filter is against what was drawn BEFORE this line, and every field of the
     * newest frame comes back. Filtering against a mark moved field by field
     * returned the first and swallowed the rest, which is half a blink.
     *
     * <p>A field that does not parse is dropped in silence. A missing ring is
     * the cheapest possible failure, and it may simply be another game's line:
     * this client serves three that have never heard of a skill.
     */
    static List<Cast> castsIn(String status, int alreadyDrawn) {
        if (status == null || status.isEmpty()) {
            return List.of();
        }
        var found = new ArrayList<Cast>();
        for (var field : status.split("[|]")) {
            if (!field.startsWith("cast=")) {
                continue;
            }
            var parts = field.substring("cast=".length()).split(",");
            if (parts.length < 5) {
                continue;
            }
            try {
                int frame = Integer.parseInt(parts[1].trim());
                if (frame <= alreadyDrawn) {
                    continue; // the same line is sent again until something changes
                }
                found.add(new Cast(parts[0].trim(), frame,
                        new Coord3D(Float.parseFloat(parts[2].trim()),
                                Float.parseFloat(parts[3].trim()), 0f),
                        Float.parseFloat(parts[4].trim())));
            } catch (NumberFormatException malformed) {
                // Somebody else's line, or a version that disagrees. No ring.
            }
        }
        return List.copyOf(found);
    }

    /**
     * A skill went off at a place: open its ring and knock the camera.
     *
     * <p>Given the recipe by name rather than by value, so a hero added next month
     * gets his effects by naming blocks in a file — the same bargain the
     * projectiles strike.
     *
     * @param at      where on the floor it happened
     * @param camera  where the eye is, so a far-off one can be refused
     * @param scale   what the skill's own radius is, or 0 to take the recipe's.
     *     A file should be able to say "as wide as the skill reaches" once rather
     *     than repeating every skill's radius in its effect block.
     */
    void cast(String recipeName, Coord3D at, com.jme3.math.Vector3f camera, float scale,
            BiFunction<Float, Float, Float> floorAt) {
        var recipe = visuals.effectNamed(recipeName);
        if (recipe == null || at == null || tooFarOff(at, camera)) {
            return;
        }
        // The sparks and the flash first, so the ring opens THROUGH them rather
        // than beside them. Its own pool and its own ceiling, so a cast over the
        // budget quietly loses its fire and keeps its ring, or the other way
        // round -- neither of which is a skill failing to go off.
        if (fire != null && recipe.has(Visuals.EffectVisual.IMPACT_BURST)) {
            fire.landed(recipeName, new com.jme3.math.Vector3f(at.x(), floorAt == null
                    ? 0f : floorAt.apply(at.x(), at.y()) + look.height(), at.y()), camera);
        }
        if (recipe.has(Visuals.EffectVisual.SHOCKWAVE) && recipe.waveTo > 0f) {
            float to = scale > 0f ? scale : recipe.waveTo;
            float from = scale > 0f && recipe.waveTo > 0f
                    ? scale * (recipe.waveFrom / recipe.waveTo) : recipe.waveFrom;
            start(new RingWanted(at, from, to, recipe.waveSeconds, recipe.waveEase,
                    recipe.waveEdge, recipe.waveWash, rgb(recipe.colour), false));
        }
        if (recipe.has(Visuals.EffectVisual.GROUND_MARK) && recipe.markSeconds > 0f) {
            float radius = recipe.markRadius > 0f ? recipe.markRadius
                    : scale > 0f ? scale : recipe.waveTo;
            start(new RingWanted(at, radius, radius, recipe.markSeconds, 1f,
                    recipe.waveEdge, recipe.waveWash, rgb(recipe.colour), true));
        }
        knock(recipe.shakeSeconds, recipe.shakePower);
    }

    private record RingWanted(Coord3D at, float from, float to, float seconds, float ease,
            float edge, float wash, int colour, boolean standing) {
    }

    private void start(RingWanted wanted) {
        if (wanted.to() <= 0.01f || open.size() >= cap) {
            return; // over the ceiling: the skill keeps its fire and loses its ring
        }
        open.add(new Ring(borrow(), wanted.at(), wanted.from(), wanted.to(), wanted.seconds(),
                wanted.ease(), wanted.edge(), wanted.wash(), wanted.colour(), wanted.standing()));
    }

    /**
     * Knock the camera, taking the louder of this and whatever is already going.
     *
     * <p>Never the sum. Two things landing together is one thump to a pair of
     * eyes, and adding them is what turns a meteor beside a nova into something
     * the player cannot look at.
     */
    void knock(float seconds, float power) {
        if (seconds <= 0f || power <= 0f) {
            return;
        }
        if (power * seconds <= shakePower * shakeLeft) {
            return;
        }
        shakeFor = seconds;
        shakeLeft = seconds;
        shakePower = power;
    }

    /**
     * How far the camera should be moved this frame, in world units.
     *
     * <p>An OFFSET rather than a new position, so whatever else is deciding where
     * the camera looks goes on deciding it. It dies away as a square, which is
     * what a struck thing does: most of the movement is in the first fifth of it
     * and the rest is a settling.
     */
    com.jme3.math.Vector3f shakeNow() {
        float strength = shakeStrength();
        if (strength <= 0f) {
            return com.jme3.math.Vector3f.ZERO;
        }
        return new com.jme3.math.Vector3f(
                (wobble.nextFloat() * 2f - 1f) * strength,
                (wobble.nextFloat() * 2f - 1f) * strength * 0.5f,
                (wobble.nextFloat() * 2f - 1f) * strength);
    }

    /**
     * How hard the camera is being knocked this instant, before the direction is
     * picked. The number the direction is multiplied by, and the only part of a
     * shake that can be asserted about -- the rest is noise, on purpose.
     */
    float shakeStrength() {
        if (shakeLeft <= 0f) {
            return 0f;
        }
        float left = shakeLeft / shakeFor;
        return shakePower * left * left;
    }

    /** Move every ring on, and let the shake die down. */
    void update(float tpf, BiFunction<Float, Float, Float> floorAt) {
        shakeLeft = Math.max(0f, shakeLeft - tpf);
        var going = open.iterator();
        while (going.hasNext()) {
            var ring = going.next();
            ring.gone += tpf;
            if (ring.done()) {
                ring.drawn.hide();
                spare.add(ring.drawn);
                going.remove();
                continue;
            }
            float alpha = Math.max(0f, ring.alphaNow());
            ring.drawn.show(ring.at, ring.radius(), look.height(), ring.colour,
                    ring.edge * alpha, ring.wash * alpha, floorAt);
        }
    }

    /** Take everything back: a world being rebuilt has nothing left burning in it. */
    void clear() {
        for (var ring : open) {
            ring.drawn.hide();
            spare.add(ring.drawn);
        }
        open.clear();
        shakeLeft = 0f;
    }

    /** How many rings the pool has ever had to build; the proof that it stops. */
    int madeSoFar() {
        return madeSoFar;
    }

    /** How many are open right now. */
    int openCount() {
        return open.size();
    }

    private GroundRing borrow() {
        if (!spare.isEmpty()) {
            return spare.remove(spare.size() - 1);
        }
        madeSoFar++;
        return new GroundRing(assets, root, look.bandWidth(), look.segments(), look.brightness());
    }

    private boolean tooFarOff(Coord3D at, com.jme3.math.Vector3f camera) {
        if (tooFar <= 0f || camera == null) {
            return false;
        }
        float dx = at.x() - camera.x;
        float dy = at.y() - camera.z;
        float dz = camera.y;
        return dx * dx + dy * dy + dz * dz > tooFar * tooFar;
    }

    private static int rgb(java.awt.Color colour) {
        return colour == null ? 0xFFFFFF : colour.getRGB() & 0xFFFFFF;
    }
}
