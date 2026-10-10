package uz.dukeengine.core.pathfind;

/**
 * Where a piece of scenery stands in the way along its length — a wall — in world units: from its origin {@code length}
 * along its facing and {@code depth} across it, centred, its facing in degrees about the vertical as a kit piece is
 * turned (0 along +x, 90 toward -y). See {@code GameLogic.setSceneryBoxes}.
 */
public record SceneryBox(float x, float y, float length, float depth, float facing) {

    /** Whether {@code (px, py)} lies inside it: from its origin up to, not on, its far end and its sides. */
    public boolean covers(float px, float py) {
        double turned = Math.toRadians(facing);
        double alongX = StrictMath.cos(turned);
        double alongY = -StrictMath.sin(turned);
        double dx = px - x;
        double dy = py - y;
        double along = dx * alongX + dy * alongY;
        double across = dx * -alongY + dy * alongX;
        return along >= 0 && along < length && across >= -depth / 2 && across < depth / 2;
    }
}
