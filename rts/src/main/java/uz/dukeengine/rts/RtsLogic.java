package uz.dukeengine.rts;

import java.util.List;
import uz.dukeengine.combat.message.CombatOrder;
import uz.dukeengine.combat.module.WeaponUpdate;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.rts.message.GameMessage;

/**
 * The RTS world with the standard orders applied, which {@link RtsFlavour} runs: every hand-rolled simulation in the
 * engine's tests wires the same orders to the same modules, and this is that routing built in —
 * <ul>
 *   <li>a move → the units' {@link MoveUpdate}s, placed as one group by the game's layout;</li>
 *   <li>an attack → their {@link WeaponUpdate}s;</li>
 *   <li>a stop → halted, holding their fire;</li>
 *   <li>production, research, construction, selling, attack-moves, guards and holds → the RTS's own modules.</li>
 * </ul>
 *
 * <p>What runs the world does the rest on its thread each frame ({@code GameLogic.eachFrame}): the orders posted from
 * other threads, the game's per-frame code, a side's defeat.
 */
public final class RtsLogic extends RtsSimulation {

    @Override
    protected void onRtsCommand(GameMessage command) {
        switch (command) {
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
        }
    }

    /**
     * A move, an attack or a stop: every unit named that is the issuer's gives up its errands; a move holds its fire and
     * is sent by the game's layout as one group, an attack locks and aims its weapon, a stop halts it and holds its
     * fire. Each told to the units' order listeners after.
     */
    @Override
    protected void onCombatOrder(CombatOrder order) {
        switch (order) {
            case CombatOrder.MoveTo move -> {
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
            case CombatOrder.AttackObject attack -> {
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
            case CombatOrder.StopMoving stop -> {
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
        }
    }

    /**
     * An attack as the reference's dispatch gives one: an attack naming a slot locks the weapon to it until the attack
     * is over ({@code MSG_DO_WEAPON_AT_OBJECT}), any other lets a lock until then go ({@code MSG_DO_ATTACK_OBJECT});
     * an attack refused leaves no lock of its own behind.
     */
    private static void attack(WeaponUpdate weapon, CombatOrder.AttackObject attack) {
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
    }
}
