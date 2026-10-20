package uz.dukeengine.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.input.InputManager;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.view.WorldSnapshot;
import uz.dukeengine.game.DukeGame;

/**
 * The game's command buttons: the bar of them the client draws for the selection, pressed by a click or by the key
 * a button's label names, and a button — the bar's, or one of the game's own canvas — that needs a place or a thing,
 * armed and waiting for it with its ghost, circle or picture on the ground.
 */
final class CommandButtons {

    private final AssetManager assetManager;
    private final Node guiNode;
    private final Node markerNode;
    private final Visuals visuals;
    private final Looks looks;
    private final Bodies bodies;
    private final Screens screens;
    /** A font the game named, or the client's own where it names none or it will not load. */
    private final Function<String, BitmapFont> fonts;
    private final Camera cam;
    private final InputManager inputManager;
    private final Orders orders;
    private final WorldPointer pointer;
    private final SkillKeys skillKeys;
    private final Supplier<DukeGame> game;
    private final Supplier<WorldSnapshot> snapshot;
    /** Whether the client's own HUD is up: in play, for a game that does not draw its own. */
    private final BooleanSupplier clientHud;
    private final WorldMoments.Floor floor;
    private CommandBar commandBar;
    /** Whether the player has put the command bar away. */
    private boolean commandBarHidden;
    /** The button waiting for its place or its thing — the bar's, or the game's. Never at once with a hotkey's. */
    private final Aiming aiming = new Aiming();
    /** The circle round the cursor an aim at the ground may ask for, made when first wanted. */
    private GroundRing aimRing;
    /** The picture on the ground an aim may ask for in place of the circle, made when first wanted. */
    private GroundDecal aimDecal;
    /** What the armed button will put down, drawn at the cursor, or null for a button with no ghost. */
    private Ghost ghost;

    CommandButtons(AssetManager assetManager, Node guiNode, Node markerNode, Visuals visuals, Looks looks,
            Bodies bodies, Screens screens, Function<String, BitmapFont> fonts, Camera cam,
            InputManager inputManager, Orders orders, WorldPointer pointer, SkillKeys skillKeys,
            Supplier<DukeGame> game, Supplier<WorldSnapshot> snapshot, BooleanSupplier clientHud,
            WorldMoments.Floor floor) {
        this.assetManager = assetManager;
        this.guiNode = guiNode;
        this.markerNode = markerNode;
        this.visuals = visuals;
        this.looks = looks;
        this.bodies = bodies;
        this.screens = screens;
        this.fonts = fonts;
        this.cam = cam;
        this.inputManager = inputManager;
        this.orders = orders;
        this.pointer = pointer;
        this.skillKeys = skillKeys;
        this.game = game;
        this.snapshot = snapshot;
        this.clientHud = clientHud;
        this.floor = floor;
    }

    /** The bar put away or brought back, as the player's key says. */
    void toggle() {
        commandBarHidden = !commandBarHidden;
    }

    /** The bar put away, with the match it was for. */
    void hide() {
        if (commandBar != null) {
            commandBar.hide();
        }
    }

    /** Whether a button waits for its place or its thing. */
    boolean armed() {
        return aiming.isArmed();
    }

    /** The pointer the game named for the armed button's aim, or null for the client's own. */
    String aimPointer() {
        return aiming.isArmed() ? aiming.pointer() : null;
    }

    /** Whether a press of the armed button's place is held, to be put down where it is let go. */
    boolean placing() {
        return aiming.placement() != null && aiming.placement().pressed();
    }

    /**
     * Draw what the game says may be done with the selection, when the client's own HUD is up.
     *
     * <p>A game that never asked for a bar sends no buttons and nothing is drawn, which is what every
     * game had before there was one.
     */
    void updateCommandBar() {
        var snapshot = this.snapshot.get();
        if (!clientHud.getAsBoolean() || commandBarHidden) {
            if (commandBar != null) {
                commandBar.hide();
            }
            return;
        }
        var buttons = snapshot == null ? java.util.List.<uz.dukeengine.core.view.CommandButton>of()
                : snapshot.commands();
        if (commandBar == null) {
            if (buttons.isEmpty()) {
                return; // nothing to draw and nothing to build for it
            }
            commandBar = new CommandBar(screens.craft(), fonts.apply(visuals.getMenuStyle().rowFont()));
            guiNode.attachChild(commandBar.node());
        }
        commandBar.unhide();
        commandBar.show(buttons, cam.getWidth());
        if (aiming.isArmed() && aiming.fromTheBar()
                && buttons.stream().noneMatch(one -> one.id().equals(aiming.button().id()))) {
            disarmButton(); // the thing that offered it is no longer selected: nothing is waiting to build
        }
    }

