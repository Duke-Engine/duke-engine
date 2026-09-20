package uz.dukeengine.game.view;

/**
 * One drawable unit as the renderer sees it — an immutable copy of the fields
 * presentation needs, safe to read from the Swing thread while the simulation
 * runs on its own thread.
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
        int productionQueue) {

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
