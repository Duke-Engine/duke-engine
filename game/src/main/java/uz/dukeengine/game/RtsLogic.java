package uz.dukeengine.game;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.function.Consumer;
import java.util.concurrent.ConcurrentLinkedQueue;
import uz.dukeengine.core.message.Command;
import uz.dukeengine.rts.RtsSimulation;
import uz.dukeengine.rts.message.GameMessage;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.combat.module.WeaponUpdate;

/**
 * The batteries-included RTS simulation behind {@link DukeGame}.
 *
 * <p>Every hand-rolled {@code GameLogic} subclass in the engine's tests wires the
 * same three commands to the same modules; this class makes that standard RTS
 * routing built in, Unity-style:
 * <ul>
 *   <li>{@code MoveTo} → the unit's {@link MoveUpdate} (pathfinds and walks)</li>
 *   <li>{@code AttackObject} → the unit's {@link WeaponUpdate} (engages)</li>
 *   <li>{@code StopMoving} → halts movement and holds fire</li>
 * </ul>
 *
 * <p>It also accepts commands from other threads (the Swing input layer) through
 * a concurrent inbox drained on the logic thread, runs the game's tick/interval
 * callbacks, and fires a defeat callback when a player who had units loses all
 * of them — the standard annihilation rule.
 */
final class RtsLogic extends RtsSimulation {

    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(RtsLogic.class.getName());

    /**
     * Commands posted from the UI thread, drained each frame on the logic thread.
     *
     * <p>{@link Command} rather than {@link GameMessage}: a game built on this may
     * declare commands of its own, and they belong in the same queue as the
     * standard orders — that queue is what makes input land on a frame boundary
     * and reach the replay log.
     */
    private final Queue<Command> inbox = new ConcurrentLinkedQueue<>();

    /** Where a command outside the RTS set goes, if the game handles any. */
    private Consumer<Command> gameCommands;

    /** Arbitrary work posted from other threads, run on the logic thread. */
    private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();

    /** When set, local commands go over the wire instead of straight in. */
    private MultiplayerSession session;

    void setSession(MultiplayerSession session) {
        this.session = session;
    }

    private final List<Runnable> tickCallbacks = new ArrayList<>();
    private final List<IntervalCallback> intervalCallbacks = new ArrayList<>();

    private final Set<Integer> everOwnedObjects = new HashSet<>();
    private final Set<Integer> defeated = new HashSet<>();
    private DefeatListener defeatListener;

    private record IntervalCallback(int everyFrames, Runnable action) {
    }

    @FunctionalInterface
    interface DefeatListener {
        void onDefeated(int playerIndex);
    }

    /** Thread-safe: post a command from any thread (typically the Swing EDT). */
    void post(Command command) {
        inbox.add(command);
    }

    /** Install the handler for the game's own commands. */
    void setGameCommandHandler(Consumer<Command> handler) {
        this.gameCommands = handler;
    }

    @Override
    protected void onOtherCommand(Command command) {
        if (gameCommands == null) {
            super.onOtherCommand(command); // no game set declared: still a mistake
            return;
        }
        gameCommands.accept(command);
    }

    /** Thread-safe: run {@code task} on the logic thread next frame. */
    void postTask(Runnable task) {
        tasks.add(task);
    }

    void addTickCallback(Runnable action) {
        tickCallbacks.add(action);
    }

    void addIntervalCallback(int everyFrames, Runnable action) {
        intervalCallbacks.add(new IntervalCallback(Math.max(1, everyFrames), action));
    }

    void setDefeatListener(DefeatListener listener) {
        this.defeatListener = listener;
    }

