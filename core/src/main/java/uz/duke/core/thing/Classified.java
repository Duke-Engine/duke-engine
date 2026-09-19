package uz.duke.core.thing;

import java.util.Set;

/** A thing with classification flags, SAGE's {@code KindOf}: what selection and targeting ask about. */
public interface Classified extends ThingTemplate {

    /** SAGE's field name, and so the block's key: {@code KindOf = [INFANTRY, SELECTABLE]}. */
    Set<Kind> kindOf();

    default boolean isKindOf(Kind kind) {
        return kindOf().contains(kind);
    }

    /** The flags of any template: its own if it is classified, none if not. */
    static Set<Kind> of(ThingTemplate template) {
        return template instanceof Classified classified ? classified.kindOf() : Set.of();
    }
}
