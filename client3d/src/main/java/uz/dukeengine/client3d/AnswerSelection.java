package uz.dukeengine.client3d;

import java.util.Set;
import uz.dukeengine.core.view.UnitView;

/**
 * What the selection holds, for the voice that answers an order — see {@link Visuals#orderAnswerBy}: the first of it,
 * whose voice speaks, as the reference's {@code VoiceMoveUpgraded} is chosen by an upgrade that unit holds.
 *
 * @param template   the first selected thing's template
 * @param conditions the words it holds
 */
public record AnswerSelection(String template, Set<String> conditions) {

    public AnswerSelection {
        conditions = conditions == null ? Set.of() : Set.copyOf(conditions);
    }

    /** The first of {@code selected}, or null where nothing is selected. */
    static AnswerSelection of(java.util.List<UnitView> selected) {
        if (selected.isEmpty()) {
            return null;
        }
        var first = selected.getFirst();
        return new AnswerSelection(first.templateName(), Set.copyOf(first.conditions()));
    }
}
