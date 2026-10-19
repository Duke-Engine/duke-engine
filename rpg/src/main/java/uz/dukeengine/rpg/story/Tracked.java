package uz.dukeengine.rpg.story;

import java.util.List;

/**
 * A quest as the tracker lists it: its kind, its title and the steps under it — the current one, and any just finished.
 */
public record Tracked(Quest.Kind kind, String title, List<Line> steps) {

    public Tracked {
        steps = List.copyOf(steps);
    }

    /** One step's line, whether it is done, and the frame it began. */
    public record Line(String text, boolean done, int since) {
    }
}
