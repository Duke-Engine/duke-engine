package uz.duke.dungeon.content;

import uz.duke.core.data.Clip;
import java.util.List;

/**
 * Animations many units share: the files the clips come from, and which clip plays in each state.
 * Declared once and linked from every unit that moves on the same skeleton —
 * {@code Animations = Humanoid} — the way a unit links its {@code Effect}; a unit writes only the
 * clips it plays differently.
 *
 * <p>A creature kit ships rigged models with no animation and an animation library ships animation
 * with no creature. They meet because both were built on the same skeleton, so one set moves every
 * creature on it, and a new one needs no animation work at all.
 *
 * <p>A clip is a name inside one of the libraries, fetched by name: a name in none of them leaves
 * the unit standing still in that state rather than failing.
 *
 * @param libraries the files the clips are taken from
 * @param idle      the clip a unit stands in; it loops, as {@code walk} does
 * @param attack    played once, on the frame a blow lets go — a moment like {@code hurt} and
 *     {@code death}, not a state
 * @param hurt      a flinch when something takes health off it; none plays nothing
 * @param death     played once as it falls, before the body is taken away
 */
public record AnimationSet(String name, List<String> libraries, @Clip String idle, @Clip String walk,
        @Clip String attack, @Clip String hurt, @Clip String death) {

    /** No set: a unit plays only the clips it names, from no file. */
    public static final AnimationSet NONE = new AnimationSet("", List.of(), null, null, null, null, null);

    public AnimationSet {
        libraries = libraries == null ? List.of() : List.copyOf(libraries);
    }
}
