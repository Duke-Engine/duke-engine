package uz.duke.worldbuilder.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import javax.swing.JPanel;
import uz.duke.worldbuilder.StageDraft;

/**
 * The stage from above.
 *
 * <p>Two dimensions and no models, which is not a compromise. What is being
 * decided here is where things stand relative to each other and to the walls, and
 * that is a plan view — a 3D preview would show a corner of the same answer more
 * slowly. The player sees the 3D one by running the stage, which is the honest
 * way to look at it anyway.
 *
 * <p>Storeys are drawn as shades rather than as height: a floor one storey up is
 * one step lighter, and a stair is marked. Plan views cannot show height any other
 * way, which the game's own minimap found out first.
 */
final class StageCanvas extends JPanel {

    private static final Color STONE = new Color(28, 28, 32);
    private static final Color GRID = new Color(44, 44, 50);
    private static final Color FLOOR = new Color(72, 72, 82);
    private static final Color STAIR = new Color(150, 130, 80);
    private static final Color ROOM_EDGE = new Color(96, 104, 120);
    private static final Color ENTRANCE = new Color(90, 170, 255);
    private static final Color BOSS_RING = new Color(255, 210, 90);
    private static final Color PROP = new Color(150, 146, 138);
    private static final Color HOVER = new Color(255, 255, 255, 70);

    private final Runnable onEdit;
    private final Palette palette;
    private StageDraft draft;

    private float zoom = 14f;
    private int panX = 20;
    private int panY = 20;
    private Point dragFrom;
    private int hoverX = -1;
    private int hoverY = -1;

    StageCanvas(StageDraft draft, Palette palette, Runnable onEdit) {
        this.draft = draft;
        this.palette = palette;
        this.onEdit = onEdit;
        setBackground(new Color(18, 18, 21));
        setPreferredSize(new Dimension(900, 620));
        var mouse = new Mouse();
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
    }

    void show(StageDraft draft) {
        this.draft = draft;
        repaint();
    }

    /**
     * Put the whole map on screen.
     *
     * <p>Called whenever a new floor is drawn, because a floor can now be any size
     * at all: a two-hundred-cell stage opened at the last one's zoom is a corner of
     * itself, and an author who has just asked for something large should see that
     * he got it.
     */
    void fitToMap() {
        int across = Math.max(1, draft.cellsAcross());
        int down = Math.max(1, draft.cellsDown());
        // Before the window is laid out there is no size to fit to, so the
        // preferred one stands in — the first fit happens at construction.
        int usableX = getWidth() > 0 ? getWidth() : getPreferredSize().width;
        int usableY = getHeight() > 0 ? getHeight() : getPreferredSize().height;
        zoom = Math.clamp(Math.min((usableX - 40f) / across, (usableY - 40f) / down), 3f, 44f);
        panX = Math.round((usableX - across * zoom) / 2f);
        panY = Math.round((usableY - down * zoom) / 2f);
        repaint();
    }

    /** The cell under a point on screen, or null for a point off the map. */
    private int[] cellAt(Point at) {
        int cx = (int) Math.floor((at.x - panX) / zoom);
        int cy = (int) Math.floor((at.y - panY) / zoom);
        boolean onMap = cx >= 0 && cy >= 0 && cx < draft.cellsAcross() && cy < draft.cellsDown();
        return onMap ? new int[] {cx, cy} : null;
    }

