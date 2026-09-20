package uz.dukeengine.dungeon.tools;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Cuts a sheet of drawings into one file per icon.
 *
 * <p>A sheet arrives as a grid of pictures on transparency, and what the game
 * wants is a file apiece. Doing that by hand is twenty-two crops nobody can
 * check and nobody can repeat, so it is done here — from the sheets kept beside
 * this tool in {@code dungeon/art/icons}, which is the whole reason they are
 * kept: a cut nobody can rerun is a cut nobody dares change.
 *
 * <pre>./gradlew :dungeon:cutIcons</pre>
 *
 * <p><b>Not on a grid.</b> The obvious way — divide by four and by three — makes
 * a picture of whatever the generator happened to put in each quarter, at
 * whatever size and off-centre by whatever it drifted. These drift: on the skill
 * sheet the last row holds two, and they sit under the middle two columns rather
 * than the first two. So the grid is used to say how many icons there are and
 * never to say where they end.
 *
 * <p>Three things were in the way, and each is a rule below.
 */
public final class IconSheets {

    /**
     * A pixel this opaque is part of a drawing rather than of the haze around it.
     *
     * <p><b>The AI's leavings.</b> The skill sheet carries five grey smudges in
     * its top-right corner — the generator's, meaning nothing, and two of them
     * bigger than a real fleck of ice on the frost icon, so no size can tell
     * them apart. What can is that a real piece of a drawing has something solid
     * in it: the smudges peak at alpha 208 and have not one pixel above 200,
     * while every genuine piece runs to 255 across half its area.
     */
    private static final int SOLID = 200;

    /** And this faint still belongs to it — the glow, the trail, the spark. */
    private static final int VISIBLE = 16;

    /** How much solid a piece needs before it counts as one rather than as dirt. */
    private static final int LEAST_SOLID_PIXELS = 50;

    /** Air left round the drawing inside its square, as a share of its longest side. */
    private static final double MARGIN = 0.06;

    private IconSheets() {
    }

    /**
     * One sheet: where it is, what comes out, how big, and what is in it.
     *
     * @param rows how many icons each row of the sheet holds, in order. The last
     *     row of a sheet is not always full, and this is the only place that is
     *     said — see {@link #split}, which uses it to decide where one icon ends
     *     and the next begins.
     */
    private record Sheet(String from, String into, int size, int[] rows, String... names) {
    }

    private static final Sheet[] SHEETS = {
        new Sheet("dungeon/art/icons/skills_sheet.png",
                "dungeon/src/main/resources/icons/skills", 256, new int[] {4, 4, 2},
                "skill_arrow_shot", "skill_arrow_volley", "skill_dash", "skill_cleave",
                "skill_guard", "skill_fire_arrow", "skill_frost_nova", "skill_whirlwind",
                "skill_meteor", "skill_piercing_volley"),
        new Sheet("dungeon/art/icons/commands_sheet.png",
                "dungeon/src/main/resources/icons/commands", 128, new int[] {2, 2},
                "cmd_move", "cmd_attack", "cmd_guard", "cmd_stop"),
        new Sheet("dungeon/art/icons/stats_sheet.png",
                "dungeon/src/main/resources/icons/stats", 64, new int[] {4, 4},
                "stat_attack", "stat_armor", "stat_speed", "stat_lifesteal",
                "stat_health", "stat_crit", "stat_cooldown", "stat_range"),
        // Into the stats folder, and at its size: an attribute's picture stands in the
        // same kind of socket a figure's does, and the largest of them -- the primary's
        // -- is under sixty-four pixels at the panel's largest scale.
        new Sheet("dungeon/art/icons/attributes_sheet.png",
                "dungeon/src/main/resources/icons/stats", 64, new int[] {3},
                "stat_strength", "stat_agility", "stat_intelligence"),
    };

