package uz.dukeengine.client3d;

/** How low and how high a map's ground stands anywhere, in world units. */
record GroundSpan(float lowest, float highest) {

    /** Its storeys, a stair up from the highest of them, and its relief's lowest and highest corners. */
    static GroundSpan of(uz.dukeengine.core.pathfind.PathGrid grid) {
        int lowestLevel = 0;
        int highestLevel = 0;
        for (int cy = 0; cy < grid.getHeight(); cy++) {
            for (int cx = 0; cx < grid.getWidth(); cx++) {
                lowestLevel = Math.min(lowestLevel, grid.level(cx, cy));
                highestLevel = Math.max(highestLevel, grid.level(cx, cy));
            }
        }
        float lowest = lowestLevel * grid.getLevelHeight();
        float highest = (highestLevel + 1) * grid.getLevelHeight();
        var relief = grid.getRelief();
        if (relief == null) {
            return new GroundSpan(lowest, highest);
        }
        int least = Integer.MAX_VALUE;
        int most = Integer.MIN_VALUE;
        for (int row = 0; row < relief.rows(); row++) {
            for (int column = 0; column < relief.columns(); column++) {
                least = Math.min(least, relief.at(column, row));
                most = Math.max(most, relief.at(column, row));
            }
        }
        var fixed = uz.dukeengine.core.pathfind.HeightMap.SUBCELL;
        return new GroundSpan(
                lowest + uz.dukeengine.core.pathfind.HeightMap.lengthOf(least * fixed, grid.getCellSize()),
                highest + uz.dukeengine.core.pathfind.HeightMap.lengthOf(most * fixed, grid.getCellSize()));
    }
}
