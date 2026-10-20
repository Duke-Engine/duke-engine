package uz.dukeengine.action;

import java.util.List;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.module.Steering;
import uz.dukeengine.core.thing.GameObject;

/**
 * A walk along a direction with no route — a held W A S D: {@code angle} in 256ths of a turn, 0 along +x and
 * counter-clockwise seen from above. Each frame the whole step, else its x part alone, else its y part alone — in that
 * order, so every machine slides alike along what stops it — or none, facing the way still and the walk kept.
 */
public record Stride(int angle) implements Steering {

    public Stride {
        angle &= 0xFF;
    }

    /**
     * Walk {@code thing} along {@code angle} until it is stopped or sent somewhere; below 0, stop it where it stands.
     * A thing whose legs are not a {@link MoveUpdate} only stops.
     */
    public static void walk(GameObject thing, int angle) {
        var legs = thing.getLocomotor();
        if (legs instanceof MoveUpdate walker) {
            walker.steerBy(angle < 0 ? null : new Stride(angle));
        } else if (legs != null && angle < 0) {
            legs.stop();
        }
    }

    @Override
    public boolean step(MoveUpdate legs, float step) {
        var owner = legs.getOwner();
        double turn = angle * (2.0 * StrictMath.PI / 256.0);
        float dx = (float) StrictMath.cos(turn) * step;
        float dy = (float) StrictMath.sin(turn) * step;
        owner.setOrientation((float) StrictMath.atan2(dy, dx));
        var here = owner.getPosition();
        for (var to : List.of(new Coord3D(here.x() + dx, here.y() + dy, here.z()),
                new Coord3D(here.x() + dx, here.y(), here.z()), new Coord3D(here.x(), here.y() + dy, here.z()))) {
            if ((to.x() != here.x() || to.y() != here.y()) && legs.stepStraightTo(to)) {
                return true;
            }
        }
        return false;
    }
}