    /**
     * Whether the press at the cursor landed on the command bar, in which case it pressed a button and is
     * not the beginning of a selection.
     *
     * <p>Asked before the drag starts, like the minimap's: a click that is doing one thing must not also
     * be doing another, or building a barracks would deselect the base that was about to build it.
     */
    boolean commandBarClick() {
        if (commandBar == null || commandBar.isEmpty() || screens.now() != Screen.PLAYING) {
            return false;
        }
        var cursor = inputManager.getCursorPosition();
        var button = commandBar.at(cursor.x, cursor.y);
        if (button == null) {
            return false;
        }
        if (!button.available()) {
            return true; // dim: it takes the click and does nothing with it
        }
        pressButton(button);
        return true;
    }

    /**
     * A letter the bar shows on one of its buttons presses that button, as clicking it does — a button keeps the
     * key its label names. A dim one takes the key and does nothing, as it takes a click.
     */
    boolean pressBarKey(char letter) {
        var snapshot = this.snapshot.get();
        if (commandBar == null || commandBar.isEmpty() || commandBarHidden || snapshot == null) {
            return false;
        }
        for (var button : snapshot.commands()) {
            if (button.hotkey() != null && button.hotkey().equalsIgnoreCase(String.valueOf(letter))) {
                if (button.available()) {
                    pressButton(button);
                }
                return true;
            }
        }
        return false;
    }

    private void pressButton(uz.dukeengine.core.view.CommandButton button) {
        if (button.aim() != uz.dukeengine.core.view.CommandButton.Aim.NOW) {
            armButton(button); // it needs a place or a thing first; the next click supplies it
            return;
        }
        // Straight to the game, which turns it into one of its own commands and posts it -- the road
        // every order already travels. Nothing here touches the world.
        game.get().pressCommand(button.id());
        orders.answerOrder(button.id(), orders.selectedIds());
    }


    /**
     * Arm a button that needs a place or a thing: the next click supplies it, and a right click or Escape
     * thinks better of it — the same two-step a hotkey that aims has always had.
     */
    private void armButton(uz.dukeengine.core.view.CommandButton button) {
        arm(button, true, 0f, null, null, null);
    }

    /**
     * Arm a button of the game's own canvas exactly as the bar arms its own — see {@link Duke3D#aim}. The game is
     * told how it ended.
     */
    void armFromGame(uz.dukeengine.core.view.CommandButton button, float radius, String pointer, AimDecal decal,
            java.util.function.Consumer<AimOutcome> told) {
        arm(button, false, radius, pointer, decal, told);
    }

    private void arm(uz.dukeengine.core.view.CommandButton button, boolean fromTheBar, float radius, String pointer,
            AimDecal decal, java.util.function.Consumer<AimOutcome> told) {
        skillKeys.disarm(); // a hotkey armed first is given up: one thing waits for the next click, never two
        disarmButton();
        if (button.aim() == uz.dukeengine.core.view.CommandButton.Aim.NOW) {
            return; // nothing to wait for: a button that needs no place is pressed, not armed
        }
        aiming.arm(button, fromTheBar, radius, pointer, decal, told);
        if (button.aim() == uz.dukeengine.core.view.CommandButton.Aim.GROUND) {
            showGhost(button.ghost());
        }
    }

    /** The armed button given up by the game, as Escape gives it up — see {@link Duke3D#giveUpAim}. */
    void giveUpAimFromGame() {
        disarmButton();
    }

    /** The armed button used by the game at {@code place}, as a click there would use it — see {@link Duke3D#useAim}. */
    void useAimFromGame(Coord3D place) {
        var press = aiming.useAt(place);
        if (press == null) {
            return;
        }
        forgetTheAim();
        game.get().pressCommand(press.button().id(), press.place(), press.facing(), press.target());
        orders.answerOrder(press.button().id(), orders.selectedIds());
        orders.markOrder(place.x(), place.y(), -1, OrderMarkers.Kind.MOVE);
    }

    /** Give the armed button up: nothing sent, and whoever armed it told so. */
    void disarmButton() {
        if (!aiming.isArmed()) {
            return;
        }
        aiming.giveUp();
        forgetTheAim();
    }

    /** Take down what an aim put up: the ghost, the circle, and the game's question about the place. */
    private void forgetTheAim() {
        game.get().setAim(null, null, 0f);
        if (ghost != null) {
            ghost.remove();
            ghost = null;
        }
        if (aimRing != null) {
            aimRing.hide();
        }
        if (aimDecal != null) {
            aimDecal.hide();
        }
    }

