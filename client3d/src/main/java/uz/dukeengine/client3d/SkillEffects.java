package uz.dukeengine.client3d;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.core.math.Coord3D;

/**
 * What a skill does to the camera when it goes off, and which casts a status line is asking to
 * have drawn. The drawing itself is {@link LayeredEffects}: this is the knock, because there is
 * one camera and so there has to be one place that decides which knock wins.
 *
 * <p><b>Drawing only, and late.</b> Nothing here is ever asked before anything is
 * decided — the simulation says a skill was cast, at what, and where, and this
 * hangs a knock on it. A client that drew nothing at all would play the same game.
 */
final class SkillEffects {

    /** How far off a cast stops being worth knocking the camera for. */
    private final float tooFar;

    private final Visuals visuals;

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

    SkillEffects(Visuals visuals, float tooFar) {
        this.visuals = visuals;
        this.tooFar = tooFar;
    }

    /** The owner on a cast that belongs to the floor rather than to a creature. */
    static final int NOBODY = 0;

    /**
     * One place a cast asked to be drawn: what to draw, when it was cast, where,
     * how wide, and whose it is.
     */
    record Cast(String look, int frame, Coord3D at, float radius, int on, int by) {
    }

    /**
     * Every cast in a status line that has not been drawn yet.
     *
     * <p>The game's own channel to its own client, read here rather than in the
     * app so that it can be tested without a window. The format is
     * {@code cast=<recipe>,<frame>,<x>,<y>,<radius>,<whose>,<by>}, repeatable,
     * mixed in among whatever else the line carries. {@code whose} is the creature
     * the mark belongs to, or {@link #NOBODY} for one that belongs to the floor;
     * {@code by} is the creature that cast it, which is a different question and
     * has a different answer for every skill aimed away from its caster. Either
     * may be missing from an older line, and then it reads as nobody.
     *
     * <p><b>One cast is not always one place.</b> A blink sends the spot he left
     * and the spot he arrived at, both stamped with the same frame — so the
     * filter is against what was drawn BEFORE this line, and every field of the
     * newest frame comes back. Filtering against a mark moved field by field
     * returned the first and swallowed the rest, which is half a blink.
     *
     * <p>A field that does not parse is dropped in silence. A missing effect is
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
                        Float.parseFloat(parts[4].trim()),
                        parts.length > 5 ? Integer.parseInt(parts[5].trim()) : NOBODY,
                        parts.length > 6 ? Integer.parseInt(parts[6].trim()) : NOBODY));
            } catch (NumberFormatException malformed) {
                // Somebody else's line, or a version that disagrees. Nothing drawn.
            }
        }
        return List.copyOf(found);
    }

    /**
     * A skill went off at a place: knock the camera as hard as its recipe says.
     *
     * @param at     where on the floor it happened
     * @param camera where the eye is, so a far-off one can be refused
     */
    void cast(String recipeName, Coord3D at, com.jme3.math.Vector3f camera) {
        var recipe = visuals.effectNamed(recipeName);
        if (recipe == null || at == null || tooFarOff(at, camera)) {
            return;
        }
        knock(recipe.shakeSeconds, recipe.shakePower);
    }

    /**
     * Knock the camera, taking the louder of this and whatever is already going.
     *
     * <p>Never the sum. Two things landing together is one thump to a pair of
     * eyes, and adding them is what turns a meteor beside a nova into something
     * the player cannot look at.
     */
    void knock(float seconds, float power) {
        power *= visuals == null ? 1f : visuals.getShakeScale();
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

    /** Let the shake die down. */
    void update(float tpf) {
        shakeLeft = Math.max(0f, shakeLeft - tpf);
    }

    /** A world being rebuilt has nothing left shaking in it. */
    void clear() {
        shakeLeft = 0f;
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
}
