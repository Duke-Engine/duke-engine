package uz.dukeengine.game.view;

/**
 * One drawable unit as the renderer sees it — an immutable copy of the fields
 * presentation needs, safe to read from the Swing thread while the simulation
 * runs on its own thread.
 *
 * @param z         its own height, the simulation's: a jet's cruising height, a shell at the top of its arc
 * @param pitch     its nose up, in radians
 * @param roll      banked to its right, in radians
 * @param ownHeight whether it is drawn at {@code z} even under the ground; otherwise at {@code z} or on the ground,
 *                  whichever is higher
 */
public record UnitView(
        int id,
        String templateName,
        int playerIndex,
        float x,
        float y,
        float orientation,
        float health,
        float maxHealth,
        boolean structure,
        boolean selectable,
        boolean moving,
        boolean attacking,
        int productionQueue,
        float z,
        float pitch,
        float roll,
        boolean ownHeight,
        int statuses) {

    /** A unit on the ground, level: every view from before a thing could be drawn at its own height. */
    public UnitView(int id, String templateName, int playerIndex, float x, float y, float orientation, float health,
            float maxHealth, boolean structure, boolean selectable, boolean moving, boolean attacking,
            int productionQueue) {
        this(id, templateName, playerIndex, x, y, orientation, health, maxHealth, structure, selectable, moving,
                attacking, productionQueue, 0f, 0f, 0f, false, 0);
    }

    /** A view that says nothing of statuses: every view from before they were carried. */
    public UnitView(int id, String templateName, int playerIndex, float x, float y, float orientation, float health,
            float maxHealth, boolean structure, boolean selectable, boolean moving, boolean attacking,
            int productionQueue, float z, float pitch, float roll, boolean ownHeight) {
        this(id, templateName, playerIndex, x, y, orientation, health, maxHealth, structure, selectable, moving,
                attacking, productionQueue, z, pitch, roll, ownHeight, 0);
    }

    /** Whether the thing carries this status — held, sold, in the air, going up. */
    public boolean has(uz.dukeengine.core.thing.ObjectStatus status) {
        return (statuses & (1 << status.ordinal())) != 0;
    }

    /** Whether this unit is a production structure (has a build queue). */
    public boolean producer() {
        return productionQueue >= 0;
    }

    public boolean isDamaged() {
        return maxHealth > 0 && health < maxHealth;
    }

    public float healthFraction() {
        return maxHealth <= 0 ? 1f : health / maxHealth;
    }
}
