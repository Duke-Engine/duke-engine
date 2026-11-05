package uz.dukeengine.client3d.action;

import com.jme3.font.BitmapFont;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import java.util.List;
import uz.dukeengine.client3d.ControlScheme;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.view.UnitView;
import uz.dukeengine.core.view.WorldSnapshot;
import uz.dukeengine.game.DukeGame;

/** A window with no window: a camera that looks where it is told, and keys and a mouse a test sets. */
class TestWindow implements ControlScheme.Context {

    float lookX;
    float lookY;
    float yaw;
    float pitch = 0.9f;
    boolean held;
    float[] moved = {0f, 0f};
    boolean sprint;
    float now;
    /** How far from the hero a wall stands along any line the camera casts; none by default. */
    float wallAt = Float.MAX_VALUE;
    /** The ground's height, and the height of a hill behind the hero: where the world's y is past 105. */
    float ground;
    float hill;

    @Override
    public DukeGame game() {
        return null;
    }

    @Override
    public WorldSnapshot snapshot() {
        return null;
    }

    @Override
    public boolean playing() {
        return true;
    }

    @Override
    public List<ObjectId> selected() {
        return List.of();
    }

    @Override
    public float[] drawnAt(int id) {
        return null;
    }

    @Override
    public float topOf(int id) {
        return 0f;
    }

    @Override
    public UnitView pointedUnit() {
        return null;
    }

    @Override
    public float[] pointedGround() {
        return null;
    }

    @Override
    public float floorHeight(float x, float y) {
        return y > 105f ? hill : ground;
    }

    @Override
    public boolean keyHeld(char key) {
        return false;
    }

    @Override
    public boolean leftHeld() {
        return false;
    }

    @Override
    public boolean controlHeld(uz.dukeengine.client3d.KeyMap.Control control) {
        return sprint && control == uz.dukeengine.client3d.KeyMap.Control.SPRINT;
    }

    @Override
    public float time() {
        return now;
    }

    @Override
    public float yaw() {
        return yaw;
    }

    @Override
    public float pitch() {
        return pitch;
    }

    @Override
    public float[] lookingAt() {
        return new float[] {lookX, lookY};
    }

    @Override
    public void lookAt(float x, float y) {
        lookX = x;
        lookY = y;
    }

    @Override
    public void turnBy(float radians) {
        yaw += radians;
    }

    @Override
    public void tilt(float radians, float base, float band) {
        pitch = Math.clamp(pitch + radians, base - band, base + band);
    }

    @Override
    public void holdMouse(boolean hold) {
        held = hold;
    }

    @Override
    public boolean mouseHeld() {
        return held;
    }

    @Override
    public float[] mouseMoved() {
        return moved;
    }

    @Override
    public Node gui() {
        return new Node();
    }

    @Override
    public BitmapFont font() {
        return null;
    }

    @Override
    public Vector3f screenOf(float x, float height, float y) {
        return new Vector3f();
    }

    @Override
    public void castArmed() {
    }

    @Override
    public float reachOf(ObjectId id) {
        return 0f;
    }

    @Override
    public float clearRun(Vector3f from, Vector3f way, float length, float minHeight) {
        return Math.min(length, wallAt);
    }
}