    /**
     * Cut every sheet, or only the sheets whose file names contain one of {@code args}.
     *
     * <p>{@code ./gradlew :dungeon:cutIcons --args=attributes} cuts the one sheet that
     * changed and leaves the files every other sheet made exactly as they are.
     */
    public static void main(String[] args) throws IOException {
        for (var sheet : SHEETS) {
            if (args.length == 0 || java.util.Arrays.stream(args).anyMatch(sheet.from()::contains)) {
                cut(sheet);
            }
        }
    }

    private static void cut(Sheet sheet) throws IOException {
        var source = javax.imageio.ImageIO.read(Path.of(sheet.from()).toFile());
        if (source == null) {
            throw new IOException("not an image: " + sheet.from());
        }
        var pieces = piecesOf(source);
        var icons = split(pieces, source.getHeight(), sheet.rows());
        if (icons.size() != sheet.names().length) {
            throw new IOException(sheet.from() + ": cut " + icons.size() + " icons, but "
                    + sheet.names().length + " are named");
        }
        var into = Path.of(sheet.into());
        Files.createDirectories(into);
        for (int i = 0; i < icons.size(); i++) {
            var box = icons.get(i);
            var square = squared(source, box, sheet.size());
            javax.imageio.ImageIO.write(square, "png",
                    into.resolve(sheet.names()[i] + ".png").toFile());
            System.out.printf("  %-24s %d part(s)  %dx%d -> %d%n", sheet.names()[i],
                    box.parts, box.width(), box.height(), sheet.size());
        }
    }

    /** A rectangle round something, and how many separate pieces went into it. */
    private static final class Box {
        int x0 = Integer.MAX_VALUE;
        int y0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE;
        int y1 = Integer.MIN_VALUE;
        int parts;
        double centreX;
        double centreY;

        int width() {
            return x1 - x0 + 1;
        }

        int height() {
            return y1 - y0 + 1;
        }

        void swallow(Box other) {
            x0 = Math.min(x0, other.x0);
            y0 = Math.min(y0, other.y0);
            x1 = Math.max(x1, other.x1);
            y1 = Math.max(y1, other.y1);
            parts += Math.max(1, other.parts);
        }
    }

