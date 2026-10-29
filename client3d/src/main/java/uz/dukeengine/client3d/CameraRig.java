package uz.dukeengine.client3d;

import com.jme3.input.InputManager;
import com.jme3.input.MouseInput;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

import uz.dukeengine.client3d.platform.HeldInput;
import uz.dukeengine.core.view.WorldSnapshot;
import uz.dukeengine.game.DukeGame;

/**
 * The player's eye on the world: steered by his hands — the pan keys, the window's edges, the right and middle
 * buttons, a held mouse — or put where the game's own camera says, then placed over the ground it looks at, at its
 * slope, shaken by what landed.
 */
final class CameraRig {

    private final Camera cam;
    private final CameraFocus camera;
    /** The player's camera as the game frames it — see {@link CameraFrame}. */
    private final CameraFrame cameraFrame;
    private final Visuals visuals;
    private final InputManager inputManager;
    private final HeldInput heldInput;
    private final SceneVeil veil;
    private final SkillEffects skillEffects;
    private final ListShow listShow;
    private final com.jme3.system.Timer timer;
    private final Supplier<DukeGame> game;
    private final Supplier<WorldSnapshot> snapshot;
    /** Controls of a kind the client has not got itself — see {@link Duke3D#controls}. */
    private final Supplier<ControlScheme> scheme;
    /** Whether a match is what is going on: played, with no menu over it. */
    private final BooleanSupplier inPlay;
    /** Whether a held control of the client's is held now, and play is what is going on. */
    private final Predicate<KeyMap.Control> held;
    /** Whether a scene the game plays keeps the player's hands off the world. */
    private final BooleanSupplier sceneLocked;
    /** Whether the pointer must stay free though the scheme would hold it: the game's canvas, or the player's key. */
    private final BooleanSupplier pointerFreed;
    private final WorldMoments.Floor floor;
    /** How steeply the camera looks down, in radians: the game's camera's where it set one, else the player's. */
    private float cameraPitch = CameraFocus.DEFAULT_PITCH;
    /** The player's mouse on the camera: a middle drag turning it — see {@link Steering}. */
    private final Steering steering = new Steering();
    /**
     * How high the camera is looking, eased toward the ground under its target.
     */
    private float cameraHeight;
    /** The player's own scroll speed, a share of the framed pan speed — see {@link Duke3D#scrollSpeed}. */
    private float scrollSpeed = 1f;
    /** Which way the player's view scrolled last frame — {@code N}, {@code NE} … — or null: what the pointer shows. */
    private String scrolling;
    /** The mouse held by the window, as the scheme asks — see {@link ControlScheme.Context#holdMouse}. */
    private final MouseCapture capture = new MouseCapture();
    /** How far the held mouse moved this frame, in pixels, and the sum of the events it is read from. */
    private float heldDx;
    private float heldDy;
    private final HeldMotion motion = new HeldMotion();

    CameraRig(Camera cam, CameraFocus camera, CameraFrame cameraFrame, Visuals visuals, InputManager inputManager,
            HeldInput heldInput, SceneVeil veil, SkillEffects skillEffects, ListShow listShow,
            com.jme3.system.Timer timer, Supplier<DukeGame> game, Supplier<WorldSnapshot> snapshot,
            Supplier<ControlScheme> scheme, BooleanSupplier inPlay, Predicate<KeyMap.Control> held,
            BooleanSupplier sceneLocked, BooleanSupplier pointerFreed, WorldMoments.Floor floor) {
        this.cam = cam;
        this.camera = camera;
        this.cameraFrame = cameraFrame;
        this.visuals = visuals;
        this.inputManager = inputManager;
        this.heldInput = heldInput;
        inputManager.addRawInputListener(motion);
        this.veil = veil;
        this.skillEffects = skillEffects;
        this.listShow = listShow;
        this.timer = timer;
        this.game = game;
        this.snapshot = snapshot;
        this.scheme = scheme;
        this.inPlay = inPlay;
        this.held = held;
        this.sceneLocked = sceneLocked;
        this.pointerFreed = pointerFreed;
        this.floor = floor;
    }

    /** The player's own eyes in place of the rig, or null for the rig — see {@link Duke3D#firstPerson}. */
    private FirstPersonCamera firstPerson;

    void firstPerson(FirstPersonCamera eyes) {
        this.firstPerson = eyes;
    }

