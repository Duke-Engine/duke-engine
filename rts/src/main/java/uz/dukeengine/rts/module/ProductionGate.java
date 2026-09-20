package uz.dukeengine.rts.module;

/**
 * A module that can hold up its owner's production line.
 *
 * <p>Building units over time is a mechanism every RTS has. <em>What stops it</em>
 * is not: Generals halts a base that has outgrown its power plants, Warcraft
 * refuses to train past a food cap, Age of Empires lets you build regardless and
 * charges you for it. Wiring one of those into {@link ProductionUpdate} — as the
 * power check used to be — made every game on this engine play by Generals' rule.
 *
 * <p>So the factory asks whatever is attached to it. A structure with no gate
 * builds without interruption; a game that wants a condition attaches a module
 * that answers it. {@link CapacityGate} is the common one and ships here, but it
 * is one answer among however many a game invents.
 *
 * <p>Every gate must agree before the line moves, and they are asked in module
 * order, which is fixed at attachment — so the answer is the same on every
 * machine.
 */
public interface ProductionGate {

    /**
     * Whether the owner's production may advance this frame.
     *
     * <p>Consulted every frame, so it may follow the world's state — but like
     * everything else in the logic step it must be a pure function of that state.
     */
    boolean canProduce();
}
