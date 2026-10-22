package uz.dukeengine.client3d;

import java.util.Set;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.core.view.UnitView;

/**
 * What a click giving the game's word was aimed at, for the voice that answers it — see {@link
 * Visuals#orderAnswerBy}: the reference's {@code VoiceAttackAir}, {@code VoiceCrush}
 * or {@code VoiceGarrison}, one word answered by what it was given on.
 *
 * @param template   the thing's template
 * @param relation   what it is to the local player
 * @param airborne   whether it is in the air
 * @param conditions the words it holds — a garrison, an upgrade, whatever the game set on it
 * @param structure  whether it is a structure
 */
public record AnswerTarget(String template, Relation relation, boolean airborne, Set<String> conditions,
        boolean structure) {

    /** What a thing is to the player who clicked. */
    public enum Relation { OWN, ALLIED, HOSTILE, NEUTRAL }

    public AnswerTarget {
        conditions = conditions == null ? Set.of() : Set.copyOf(conditions);
    }

    /** A target with no words and no structure: every target from before the voice could read them. */
    public AnswerTarget(String template, Relation relation, boolean airborne) {
        this(template, relation, airborne, Set.of(), false);
    }

    static AnswerTarget of(UnitView view, int localPlayer) {
        if (view == null) {
            return null;
        }
        var relation = view.playerIndex() == localPlayer ? Relation.OWN
                : view.hostile() ? Relation.HOSTILE : view.allied() ? Relation.ALLIED : Relation.NEUTRAL;
        return new AnswerTarget(view.templateName(), relation, view.has(ObjectStatus.AIRBORNE),
                Set.copyOf(view.conditions()), view.structure());
    }
}
