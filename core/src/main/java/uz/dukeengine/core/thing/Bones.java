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
        var file = ModelBones.of(modelFor(drawn, words), bone);
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
        var local = inFrame(thing, bone);
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
