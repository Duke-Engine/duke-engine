package uz.dukeengine.client3d;

import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.core.view.UnitView;

/**
 * What a click giving the game's word was aimed at, for the voice that answers it — see {@link
 * Visuals#orderAnswerBy}: the reference's {@code VoiceAttackAir}, {@code VoiceCrush}
 * or {@code VoiceGarrison}, one word answered by what it was given on.
 *
 * @param template the thing's template
 * @param relation what it is to the local player
 * @param airborne whether it is in the air
 */
public record AnswerTarget(String template, Relation relation, boolean airborne) {

    /** What a thing is to the player who clicked. */
    public enum Relation { OWN, ALLIED, HOSTILE, NEUTRAL }

    static AnswerTarget of(UnitView view, int localPlayer) {
        if (view == null) {
            return null;
        }
        var relation = view.playerIndex() == localPlayer ? Relation.OWN
                : view.hostile() ? Relation.HOSTILE : view.allied() ? Relation.ALLIED : Relation.NEUTRAL;
        return new AnswerTarget(view.templateName(), relation, view.has(ObjectStatus.AIRBORNE));
    }
}