    /**
     * The armed button's press: the thing, sent at once — or, for a place, where it goes down. A place is
     * sent when the press is let go, so a drag in between can turn it; see {@link PlacementDrag}.
     */
    void aimArmedButton() {
        var button = aiming.button();
        if (button.aim() == uz.dukeengine.core.view.CommandButton.Aim.UNIT) {
            var unit = pointer.pickUnit();
            if (unit == null) {
                return; // it had to be a thing, and the click found none
            }
            var press = aiming.target(unit.view.id());
            forgetTheAim();
            game.get().pressCommand(press.button().id(), null, 0f, press.target());
            orders.answer(ThingAnswer.toThePress(button, visuals,
                    AnswerTarget.of(unit.view, game.get().getLocalPlayerIndex())), unit.view, orders.selectedIds());
            return;
        }
        var ground = pointer.pickGround();
        var cursor = inputManager.getCursorPosition();
        aiming.placement().press(cursor.x, cursor.y, ground == null ? null : new Coord3D(ground.x, ground.z, 0f));
    }

    /**
     * The press let go: put down where it went down, facing the way it was dragged or its button's way.
     *
     * <p>A place the simulation last said will not do is not sent, and the button stays armed — the ghost
     * was red, and a click on red is a player looking for somewhere green. The simulation's word decides
     * either way: a place sent is judged again when its order is applied, and refused there costs nothing.
     */
    void releaseArmedButton() {
        var snapshot = this.snapshot.get();
        var press = aiming.putDown(snapshot != null && snapshot.aimFits(),
                refused -> game.get().refusePress(refused.button().id(), refused.place(), refused.facing()));
        if (press == null) {
            return;
        }
        forgetTheAim();
        game.get().pressCommand(press.button().id(), press.place(), press.facing(), -1);
        orders.answerOrder(press.button().id(), orders.selectedIds());
        orders.markOrder(press.place().x(), press.place().y(), -1, OrderMarkers.Kind.MOVE);
    }

    /**
     * What the armed button will put down, drawn where it would stand.
     *
     * <p>Named by the game as a template — drawn as that template is drawn, at its size and turned its
     * way — or, failing that, as a model's path. One flat translucent colour over the whole of it, because
     * a ghost is a question, not the building: what it answers is green or red.
     */
    private void showGhost(String named) {
        if (named == null) {
            return;
        }
        var look = looks.visualFor(named);
        Spatial body = look != null && look.modelPath != null ? bodies.buildBody(look, java.util.List.of()) : null;
        if (body == null) {
            body = bodies.asFiled(named, "ghost");
            if (body == null) {
                return;
            }
        }
        var ghostLook = visuals.getGhostLook();
        if (ghostLook != null && visuals.getHouseColour() != null) {
            var game = this.game.get();
            Bodies.paintOwner(body, visuals.getHouseColour(),
                    Materials.colour(game.getColor(game.getLocalPlayerIndex())));
        }
        ghost = new Ghost(assetManager, markerNode, body, ghostLook);
    }

    /**
     * Every frame an armed button waits: tell the game where the cursor is, and draw the ghost there in the
     * colour the simulation last gave for it — a frame behind the cursor, which is not worth racing for.
     */
    void followTheAim() {
        var snapshot = this.snapshot.get();
        var placement = aiming.placement();
        if (!aiming.isArmed() || placement == null) {
            return;
        }
        var ground = pointer.pickGround();
        var cursor = inputManager.getCursorPosition();
        placement.cursor(cursor.x, cursor.y, ground == null ? null : new Coord3D(ground.x, ground.z, 0f));
        var where = placement.where();
        if (where == null) {
            return;
        }
        // Asked at the facing the ghost has now: for a box the footprint the player sees is the question.
        game.get().setAim(aiming.button().id(), where, placement.facing());
        boolean fits = snapshot != null && snapshot.aimFits();
        if (ghost != null) {
            ghost.node().setLocalTranslation(where.x(), floor.at(where.x(), where.y()), where.y());
            ghost.node().setLocalRotation(PlacementDrag.turnedTo(placement.facing()));
            ghost.answer(fits, snapshot == null ? java.util.List.of() : snapshot.aimMarks(), floor::at);
        }
        if (aiming.radius() > 0f && aiming.decal() != null) {
            if (aimDecal == null) {
                aimDecal = new GroundDecal(assetManager, markerNode);
            }
            aimDecal.show(where, aiming.radius(), aiming.decal(), snapshot.frame(), floor::at);
        } else if (aiming.radius() > 0f) {
            var look = visuals.getRangeLook() == null ? RangeLook.DEFAULT : visuals.getRangeLook();
            if (aimRing == null) {
                aimRing = new GroundRing(assetManager, markerNode, look.bandWidth(), look.segments(),
                        look.brightness());
            }
            aimRing.show(where, aiming.radius(), look.height(), fits ? look.allowColour() : look.denyColour(),
                    look.edgeAlpha(), look.fillAlpha(), floor::at);
        }
    }
}
