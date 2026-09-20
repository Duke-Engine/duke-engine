package uz.dukeengine.rts.module;

/**
 * The module families every RTS has, beside the ones any game has in
 * {@link uz.dukeengine.core.module.ModuleGroups}. A spelling contract, as {@code RtsKinds} is.
 */
public final class RtsModuleGroups {

    /** Resources and what they buy: gathering, supply, power, production, loot. */
    public static final String ECONOMY = "Economy";

    /** Growing stronger over a unit's life: experience, levels, ranks. */
    public static final String PROGRESSION = "Progression";

    private RtsModuleGroups() {
    }
}
