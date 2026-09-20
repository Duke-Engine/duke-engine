package uz.duke.dungeon.stage;

import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import uz.duke.core.map.MapPackages;
import uz.duke.dungeon.gen.GeneratedDungeon.Placement;

/**
 * The picture that sits beside a map: the floor from above, a square a cell, with what stands on it marked.
 *
 * <p>A map's folder holds one — {@code preview.png} — and it is what the screen a map is chosen on shows of it.
 * Generals keeps a {@code .tga} beside every map and Warcraft III bakes one into the archive, both for the same
 * reason: a player choosing between maps is choosing between places, and a place is a shape before it is a name.
 *
 * <p>Written by the generator for the map it draws, and for maps already drawn by:
 *
 * <pre>{@code ./gradlew :dungeon:writeMapPreviews}</pre>
 *
 * <p>which reads each map and draws its picture without touching the map itself. The Map tab of the IDE writes
 * the same picture for the map being edited — a map drawn by hand has one without any of this being run.
 */
public final class MapPicture {

    /** How wide or tall the picture is at its longer side; a cell is whole pixels, so it may come out under it. */
    private static final int SIDE = 512;

    private static final Color BACKGROUND = new Color(18, 18, 21);
    private static final Color ROCK = new Color(28, 28, 32);
    private static final Color FLOOR = new Color(72, 72, 82);
    private static final Color STAIR = new Color(150, 130, 80);
    private static final Color ENTRANCE = new Color(90, 170, 255);
    private static final Color BOSS = new Color(255, 90, 90);

    private MapPicture() {
    }

    /** The floor of {@code stage} from above. */
    public static BufferedImage of(Stage stage) {
        var rows = stage.floor().levelMap().strip().lines().toList();
        int across = rows.stream().mapToInt(String::length).max().orElse(1);
        int down = Math.max(rows.size(), 1);
        int cell = Math.max(1, SIDE / Math.max(across, down));
        var image = new BufferedImage(across * cell, down * cell, BufferedImage.TYPE_INT_RGB);
        var g2 = image.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(BACKGROUND);
            g2.fillRect(0, 0, image.getWidth(), image.getHeight());
            for (int y = 0; y < rows.size(); y++) {
                var row = rows.get(y);
                for (int x = 0; x < across; x++) {
                    g2.setColor(groundOf(x < row.length() ? row.charAt(x) : '#'));
                    g2.fillRect(x * cell, y * cell, cell, cell);
                }
            }
            var floor = stage.floor();
            for (var prop : floor.props()) {
                mark(g2, cell, prop.at(), colourOf(prop.kind()), 0.7f);
            }
            for (var monster : floor.monsters()) {
                mark(g2, cell, monster.at(), colourOf(monster.kind()), 0.8f);
            }
            if (floor.boss() != null) {
                mark(g2, cell, floor.boss().at(), BOSS, 1.4f);
            }
            mark(g2, cell, floor.hero(), ENTRANCE, 1.4f);
        } finally {
            g2.dispose();
        }
        return image;
    }

    /** The picture written into {@code folder} as {@code preview.png}, replacing the one there. */
    public static Path write(Stage stage, Path folder) {
        var path = folder.resolve("preview.png");
        try {
            Files.createDirectories(folder);
            ImageIO.write(of(stage), "png", path.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("could not write " + path, e);
        }
        return path;
    }

    /** Every map the game keeps, given its picture, from the map as it stands rather than from its seed. */
    public static void main(String[] args) {
        for (var pack : MapPackages.under(MapWriter.FOLDER)) {
            var stage = StageFile.read(pack.text(), pack.name());
            System.out.println("wrote " + write(stage, pack.file().getParent()).toAbsolutePath());
        }
    }

    /** Rock, or floor a shade lighter for every storey up, or — a stair, being neither — a colour of its own. */
    private static Color groundOf(char cell) {
        if (cell == '#') {
            return ROCK;
        }
        if (!Character.isDigit(cell)) {
            return STAIR;
        }
        int lift = Math.min(cell - '0', 4) * 22;
        return new Color(Math.min(255, FLOOR.getRed() + lift), Math.min(255, FLOOR.getGreen() + lift),
                Math.min(255, FLOOR.getBlue() + lift));
    }

    /** A kind's own colour, drawn from its name so two kinds are two colours without anybody choosing them. */
    private static Color colourOf(String kind) {
        return Color.getHSBColor((kind.hashCode() & 0xFFFF) / 65535f, 0.55f, 0.95f);
    }

    private static void mark(java.awt.Graphics2D g2, int cell, Placement at, Color colour, float size) {
        if (at == null) {
            return;
        }
        int wide = Math.max(2, (int) (cell * size));
        g2.setColor(colour);
        g2.fillOval(at.cellX() * cell + cell / 2 - wide / 2, at.cellY() * cell + cell / 2 - wide / 2, wide, wide);
    }
}
