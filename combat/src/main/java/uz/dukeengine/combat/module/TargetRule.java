package uz.dukeengine.combat.module;

import java.util.List;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Kind;
import uz.dukeengine.core.thing.ObjectStatus;

/**
 * One line of a game's answer to "what is this thing, to a weapon": the kinds a thing must have — any one of
 * them — whether it must be in the air, and the classes it then has. A game gives the world an ordered list
 * of these ({@link uz.dukeengine.combat.Armoury#setTargetRules}); the first that matches a thing decides
 * its classes, and a weapon that names classes ({@link WeaponUpdate.Data#targets}) fires only at a thing that
 * has one of them.
 *
 * <p><b>The words are the game's.</b> The engine compares them and never reads one. The reference game's own
 * list, read from its source ({@code WeaponSet.cpp}, {@code getVictimAntiMask}), written as these rules:
 *
 * <pre>
 *   Kinds = [SMALL_MISSILE]                       Classes = [SMALL_MISSILE]
 *   Kinds = [BALLISTIC_MISSILE]                   Classes = [BALLISTIC_MISSILE]
 *   Kinds = [PROJECTILE]                          Classes = [PROJECTILE]
 *   Kinds = [MINE, DEMOTRAP]                      Classes = [MINE, GROUND]
 *   Kinds = [VEHICLE]    InTheAir = Yes           Classes = [AIRBORNE_VEHICLE]
 *   Kinds = [INFANTRY]   InTheAir = Yes           Classes = [AIRBORNE_INFANTRY]
 *   Kinds = [PARACHUTE]  InTheAir = Yes           Classes = [PARACHUTE]
 *                        InTheAir = Yes           Classes = []
 *                                                 Classes = [GROUND]
 * </pre>
 *
 * <p>Why a list and not a table of kinds: the order is the rule. A mine is ground as well as a mine, a
 * helicopter on its pad is ground and the same helicopter in the air is not, and an aircraft of no named
 * sort is nothing at all — none of which a kind alone can say, and all of which "the first line that
 * matches" does.
 *
 * @param kinds    the kinds a thing must have, any one of them; none is any thing at all
 * @param inTheAir whether it must be in the air ({@link #isInTheAir}); no is either way
 * @param classes  what it is then, to a weapon; none is something no weapon that names classes may hit
 */
public record TargetRule(List<Kind> kinds, boolean inTheAir, List<String> classes) {

    public TargetRule {
        kinds = kinds == null ? List.of() : List.copyOf(kinds);
        classes = classes == null ? List.of() : List.copyOf(classes);
    }

    /** Whether this line is the one for {@code thing}. */
    public boolean matches(GameObject thing) {
        if (inTheAir && !isInTheAir(thing)) {
            return false;
        }
        if (kinds.isEmpty()) {
            return true;
        }
        for (var kind : kinds) {
            if (thing.isKindOf(kind)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The classes the first matching line gives {@code thing}, or none where no line matches — which, for a
     * game that gave no lines at all, is every thing.
     */
    public static List<String> classesOf(List<TargetRule> rules, GameObject thing) {
        for (var rule : rules) {
            if (rule.matches(thing)) {
                return rule.classes();
            }
        }
        return List.of();
    }

    /**
     * Whether {@code thing} is in the air. Nothing in the engine flies yet, so this is the status a game sets
     * on its aircraft while they are aloft ({@link ObjectStatus#AIRBORNE}); when the engine flies things
     * itself, it will set the same status and this will not change.
     */
    public static boolean isInTheAir(GameObject thing) {
        return thing.hasStatus(ObjectStatus.AIRBORNE);
    }
}
