package uz.dukeengine.client3d;

import com.jme3.input.InputManager;
import com.jme3.math.Ray;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.game.DukeGame;

/**
 * What the pointer is on in the world: the thing under it, the ground it meets — a hill's near face, a bridge's deck —
 * or where the scheme aims instead, and whether that ground is somewhere the player could aim at or stand on.
 */
final class WorldPointer {

    /** How near the pointer a quick cast takes a creature that is not under it. */
    static final float QUICK_CAST_REACH = 6f;

    private final Camera cam;
    private final InputManager inputManager;
    private final UnitScene unitScene;
    private final Supplier<DukeGame> game;
    /** Controls of a kind the client has not got itself, which may aim without a pointer. */
    private final Supplier<ControlScheme> scheme;
    /** The part of the window the world is drawn in — see {@link Duke3D#worldView}. */
    private final Supplier<WorldRegion> worldRegion;
    /** What the player has seen, or null for a game that is never discovered. */
    private final Supplier<Discovery> discovery;
    private final WorldMoments.Floor floor;
    /** How low and how high this map's ground stands: what the ray under the cursor is walked between. */
    private GroundSpan groundSpan = new GroundSpan(0f, 0f);

    WorldPointer(Camera cam, InputManager inputManager, UnitScene unitScene, Supplier<DukeGame> game,
            Supplier<ControlScheme> scheme, Supplier<WorldRegion> worldRegion, Supplier<Discovery> discovery,
            WorldMoments.Floor floor) {
        this.cam = cam;
        this.inputManager = inputManager;
        this.unitScene = unitScene;
        this.game = game;
        this.scheme = scheme;
        this.worldRegion = worldRegion;
        this.discovery = discovery;
        this.floor = floor;
    }

    /** A new world's grid: how low and how high its ground stands, for the ray to be walked between. */
    void readGrid(PathGrid grid) {
        groundSpan = grid == null ? new GroundSpan(0f, 0f) : GroundSpan.of(grid);
    }

    /** Whether the pointer is over the world: in the part of the window it is drawn in. */
    boolean pointerOnTheWorld() {
        var at = inputManager.getCursorPosition();
        return worldRegion.get().contains(at.x, at.y, cam.getWidth(), cam.getHeight());
    }

    /**
     * Where the ray through a screen point meets the ground.
     *
     * <p>A corner above the horizon never meets it, so the ray is followed a long
     * way instead and the projection clamps the result to the map — which is what
     * the player sees anyway: the view running off the edge of the world.
     */
    Vector3f groundUnder(float screenX, float screenY) {
        var near = cam.getWorldCoordinates(new Vector2f(screenX, screenY), 0f);
        var dir = cam.getWorldCoordinates(new Vector2f(screenX, screenY), 1f)
                .subtract(near).normalizeLocal();
        var terrain = game.get().getTerrain();
        var hit = GroundRay.groundHit(near, dir, groundSpan, terrain == null ? 5f : terrain.getCellSize() / 2f,
                floor::at);
        return terrain == null || !terrain.hasDecks() ? hit : GroundRay.deckHit(near, dir, hit, terrain.decks());
    }

    /**
     * The unit under the mouse cursor, or {@code null}.
     * The unit under the cursor, found by the boxes of the pieces of it drawn now
     * rather than by its triangles — see {@link Picking}.
     *
     * <p>Triangle-accurate picking stopped working the day the creatures got
     * models. A skinned mesh is deformed on the graphics card; the copy this side
     * keeps is the pose it was modelled in, so the shape the ray is tested against
     * is not the shape on screen. Clicking a monster missed it and the click fell
     * through to the floor behind — which reads as an order given to the wrong
     * place rather than as a click that hit nothing.
     *
     * <p>A piece's box does not care how a thing is posed. It is a more
     * generous target than the mesh, which is the right way to be wrong: in a
     * dungeon the cost of a click landing on the monster you meant is nothing, and
     * the cost of it landing on the floor behind him is a hero who walks into a
     * room instead of shooting into it. The box of the whole model in the world's
     * axes was too generous: a hidden muzzle flash made a tank a third longer, and
     * a building put down at 45 degrees twice its area, so a click on the ground
     * beside one's own things picked them and the move was never given.
     *
     * <p>Only things the game says are selectable, so an arrow crossing in front
     * of a monster cannot be clicked instead of it.
     */
    UnitNode pickUnit() {
        if (!pointerOnTheWorld()) {
            return null; // nothing of the world under a pointer off the world's part of the window
        }
        var click = inputManager.getCursorPosition();
        var near = cam.getWorldCoordinates(new Vector2f(click.x, click.y), 0f);
        var ray = new Ray(near, cam.getWorldCoordinates(new Vector2f(click.x, click.y), 1f)
                .subtract(near).normalizeLocal());
        UnitNode nearest = null;
        float closest = Float.MAX_VALUE;
        for (var node : unitScene.nodes()) {
            var picked = unitScene.pickedAs(node);
            if (picked == null || !unitScene.pickable(picked.view) || node.body == null) {
                continue;
            }
            // The nearest hit along the ray wins, so clicking a monster standing in
            // front of another picks the one you can see — not the one whose middle
            // is nearer, which a big thing behind a small one can be.
            float away = Picking.drawnHit(node.body, ray);
            if (away < closest) {
                closest = away;
                nearest = picked;
            }
        }
        return nearest;
    }