    @Override
    protected void onRtsCommand(GameMessage command) {
        switch (command) {
            case GameMessage.MoveTo move -> {
                var movers = new java.util.ArrayList<uz.dukeengine.core.thing.GameObject>();
                for (var id : move.units()) {
                    var unit = findObject(id);
                    if (unit == null || unit.getPlayerIndex() != move.playerIndex()) {
                        continue; // gone, or not the issuer's unit to command
                    }
                    uz.dukeengine.combat.module.Errand.giveUpAll(unit);
                    var weapon = unit.findModule(WeaponUpdate.class);
                    if (weapon != null) {
                        weapon.holdFire(); // an explicit move overrides the current target
                    }
                    if (unit.getLocomotor() != null) {
                        movers.add(unit);
                    }
                }
                getGroupLayout().send(this, movers, move.destination(), move.click());
                tellOrder(move, move.units(), move.playerIndex());
            }
            case GameMessage.AttackObject attack -> {
                for (var id : attack.units()) {
                    var unit = findObject(id);
                    if (unit == null || unit.getPlayerIndex() != attack.playerIndex()) {
                        continue;
                    }
                    uz.dukeengine.combat.module.Errand.giveUpAll(unit);
                    var weapon = unit.findModule(WeaponUpdate.class);
                    if (weapon != null) {
                        attack(weapon, attack);
                    }
                }
                tellOrder(attack, attack.units(), attack.playerIndex()); // weaponless or not
            }
            case GameMessage.StopMoving stop -> {
                for (var id : stop.units()) {
                    var unit = findObject(id);
                    if (unit == null || unit.getPlayerIndex() != stop.playerIndex()) {
                        continue;
                    }
                    uz.dukeengine.combat.module.Errand.giveUpAll(unit);
                    var ai = unit.getLocomotor();
                    if (ai != null) {
                        ai.stop();
                    }
                    var weapon = unit.findModule(WeaponUpdate.class);
                    if (weapon != null) {
                        weapon.holdFire();
                    }
                }
                tellOrder(stop, stop.units(), stop.playerIndex());
            }
            case GameMessage.QueueProduction order -> {
                var production = ownProduction(order.factory(), order.playerIndex());
                var template = getThingFactory().findTemplate(order.unitTemplate());
                // the build menu is the contract: only listed units may be queued
                if (production != null && template != null && production.canBuild(order.unitTemplate())) {
                    production.queue(template);
                }
            }
            case GameMessage.SetRallyPoint rally -> {
                var production = ownProduction(rally.factory(), rally.playerIndex());
                if (production != null) {
                    production.setRallyPoint(rally.point());
                }
            }
            case GameMessage.Construct build -> construct(build);
            case GameMessage.CancelConstruction cancel -> cancelConstruction(cancel);
            case GameMessage.ResumeConstruction resume -> resumeConstruction(resume);
            case GameMessage.QueueResearch research -> {
                var production = ownProduction(research.factory(), research.playerIndex());
                var upgrade = findUpgrade(research.upgrade());
                // the research list is the contract, as the build menu is for units
                if (production != null && upgrade != null && production.canResearch(research.upgrade())) {
                    production.queueResearch(upgrade);
                }
            }
            case GameMessage.CancelProduction cancel -> {
                var production = ownProduction(cancel.factory(), cancel.playerIndex());
                if (production != null) {
                    production.cancel(cancel.index());
                }
            }
            case GameMessage.Sell sell -> uz.dukeengine.rts.construction.Selling.order(this, sell);
            case GameMessage.AttackMove move -> {
                uz.dukeengine.rts.module.AttackMoveOrder.order(this, move);
                tellOrder(move, move.units(), move.playerIndex());
            }
            case GameMessage.Guard guard -> {
                uz.dukeengine.rts.module.GuardOrder.order(this, guard);
                tellOrder(guard, guard.units(), guard.playerIndex());
            }
            case GameMessage.Evacuate evacuate -> {
                uz.dukeengine.rts.module.ContainModule.evacuate(this, evacuate);
                // Told after the engine's own hold is applied, its riders kept on, as the other orders are.
                tellOrder(evacuate, List.of(evacuate.container()), evacuate.playerIndex());
            }
            case GameMessage.ExitContainer exit -> {
                uz.dukeengine.rts.module.ContainModule.exit(this, exit);
                tellOrder(exit, List.of(exit.passenger()), exit.playerIndex());
            }
            case GameMessage.GameOrder order -> ordered(order);
        }
    }

    /**
     * An attack as the reference's dispatch gives one: an attack naming a slot locks the weapon to it until the attack
     * is over ({@code MSG_DO_WEAPON_AT_OBJECT}), any other lets a lock until then go ({@code MSG_DO_ATTACK_OBJECT});
     * an attack refused leaves no lock of its own behind.
     */
    private static void attack(WeaponUpdate weapon, GameMessage.AttackObject attack) {
        var until = WeaponUpdate.Lock.TEMPORARILY;
        if (attack.slot() < 0 || !weapon.lock(attack.slot(), until)) {
            weapon.unlock(until);
        }
        if (!weapon.attack(attack.target(), attack.forced(), attack.source())) {
            weapon.unlock(until);
        }
    }

    /** The production module of {@code factory} if it belongs to {@code player}. */
    private uz.dukeengine.rts.module.ProductionUpdate ownProduction(uz.dukeengine.core.thing.ObjectId factory, int player) {
        var structure = findObject(factory);
        if (structure == null || structure.getPlayerIndex() != player) {
            return null;
        }
        return structure.findModule(uz.dukeengine.rts.module.ProductionUpdate.class);
    }

    @Override
    protected void simulate() {
        for (var task = tasks.poll(); task != null; task = tasks.poll()) {
            task.run();
        }
        for (var command = inbox.poll(); command != null; command = inbox.poll()) {
            if (session != null && command instanceof GameMessage rts) {
                session.issueLocal(rts); // ships to both peers, applied in lock-step
            } else {
                if (session != null) {
                    // The wire codec speaks the RTS set, and applying this one locally
                    // would desync, so say so loudly — and say what does travel.
                    var unsendable = command;
                    LOG.warning(() -> "game command cannot be sent to peers: "
                            + unsendable.getClass().getName() + "; send it as a GameMessage.GameOrder");
                    continue;
                }
                issueCommand(command); // applied at the start of the next frame
            }
        }

        for (var callback : tickCallbacks) {
            callback.run();
        }
        for (var interval : intervalCallbacks) {
            if (getFrame() > 0 && getFrame() % interval.everyFrames() == 0) {
                interval.action().run();
            }
        }

        checkDefeats();
    }

    /** Annihilation rule: a player who had objects and now has none is defeated. */
    private void checkDefeats() {
        for (var object : getObjects()) {
            everOwnedObjects.add(object.getPlayerIndex());
        }
        if (defeatListener == null) {
            return;
        }
        for (var playerIndex : everOwnedObjects) {
            if (defeated.contains(playerIndex)) {
                continue;
            }
            boolean anyAlive = false;
            for (var object : getObjects()) {
                if (object.getPlayerIndex() == playerIndex && !object.isEffectivelyDead()) {
                    anyAlive = true;
                    break;
                }
            }
            if (!anyAlive) {
                defeated.add(playerIndex);
                defeatListener.onDefeated(playerIndex);
            }
        }
    }
}
