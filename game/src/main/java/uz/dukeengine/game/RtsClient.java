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

    private volatile java.util.function.Supplier<String> contextOrder = () -> null;

    void setContextOrder(java.util.function.Supplier<String> contextOrder) {
        this.contextOrder = contextOrder == null ? () -> null : contextOrder;
    }

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
            if (shown(event, everything)) {
                events.add(event);
            }
        }
        var units = new ArrayList<UnitView>();
        var shown = new ArrayList<>(everything ? logic.getObjects() : logic.getVisibleObjects(viewerPlayer));
        if (!everything) {
            // A thing drawn along a line lies where the ground under it is, and is fogged as the ground is: shown.
            var seen = new java.util.HashSet<>(shown);
            for (var object : logic.getObjects()) {
                if (object.getSpan() != null && !seen.contains(object)) {
                    shown.add(object);
                }
            }
        }
        for (var object : shown) {
            var carrier = object.isContained() ? uz.dukeengine.rts.module.ContainModule.holdOf(object) : null;
            boolean rider = carrier != null && carrier.riderBone() != null;
            if (object.isContained() && !rider || object.hasStatus(uz.dukeengine.core.thing.ObjectStatus.HIDDEN)) {
                continue; // riding inside a transport, or not there to be seen — not on the map
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
                    object.isKindOf(RtsKinds.SELECTABLE) && !rider
                            && !object.hasStatus(uz.dukeengine.core.thing.ObjectStatus.SOLD)
                            && !object.hasStatus(uz.dukeengine.core.thing.ObjectStatus.UNSELECTABLE),
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
                    java.util.List.copyOf(object.getConditions()),
                    built(object),
                    rider ? carrier.getOwner().getId().value() : -1,
                    everything || object.getPlayerIndex() == viewerPlayer || logic.getRelationship(viewerPlayer,
                            object.getPlayerIndex()) == uz.dukeengine.core.player.Relationship.ALLIES,
                    object.getSpan(),
                    ai != null));
        }
        var rallies = new ArrayList<uz.dukeengine.game.view.RallyView>();
        for (var object : shown) {
            if (!everything && object.getPlayerIndex() != viewerPlayer) {
                continue; // a rally point is shown to its own side
            }
            var production = object.findModule(uz.dukeengine.rts.module.ProductionUpdate.class);
            var line = production == null ? null : production.rallyLine();
            if (line != null) {
                rallies.add(new uz.dukeengine.game.view.RallyView(object.getId().value(), line.rallyPoint(),
                        line.points(), line.nodes()));
            }
        }
        var beams = new ArrayList<uz.dukeengine.game.view.BeamView>();
        for (var beam : logic.getBeams()) {
            if (everything || logic.canSee(viewerPlayer, beam.from()) || logic.canSee(viewerPlayer, beam.to())) {
                beams.add(new uz.dukeengine.game.view.BeamView(beam.id(), beam.look(), beam.from(), beam.to(),
                        beam.width()));
            }
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
                everything || logic.isMapRevealedTo(viewerPlayer),
                contextOrder.get(),
                beams,
                rallies);
    }

    /**
     * Whether this viewer is shown {@code event}: where it happened is in its sight, and it is no shot of a thing hidden
     * from it — or, for a text that says who sees it, it is one of those players, or the owner of the thing the text is
     * about, or sees that thing.
     */
    private boolean shown(uz.dukeengine.core.event.WorldEvent event, boolean everything) {
        if (event instanceof uz.dukeengine.core.event.TextFloated text
                && (!text.shownTo().isEmpty() || text.about() != null)) {
            if (everything) {
                return true;
            }
            if (!text.shownTo().isEmpty()) {
                return text.shownTo().contains(viewerPlayer);
            }
            var thing = logic.findObject(text.about());
            return thing != null && logic.canSee(viewerPlayer, thing);
        }
        var where = event.where();
        return (everything || where == null || logic.canSee(viewerPlayer, where)) && !firedUnseen(event, everything);
    }

    /**
     * Whether {@code event} is a shot fired by something hidden from this viewer, whose shots it is not shown — but a
     * shot the game shows anyway, a mine's or a weapon's that says so; and, where the game keeps them to the owner,
     * one fired by a thing hidden from anyone, shown to an ally no more than to an enemy.
     */
    private boolean firedUnseen(uz.dukeengine.core.event.WorldEvent event, boolean everything) {
        if (!(event instanceof uz.dukeengine.rts.event.WeaponFired fired)) {
            return false;
        }
        var shooter = logic.findObject(fired.shooter());
        if (shooter == null || shownAnyway(shooter, fired.weapon())) {
            return false;
        }
        if (everything) {
            return shooter.hasStatus(uz.dukeengine.core.thing.ObjectStatus.HIDDEN);
        }
        if (shooter.isHiddenFrom(viewerPlayer)) {
            return true;
        }
        return logic.isHiddenShotsToOwnerOnly() && viewerPlayer != shooter.getPlayerIndex()
                && hiddenFromAnyone(shooter);
    }

    /** Whether the game shows {@code shooter}'s shots with {@code weapon} though it is hidden. */
    private boolean shownAnyway(uz.dukeengine.core.thing.GameObject shooter, String weapon) {
        for (var kind : logic.getShownWhenHidden()) {
            if (shooter.isKindOf(kind)) {
                return true;
            }
        }
        var fired = logic.findWeapon(weapon);
        return fired != null && fired.shownWhenHidden();
    }

    private boolean hiddenFromAnyone(uz.dukeengine.core.thing.GameObject shooter) {
        for (int player = 0; player < logic.getPlayerList().getPlayerCount(); player++) {
            if (player != shooter.getPlayerIndex() && shooter.isHiddenFrom(player)) {
                return true;
            }
        }
        return false;
    }

    /**
     * How far a thing is built, for the client to draw it rising: a site's progress, a sold building's share of the
     * way down (from 1 to the reference's -0.5), 1 for everything else.
     */
    private static float built(uz.dukeengine.core.thing.GameObject object) {
        if (object.hasStatus(uz.dukeengine.core.thing.ObjectStatus.UNDER_CONSTRUCTION)) {
            var site = object.findModule(uz.dukeengine.rts.construction.ConstructionSite.class);
            return site == null ? 1f : site.progress();
        }
        if (object.hasStatus(uz.dukeengine.core.thing.ObjectStatus.SOLD)) {
            var sale = object.findModule(uz.dukeengine.rts.construction.Selling.Coming.class);
            return sale == null ? 1f : Math.min(1f, sale.share() / 100f);
        }
        return 1f;
    }

}
