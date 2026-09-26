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
    private volatile java.util.function.Supplier<uz.dukeengine.game.view.AimAnswer> aimAnswer =
            () -> uz.dukeengine.game.view.AimAnswer.YES;

    void setAimAnswer(java.util.function.Supplier<uz.dukeengine.game.view.AimAnswer> aimAnswer) {
        this.aimAnswer = aimAnswer == null ? () -> uz.dukeengine.game.view.AimAnswer.YES : aimAnswer;
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

    /** The game's words for its things' moments, added to what each holds; null for none. */
    private volatile uz.dukeengine.game.view.MomentWords momentWords;
    /** Each thing's turret's turn at the last snapshot, to tell the frames it turns: the simulation's thread only. */
    private java.util.Map<Integer, Float> turretTurns = new java.util.HashMap<>();

    void setMomentWords(uz.dukeengine.game.view.MomentWords words) {
        this.momentWords = words;
    }

    /** The words {@code object} holds, and the game's words for its moments now. */
    private java.util.List<String> wordsOf(uz.dukeengine.core.thing.GameObject object, boolean moving,
            uz.dukeengine.rts.module.WeaponUpdate weapon, java.util.Map<Integer, Float> turns) {
        var words = momentWords;
        if (words == null) {
            return java.util.List.copyOf(object.getConditions());
        }
        var all = new java.util.TreeSet<>(object.getConditions());
        addWord(all, moving ? words.moving() : null);
        if (weapon != null) {
            addWord(all, weapon.isAttacking() ? words.attacking() : null);
            for (var slot : weapon.slotsNow(logic.getFrame() - 1)) { // the frame just run
                if (slot.fired()) {
                    addWord(all, uz.dukeengine.game.view.MomentWords.of(words.firing(), slot.slot()));
                } else if (slot.status() == uz.dukeengine.rts.module.WeaponStatus.BETWEEN_SHOTS) {
                    addWord(all, uz.dukeengine.game.view.MomentWords.of(words.betweenShots(), slot.slot()));
                } else if (slot.status() == uz.dukeengine.rts.module.WeaponStatus.RELOADING) {
                    addWord(all, uz.dukeengine.game.view.MomentWords.of(words.reloading(), slot.slot()));
                } else if (slot.status() == uz.dukeengine.rts.module.WeaponStatus.PRE_ATTACK) {
                    addWord(all, uz.dukeengine.game.view.MomentWords.of(words.preAttack(), slot.slot()));
                }
            }
        }
        if (words.turretTurning() != null) {
            for (var module : object.getModules()) {
                if (module instanceof uz.dukeengine.rts.module.Turret turret) {
                    float turn = turret.turretTurn();
                    var was = turretTurns.get(object.getId().value());
                    turns.put(object.getId().value(), turn);
                    addWord(all, was != null && was != turn ? words.turretTurning() : null);
                    break;
                }
            }
        }
        return java.util.List.copyOf(all);
    }

    private static void addWord(java.util.Set<String> words, String word) {
        if (word != null) {
            words.add(word);
        }
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
        var turns = new java.util.HashMap<Integer, Float>();
        for (var object : shown) {
            var view = viewOf(object, everything, turns);
            if (view != null) {
                units.add(view);
                noteInSight(object, view);
            }
        }
        if (!everything) {
            addWhatItStillShows(units);
        }
        turretTurns = turns;
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
        var effects = new ArrayList<uz.dukeengine.game.view.EffectView>();
        if (!logic.getRidingEffects().isEmpty()) {
            var drawn = new java.util.HashSet<Integer>();
            units.forEach(view -> drawn.add(view.id()));
            for (var effect : logic.getRidingEffects()) {
                if (drawn.contains(effect.thing().value())) { // seen where the thing it rides is
                    effects.add(new uz.dukeengine.game.view.EffectView(effect.id(), effect.name(),
                            effect.thing().value(), effect.bone(), effect.offset()));
                }
            }
        }
        var player = everything ? null : logic.getRtsPlayer(viewerPlayer);
        var aimed = aimAnswer.get();
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
                aimed.fits(),
                attackable.getAsBoolean(),
                camera.get(),
                everything || logic.isMapRevealedTo(viewerPlayer),
                contextOrder.get(),
                beams,
                rallies,
                effects,
                everything || logic.getSightCells() == null ? null : logic.getSightCells().view(viewerPlayer),
                aimed.marks());
    }

    /**
     * What this viewer is shown of {@code object}, or null for nothing — carried inside a hold that shows none of its
     * passengers, or not there to be seen.
     */
    private UnitView viewOf(uz.dukeengine.core.thing.GameObject object, boolean everything,
            java.util.Map<Integer, Float> turns) {
        var carrier = object.isContained() ? uz.dukeengine.rts.module.ContainModule.holdOf(object) : null;
        boolean rider = carrier != null && (carrier.rides(object) || carrier.showsPassengers());
        if (object.isContained() && !rider || object.hasStatus(uz.dukeengine.core.thing.ObjectStatus.HIDDEN)) {
            return null; // riding inside a transport, or not there to be seen — not on the map
        }
        var template = object.getTemplate();
        var position = object.getPosition();
        var body = object.getBody();
        var ai = object.getLocomotor();
        var weapon = object.findModule(uz.dukeengine.rts.module.WeaponUpdate.class);
        var production = object.findModule(uz.dukeengine.rts.module.ProductionUpdate.class);
        var hold = object.findModule(uz.dukeengine.rts.module.ContainModule.class);
        var regard = everything || object.getPlayerIndex() == viewerPlayer
                ? uz.dukeengine.core.player.Relationship.ALLIES
                : logic.getRelationship(viewerPlayer, object.getPlayerIndex());
        boolean allied = regard == uz.dukeengine.core.player.Relationship.ALLIES;
        return new UnitView(
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
                wordsOf(object, ai != null && ai.isMoving(), weapon, turns),
                built(object),
                rider ? carrier.getOwner().getId().value() : -1,
                allied,
                object.getSpan(),
                ai != null,
                object.getDrawnAs(),
                // In the colours of the player it is disguised as to a viewer not on its side, as the reference
                // draws a disguised bomb truck to its enemies; its own to its side.
                allied || object.getWearsColoursOf() < 0 ? object.getPlayerIndex() : object.getWearsColoursOf(),
                object.getDrawnOpacity(),
                regard == uz.dukeengine.core.player.Relationship.ENEMIES,
                ai == null ? 0f : ai.speedMoved(),
                false,
                turretsOf(object),
                object.getLift(),
                object.getYaw(),
                object.getCorners());
    }

    /** How {@code object}'s turrets stand, as its game's {@code Turret} has them; none for a thing without one. */
    private static uz.dukeengine.game.view.Turrets turretsOf(uz.dukeengine.core.thing.GameObject object) {
        for (var module : object.getModules()) {
            if (module instanceof uz.dukeengine.rts.module.Turret turret) {
                return new uz.dukeengine.game.view.Turrets(turret.turretTurn(), turret.turretPitch(),
                        turret.altTurretTurn(), turret.altTurretPitch());
            }
        }
        return uz.dukeengine.game.view.Turrets.NONE;
    }

    /** Which things this viewer goes on seeing as he last saw them, their ground fogged — see {@link #remember}. */
    private volatile java.util.function.Predicate<uz.dukeengine.core.thing.GameObject> remembers;
    /** How many frames a thing out of this viewer's sight stays in his view, and a dead one. */
    private volatile int keepFrames;
    private volatile int keepDeadFrames;
    /** The last view this viewer had of each thing the game remembers, while it was in sight. */
    private final java.util.Map<Integer, UnitView> remembered = new java.util.LinkedHashMap<>();
    /** The last frame this viewer had each thing in sight. */
    private final java.util.Map<Integer, Integer> lastInSight = new java.util.LinkedHashMap<>();
    /** The viewer the memories above are his. */
    private int memoriesOf = Integer.MIN_VALUE;

    /**
     * Which things a viewer goes on seeing once seen, drawn as he last saw each while it was in sight for as long as
     * the ground under it is seen but not in sight — the reference's ghosts of still things ({@code W3DGhostObject}):
     * dropped once that ground is in sight again. Asked on the simulation thread; null for none.
     */
    void remember(java.util.function.Predicate<uz.dukeengine.core.thing.GameObject> which) {
        this.remembers = which;
    }

    /**
     * How many frames a thing out of a viewer's sight stays in his view as it is — the reference's 60, a plane that
     * pops out of the shroud, fires and heads back — and a dead one, its 150 ({@code GameClient::update}).
     */
    void keepOutOfSight(int frames, int deadFrames) {
        this.keepFrames = Math.max(0, frames);
        this.keepDeadFrames = Math.max(0, deadFrames);
    }

    private void noteInSight(uz.dukeengine.core.thing.GameObject object, UnitView view) {
        if (memoriesOf != viewerPlayer) {
            remembered.clear();
            lastInSight.clear();
            memoriesOf = viewerPlayer;
        }
        int id = view.id();
        lastInSight.put(id, logic.getFrame());
        var which = remembers;
        if (which != null && which.test(object)) {
            remembered.put(id, view);
        }
    }

    /**
     * What this viewer still sees of things out of his sight: each thing still in the world a while after he last had
     * it in sight, as it is — a thing gone from the world is gone from his view too; and each thing he remembers as he
     * last saw it, while its ground is seen and not in sight — dropped once its ground is in sight again, where it is
     * drawn as it is now, or not at all.
     */
    private void addWhatItStillShows(java.util.List<UnitView> units) {
        var drawn = new java.util.HashSet<Integer>();
        units.forEach(view -> drawn.add(view.id()));
        int frame = logic.getFrame();
        int longest = Math.max(keepFrames, keepDeadFrames);
        for (var it = lastInSight.entrySet().iterator(); it.hasNext();) {
            var seen = it.next();
            int id = seen.getKey();
            if (drawn.contains(id) || remembered.containsKey(id)) {
                continue; // in sight; or remembered, which its ghost stands for once its ground is fogged
            }
            var object = logic.findObject(new uz.dukeengine.core.thing.ObjectId(id));
            int since = frame - seen.getValue();
            if (object == null || since > longest) {
                it.remove();
                continue;
            }
            if (since > (object.isEffectivelyDead() ? keepDeadFrames : keepFrames)) {
                continue;
            }
            var view = viewOf(object, false, new java.util.HashMap<>());
            if (view != null) {
                units.add(view);
                drawn.add(id);
            }
        }
        var cells = logic.getSightCells();
        for (var it = remembered.entrySet().iterator(); it.hasNext();) {
            var memory = it.next();
            if (drawn.contains(memory.getKey())) {
                continue;
            }
            var view = memory.getValue();
            var ground = new uz.dukeengine.core.math.Coord3D(view.x(), view.y(), view.z());
            boolean inSight = cells != null ? cells.inSight(viewerPlayer, ground) : logic.canSee(viewerPlayer, ground);
            if (inSight) {
                it.remove(); // its ground in sight again: what is there now is drawn, if anything is
            } else if (cells == null || cells.everSeen(viewerPlayer, ground)) {
                units.add(view.asRemembered());
            }
        }
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
