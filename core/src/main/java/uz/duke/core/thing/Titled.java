package uz.duke.core.thing;

/** A thing with a name for people, rather than for files: what a panel or a build menu shows. */
public interface Titled extends ThingTemplate {

    String displayName();

    /** What a person reads for any template: its display name, or its template name when it has none. */
    static String of(ThingTemplate template) {
        return template instanceof Titled titled && titled.displayName() != null && !titled.displayName().isBlank()
                ? titled.displayName() : template.name();
    }
}
