package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import java.util.Set;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.view.Turrets;

/** Turrets drawn turned and pitched as the simulation has them ({@code handleClientTurretPositioning}). */
class TurretBonesTest {

    /** A tank: its turret 5 ahead, the gun's pivot on it, a muzzle 2 along; a second turret, and an upgraded one. */
    private static Node tank() {
        var model = new Node("Tank");
        hang(hang(hang(model, "TURRET", 5f, 0f), "TURRETEL", 1f, 1f), "MUZZLE", 2f, 0f);
        hang(hang(model, "TURRETUP01", -3f, 2f), "UPMUZZLE", 1f, 0f);
        hang(hang(hang(model, "TURRET02", -6f, 0f), "TURRETEL02", 1f, 0f), "MUZZLE02", 2f, 0f);
        return model;
    }

    private static Node hang(Node on, String name, float x, float y) {
        var node = new Node(name);
        node.setLocalTranslation(x, y, 0f);
        on.attachChild(node);
        return node;
    }

    private static Vector3f at(Node model, String name) {
        model.updateGeometricState();
        return Bones.named(model, name).getWorldTranslation();
    }

    private static void near(Vector3f expected, Vector3f actual, String what) {
        assertEquals(0f, expected.distance(actual), 1e-4f, what + ": " + actual);
    }

    private static Visuals.UnitVisual look(java.util.function.Consumer<Visuals.UnitVisual> named) {
        return Visuals.create().unit("Tank", look -> named.accept(look.model("models/tank.glb"))).of("Tank");
    }

    @Test
    void aTurretTurnedAQuarterRoundAndPitchedHalfARadianIsDrawnSo() {
        var model = tank();
        var look = look(l -> l.turret(Set.of(), 0, "TURRET", 0f, "TURRETEL", 0f));
        var bones = new TurretBones();
        bones.dress(1, model, look.turretsFor(Set.of()));

        bones.turn(1, new Turrets(FastMath.HALF_PI, 0f, 0f, 0f));
        near(new Vector3f(5f, 1f, 3f), at(model, "MUZZLE"), "a quarter round about TURRET's pivot, the way it turns");

        bones.turn(1, new Turrets(0f, 0.5f, 0f, 0f));
        var muzzle = at(model, "MUZZLE").subtract(at(model, "TURRETEL"));
        assertEquals(0.5f, FastMath.atan2(muzzle.y, muzzle.x), 1e-4f, "raised half a radian");

        bones.turn(1, Turrets.NONE);
        near(new Vector3f(8f, 1f, 0f), at(model, "MUZZLE"), "at 0 and 0, as the file");
    }

    @Test
    void aFixedAngleOf180DrawsTheTurretHalfRoundAtNone() {
        var model = tank();
        var bones = new TurretBones();
        bones.dress(1, model, look(l -> l.turret(Set.of(), 0, "TURRET", 180f, null, 0f)).turretsFor(Set.of()));
        bones.turn(1, Turrets.NONE);
        near(new Vector3f(2f, 1f, 0f), at(model, "MUZZLE"), "half round");
    }

    @Test
    void aStateNamingTheUpgradedTurretTurnsThatAndLeavesTheFirstAsTheFileHasIt() {
        var model = tank();
        var look = look(l -> l.turret(Set.of(), 0, "TURRET", 0f, null, 0f)
                .turret(Set.of("UPGRADED"), 0, "TURRETUP01", 0f, null, 0f));
        var bones = new TurretBones();
        var quarter = new Turrets(FastMath.HALF_PI, 0f, 0f, 0f);
        bones.dress(1, model, look.turretsFor(Set.of()));
        bones.turn(1, quarter);

        bones.dress(1, model, look.turretsFor(Set.of("UPGRADED")));
        bones.turn(1, quarter);
        near(new Vector3f(-3f, 2f, 1f), at(model, "UPMUZZLE"), "the upgraded turret turned");
        near(new Vector3f(8f, 1f, 0f), at(model, "MUZZLE"), "the first as the file has it");
    }

    @Test
    void aSecondTurretTurnsByTheSecondTurretsTurnAndPitch() {
        var model = tank();
        var bones = new TurretBones();
        bones.dress(1, model, look(l -> l.turret(Set.of(), 1, "TURRET02", 0f, "TURRETEL02", 0f))
                .turretsFor(Set.of()));
        bones.turn(1, new Turrets(1f, 1f, FastMath.HALF_PI, 0f));
        near(new Vector3f(-6f, 0f, 3f), at(model, "MUZZLE02"), "turned by the second's turn");
        near(new Vector3f(8f, 1f, 0f), at(model, "MUZZLE"), "the first, named by no bone, as the file");
    }
}
