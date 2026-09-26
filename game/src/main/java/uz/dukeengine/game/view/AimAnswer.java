package uz.dukeengine.game.view;

import java.util.List;

/**
 * The game's answer about an armed button's place: whether it would do, and the rectangles of ground to mark with it —
 * what stands in the way, as the reference marks it ({@code BuildAssistant::isLocationLegalToBuild}).
 */
public record AimAnswer(boolean fits, List<AimMark> marks) {

    /** It would do, and nothing is marked. */
    public static final AimAnswer YES = new AimAnswer(true, List.of());

    public AimAnswer {
        marks = marks == null ? List.of() : List.copyOf(marks);
    }
}