    /**
     * Every separate piece of drawing on the sheet.
     *
     * <p><b>Grown from the solid outward.</b> A run over everything visible would
     * make the haze its own piece; a run over only the solid would cut the glow
     * off a fire arrow at the edge of its flame. So the flood runs over
     * everything visible and a blob is kept only if it has {@link #SOLID} in it —
     * the glow comes along with the thing it belongs to, and the generator's
     * smudges, which have no solid anywhere, come along with nothing.
     */
    private static List<Box> piecesOf(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        var alpha = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                alpha[y * w + x] = image.getRGB(x, y) >>> 24;
            }
        }
        var seen = new boolean[w * h];
        var found = new ArrayList<Box>();
        var queue = new ArrayDeque<Integer>();
        for (int start = 0; start < alpha.length; start++) {
            if (seen[start] || alpha[start] < VISIBLE) {
                continue;
            }
            seen[start] = true;
            queue.add(start);
            var box = new Box();
            int solid = 0;
            long sumX = 0;
            long sumY = 0;
            int count = 0;
            while (!queue.isEmpty()) {
                int at = queue.poll();
                int x = at % w;
                int y = at / w;
                if (alpha[at] >= SOLID) {
                    solid++;
                }
                sumX += x;
                sumY += y;
                count++;
                box.x0 = Math.min(box.x0, x);
                box.y0 = Math.min(box.y0, y);
                box.x1 = Math.max(box.x1, x);
                box.y1 = Math.max(box.y1, y);
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx;
                        int ny = y + dy;
                        if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
                            continue;
                        }
                        int next = ny * w + nx;
                        if (!seen[next] && alpha[next] >= VISIBLE) {
                            seen[next] = true;
                            queue.add(next);
                        }
                    }
                }
            }
            if (solid >= LEAST_SOLID_PIXELS) {
                box.parts = 1;
                box.centreX = sumX / (double) count;
                box.centreY = sumY / (double) count;
                found.add(box);
            }
        }
        return found;
    }

    /**
     * The pieces gathered into icons: by row, then by the gaps between them.
     *
     * <p><b>Half these icons are in pieces.</b> The move command is an arrow and a
     * ring under it, the attack command a sword and an axe that never touch, the
     * speed stat a foot and three separate dashes. Every one of those is several
     * blobs and one picture, and anything that takes a blob for an icon splits
     * them.
     *
     * <p>What puts them back together is knowing <em>how many</em> a row holds.
     * Sorted across, the icons are divided by the widest gaps there are, and
     * taking the largest {@code n - 1} of them cuts the row in exactly the right
     * places however far the drawings have drifted from any grid — which is why
     * the last row of the skill sheet, whose two icons sit under the middle
     * columns, needs nothing said about it.
     */
    private static List<Box> split(List<Box> pieces, int height, int[] rows) {
        var icons = new ArrayList<Box>();
        for (int row = 0; row < rows.length; row++) {
            int band = row;
            var inRow = new ArrayList<>(pieces.stream()
                    .filter(p -> Math.min(rows.length - 1,
                            (int) (p.centreY * rows.length / height)) == band)
                    .sorted(Comparator.comparingDouble(p -> p.centreX))
                    .toList());
            if (inRow.size() < rows[row]) {
                throw new IllegalStateException("row " + row + " holds " + inRow.size()
                        + " pieces, too few for " + rows[row] + " icons");
            }
            // The widest gaps are the ones between icons; every narrower one is
            // inside one. Ordered by size, cut at the first n - 1 of them.
            var gaps = new ArrayList<Integer>();
            for (int i = 1; i < inRow.size(); i++) {
                gaps.add(i);
            }
            int wanted = rows[row];
            gaps.sort(Comparator.comparingDouble(
                    (Integer i) -> inRow.get(i).centreX - inRow.get(i - 1).centreX).reversed());
            var cuts = new ArrayList<>(gaps.subList(0, wanted - 1));
            cuts.sort(Comparator.naturalOrder());
            cuts.add(inRow.size());
            int from = 0;
            for (int to : cuts) {
                var icon = new Box();
                for (int i = from; i < to; i++) {
                    icon.swallow(inRow.get(i));
                }
                icons.add(icon);
                from = to;
            }
        }
        return icons;
    }

    /**
     * The drawing, centred in a square of its own longest side, at the size asked
     * for.
     *
     * <p>Square because a slot is square: a tall drawing squeezed into one is a
     * tall drawing made fat. Centred with the same air on both sides for the same
     * reason — an icon nudged left in its file is an icon nudged left in every
     * slot it is ever put in, and no amount of laying out afterwards can find
     * that and undo it.
     *
     * <p>Shrunk by halving rather than in one jump. A big drawing sampled
     * straight down to sixty-four loses every second pixel and comes back
     * sparkling along its edges; halving repeatedly averages what it drops, which
     * is what the eye expects of something getting smaller.
     */
    private static BufferedImage squared(BufferedImage source, Box box, int size) {
        int side = (int) Math.round(Math.max(box.width(), box.height()) * (1 + 2 * MARGIN));
        var square = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        var into = square.createGraphics();
        into.drawImage(source.getSubimage(box.x0, box.y0, box.width(), box.height()),
                (side - box.width()) / 2, (side - box.height()) / 2, null);
        into.dispose();

        var shrinking = square;
        while (shrinking.getWidth() / 2 > size) {
            shrinking = scaled(shrinking, shrinking.getWidth() / 2);
        }
        return shrinking.getWidth() == size ? shrinking : scaled(shrinking, size);
    }

    private static BufferedImage scaled(BufferedImage source, int size) {
        var smaller = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        var into = smaller.createGraphics();
        into.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        into.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        into.drawImage(source, 0, 0, size, size, null);
        into.dispose();
        return smaller;
    }

}
