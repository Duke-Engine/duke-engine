package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Ray;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import org.junit.jupiter.api.Test;

/** A click picks what is drawn under it: the pieces shown now, each as its own box turned with it. */
class PickingTest {

    private static final Vector3f DOWN = new Vector3f(0f, -1f, 0f);

    /** A tank 60 long and 17 wide, its hidden muzzle flash reaching 15 past its nose, turned 45 degrees. */
    private static Node tank() {
        var tank = new Node("tank");
        tank.attachChild(new Geometry("hull", new Box(30f, 5f, 8.5f)));
        var flash = new Geometry("TURRETFX01", new Box(7.5f, 2f, 2f));
        flash.setLocalTranslation(37.5f, 3f, 0f);
        flash.setCullHint(Spatial.CullHint.Always); // hidden in the state the tank is in
        tank.attachChild(flash);
        tank.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.QUARTER_PI, Vector3f.UNIT_Y));
        tank.updateGeometricState();
        return tank;
    }

    /** Straight down onto a point of the ground: where the tank's frame puts {@code (along, across)}. */
    private static Ray downOnto(Spatial thing, float along, float across) {
        var at = thing.localToWorld(new Vector3f(along, 0f, across), null);
        return new Ray(new Vector3f(at.x, 100f, at.z), DOWN);
    }

    @Test
    void aClickOnTheHullPicksItAndBesideItOrOnAHiddenFlashPicksNothing() {
        var tank = tank();

        assertEquals(95f, Picking.drawnHit(tank, downOnto(tank, 0f, 0f)), 1e-3f, "the hull's roof, 5 up");
        var beside = downOnto(tank, 0f, 18.5f);
        assertTrue(tank.getWorldBound().intersects(beside), "10 beside the hull: inside the world's box round it");
        assertEquals(Float.POSITIVE_INFINITY, Picking.drawnHit(tank, beside), "and nothing of it is drawn there");
        assertEquals(Float.POSITIVE_INFINITY, Picking.drawnHit(tank, downOnto(tank, 40f, 0f)),
                "where its hidden flash would be: nothing");
    }

    @Test
    void aClickInTheCornerOfATurnedFactorysOldBoxPicksNothing() {
        var factory = new Node("factory");
        factory.attachChild(new Geometry("walls", new Box(56.5f, 20f, 61f)));
        factory.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.QUARTER_PI, Vector3f.UNIT_Y));
        factory.updateGeometricState();
        var corner = new Ray(new Vector3f(75f, 100f, 75f), DOWN);

        assertTrue(factory.getWorldBound().intersects(corner), "inside the box the world's axes draw round it");
        assertEquals(Float.POSITIVE_INFINITY, Picking.drawnHit(factory, corner), "off its walls");
    }

    @Test
    void theNearestHitAlongTheRayWinsNotTheNearestMiddle() {
        var building = new Node("building");
        building.attachChild(new Geometry("walls", new Box(40f, 30f, 40f)));
        building.updateGeometricState();
        var soldier = new Node("soldier");
        soldier.attachChild(new Geometry("body", new Box(2f, 4f, 2f)));
        soldier.setLocalTranslation(0f, 34f, 0f); // on the roof, between the camera and the building
        soldier.updateGeometricState();
        var ray = new Ray(new Vector3f(0f, 100f, 60f), new Vector3f(0f, -1f, -0.9f).normalizeLocal());

        float atSoldier = Picking.drawnHit(soldier, ray);
        float atBuilding = Picking.drawnHit(building, ray);
        assertTrue(atSoldier < atBuilding, "the soldier is met first: " + atSoldier + " against " + atBuilding);
    }

    @Test
    void aGlowIsNotClickedButAThingMadeOfNothingElseStillIs() {
        var assets = new DesktopAssetManager(true);
        var truck = new Node("truck");
        truck.attachChild(new Geometry("body", new Box(18f, 5f, 8f)));
        var beams = new Geometry("headlights", new Box(10f, 2f, 6f));
        beams.setLocalTranslation(28f, 3f, 0f);
        beams.setMaterial(added(assets));
        truck.attachChild(beams);
        truck.updateGeometricState();
        assertEquals(Float.POSITIVE_INFINITY, Picking.drawnHit(truck, new Ray(new Vector3f(30f, 100f, 0f), DOWN)),
                "the beams of its lights are drawn added to the ground: not the truck");

        var ghost = new Node("ghost");
        var sheet = new Geometry("sheet", new Box(3f, 8f, 3f));
        sheet.setMaterial(added(assets));
        ghost.attachChild(sheet);
        ghost.updateGeometricState();
        assertEquals(92f, Picking.drawnHit(ghost, new Ray(new Vector3f(0f, 100f, 0f), DOWN)), 1e-3f,
                "a thing drawn all see-through is still clicked by what it has");
    }

    private static Material added(DesktopAssetManager assets) {
        var material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        material.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Additive);
        return material;
    }
}
