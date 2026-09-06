package uz.duke.studio.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToolBar;
import uz.duke.studio.model.StudioProject;
import uz.duke.studio.model.StudioProject.MapDef;
import uz.duke.studio.model.StudioProject.Placement;

/**
 * The Studio's <b>map editor</b> — the RTS-correct scene view. A map carries no
 * armies (factions bring their own starting bases); here you paint terrain,
 * drop the numbered <b>start positions</b> players spawn at, and place neutral
 * objects (resource piles, critters). Pan/zoom + terrain brush included.
 */
public final class MapPanel extends JPanel {

    private static final String TERRAIN_TOOL = "◼ Terrain (paint)";
    private static final String START_TOOL = "◆ Start position";
    private static final float CELL = 10f; // world units per pathfinding cell

    private StudioProject project;
    private MapDef map;
    private final Runnable onChange;
    private final JComboBox<String> tool = new JComboBox<>();
    private final JComboBox<Integer> startSlot = new JComboBox<>();
    private final JComboBox<String> brush = new JComboBox<>();
    private final Canvas canvas = new Canvas();

    public MapPanel(StudioProject project, Runnable onChange) {
        super(new BorderLayout());
        this.project = project;
        this.onChange = onChange;
        this.map = project.maps.isEmpty() ? null : project.maps.get(0);

        var bar = new JToolBar();
        bar.setFloatable(false);
        bar.add(new JLabel(" Tool: "));
        bar.add(tool);
        bar.add(new JLabel("  Start #: "));
        for (int i = 1; i <= MapDef.MAX_STARTS; i++) {
            startSlot.addItem(i);
        }
        bar.add(startSlot);
        bar.add(new JLabel("  Brush: "));
        brush.addItem("1");
        brush.addItem("2");
        brush.addItem("3");
        bar.add(brush);
        bar.add(new JLabel("  (LMB place/paint · drag paints · RMB remove · wheel zoom · MMB pan)"));
        add(bar, BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);
        refreshTools();
    }

    public void setProject(StudioProject project) {
        this.project = project;
        this.map = project.maps.isEmpty() ? null : project.maps.get(0);
        refreshTools();
        repaint();
    }

    /** Point the editor at a specific map (the map selector calls this). */
    public void editMap(MapDef map) {
        this.map = map;
        refreshTools();
        repaint();
    }

    public void refreshTools() {
        var tools = new DefaultComboBoxModel<String>();
        tools.addElement(TERRAIN_TOOL);
        tools.addElement(START_TOOL);
        for (var unit : project.units) {
            tools.addElement(unit.name);
        }
        var selected = (String) tool.getSelectedItem();
        tool.setModel(tools);
        if (selected != null) {
            tool.setSelectedItem(selected);
        }
        repaint();
    }

    private final class Canvas extends JPanel {

        private float zoom = 1f;
        private int panX;
        private int panY;
        private Point panOrigin;
        private Boolean paintBlocking;
        private boolean painted;