    /** The player's own scroll speed — see {@link Duke3D#scrollSpeed}. */
    void scrollSpeed(float share) {
        this.scrollSpeed = Math.max(0f, share);
    }

    /** Which way the player's view scrolled last frame — {@code N}, {@code NE} … — or null. */
    String scrolling() {
        return scrolling;
    }

    /** The mouse held by the window, or let go — see {@link ControlScheme.Context#holdMouse}. */
    void holdMouse(boolean held) {
        capture.want(held);
    }

    boolean mouseHeld() {
        return capture.captured();
    }

    /** How far the held mouse moved this frame, in pixels. */
    float[] mouseMoved() {
        return new float[] {heldDx, heldDy};
    }

    /** The right button down at a point of the window, counted from its top, where the game framed a right drag. */
    void rightDown(float x, float y) {
        steering.rightDown(x, y, timer.getTimeInSeconds(), camera.targetX(), camera.targetZ(), visuals.getRightDrag());
    }

    /** The right button let go: whether it was a click, not a drag, and so acts. */
    boolean rightUp(float x, float y) {
        return steering.rightUp(x, y, timer.getTimeInSeconds(), camera.targetX(), camera.targetZ());
    }

    /**
     * Somewhere to look before there is anything to look at. The moment the game
     * starts, {@link CameraFocus#requestOwnUnit()} replaces this with the player's
     * own units — this is only what fills the screen until then.
     */
    void centreOnTheMap() {
        var grid = game.get().getTerrain();
        camera.lookAt(grid == null ? 350f : grid.getWidth() * grid.getCellSize() / 2f,
                grid == null ? 225f : grid.getHeight() * grid.getCellSize() / 2f);
    }

    /** A frame of the camera: the mouse held or let go, the eye steered or directed, and placed. */
    void frame(float tpf) {
        holdTheMouse();
        var scheme = this.scheme.get();
        var snapshot = this.snapshot.get();
        var game = this.game.get();
        scheme.frame(tpf); // a scene the game directs or not: a key let go is always told
        var directed = snapshot.camera();
        if (firstPerson != null) {
            scrolling = null; // its own eyes: no rig, no pan keys, no edges
        } else if (directed != null) {
            // The game has the camera: it goes where the game's code put it, and the player's controls wait.
            camera.restore(new CameraFocus.View(directed.x(), directed.y(), directed.angle(),
                    Float.isNaN(directed.zoom()) ? camera.distance() : directed.zoom()));
            cameraPitch = Float.isNaN(directed.pitch()) ? camera.pitch() : directed.pitch();
            scrolling = null;
        } else {
            if (!sceneLocked.getAsBoolean()) {
                steerTheCamera(tpf);
            }
            cameraPitch = camera.pitch();
            game.setCameraSeen(camera.targetX(), camera.targetZ(), camera.yaw(), camera.pitch(), camera.distance());
        }
        if (firstPerson != null) {
            firstPerson.place(cam);
        } else {
            placeTheCamera(tpf);
        }
        veil.show(game.camera().screen(), cam.getWidth(), cam.getHeight());
    }

    /** The player's own hands on the camera: the pan keys, the window's edges and the right button, turning, zooming. */
    private void steerTheCamera(float tpf) {
        var moved = game.get().takeViewMove();
        if (moved != null) {
            camera.lookAt(moved.x(), moved.y()); // the game put his view here; he scrolls on from it
        }
        var at = inputManager.getCursorPosition();
        int tall = cam.getHeight();
        // No edge while a menu is up: the pointer is being used for something else, and a view that drifted out from
        // under a choice would be its own kind of bug.
        boolean atTheEdges = inPlay.getAsBoolean() && heldInput.pointerInside()
                && !capture.captured(); // a held mouse scrolls nothing at the edges
        steering.stillHeld(heldInput.buttonHeld(MouseInput.BUTTON_RIGHT),
                heldInput.buttonHeld(MouseInput.BUTTON_MIDDLE));
        var hands = new Steering.Hands(held.test(KeyMap.Control.PAN_LEFT), held.test(KeyMap.Control.PAN_RIGHT),
                held.test(KeyMap.Control.PAN_UP), held.test(KeyMap.Control.PAN_DOWN), at.x, tall - at.y, atTheEdges,
                cam.getWidth(), tall);
        var pan = steering.scroll(hands, tpf, camera.panAcross() * scrollSpeed, camera.panAlong() * scrollSpeed,
                visuals.getEdgeScroll());
        scrolling = Cursors.scrollDirection(pan.x, pan.y, steering.dragging());
        // Across and down the screen, which is the ground turned the way the camera is.
        float cos = FastMath.cos(camera.yaw());
        float sin = FastMath.sin(camera.yaw());
        camera.panBy(pan.x * cos + pan.y * sin, pan.y * cos - pan.x * sin);
        scheme.get().steer(pan.x != 0f || pan.y != 0f, tpf);
        camera.turnBy(steering.turn(inputManager.getCursorPosition().x, turnPerPixel()));
        if (held.test(KeyMap.Control.TURN_LEFT)) {
            camera.heldTurn(1, tpf);
        }
        if (held.test(KeyMap.Control.TURN_RIGHT)) {
            camera.heldTurn(-1, tpf);
        }
        if (held.test(KeyMap.Control.ZOOM_IN)) {
            camera.heldZoom(true, tpf);
        }
        if (held.test(KeyMap.Control.ZOOM_OUT)) {
            camera.heldZoom(false, tpf);
        }
        camera.approach(tpf); // the eye closing on where the wheel and the keys set it
    }