    // ---- drawing ----

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        var g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        paintGround(g2);
        paintRooms(g2);
        paintThings(g2);
        if (hoverX >= 0) {
            g2.setColor(HOVER);
            g2.fillRect(x(hoverX), y(hoverY), (int) zoom, (int) zoom);
        }
    }

    private void paintGround(Graphics2D g2) {
        var walls = draft.terrain().strip().split("\n");
        var heights = draft.storeys().strip().split("\n");
        int cell = Math.max(1, (int) zoom);
        for (int cy = 0; cy < walls.length; cy++) {
            // A hand-edited file's two layers may not be the same size. The fault
            // list says so in words; this only has to keep drawing long enough for
            // the author to read it.
            var storeyRow = cy < heights.length ? heights[cy] : "";
            for (int cx = 0; cx < walls[cy].length(); cx++) {
                char wall = walls[cy].charAt(cx);
                char height = cx < storeyRow.length() ? storeyRow.charAt(cx) : '0';
                g2.setColor(colourOf(wall, height));
                g2.fillRect(x(cx), y(cy), cell, cell);
            }
        }
        if (zoom >= 9f) {
            g2.setColor(GRID);
            for (int cx = 0; cx <= draft.cellsAcross(); cx++) {
                g2.drawLine(x(cx), y(0), x(cx), y(draft.cellsDown()));
            }
            for (int cy = 0; cy <= draft.cellsDown(); cy++) {
                g2.drawLine(x(0), y(cy), x(draft.cellsAcross()), y(cy));
            }
        }
    }

    /** Stone, or floor one shade lighter for every storey it stands above the way in. */
    private static Color colourOf(char wall, char height) {
        if (wall == '#') {
            return STONE;
        }
        if (height == '/') {
            return STAIR;
        }
        int storey = height >= '0' && height <= '9' ? height - '0' : 0;
        int lift = Math.min(storey, 4) * 22;
        return new Color(Math.min(255, FLOOR.getRed() + lift),
                Math.min(255, FLOOR.getGreen() + lift),
                Math.min(255, FLOOR.getBlue() + lift));
    }

    private void paintRooms(Graphics2D g2) {
        g2.setStroke(new BasicStroke(1f));
        for (int i = 0; i < draft.rooms().size(); i++) {
            var room = draft.rooms().get(i);
            g2.setColor(i == draft.bossRoom() ? BOSS_RING : ROOM_EDGE);
            g2.drawRect(x(room.x()), y(room.y()),
                    (int) (room.w() * zoom), (int) (room.h() * zoom));
            if (zoom >= 11f) {
                g2.drawString(String.valueOf(i), x(room.x()) + 3, y(room.y()) + 12);
            }
        }
    }

    private void paintThings(Graphics2D g2) {
        for (var prop : draft.props()) {
            g2.setColor(PROP);
            var at = prop.at();
            g2.fillRect(x(at.cellX()) + (int) (zoom * 0.25f), y(at.cellY()) + (int) (zoom * 0.25f),
                    (int) (zoom * 0.5f), (int) (zoom * 0.5f));
        }
        for (var monster : draft.monsters()) {
            dot(g2, monster.at().cellX(), monster.at().cellY(),
                    palette.colourOf(monster.kind()), 0.72f);
        }
        if (draft.bossAt() != null) {
            dot(g2, draft.bossAt().cellX(), draft.bossAt().cellY(),
                    palette.colourOf(draft.bossKind()), 1.1f);
            g2.setColor(BOSS_RING);
            g2.setStroke(new BasicStroke(2f));
            int size = (int) (zoom * 1.4f);
            g2.drawOval(x(draft.bossAt().cellX()) + (int) (zoom / 2) - size / 2,
                    y(draft.bossAt().cellY()) + (int) (zoom / 2) - size / 2, size, size);
        }
        if (draft.entrance() != null) {
            dot(g2, draft.entrance().cellX(), draft.entrance().cellY(), ENTRANCE, 0.9f);
        }
    }

    private void dot(Graphics2D g2, int cx, int cy, Color colour, float size) {
        g2.setColor(colour);
        int width = Math.max(3, (int) (zoom * size));
        g2.fillOval(x(cx) + (int) (zoom / 2) - width / 2, y(cy) + (int) (zoom / 2) - width / 2,
                width, width);
    }

    private int x(int cx) {
        return panX + (int) (cx * zoom);
    }

    private int y(int cy) {
        return panY + (int) (cy * zoom);
    }

    // ---- the mouse ----

    private final class Mouse extends MouseAdapter {

        @Override
        public void mousePressed(MouseEvent e) {
            if (javax.swing.SwingUtilities.isMiddleMouseButton(e)) {
                dragFrom = e.getPoint();
                return;
            }
            place(e);
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (dragFrom != null) {
                panX += e.getX() - dragFrom.x;
                panY += e.getY() - dragFrom.y;
                dragFrom = e.getPoint();
                repaint();
                return;
            }
            // Dragging does not paint: one click is one creature, and a dragged
            // line of skeletons is a room nobody meant to fill.
            hover(e);
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            dragFrom = null;
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            hover(e);
        }

        @Override
        public void mouseExited(MouseEvent e) {
            hoverX = -1;
            repaint();
        }

        @Override
        public void mouseWheelMoved(MouseWheelEvent e) {
            // Zoom about the pointer, so the cell under it stays under it.
            var before = cellPoint(e.getPoint());
            zoom = Math.clamp(zoom * (e.getWheelRotation() < 0 ? 1.15f : 1 / 1.15f), 3f, 44f);
            var after = cellPoint(e.getPoint());
            panX += (int) ((after[0] - before[0]) * zoom);
            panY += (int) ((after[1] - before[1]) * zoom);
            repaint();
        }

        private float[] cellPoint(Point at) {
            return new float[] {(at.x - panX) / zoom, (at.y - panY) / zoom};
        }

        private void hover(MouseEvent e) {
            var cell = cellAt(e.getPoint());
            int wasX = hoverX;
            int wasY = hoverY;
            hoverX = cell == null ? -1 : cell[0];
            hoverY = cell == null ? -1 : cell[1];
            if (wasX != hoverX || wasY != hoverY) {
                repaint();
            }
        }

        private void place(MouseEvent e) {
            var cell = cellAt(e.getPoint());
            var tool = palette.selected();
            if (cell == null || tool == null) {
                return;
            }
            if (javax.swing.SwingUtilities.isRightMouseButton(e)) {
                if (!draft.removeAt(cell[0], cell[1])) {
                    return; // nothing there; not worth an undo step
                }
            } else {
                tool.placeOn(draft, cell[0], cell[1]);
            }
            onEdit.run();
            repaint();
        }
    }
}
