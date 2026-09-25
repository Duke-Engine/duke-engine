package uz.dukeengine.core.thing;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import uz.dukeengine.core.content.ModelBones;
import uz.dukeengine.core.math.Coord3D;

/**
 * Where a named bone of a thing's model stands, for the simulation — the reference fires from, docks at and lets
 * units out at named bones: a War Factory's DOCKACTION, a barracks' EXITSTART, an A-10's WeaponA01. Read from the model
 * file its {@link Drawn} template names ({@link ModelBones}), scaled and turned as the client draws it — its
 * {@code ModelScale}, its {@code Facing} — so the point is the one drawn, and the same on every machine.
 */
public final class Bones {

    private Bones() {
    }

    /**
     * Where {@code bone} stands in {@code thing}'s own frame — x its forward, y the ground's other way, z up — in its
     * model's default pose; null where its template names no model, or the model no such bone.
     */
    public static Coord3D inFrame(GameObject thing, String bone) {
        return inFrame(thing, bone, Set.of());
    }

    /** The same, of the model it is drawn with while {@code words} hold — the template's {@code Models} for them. */
    public static Coord3D inFrame(GameObject thing, String bone, Set<String> words) {
        if (!(thing.getTemplate() instanceof Drawn drawn) || !drawn.hasModel()) {
            return null;
        }
        return inFrameOf(drawn, ModelBones.of(modelFor(drawn, words), bone));
    }

    /**
     * Where {@code bone} stands in the world on {@code model} — a whole path, the thing's own model or one of its draw
     * layers', drawn at the thing with its template's scale and facing — at the last frame of the model's clip {@code
     * clip} (its default pose for null), and turned with the node {@code turret} by {@code turretTurn} radians where it
     * hangs under it: an orbital cannon's antenna its clip raised, a gun's launch bone on its turret. Names matched
     * without case. Null where the template draws no model, or the file has no such bone.
     */
    public static Coord3D inWorld(GameObject thing, String model, String bone, String clip, String turret,
            float turretTurn) {
        if (!(thing.getTemplate() instanceof Drawn drawn)) {
            return null;
        }
        return worldOf(thing, inFrameOf(drawn, ModelBones.of(model, bone, clip, turret, turretTurn)));
    }

    /** A point of a model file drawn as {@code drawn} draws it, in its thing's own frame; null for none. */
    private static Coord3D inFrameOf(Drawn drawn, Coord3D file) {
        if (file == null) {
            return null;
        }
        // As the client turns a body by its facing about the up axis: x toward -z for a turn the right way round.
        double facing = StrictMath.toRadians(drawn.facing());
        double cos = StrictMath.cos(facing);
        double sin = StrictMath.sin(facing);
        float scale = drawn.modelScale();
        double x = file.x() * scale;
        double z = file.z() * scale;
        // The file is y up; the world's ground is x and y, up its z.
        return new Coord3D((float) (x * cos + z * sin), (float) (-x * sin + z * cos), file.y() * scale);
    }

    /** Where {@code bone} stands in the world: at the thing, turned with it; null as {@link #inFrame} is. */
    public static Coord3D inWorld(GameObject thing, String bone) {
        return worldOf(thing, inFrame(thing, bone));
    }

    /** A point of a thing's own frame in the world: at the thing, turned with it; null for none. */
    private static Coord3D worldOf(GameObject thing, Coord3D local) {
        if (local == null) {
            return null;
        }
        double cos = StrictMath.cos(thing.getOrientation());
        double sin = StrictMath.sin(thing.getOrientation());
        var at = thing.getPosition();
        return new Coord3D((float) (at.x() + local.x() * cos - local.y() * sin),
                (float) (at.y() + local.x() * sin + local.y() * cos), at.z() + local.z());
    }

    /** The file drawn while {@code words} hold: the best fit of its per-word models, or its plain one. */
    private static String modelFor(Drawn drawn, Set<String> words) {
        var models = drawn.models();
        if (words.isEmpty() || models.isEmpty()) {
            return drawn.model();
        }
        var keys = new ArrayList<>(models.keySet());
        var sets = new ArrayList<List<String>>(keys.size());
        for (var key : keys) {
            sets.add(List.of(key.trim().split("\\s+")));
        }
        int best = Conditions.bestFit(sets, words);
        return best < 0 ? drawn.model() : models.get(keys.get(best));
    }
}