    /**
     * Where the mouse is pointing at the ground.
     *
     * <p>One way of answering this, used by everything that asks — orders,
     * markers, aimed skills, the minimap's outline. There were two: this one met
     * the plane at zero and was what every click actually went through, while the
     * one that knew about storeys was only being asked by the outline. So the
     * click kept landing a pace beyond the cursor upstairs however carefully the
     * other was fixed.
     */
    Vector3f pickGround() {
        var aimed = schemeAim();
        if (aimed != null) {
            return aimed; // no pointer: where the scheme aims
        }
        var click = inputManager.getCursorPosition();
        return groundUnder(click.x, click.y);
    }

    /** Where the scheme aims what is armed, on the ground; null while it aims nothing. */
    Vector3f schemeAim() {
        var scheme = this.scheme.get();
        var at = scheme.aiming() ? scheme.aimPoint() : null;
        return at == null ? null : new Vector3f(at[0], floor.at(at[0], at[1]), at[1]);
    }

    /** The creature nearest {@code ground} within {@link #QUICK_CAST_REACH} of it, the lowest id on a tie; or null. */
    UnitNode nearTheGround(Vector3f ground) {
        var views = new ArrayList<uz.dukeengine.core.view.UnitView>();
        for (var node : unitScene.nodes()) {
            if (node.view != null) {
                views.add(node.view);
            }
        }
        int id = nearestWithin(views, ground.x, ground.z, QUICK_CAST_REACH);
        return id < 0 ? null : unitScene.node(id);
    }

    /** The living creature nearest ({@code x}, {@code y}) within {@code reach}, the lowest id on a tie; -1 for none. */
    static int nearestWithin(List<uz.dukeengine.core.view.UnitView> views, float x, float y, float reach) {
        int best = -1;
        float bestGap = Float.MAX_VALUE;
        for (var view : views) {
            if (view.maxHealth() <= 0f || view.health() <= 0f) {
                continue;
            }
            float gap = (float) Math.hypot(view.x() - x, view.y() - y);
            if (gap <= reach && (gap < bestGap || gap == bestGap && view.id() < best)) {
                best = view.id();
                bestGap = gap;
            }
        }
        return best;
    }

    /**
     * Whether that spot is somewhere the player could stand and has already seen.
     *
     * <p>Asked here rather than of the simulation because both halves are the
     * client's to answer. The map's shape it has; what the player has <em>seen</em>
     * of it only it has — that is a fact about this screen, not about the world,
     * and the simulation would be wrong to hold it.
     *
     * <p>A game with no discovery at all has seen everything, which is the right
     * answer rather than a special case: with the whole map on screen there is no
     * such thing as aiming into the dark.
     */
    boolean isOpenAndSeen(Vector3f ground) {
        var grid = game.get().getTerrain();
        if (grid == null) {
            return true;
        }
        int cx = grid.toCellX(new Coord3D(ground.x, ground.z, 0f));
        int cy = grid.toCellY(new Coord3D(ground.x, ground.z, 0f));
        if (!grid.inBounds(cx, cy) || grid.isTerrainBlocked(cx, cy)) {
            return false;
        }
        var discovery = this.discovery.get();
        return discovery == null || discovery.stateAt(cx, cy) != Discovery.State.UNSEEN;
    }

    /**
     * Whether that spot is somewhere he could put his feet, as far as the player
     * knows.
     *
     * <p>A different question from {@link #isOpenAndSeen}, and the difference is
     * the whole of what each is for. A skill has to be <em>aimed</em>, so it may
     * only be sent somewhere the player has actually seen. A walking order is not
     * aimed — it may be given anywhere on the map, the dark included, and how far
     * he gets is the simulation's business.
     *
     * <p>So the dark reads as open here. Not as a kindness: a pointer that turned
     * away over undiscovered stone would be reading the map out to the player
     * through the shape of his own cursor.
     *
     * <p>What it does say no to is a place he plainly cannot stand — a wall, or
     * the barrel in the middle of the room. That is a warning and not a veto: the
     * click still goes, and he walks as near it as the floor allows.
     */
    boolean couldStandThere(Vector3f ground) {
        var grid = game.get().getTerrain();
        if (grid == null) {
            return true;
        }
        var at = new Coord3D(ground.x, ground.z, 0f);
        int cx = grid.toCellX(at);
        int cy = grid.toCellY(at);
        if (!grid.inBounds(cx, cy)) {
            return false;
        }
        var discovery = this.discovery.get();
        if (discovery != null && discovery.stateAt(cx, cy) == Discovery.State.UNSEEN) {
            return true; // unknown ground is not known to be bad
        }
        return !grid.isBlocked(cx, cy);
    }
}
