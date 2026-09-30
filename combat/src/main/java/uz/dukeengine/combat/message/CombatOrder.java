package uz.dukeengine.combat.message;

import java.util.List;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.message.Command;
import uz.dukeengine.core.thing.ObjectId;

/**
 * The orders every game whose things fight gives them, whatever its genre — the reference's {@code MSG_DO_MOVETO},
 * {@code MSG_DO_ATTACK_OBJECT} and {@code MSG_DO_STOP}: go there, attack that, stop. Each side applies them its own
 * way — an RTS to a group placed by its layout, a game of one hero to him — and adds its own set beside them ({@code
 * GameMessage} is the RTS's); both travel the one command stream, on the same frame, in the same replay.
 *
 * <p>Sealed, so a {@code switch} over them is exhaustive; every field deterministic data, ids and coordinates, never a
 * live reference, because every peer applies the identical orders on the identical frame.
 */
public sealed interface CombatOrder extends Command
        permits CombatOrder.MoveTo, CombatOrder.AttackObject, CombatOrder.StopMoving {

    /** The things it is given to. */
    List<ObjectId> units();

    /**
     * Order the given units to move to a destination, placed there as one group by the side that applies it; {@code
     * click} says it is the player's own click, which gathers a group clicked in its middle.
     */
    record MoveTo(int playerIndex, List<ObjectId> units, Coord3D destination, boolean click) implements CombatOrder {
        public MoveTo {
            units = List.copyOf(units);
        }

        /** An order the game's own code gives, not the player's click. */
        public MoveTo(int playerIndex, List<ObjectId> units, Coord3D destination) {
            this(playerIndex, units, destination, false);
        }
    }

    /**
     * Order the given units to attack a target object — {@code forced}, the player's forced attack, taken on a thing
     * passing itself off to their side as none of its targets ({@link uz.dukeengine.core.module.Disguise}).
     *
     * @param source who gave it, which decides the weapon slots it may pick ({@code WeaponSlot.autoChooseSources});
     *               a player's where it names none
     * @param slot   the slot of each unit's set in use its weapon is locked to until this attack is over or the
     *               slot's clip is empty — the reference's {@code MSG_DO_WEAPON_AT_OBJECT}; -1, no lock, and any
     *               lock until an attack is over let go
     */
    record AttackObject(int playerIndex, List<ObjectId> units, ObjectId target, boolean forced, OrderSource source,
            int slot) implements CombatOrder {
        public AttackObject {
            units = List.copyOf(units);
            source = source == null || source == OrderSource.NONE ? OrderSource.PLAYER : source;
            slot = Math.max(-1, slot);
        }

        /** A player's attack, with no lock: every attack from before one could say its source. */
        public AttackObject(int playerIndex, List<ObjectId> units, ObjectId target, boolean forced) {
            this(playerIndex, units, target, forced, OrderSource.PLAYER, -1);
        }

        /** An attack that is not forced, as every one was before one could be. */
        public AttackObject(int playerIndex, List<ObjectId> units, ObjectId target) {
            this(playerIndex, units, target, false);
        }
    }

    /** Order the given units to halt. */
    record StopMoving(int playerIndex, List<ObjectId> units) implements CombatOrder {
        public StopMoving {
            units = List.copyOf(units);
        }
    }
}
