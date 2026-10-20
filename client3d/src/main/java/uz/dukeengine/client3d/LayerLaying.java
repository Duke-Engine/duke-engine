package uz.dukeengine.client3d;

import com.jme3.bounding.BoundingBox;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import java.util.Random;

/**
 * Writes a {@link LayerPlay}'s particles: all of them at the start, or the ones a fed layer is
 * owed each frame. Every number here is drawn from the dice, which only the picture ever sees.
 */
final class LayerLaying {

    private final LayerSpots spots;
    private final Random dice;
    /** How many particles have been made, for a test. */
    private int bornSoFar;

    LayerLaying(LayerSpots spots, Random dice) {
        this.spots = spots;
        this.dice = dice;
    }

    int bornSoFar() {
        return bornSoFar;
    }

    /** Write the particles a layer starts with: all of them, or none for one fed over time. */
    void lay(LayerPlay going, LayeredEffects.Moment moment) {
        var layer = going.layer;
        var drawn = going.drawn;
        if (EffectLayer.PILLAR.equals(layer.type())) {
            layPillar(going);
            drawn.reach(going.origin(), going.reach());
            drawn.upload();
            return;
        }
        if (layer.continuous() && !layer.pathTrail()) {
            drawn.reach(going.origin(), going.reach());
            drawn.upload();
            return; // it lets them out as it goes
        }
        int count = drawn.capacity;
        boolean path = EffectLayer.PATH.equals(layer.at()) && moment.hasPath();
        for (int i = 0; i < count; i++) {
            float along = count <= 1 ? 0f : i / (float) (count - 1);
            Vector3f born;
            float birth;
            if (path) {
                var from = spots.floored(moment.from());
                var to = spots.floored(moment.to());
                born = new Vector3f(FastMath.interpolateLinear(along, from.x, to.x), 0f,
                        FastMath.interpolateLinear(along, from.z, to.z));
                born.y = spots.floorUnder(born.x, born.z);
                // Laid in the order he ran it, so the dust rises behind him.
                birth = going.span * along;
            } else {
                born = going.origin();
                birth = 0f;
            }
            float life = stays(layer) && !layer.continuous()
                    ? going.span : between(layer.lifeMin(), layer.lifeMax());
            birthOne(going, i, born, birth, life);
        }
        if (EffectLayer.BEAM.equals(layer.type()) && moment.hasPath()) {
            layBeam(going, moment);
        }
        drawn.reach(going.origin(), going.reach()
                + (moment.hasPath() ? moment.from().distance(moment.to()) : 0f));
        drawn.upload();
    }

    /** A layer that lets particles out over time gets the ones it is owed. */
    void feed(LayerPlay one, float local, float tpf) {
        if (!one.emitting || one.drawn == null) {
            return;
        }
        // At its span, on somebody or on a spot alike: its last live out their lives, and none is made after.
        if (one.rides == null && local >= one.span) {
            one.stopFeeding();
            return;
        }
        one.owed += one.layer.rate() * tpf;
        int born = 0;
        var heading = one.anchor.subtract(one.lastAnchor);
        heading.y = 0f;
        if (heading.lengthSquared() > 0.0001f) {
            heading.normalizeLocal();
            one.facingX = heading.x;
            one.facingZ = heading.z;
        }
        var at = one.raised(local);
        var centre = one.attached ? new Vector3f() : at;
        while (one.owed >= 1f && born < one.drawn.capacity) {
            one.owed -= 1f;
            float life = between(one.layer.lifeMin(), one.layer.lifeMax());
            birthOne(one, one.next++, centre, local, life);
            born++;
        }
        if (born > 0) {
            if (!one.attached) {
                // Round everywhere it has let particles out rather than round where
                // it is now: a trail is BEHIND what lays it, and a box that moved
                // with the fireball would have its own smoke culled.
                float reach = one.reach();
                var here = new BoundingBox(at.clone(), reach, reach, reach);
                one.bound = one.bound == null ? here : (BoundingBox) one.bound.merge(here);
                one.drawn.reach(one.bound);
            }
            one.drawn.upload();
        }
    }

    /** Whether a layer stays its whole span, rather than living out one particle's life. */
    private static boolean stays(EffectLayer layer) {
        var type = layer.type();
        return EffectLayer.AURA.equals(type) || EffectLayer.MARK.equals(type);
    }

    /**
     * A pillar stands on the floor under where it happened -- as many columns as the
     * layer asks for and no more, because a column is light, and two drawn over one
     * another are twice as bright as the file said.
     */
    private void layPillar(LayerPlay going) {
        var layer = going.layer;
        float way = EffectLayer.DOWN.equals(layer.direction()) ? -1f : 1f;
        var foot = going.origin();
        int columns = Math.min(layer.count(), going.drawn.capacity);
        for (int i = 0; i < columns; i++) {
            float life = between(layer.lifeMin(), layer.lifeMax());
            going.drawn.put(i, foot.x, foot.y, foot.z, 0f, 0f, 0f, 0f, life, dice.nextFloat(),
                    jitter(layer.sizeJitter()) * going.unit, 0f, way, 0f,
                    layer.height() * going.unit);
        }
    }

