package uz.dukeengine.client3d.action;

import com.jme3.font.BitmapText;
import com.jme3.scene.Spatial;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import uz.dukeengine.client3d.ControlScheme;
import uz.dukeengine.client3d.SkillRange;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.game.DukeGame;

/**
 * Controls for a game played from one hero's shoulder, plugged into the client — {@code duke.controls(new
 * ActionControls().walk(...).follow(...))}: walking by held keys, a camera kept on him and turned by a held mouse, the
 * left button locking on, swinging or striking ahead, skills aimed along the camera's forward or cast at once, and the
 * nearest thing worth an order offered on a key. Each part is off until asked for; none of it reaches the simulation but
 * through the orders the game sends.
 */
public final class ActionControls implements ControlScheme {

    private Context window;

    private MouseScheme mouse = MouseScheme.CLASSIC;
    private final MouseScheme.Swing swing = new MouseScheme.Swing();

    private DirectionKeys walkKeys = DirectionKeys.WASD;
    private BiConsumer<DukeGame, Integer> walked;
    private final DirectionKeys.Held heldDirection = new DirectionKeys.Held();

    private CameraFollow follow = CameraFollow.NONE;
    private final CameraFollow.Eye eye = new CameraFollow.Eye();
    /** The pitch the follow was at when its tilt began, which the mouse tilts it within. */
    private float orbitPitch = Float.NaN;

    private boolean holdMouse;

    private boolean aimAhead;
    private boolean releaseToCast;
    private ForwardAim aim;

    private Interact interact;
    private BitmapText prompt;

    /** How the mouse plays — {@link MouseScheme#MODERN}, {@link MouseScheme#ACTION}, or the client's own. */
    public ActionControls mouse(MouseScheme scheme) {
        this.mouse = scheme == null ? MouseScheme.CLASSIC : scheme;
        return this;
    }

    /**
     * Walk by held keys: read each frame and turned into a direction on the ground as the camera is turned ({@link
     * DirectionKeys#angleOf}, 256ths of a turn, or {@link DirectionKeys#NONE}), {@code changed} told only when it
     * changes — and {@code NONE} once while a menu, a screen of the game's or another window has the keys. The game
     * sends it on as {@code ActionOrders.along}.
     */
    public ActionControls walk(DirectionKeys keys, BiConsumer<DukeGame, Integer> changed) {
        this.walkKeys = keys == null ? DirectionKeys.WASD : keys;
        this.walked = changed;
        return this;
    }

    /** The camera kept on one of his units — see {@link CameraFollow}; {@link CameraFollow#NONE} lets it go. */
    public ActionControls follow(CameraFollow look) {
        this.follow = look == null ? CameraFollow.NONE : look;
        return this;
    }

    /**
     * Hold the mouse — the pointer hidden and its motion turning a follow that orbits and aiming — let go while a screen
     * holds the keys, a menu or a cutscene is up, Alt is held ({@code KeyMap.Control.FREE_POINTER}) or another window
     * has the focus.
     */
    public ActionControls holdMouse(boolean held) {
        this.holdMouse = held;
        if (window != null) {
            window.holdMouse(held);
        }
        return this;
    }

    /**
     * Aim a key's skill from the hero along the camera's forward, the held mouse's motion up and down its distance;
     * cast by the left button or the key again, or — {@code castOnRelease} — by letting the key go; given up by the
     * right button, Escape or another skill's key.
     */
    public ActionControls aimAhead(boolean castOnRelease) {
        this.aimAhead = true;
        this.releaseToCast = castOnRelease;
        return this;
    }

    /**
     * Offer the nearest thing worth an order — among the things a click on would give a word, and the map's scenery the
     * ground rule names one for — within {@code reach} of the hero inside {@code coneDegrees} round his facing (the
     * camera's forward while he walks), {@code prompt(word, key)} drawn over it, null leaving it unoffered. The game
     * binds its key to {@link #interact}.
     */
    public ActionControls interact(char key, float reach, float coneDegrees,
            BiFunction<String, Character, String> promptOf) {
        this.interact = new Interact(Character.toUpperCase(key), reach, (float) Math.toRadians(coneDegrees), promptOf);
        return this;
    }

