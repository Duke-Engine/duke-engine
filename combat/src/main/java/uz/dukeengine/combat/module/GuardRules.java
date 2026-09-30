package uz.dukeengine.combat.module;

import uz.dukeengine.core.GameConstants;

/**
 * How a guard guards and an attack-move chases: the game's numbers, the reference's by default
 * ({@code AIGuardMachine} and {@code GameData.ini}).
 *
 * @param innerHuman     how far out a human's unit takes on an enemy, times its vision — 1.8
 * @param innerComputer  the same for a computer's unit — 1.1
 * @param outerHuman     how far from where it guards a human's unit chases before it lets go, times its vision — 2.2
 * @param outerComputer  the same for a computer's unit — 1.333
 * @param chaseFrames    how long it chases before it lets go — 10 000 ms
 * @param lookWhileHolding   how often it looks for an enemy while it holds its ground — 500 ms
 * @param lookWhileReturning how often it looks while it walks back — 1000 ms
 */
public record GuardRules(float innerHuman, float innerComputer, float outerHuman, float outerComputer,
        int chaseFrames, int lookWhileHolding, int lookWhileReturning) {

    public static final GuardRules DEFAULT = new GuardRules(1.8f, 1.1f, 2.2f, 1.333f,
            frames(10_000), frames(500), frames(1000));

    public GuardRules {
        chaseFrames = Math.max(1, chaseFrames);
        lookWhileHolding = Math.max(1, lookWhileHolding);
        lookWhileReturning = Math.max(1, lookWhileReturning);
    }

    /** How far out a unit of this side takes on an enemy, times its vision. */
    public float inner(boolean computer) {
        return computer ? innerComputer : innerHuman;
    }

    /** How far from where it guards a unit of this side chases, times its vision. */
    public float outer(boolean computer) {
        return computer ? outerComputer : outerHuman;
    }

    private static int frames(int milliseconds) {
        return Math.round(milliseconds * GameConstants.LOGICFRAMES_PER_SECOND / 1000f);
    }
}
