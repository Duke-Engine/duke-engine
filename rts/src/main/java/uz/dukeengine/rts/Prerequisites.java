package uz.dukeengine.rts;

import java.util.List;
import uz.dukeengine.core.thing.ThingTemplate;

/**
 * What a side needs before it may make a thing, and how many of it the side may have at once — an RTS's tech tree:
 * a tank needs a war factory standing, a better tank a technology the side chose as well, a hero one at a time. The
 * simulation decides it ({@link RtsSimulation#canBuild}), so an order nobody could have given from the bar is refused
 * on every machine alike. SAGE's {@code ProductionPrerequisite}, {@code Buildable} and {@code MaxSimultaneousOfType}.
 *
 * <p>Every method has a default, so a record implements this by having whichever of the components it has.
 */
public interface Prerequisites extends ThingTemplate {

    /** Who may make it: SAGE's {@code BuildableStatus}. */
    enum Buildability {
        /** Anyone whose side meets what it needs. */
        YES,
        /** Nobody: it comes into the world some other way. */
        NO,
        /** Only a computer player — {@code Only_By_AI}. */
        ONLY_BY_COMPUTER,
        /** Anyone, whatever the side has or how many it has — {@code Ignore_Prerequisites}. */
        IGNORING_PREREQUISITES
    }

    /**
     * What the side must own, finished and alive: each entry one requirement, its templates separated by {@code |},
     * any one of which meets it — {@code Prerequisites = [WarFactory, SupplyCenter | SupplyDropZone]}. Whether a
     * thing of one template counts as another is the game's ({@link RtsSimulation#countsAs}).
     */
    default List<String> prerequisites() {
        return List.of();
    }

    /** Words the side must hold: a science it chose ({@link RtsSimulation#grant}), or an upgrade of the side's. */
    default List<String> requiredWords() {
        return List.of();
    }

    default Buildability buildability() {
        return Buildability.YES;
    }

    /** How many the side may have at once, standing, going up or queued in its factories; 0 for no limit. */
    default int maxSimultaneous() {
        return 0;
    }

    /**
     * A key it shares that limit with other templates by, every one of them counted together — and a match may set
     * the limit for a key itself ({@link RtsSimulation#setCap}), as a game's superweapon option does. Null for none.
     */
    default String maxSimultaneousLinkKey() {
        return null;
    }

    /** The alternatives of one requirement. */
    static List<String> alternatives(String requirement) {
        return java.util.Arrays.stream(requirement.split("\\|")).map(String::strip).filter(name -> !name.isEmpty())
                .toList();
    }
}