    /** Give the nearest thing worth an order what a click on it gives; from any thread. */
    public void interact(DukeGame game) {
        if (interact != null) {
            interact.give(game);
        }
    }

    @Override
    public void attach(Context context) {
        this.window = context;
        context.holdMouse(holdMouse);
        if (interact != null) {
            var looking = interact;
            context.game().eachSnapshot(() -> looking.look(context.game()));
        }
    }

    // ---- each frame ----

    @Override
    public void frame(float tpf) {
        orbit();
        if (aim != null) {
            aim.nudge(window.mouseMoved()[1]); // up and down: further and nearer
        }
        tellTheDirection();
        followTheSwing();
        offerTheNearest();
    }

    @Override
    public void steer(boolean panned, float tpf) {
        if (!follow.follows() || window.snapshot() == null) {
            return;
        }
        int mine = window.game().getLocalPlayerIndex();
        for (var view : window.snapshot().units()) {
            if (view.playerIndex() != mine || follow.of() != null && !follow.of().equals(view.templateName())) {
                continue;
            }
            var at = window.drawnAt(view.id());
            eye.step(follow, window, at == null ? view.x() : at[0], at == null ? view.y() : at[1], view.orientation(),
                    view.moving(), panned, window.time(), tpf);
            return;
        }
    }

    /** The follow turned round him by the held mouse, and tilted within its band — see {@link CameraFollow#orbiting}. */
    private void orbit() {
        if (!window.mouseHeld() || !follow.follows() || follow.orbitSensitivity() <= 0f) {
            return;
        }
        var moved = window.mouseMoved();
        window.turnBy(follow.turnOf(moved[0]));
        if (follow.pitchBand() > 0f) {
            if (Float.isNaN(orbitPitch)) {
                orbitPitch = window.pitch();
            }
            window.tilt(-moved[1] * follow.orbitSensitivity(), orbitPitch, follow.pitchBand());
        }
    }

    /** The held keys' direction, told the game when it changes. */
    private void tellTheDirection() {
        if (walked == null) {
            return;
        }
        var now = heldDirection.next(window.keyHeld(walkKeys.up()), window.keyHeld(walkKeys.left()),
                window.keyHeld(walkKeys.down()), window.keyHeld(walkKeys.right()), window.yaw(), !window.playing());
        if (now != null) {
            walked.accept(window.game(), now);
        }
    }

    /** The prompt drawn over the nearest thing worth an order. */
    private void offerTheNearest() {
        if (interact == null) {
            return;
        }
        if (prompt == null) {
            prompt = new BitmapText(window.font());
            window.gui().attachChild(prompt);
        }
        var target = window.playing() ? interact.target() : null;
        String said = target == null ? null : interact.promptOf().apply(target.word(), interact.key());
        if (said == null) {
            prompt.setCullHint(Spatial.CullHint.Always);
            return;
        }
        float height = target.unit() < 0 ? 0f : window.topOf(target.unit());
        var onScreen = window.screenOf(target.x(), window.floorHeight(target.x(), target.y()) + Math.max(height, 4f)
                + 2f, target.y());
        prompt.setText(said);
        prompt.setLocalTranslation(onScreen.x - prompt.getLineWidth() / 2f, onScreen.y + prompt.getLineHeight(), 0f);
        prompt.setCullHint(onScreen.z > 1f ? Spatial.CullHint.Always : Spatial.CullHint.Inherit);
    }

    // ---- the buttons ----

    @Override
    public boolean left(boolean pressed) {
        if (mouse.action()) {
            leftAhead(pressed);
            return true;
        }
        if (mouse.modern()) {
            leftModern(pressed);
            return true;
        }
        return false;
    }

    @Override
    public boolean rightOrders(boolean worded, boolean onAThing) {
        return mouse.rightOrders(worded, onAThing);
    }