        Canvas() {
            setBackground(new Color(28, 30, 26));
            var mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (map == null) {
                        return;
                    }
                    if (e.getButton() == MouseEvent.BUTTON2) {
                        panOrigin = new Point(e.getX() - panX, e.getY() - panY);
                        return;
                    }
                    float wx = worldX(e.getX());
                    float wy = worldY(e.getY());
                    if (wx < 0 || wy < 0 || wx > worldWidth() || wy > worldHeight()) {
                        return;
                    }
                    if (e.getButton() == MouseEvent.BUTTON1) {
                        leftPress(wx, wy);
                    } else if (e.getButton() == MouseEvent.BUTTON3) {
                        removeNearest(wx, wy);
                    }
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (panOrigin != null) {
                        panX = e.getX() - panOrigin.x;
                        panY = e.getY() - panOrigin.y;
                        repaint();
                    } else if (paintBlocking != null) {
                        applyBrush(worldX(e.getX()), worldY(e.getY()));
                        repaint();
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    panOrigin = null;
                    if (paintBlocking != null) {
                        paintBlocking = null;
                        if (painted) {
                            painted = false;
                            onChange.run();
                        }
                    }
                }

                @Override
                public void mouseWheelMoved(MouseWheelEvent e) {
                    float before = scale();
                    zoom = Math.max(1f, Math.min(8f, zoom * (e.getWheelRotation() < 0 ? 1.2f : 0.84f)));
                    float after = scale();
                    panX = Math.round(e.getX() - (e.getX() - panX) * after / before);
                    panY = Math.round(e.getY() - (e.getY() - panY) * after / before);
                    repaint();
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
            addMouseWheelListener(mouse);
        }

        private void leftPress(float wx, float wy) {
            var selected = (String) tool.getSelectedItem();
            if (TERRAIN_TOOL.equals(selected)) {
                var key = (int) (wx / CELL) + "," + (int) (wy / CELL);
                paintBlocking = !map.blockedCells.contains(key);
                applyBrush(wx, wy);
                repaint();
            } else if (START_TOOL.equals(selected)) {
                int slot = startSlot.getSelectedIndex();
                while (map.startPositions.size() <= slot) {
                    map.startPositions.add(new float[] {wx, wy});
                }
                map.startPositions.set(slot, new float[] {wx, wy});
                onChange.run();
                repaint();
            } else if (selected != null) {
                map.neutrals.add(new Placement(selected, 0, wx, wy)); // owner ignored for neutrals
                onChange.run();
                repaint();
            }
        }

        private void applyBrush(float wx, float wy) {
            int radius = Math.max(0, brush.getSelectedIndex());
            int centerX = (int) (wx / CELL);
            int centerY = (int) (wy / CELL);
            for (int cy = centerY - radius; cy <= centerY + radius; cy++) {
                for (int cx = centerX - radius; cx <= centerX + radius; cx++) {
                    if (cx < 0 || cy < 0 || cx >= map.cellsWide || cy >= map.cellsHigh) {
                        continue;
                    }
                    var key = cx + "," + cy;
                    if (paintBlocking && !map.blockedCells.contains(key)) {
                        map.blockedCells.add(key);
                        painted = true;
                    } else if (!paintBlocking && map.blockedCells.remove(key)) {
                        painted = true;
                    }
                }
            }
        }

        private void removeNearest(float wx, float wy) {
            Placement nearest = null;
            float best = 20f;
            for (var neutral : map.neutrals) {
                float dist = (float) Math.hypot(neutral.x - wx, neutral.y - wy);
                if (dist < best) {
                    best = dist;
                    nearest = neutral;
                }
            }
            if (nearest != null) {
                map.neutrals.remove(nearest);
                onChange.run();
                repaint();
            }
        }

        private float worldWidth() {
            return map.cellsWide * CELL;
        }

        private float worldHeight() {
            return map.cellsHigh * CELL;
        }

        private float scale() {
            return Math.min(getWidth() / worldWidth(), getHeight() / worldHeight()) * zoom;
        }

        private float worldX(int screenX) {
            return (screenX - panX) / scale();
        }

        private float worldY(int screenY) {
            return (screenY - panY) / scale();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (map == null) {
                return;
            }
            var g = (Graphics2D) graphics;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            float s = scale();
            int cellPx = Math.max(1, Math.round(CELL * s));

            g.setColor(new Color(40, 46, 36));
            g.fillRect(panX, panY, Math.round(worldWidth() * s), Math.round(worldHeight() * s));
            g.setColor(new Color(50, 56, 46));
            for (int cx = 0; cx <= map.cellsWide; cx++) {
                g.drawLine(panX + Math.round(cx * CELL * s), panY,
                        panX + Math.round(cx * CELL * s), panY + Math.round(worldHeight() * s));
            }
            for (int cy = 0; cy <= map.cellsHigh; cy++) {
                g.drawLine(panX, panY + Math.round(cy * CELL * s),
                        panX + Math.round(worldWidth() * s), panY + Math.round(cy * CELL * s));
            }

            g.setColor(new Color(90, 82, 64));
            for (var cell : map.blockedCells) {
                var parts = cell.split(",");
                if (parts.length == 2) {
                    try {
                        int cx = Integer.parseInt(parts[0].trim());
                        int cy = Integer.parseInt(parts[1].trim());
                        g.fillRect(panX + Math.round(cx * CELL * s), panY + Math.round(cy * CELL * s),
                                cellPx, cellPx);
                    } catch (NumberFormatException ignored) {
                        // skip malformed entry
                    }
                }
            }

            // neutral objects
            for (var neutral : map.neutrals) {
                int px = panX + Math.round(neutral.x * s);
                int py = panY + Math.round(neutral.y * s);
                g.setColor(new Color(180, 180, 120));
                g.fillOval(px - 5, py - 5, 10, 10);
                g.setColor(Color.WHITE);
                g.drawString(neutral.unitName, px + 7, py);
            }

            // start positions
            for (int i = 0; i < map.startPositions.size(); i++) {
                var pos = map.startPositions.get(i);
                int px = panX + Math.round(pos[0] * s);
                int py = panY + Math.round(pos[1] * s);
                g.setColor(new Color(80, 200, 255));
                int r = 14;
                g.drawOval(px - r, py - r, r * 2, r * 2);
                g.drawLine(px - r, py, px + r, py);
                g.drawLine(px, py - r, px, py + r);
                g.setColor(Color.WHITE);
                g.drawString("Start " + (i + 1), px + r + 2, py);
            }
        }
    }
}
