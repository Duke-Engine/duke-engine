package uz.dukeengine.game;

import java.util.ArrayList;
import uz.dukeengine.core.GameClient;
import uz.dukeengine.rts.thing.RtsKinds;
import uz.dukeengine.game.view.CommandButton;
import uz.dukeengine.game.view.UnitView;
import uz.dukeengine.game.view.WorldSnapshot;
import uz.dukeengine.rts.module.PowerGrid;

/**
 * The presentation client behind {@link DukeGame}: once per client frame it
 * copies what the local player can see into an immutable {@link WorldSnapshot}
 * for the Swing layer to draw.
 *
 * <p>This is the only place simulation state crosses threads, and it crosses as
 * a deep copy — the window never touches live {@code GameObject}s, so the
 * deterministic logic stays single-threaded.
 */
final class RtsClient extends GameClient {

    /** The viewer of a machine that watches: every object, every moment, through nobody's fog. */
    static final int EVERYONE = Integer.MAX_VALUE;

    private final RtsLogic logic;
    private volatile int viewerPlayer = -1; // bound once players exist, at game start
    private volatile WorldSnapshot snapshot = WorldSnapshot.EMPTY;
    private volatile String banner = "";
    /** The game's own HUD line — the engine sets it, never reads it. */
    private volatile String status = "";

    /** Big centered message ("VICTORY") shown by the display layer. */
    void setBanner(String banner) {
        this.banner = banner == null ? "" : banner;
    }

    void setStatus(String status) {
        this.status = status == null ? "" : status;
    }

    /**
     * What the player may do with whatever he has selected.
     *
     * <p>Asked here rather than by the window, and that is the whole of why it is safe: it is asked on the
     * simulation thread, as the snapshot is built, so it may read live state -- what a barracks can train,
     * what this player can afford -- and what crosses is the answer rather than the objects.
     */
    private volatile java.util.function.Supplier<java.util.List<CommandButton>> commands = java.util.List::of;

    void setCommands(java.util.function.Supplier<java.util.List<CommandButton>> commands) {
        this.commands = commands == null ? java.util.List::of : commands;
    }

    /** Whether the place an armed button is aimed at would do, asked here for the same reason as the bar. */
    private volatile java.util.function.BooleanSupplier aimFits = () -> true;

    void setAimFits(java.util.function.BooleanSupplier aimFits) {
        this.aimFits = aimFits == null ? () -> true : aimFits;
    }

    /** Whether an attack on what the pointer is on would be taken, asked here for the same reason as the bar. */
    private volatile java.util.function.BooleanSupplier attackable = () -> true;

    void setAttackable(java.util.function.BooleanSupplier attackable) {
        this.attackable = attackable == null ? () -> true : attackable;
    }

    /** Where the game has put the camera, asked on the simulation thread as the snapshot is built; null for none. */
    private volatile java.util.function.Supplier<uz.dukeengine.game.view.CameraView> camera = () -> null;

    void setCamera(java.util.function.Supplier<uz.dukeengine.game.view.CameraView> camera) {
        this.camera = camera == null ? () -> null : camera;
    }

    RtsClient(RtsLogic logic) {
        this.logic = logic;
    }

    void setViewerPlayer(int viewerPlayer) {
        this.viewerPlayer = viewerPlayer;
    }

    /** The latest frame of the world; safe to call from any thread. */
    WorldSnapshot getSnapshot() {
        return snapshot;
    }

    @Override
    protected void render() {
        // Drained every client frame, viewer or not: the queue is bounded, and
        // leaving it to fill would silently start dropping the oldest moments.
        var drained = logic.drainEvents();
        if (viewerPlayer < 0) {
            return; // no viewer bound yet — nothing to show
        }
        // Fog applies to moments as much as to state: without this you would hear
        // an explosion in territory you have no eyes on.
        var events = new ArrayList<uz.dukeengine.core.event.WorldEvent>();
        boolean everything = viewerPlayer == EVERYONE;
        for (var event : drained) {
            var where = event.where();
            if (everything || where == null || logic.canSee(viewerPlayer, where)) {
                events.add(event);
            }
        }
        var units = new ArrayList<UnitView>();
        for (var object : everything ? logic.getObjects() : logic.getVisibleObjects(viewerPlayer)) {
            if (object.isContained()) {
                continue; // riding inside a transport — not on the map
            }
            var template = object.getTemplate();
            var position = object.getPosition();
            var body = object.getBody();
            var ai = object.getLocomotor();
            var weapon = object.findModule(uz.dukeengine.rts.module.WeaponUpdate.class);
            var production = object.findModule(uz.dukeengine.rts.module.ProductionUpdate.class);
            var hold = object.findModule(uz.dukeengine.rts.module.ContainModule.class);
            units.add(new UnitView(
                    object.getId().value(),
                    template.name(),
                    object.getPlayerIndex(),
                    position.x(),
                    position.y(),
                    object.getOrientation(),
                    body == null ? 0f : body.getHealth(),
                    body == null ? 0f : body.getMaxHealth(),
                    object.isKindOf(RtsKinds.STRUCTURE),
                    object.isKindOf(RtsKinds.SELECTABLE)
                            && !object.hasStatus(uz.dukeengine.core.thing.ObjectStatus.SOLD),
                    ai != null && ai.isMoving(),
                    weapon != null && weapon.isAttacking(),
                    production == null ? -1 : production.getQueueSize(),
                    position.z(),
                    object.getPitch(),
                    object.getRoll(),
                    object.keepsOwnHeight(),
                    object.statusBits(),
                    hold == null ? java.util.List.of()
                            : hold.getPassengers().stream().map(uz.dukeengine.core.thing.ObjectId::value).toList(),
                    java.util.List.copyOf(object.getConditions())));
        }
        var player = everything ? null : logic.getRtsPlayer(viewerPlayer);
        snapshot = new WorldSnapshot(
                logic.getFrame(),
                logic.getGameTimeSeconds(),
                logic.isGamePaused(),
                player == null ? 0 : player.getMoney(),
                everything ? 0 : PowerGrid.surplus(logic, viewerPlayer),
                units,
                events,
                banner,
                status,
                commands.get(),
                aimFits.getAsBoolean(),
                attackable.getAsBoolean(),
                camera.get(),
                everything || logic.isMapRevealedTo(viewerPlayer));
    }
}