    /** The left button, the action way: a strike along the camera's forward. */
    private void leftAhead(boolean pressed) {
        var hero = window.selected();
        var at = hero.isEmpty() || window.snapshot() == null ? null : window.drawnAt(hero.getFirst().value());
        if (at == null) {
            return;
        }
        int me = window.game().getLocalPlayerIndex();
        if (!pressed) {
            swing.releaseAhead(me, hero).forEach(window.game()::postCommand);
            return;
        }
        swing.pressAhead(mouse, me, hero, at[0], at[1], window.reachOf(hero.getFirst()), forward(), enemiesSeen(),
                window.snapshot().frame()).forEach(window.game()::postCommand);
    }

    /** The left button, the modern way: a lock on the enemy under it, else a swing toward the ground, held. */
    private void leftModern(boolean pressed) {
        var hero = window.selected();
        if (hero.isEmpty()) {
            return;
        }
        int me = window.game().getLocalPlayerIndex();
        int frame = window.snapshot() == null ? 0 : window.snapshot().frame();
        if (!pressed) {
            swing.release(me, hero).forEach(window.game()::postCommand);
            return;
        }
        var unit = window.pointedUnit();
        int enemy = unit != null && unit.hostile() ? unit.id() : -1;
        float gap = Float.MAX_VALUE;
        var mine = window.drawnAt(hero.getFirst().value());
        if (enemy >= 0 && mine != null) {
            gap = (float) Math.hypot(unit.x() - mine[0], unit.y() - mine[1]);
        }
        var ground = window.pointedGround();
        swing.press(mouse, me, hero, enemy, gap, ground == null ? null : new Coord3D(ground[0], ground[1], ground[2]),
                frame).forEach(window.game()::postCommand);
    }

    /** A held swing's point followed as the pointer, or the camera, moves. */
    private void followTheSwing() {
        if (!swing.isHeld() || window.snapshot() == null) {
            return;
        }
        int me = window.game().getLocalPlayerIndex();
        var hero = window.selected();
        if (mouse.action()) {
            var at = hero.isEmpty() ? null : window.drawnAt(hero.getFirst().value());
            if (!window.playing() || !window.leftHeld() || at == null) {
                swing.releaseAhead(me, hero).forEach(window.game()::postCommand);
                return;
            }
            swing.dragAhead(mouse, me, hero, at[0], at[1], window.reachOf(hero.getFirst()), forward(), enemiesSeen(),
                    window.snapshot().frame()).forEach(window.game()::postCommand);
            return;
        }
        if (!window.playing() || !window.leftHeld()) {
            swing.release(me, hero).forEach(window.game()::postCommand);
            return;
        }
        var ground = window.pointedGround();
        if (ground != null) {
            swing.drag(me, hero, new Coord3D(ground[0], ground[1], ground[2]), window.snapshot().frame())
                    .forEach(window.game()::postCommand);
        }
    }

    /** The world's angle of the camera's forward — up the screen, as the walking keys go. */
    private float forward() {
        float yaw = window.yaw();
        return (float) Math.atan2(-Math.cos(yaw), -Math.sin(yaw));
    }

    /** The living enemies the player sees now, for the soft lock. */
    private List<MouseScheme.Candidate> enemiesSeen() {
        var seen = new ArrayList<MouseScheme.Candidate>();
        for (var view : window.snapshot().units()) {
            if (view.hostile() && view.health() > 0f) {
                seen.add(new MouseScheme.Candidate(view.id(), view.x(), view.y()));
            }
        }
        return seen;
    }

    // ---- aiming ----

    @Override
    public boolean armed(char key, boolean byMouse, SkillRange range) {
        if (!aimAhead || byMouse) {
            return false;
        }
        aim = new ForwardAim(range == null ? 0f : range.reach(), range == null ? SkillRange.Shape.AT_A_SPOT
                : range.shape());
        return true;
    }

    @Override
    public float[] aimPoint() {
        var hero = window.selected();
        var at = aim == null || hero.isEmpty() ? null : window.drawnAt(hero.getFirst().value());
        return at == null ? null : aim.point(at[0], at[1], window.yaw());
    }

    @Override
    public boolean aiming() {
        return aim != null;
    }

    @Override
    public boolean castsOnRelease() {
        return releaseToCast;
    }

    @Override
    public void disarmed() {
        aim = null;
    }

    @Override
    public boolean castsAtOnce(boolean byMouse) {
        return mouse.modern() && mouse.quickCast() && !byMouse;
    }
}