    /** The eye where the focus says, riding up with the ground under it, at its slope, shaken by what landed. */
    private void placeTheCamera(float tpf) {
        // The camera rides up with the ground under what it is looking at, or a
        // room on the second storey is a room seen from underneath. Eased rather
        // than stepped: a storey is a whole body's height and arriving at one in a
        // single frame reads as the world jumping.
        float wanted = floor.at(camera.targetX(), camera.targetZ());
        cameraHeight += (wanted - cameraHeight) * Math.min(1f, tpf * 6f);

        var target = new Vector3f(camera.targetX(), cameraHeight, camera.targetZ());
        // The knock from whatever just landed, added to where the camera was
        // going to be rather than replacing it -- so the shake never argues with
        // the pan, the zoom or the storey it is riding up.
        //
        // Only the EYE is moved and not what it is looking at. Shaking both is a
        // camera being carried about; shaking one is the ground being hit, which
        // is what is actually happening. It also keeps the thing the player is
        // watching in the middle of the screen while the world rattles round it.
        var knock = skillEffects == null ? Vector3f.ZERO : skillEffects.shakeNow();
        if (listShow != null) {
            knock = knock.add(listShow.shakeNow());
        }
        cam.setLocation(target.add(camera.eyeOffset(cameraPitch)).addLocal(knock));
        cam.lookAt(target, Vector3f.UNIT_Y);
    }

    /** How far a middle drag turns the view for each pixel it moves across, in radians; 0 where the game set none. */
    private float turnPerPixel() {
        float perPixel = cameraFrame.turnPerPixel();
        return Float.isNaN(perPixel) ? 0f : perPixel;
    }

    /**
     * The middle button on the player's camera, where the game framed a turn for it: held, a drag across turns the view
     * (the reference's {@code LookAtTranslator}); let go as a click, the view is put back as a reset puts it.
     */
    void turnByTheMiddleButton(boolean pressed) {
        var at = inputManager.getCursorPosition();
        float now = timer.getTimeInSeconds();
        var snapshot = this.snapshot.get();
        if (pressed) {
            if (inPlay.getAsBoolean() && snapshot.camera() == null && turnPerPixel() != 0f) {
                steering.middleDown(at.x, at.y, now);
            }
        } else if (steering.middleUp(at.x, at.y, now) && snapshot.camera() == null) {
            camera.resetView();
        }
    }

    /** The mouse held or let go as the screen asks, and how far it moved this frame read while it is held. */
    private void holdTheMouse() {
        boolean was = capture.captured();
        var snapshot = this.snapshot.get();
        boolean freed = !inPlay.getAsBoolean() || pointerFreed.getAsBoolean() || snapshot == null
                || snapshot.camera() != null || !heldInput.focused();
        boolean held = capture.update(freed);
        if (held != was) {
            inputManager.setCursorVisible(!held); // jME's window hides and holds the pointer, or lets it go
        }
        var sum = motion.take(); // taken every frame, so a mouse held again starts from none
        var moved = capture.motion(sum[0], sum[1]);
        heldDx = moved[0];
        heldDy = moved[1];
    }
}
