package uz.duke.core.thing;

import java.util.List;
import uz.duke.core.module.ModuleData;

/**
 * What every kind of thing is, ported from SAGE's {@code ThingTemplate}: a name, and
 * the modules each instance is built with. {@link ThingFactory} stamps out
 * {@link GameObject}s from it.
 *
 * <p>Everything else a thing may have is a capability of its own — {@link Solid},
 * {@link Sighted}, {@link Classified}, {@link Titled} — so a template says what it has
 * by what it implements, and the code that reads a field asks only for the capability
 * it needs: collision for {@code Solid}, fog for {@code Sighted}. A game's templates are
 * records that pick theirs; a monster is solid and sees, an arrow does neither. SAGE
 * gave every Object every field, and a field a thing had no use for sat at zero.
 *
 * <p>Templates are shared and never mutated, so a single one backs thousands of objects.
 */
public interface ThingTemplate {

    String name();

    /** What each instance is built with: one module per entry, the data its block was read into. */
    List<ModuleData> modules();

    /** An {@code Object} built in code, as tests and tools build one. */
    static ObjectTemplate.Builder named(String name) {
        return ObjectTemplate.named(name);
    }
}
