package uz.dukeengine.client3d;

import com.jme3.bounding.BoundingBox;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import uz.dukeengine.core.math.Coord3D;

/**
 * A thing drawn along a line — {@link Visuals.UnitVisual#alongALine}, the reference's {@code W3DBridgeBuffer}: its
 * model's first piece, its middle piece repeated as many times as fits best, and its last, end to end along the model's
 * x from one end of the line to the other, stretched along it so the last ends exactly at the far end, and scaled across
 * and up by the look's own numbers. A model whose pieces are not there, or do not meet within 5% of its length, is
 * drawn once, stretched.
 */
final class LinePieces {

    /** How far a model's pieces may fall short of it, or overrun it, and still be laid piece by piece. */
    private static final float MEET = 0.05f;

    private LinePieces() {
    }

    /**
     * How many middle pieces go between the ends, and how much every piece is stretched along the line.
     *
     * @param length how far apart the line's ends are
     */
    record Layout(int middles, float stretch) {
    }

    /**
     * The whole number of middles nearest {@code (length − first − last) / middle}, none allowed — the last then meets
     * the first — and the stretch that makes them end at the line's far end.
     */
    static Layout layout(float first, float middle, float last, float length) {
        int middles = middle <= 0f ? 0 : Math.max(0, Math.round((length - first - last) / middle));
        float laid = first + middles * middle + last;
        return new Layout(middles, laid <= 0f ? 1f : length / laid);
    }

    /** Along the model's x, where a part of it starts and ends in the model's own frame. */
    private record Reach(float from, float to) {

        float length() {
            return to - from;
        }
    }

    /**
     * The model laid along the line from {@code from} to {@code to} — both in the simulation's frame — as a node in the
     * client's: at the first end, turned so its x runs to the second and rising with it.
     */
    static Node lay(Spatial model, Visuals.UnitVisual.LineLook look, Coord3D from, Coord3D to) {
        var start = new Vector3f(from.x(), from.z(), from.y());
        var end = new Vector3f(to.x(), to.z(), to.y());
        var way = end.subtract(start);
        float length = way.length();
        var laid = new Node("along a line");
        laid.setLocalTranslation(start);
        laid.setLocalRotation(along(way));
        model.setLocalTransform(new Transform());
        model.updateGeometricState();
        var whole = reachOf(model);
        var first = named(model, look.first());
        var middle = named(model, look.middle());
        var last = named(model, look.last());
        if (whole == null || length <= 0f) {
            return laid;
        }
        var firstReach = first == null ? null : reachOf(first);
        var middleReach = middle == null ? null : reachOf(middle);
        var lastReach = last == null ? null : reachOf(last);
        boolean inPieces = firstReach != null && middleReach != null && lastReach != null
                && Math.abs(firstReach.length() + middleReach.length() + lastReach.length() - whole.length())
                <= MEET * whole.length();
        if (!inPieces) {
            place(laid, model.clone(), model.getWorldTransform(), whole, 0f, length / whole.length(), look);
            return laid;
        }
        var fit = layout(firstReach.length(), middleReach.length(), lastReach.length(), length);
        float cursor = place(laid, first.clone(), first.getWorldTransform(), firstReach, 0f, fit.stretch(), look);
        for (int copy = 0; copy < fit.middles(); copy++) {
            cursor = place(laid, middle.clone(), middle.getWorldTransform(), middleReach, cursor, fit.stretch(), look);
        }
        place(laid, last.clone(), last.getWorldTransform(), lastReach, cursor, fit.stretch(), look);
        return laid;
    }

    /** One piece laid from {@code cursor} along the line, stretched; where the next one starts. */
    private static float place(Node laid, Spatial piece, Transform inModel, Reach reach, float cursor, float stretch,
            Visuals.UnitVisual.LineLook look) {
        var holder = new Node(piece.getName() + " laid");
        holder.setLocalScale(stretch, look.up(), look.across());
        holder.setLocalTranslation(cursor - reach.from() * stretch, 0f, 0f);
        piece.setLocalTransform(inModel.clone()); // where it sat in the model, the model now the holder
        holder.attachChild(piece);
        laid.attachChild(holder);
        return cursor + reach.length() * stretch;
    }

    /** A turn putting x along {@code way}, rising or falling with it, and y as near up as that leaves. */
    static Quaternion along(Vector3f way) {
        var x = way.normalize();
        var z = x.cross(Vector3f.UNIT_Y);
        if (z.lengthSquared() < 1e-8f) {
            z = Vector3f.UNIT_Z.clone(); // straight up or down: any side will do
        }
        z.normalizeLocal();
        var y = z.cross(x).normalizeLocal();
        return new Quaternion().fromAxes(x, y, z);
    }

    private static Reach reachOf(Spatial part) {
        part.updateModelBound();
        part.updateGeometricState();
        return part.getWorldBound() instanceof BoundingBox box && box.getXExtent() > 0f
                ? new Reach(box.getCenter().x - box.getXExtent(), box.getCenter().x + box.getXExtent()) : null;
    }

    /** The part of the model by this name, letter case aside, or null. */
    private static Spatial named(Spatial model, String name) {
        if (name == null) {
            return null;
        }
        var found = new Spatial[1];
        model.depthFirstTraversal(spatial -> {
            if (found[0] == null && name.equalsIgnoreCase(spatial.getName())) {
                found[0] = spatial;
            }
        });
        return found[0];
    }
}