    /** A beam is laid once along the whole of what it joins. */
    private void layBeam(LayerPlay going, LayeredEffects.Moment moment) {
        var from = spots.floored(moment.from());
        var to = spots.floored(moment.to());
        var along = to.subtract(from);
        float length = along.length();
        if (length < 0.001f) {
            return;
        }
        along.divideLocal(length);
        var middle = from.add(to).multLocal(0.5f);
        var layer = going.layer;
        for (int i = 0; i < going.drawn.capacity; i++) {
            float life = between(layer.lifeMin(), layer.lifeMax());
            going.drawn.put(i, middle.x, middle.y + layer.height(), middle.z, 0f, 0f, 0f,
                    0f, life, dice.nextFloat(), jitter(layer.sizeJitter()) * going.unit,
                    along.x, along.y, along.z, length);
        }
    }

    /** One particle: where in the disc, which way, how fast, how long, how big, which way up. */
    private void birthOne(LayerPlay going, int index, Vector3f centre, float birth, float life) {
        bornSoFar++;
        var layer = going.layer;
        double angle = dice.nextDouble() * Math.PI * 2.0;
        float distance = layer.radius() * going.unit * (float) Math.sqrt(dice.nextDouble());
        float ox = centre.x + (float) Math.cos(angle) * distance;
        float oz = centre.z + (float) Math.sin(angle) * distance;
        float oy;
        if (going.attached) {
            oy = layer.height();
        } else if (layer.lying() || going.rides == null) {
            // On the floor under its own spot rather than the middle's, so a ring
            // laid across the foot of a stair follows the step.
            oy = spots.floorUnder(ox, oz) + layer.height();
        } else {
            oy = centre.y + layer.height();
        }

        var way = direction(layer, (float) Math.cos(angle), (float) Math.sin(angle), going);
        float speed = between(layer.speedMin(), layer.speedMax());
        float turn = (float) Math.toRadians(layer.turn()
                + (dice.nextFloat() * 2f - 1f) * 0.5f * layer.turnJitter());
        if (EffectLayer.ARC.equals(layer.type())) {
            // Top of the texture pointing the way the blow went.
            turn += (float) Math.atan2(-going.facingX, going.facingZ);
        }
        going.drawn.put(index, ox, oy, oz, way.x * speed, way.y * speed, way.z * speed,
                birth, life, dice.nextFloat(), jitter(layer.sizeJitter()) * going.unit,
                0f, 0f, 0f, turn);
    }

    /** Which way a particle leaves, before it is given a speed. */
    private Vector3f direction(EffectLayer layer, float outX, float outZ, LayerPlay going) {
        Vector3f base = switch (layer.direction()) {
            case EffectLayer.UP -> new Vector3f(0f, 1f, 0f);
            case EffectLayer.DOWN -> new Vector3f(0f, -1f, 0f);
            case EffectLayer.OUT -> new Vector3f(outX, 0f, outZ);
            case EffectLayer.FORWARD -> new Vector3f(going.facingX, 0f, going.facingZ);
            case EffectLayer.BACK -> new Vector3f(-going.facingX, 0f, -going.facingZ);
            case EffectLayer.NONE -> new Vector3f();
            default -> randomUnit();
        };
        if (EffectLayer.NONE.equals(layer.direction()) || EffectLayer.ALL.equals(layer.direction())) {
            return base;
        }
        if (EffectLayer.OUT.equals(layer.direction())) {
            // Tilted up off the floor by up to the spread, so a ring of sparks is a
            // shallow dome rather than a flat disc nobody can see from above.
            float tilt = (float) Math.toRadians(dice.nextFloat() * layer.spread());
            return base.multLocal(FastMath.cos(tilt)).addLocal(0f, FastMath.sin(tilt), 0f)
                    .normalizeLocal();
        }
        return withinCone(base.normalizeLocal(), layer.spread());
    }

    private Vector3f withinCone(Vector3f axis, float spreadDegrees) {
        if (spreadDegrees <= 0f) {
            return axis;
        }
        float cosLimit = FastMath.cos((float) Math.toRadians(Math.min(180f, spreadDegrees)));
        float cosTheta = 1f - dice.nextFloat() * (1f - cosLimit);
        float sinTheta = FastMath.sqrt(Math.max(0f, 1f - cosTheta * cosTheta));
        float phi = dice.nextFloat() * FastMath.TWO_PI;
        var helper = Math.abs(axis.y) < 0.9f ? Vector3f.UNIT_Y : Vector3f.UNIT_X;
        var side = axis.cross(helper).normalizeLocal();
        var other = axis.cross(side).normalizeLocal();
        return axis.mult(cosTheta)
                .addLocal(side.multLocal(FastMath.cos(phi) * sinTheta))
                .addLocal(other.multLocal(FastMath.sin(phi) * sinTheta))
                .normalizeLocal();
    }

    private Vector3f randomUnit() {
        float z = dice.nextFloat() * 2f - 1f;
        float phi = dice.nextFloat() * FastMath.TWO_PI;
        float r = FastMath.sqrt(Math.max(0f, 1f - z * z));
        return new Vector3f(r * FastMath.cos(phi), z, r * FastMath.sin(phi));
    }

    private float between(float low, float high) {
        return low + (high - low) * dice.nextFloat();
    }

    private float jitter(float amount) {
        return Math.max(0.05f, 1f + (dice.nextFloat() * 2f - 1f) * amount);
    }
}
