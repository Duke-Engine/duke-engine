package uz.duke.studio.io;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import javax.imageio.ImageIO;
import uz.duke.core.pathfind.MapLoader;
import uz.duke.studio.model.StudioProject.MapDef;

/**
 * Loads a map into a Studio project from a file:
 * <ul>
 *   <li><b>Text</b> ({@code .txt}/{@code .map}) — ASCII art, {@code #} or
 *       {@code X} is impassable (the engine's own {@link MapLoader} format).</li>
 *   <li><b>Image</b> ({@code .png}/{@code .jpg}...) — one pixel per pathfinding
 *       cell; dark pixels (mountains/water in a minimap sketch) become
 *       impassable. Draw a map in any paint program and import it.</li>
 * </ul>
 * Replaces the project's map size and blocked cells; placements are kept but
 * ones that fall outside the new map are dropped.
 */
public final class MapImporter {

    /** Maps larger than this are scaled down to keep pathfinding fast. */
    private static final int MAX_CELLS = 200;

    /** Pixels darker than this luminance (0..1) are blocked terrain. */
    private static final double DARK_THRESHOLD = 0.4;

    private MapImporter() {
    }

    /** Import {@code file} into {@code map}. Returns a human summary. */
    public static String importInto(MapDef map, Path file) throws IOException {
        var name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".txt") || name.endsWith(".map")) {
            importText(map, file);
        } else {
            importImage(map, file);
        }
        dropOutOfBounds(map);
        return "Map imported: " + map.cellsWide + "×" + map.cellsHigh
                + " cells, " + map.blockedCells.size() + " blocked.";
    }

    private static void importText(MapDef map, Path file) throws IOException {
        var grid = MapLoader.fromText(Files.readString(file));
        map.cellsWide = grid.getWidth();
        map.cellsHigh = grid.getHeight();
        map.blockedCells.clear();
        for (int cy = 0; cy < grid.getHeight(); cy++) {
            for (int cx = 0; cx < grid.getWidth(); cx++) {
                if (grid.isBlocked(cx, cy)) {
                    map.blockedCells.add(cx + "," + cy);
                }
            }
        }
    }

    private static void importImage(MapDef map, Path file) throws IOException {
        BufferedImage image = ImageIO.read(file.toFile());
        if (image == null) {
            throw new IOException("not a readable image: " + file);
        }
        int step = Math.max(1, (Math.max(image.getWidth(), image.getHeight()) + MAX_CELLS - 1) / MAX_CELLS);
        int cellsWide = image.getWidth() / step;
        int cellsHigh = image.getHeight() / step;
        if (cellsWide < 2 || cellsHigh < 2) {
            throw new IOException("image too small for a map: " + image.getWidth() + "×" + image.getHeight());
        }

        map.cellsWide = cellsWide;
        map.cellsHigh = cellsHigh;
        map.blockedCells.clear();
        for (int cy = 0; cy < cellsHigh; cy++) {
            for (int cx = 0; cx < cellsWide; cx++) {
                if (averageLuminance(image, cx * step, cy * step, step) < DARK_THRESHOLD) {
                    map.blockedCells.add(cx + "," + cy);
                }
            }
        }
    }

    private static double averageLuminance(BufferedImage image, int x0, int y0, int step) {
        double sum = 0;
        int count = 0;
        for (int y = y0; y < Math.min(y0 + step, image.getHeight()); y++) {
            for (int x = x0; x < Math.min(x0 + step, image.getWidth()); x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                sum += (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
                count++;
            }
        }
        return count == 0 ? 1.0 : sum / count;
    }

    private static void dropOutOfBounds(MapDef map) {
        float worldW = map.cellsWide * 10f;
        float worldH = map.cellsHigh * 10f;
        map.neutrals.removeIf(p -> p.x < 0 || p.y < 0 || p.x > worldW || p.y > worldH);
        map.startPositions.removeIf(p -> p[0] < 0 || p[1] < 0 || p[0] > worldW || p[1] > worldH);
    }
}
