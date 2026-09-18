package uz.duke.core.thing;

import java.util.Set;

/** A thing with classification flags, SAGE's {@code KindOf}: what selection and targeting ask about. */
public interface Classified extends ThingTemplate {

    Set<Kind> kinds();

    default boolean isKindOf(Kind kind) {
        return kinds().contains(kind);
    }

    /** The flags of any template: its own if it is classified, none if not. */
    static Set<Kind> of(ThingTemplate template) {
        return template instanceof Classified classified ? classified.kinds() : Set.of();
    }
}
