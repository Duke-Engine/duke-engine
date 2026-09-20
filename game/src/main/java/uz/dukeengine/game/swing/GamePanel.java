package uz.dukeengine.game.swing;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.JPanel;
import javax.swing.Timer;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.rts.message.GameMessage;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.game.view.UnitView;
import uz.dukeengine.game.view.WorldSnapshot;

/**
 * The built-in 2D view and input layer: draws the world snapshot and turns
 * mouse/keyboard into engine commands — the RTS controls everyone knows:
 *
 * <ul>
 *   <li><b>Left click / drag</b> — select your units</li>
 *   <li><b>Right click</b> — move; on an enemy, attack</li>
 *   <li><b>WASD / arrows</b> — pan camera; <b>mouse wheel</b> — zoom</li>
 *   <li><b>S</b> — stop, <b>P</b> — pause, <b>Esc</b> — deselect</li>
 * </ul>
 *
 * <p>Runs entirely on the Swing thread against immutable {@link WorldSnapshot}s;
 * commands cross to the simulation through {@link DukeGame#postCommand}.
 */
public final class GamePanel extends JPanel {

    private static final Color BACKGROUND = new Color(24, 26, 22);
    private static final Color GRID = new Color(34, 37, 32);
    private static final Color BLOCKED = new Color(58, 54, 44);
    private static final Color SELECTION = new Color(255, 255, 255, 200);
    private static final Color DRAG_FILL = new Color(120, 200, 120, 40);
    private static final Color DRAG_EDGE = new Color(120, 220, 120, 180);
    private static final float PICK_RADIUS_WU = 2.5f;

    private final DukeGame game;

    private float camX;
    private float camY;
    private float scale = 9f; // pixels per world unit

    private final Set<Integer> selected = new HashSet<>();
    private Rectangle dragRect;
    private int dragStartX;
    private int dragStartY;

    public GamePanel(DukeGame game) {
        this.game = game;
        setBackground(BACKGROUND);
        setFocusable(true);
        installMouse();
        installKeys();
        new Timer(33, e -> repaint()).start(); // ~30 fps display refresh
    }

    // ---- coordinate mapping ----

    private float worldX(int screenX) {
        return camX + screenX / scale;
    }

    private float worldY(int screenY) {
        return camY + screenY / scale;
    }

    private int screenX(float worldX) {
        return Math.round((worldX - camX) * scale);
    }

    private int screenY(float worldY) {
        return Math.round((worldY - camY) * scale);
    }

    // ---- input ----

    private void installMouse() {
        var mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                if (e.getButton() == MouseEvent.BUTTON1) {
                    dragStartX = e.getX();
                    dragStartY = e.getY();
                    dragRect = new Rectangle(dragStartX, dragStartY, 0, 0);
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragRect != null) {
                    dragRect = normalized(dragStartX, dragStartY, e.getX(), e.getY());
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1 && dragRect != null) {
                    selectIn(dragRect);
                    dragRect = null;
                    repaint();
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON3) {
                    issueOrderAt(e.getX(), e.getY());
                }
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                float before = scale;
                scale = Math.max(3f, Math.min(24f, scale * (e.getWheelRotation() < 0 ? 1.15f : 0.87f)));
                // Keep the point under the cursor fixed while zooming.
                camX += e.getX() / before - e.getX() / scale;
                camY += e.getY() / before - e.getY() / scale;
                repaint();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
    }

    private void installKeys() {
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                float pan = 40f / scale * 4f;
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_LEFT, KeyEvent.VK_A -> camX -= pan;
                    case KeyEvent.VK_RIGHT, KeyEvent.VK_D -> camX += pan;
                    case KeyEvent.VK_UP, KeyEvent.VK_W -> camY -= pan;
                    case KeyEvent.VK_DOWN -> camY += pan;
                    case KeyEvent.VK_S -> {
                        if (e.isControlDown() || selected.isEmpty()) {
                            camY += pan; // no selection: S pans like a camera key
                        } else {
                            stopSelected();
                        }
                    }
                    case KeyEvent.VK_P -> game.togglePause();
                    case KeyEvent.VK_ESCAPE -> selected.clear();
                    default -> {
                    }
                }
                repaint();
            }
        });
    }

    private static Rectangle normalized(int x0, int y0, int x1, int y1) {
        return new Rectangle(Math.min(x0, x1), Math.min(y0, y1), Math.abs(x1 - x0), Math.abs(y1 - y0));
    }

    /** Box- or click-select the local player's units. */
    private void selectIn(Rectangle rect) {
        var snapshot = game.getSnapshot();
        selected.clear();
        boolean isClick = rect.width < 4 && rect.height < 4;
        UnitView closest = null;
        float closestDist = Float.MAX_VALUE;

        for (var unit : snapshot.units()) {
            if (unit.playerIndex() != game.getLocalPlayerIndex() || !unit.selectable()) {
                continue;
            }
            int sx = screenX(unit.x());
            int sy = screenY(unit.y());
            if (isClick) {
                float wx = worldX(rect.x);
                float wy = worldY(rect.y);
                float dx = unit.x() - wx;
                float dy = unit.y() - wy;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist <= PICK_RADIUS_WU && dist < closestDist) {
                    closest = unit;
                    closestDist = dist;
                }
            } else if (rect.contains(sx, sy)) {
                selected.add(unit.id());
            }
        }
        if (isClick && closest != null) {
            selected.add(closest.id());
        }
    }

    /** Right-click: attack the enemy under the cursor, otherwise move there. */
    private void issueOrderAt(int mouseX, int mouseY) {
        if (selected.isEmpty()) {
            return;
        }
        var snapshot = game.getSnapshot();
        int local = game.getLocalPlayerIndex();
        var units = selectedIds(snapshot);
        if (units.isEmpty()) {
            return;
        }

        float wx = worldX(mouseX);
        float wy = worldY(mouseY);
        UnitView enemy = null;
        float enemyDist = Float.MAX_VALUE;
        for (var unit : snapshot.units()) {
            if (unit.playerIndex() == local || unit.playerIndex() == 0) {
                continue;
            }
            float dx = unit.x() - wx;
            float dy = unit.y() - wy;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist <= PICK_RADIUS_WU && dist < enemyDist) {
                enemy = unit;
                enemyDist = dist;
            }
        }

        if (enemy != null) {
            game.postCommand(new GameMessage.AttackObject(local, units, new ObjectId(enemy.id())));
        } else {
            game.postCommand(new GameMessage.MoveTo(local, units, new Coord3D(wx, wy, 0f)));
        }
    }

    private void stopSelected() {
        var units = selectedIds(game.getSnapshot());
        if (!units.isEmpty()) {
            game.postCommand(new GameMessage.StopMoving(game.getLocalPlayerIndex(), units));
        }
    }

    /** Selected ids that still exist in the snapshot (drops dead units). */
    private List<ObjectId> selectedIds(WorldSnapshot snapshot) {
        var alive = new ArrayList<ObjectId>();
        for (var unit : snapshot.units()) {
            if (selected.contains(unit.id())) {
                alive.add(new ObjectId(unit.id()));
            }
        }
        return alive;
    }

    // ---- painting ----

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        var g = (Graphics2D) graphics;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        var snapshot = game.getSnapshot();
        paintTerrain(g);
        for (var unit : snapshot.units()) {
            paintUnit(g, unit);
        }
        paintDragRect(g);
        paintHud(g, snapshot);
    }

    private void paintTerrain(Graphics2D g) {
        var grid = game.getTerrain();
        if (grid == null) {
            return;
        }
        float cell = grid.getCellSize();
        int cellPx = Math.max(1, Math.round(cell * scale));
        for (int cy = 0; cy < grid.getHeight(); cy++) {
            for (int cx = 0; cx < grid.getWidth(); cx++) {
                int sx = screenX(cx * cell);
                int sy = screenY(cy * cell);
                if (sx + cellPx < 0 || sy + cellPx < 0 || sx > getWidth() || sy > getHeight()) {
                    continue;
                }
                g.setColor(grid.isBlocked(cx, cy) ? BLOCKED : GRID);
                if (grid.isBlocked(cx, cy)) {
                    g.fillRect(sx, sy, cellPx, cellPx);
                } else {
                    g.drawRect(sx, sy, cellPx, cellPx);
                }
            }
        }
    }

    private void paintUnit(Graphics2D g, UnitView unit) {
        var color = game.getColor(unit.playerIndex());
        int sx = screenX(unit.x());
        int sy = screenY(unit.y());

        if (unit.structure()) {
            int half = Math.round(2.5f * scale);
            g.setColor(color);
            g.fillRect(sx - half, sy - half, half * 2, half * 2);
            g.setColor(color.darker());
            g.drawRect(sx - half, sy - half, half * 2, half * 2);
        } else {
            int radius = Math.round(1.2f * scale);
            g.setColor(color);
            g.fillOval(sx - radius, sy - radius, radius * 2, radius * 2);
            // facing line
            g.setColor(color.brighter());
            int fx = sx + Math.round((float) Math.cos(unit.orientation()) * radius * 1.4f);
            int fy = sy + Math.round((float) Math.sin(unit.orientation()) * radius * 1.4f);
            g.drawLine(sx, sy, fx, fy);
        }

        if (selected.contains(unit.id())) {
            g.setColor(SELECTION);
            g.setStroke(new BasicStroke(2f));
            int ring = Math.round((unit.structure() ? 3.0f : 1.7f) * scale);
            g.drawOval(sx - ring, sy - ring, ring * 2, ring * 2);
            g.setStroke(new BasicStroke(1f));
        }

        if (unit.isDamaged()) {
            int barW = Math.round(3f * scale);
            int barY = sy - Math.round((unit.structure() ? 3.4f : 2.2f) * scale);
            g.setColor(Color.DARK_GRAY);
            g.fillRect(sx - barW / 2, barY, barW, 3);
            g.setColor(unit.healthFraction() > 0.5f ? Color.GREEN : unit.healthFraction() > 0.25f ? Color.ORANGE : Color.RED);
            g.fillRect(sx - barW / 2, barY, Math.round(barW * unit.healthFraction()), 3);
        }
    }

    private void paintDragRect(Graphics2D g) {
        if (dragRect == null) {
            return;
        }
        g.setColor(DRAG_FILL);
        g.fill(dragRect);
        g.setColor(DRAG_EDGE);
        g.draw(dragRect);
    }

    private void paintHud(Graphics2D g, WorldSnapshot snapshot) {
        g.setColor(new Color(0, 0, 0, 140));
        g.fillRect(0, 0, getWidth(), 26);
        g.setColor(Color.WHITE);
        String power = snapshot.localPlayerPowerSurplus() >= 0 ? "+" + snapshot.localPlayerPowerSurplus()
                : String.valueOf(snapshot.localPlayerPowerSurplus());
        g.drawString("$ %d   power %s   t=%.1fs   selected: %d%s".formatted(
                snapshot.localPlayerMoney(), power, snapshot.gameTimeSeconds(), selected.size(),
                snapshot.paused() ? "   [PAUSED]" : ""), 10, 18);

        g.setColor(new Color(255, 255, 255, 120));
        g.drawString("LMB select   RMB move/attack   wheel zoom   WASD pan   S stop   P pause", 10, getHeight() - 10);

        if (snapshot.hasBanner()) {
            g.setFont(g.getFont().deriveFont(java.awt.Font.BOLD, 48f));
            var metrics = g.getFontMetrics();
            g.setColor(new Color(255, 225, 80));
            g.drawString(snapshot.banner(),
                    (getWidth() - metrics.stringWidth(snapshot.banner())) / 2,
                    getHeight() / 2);
        }
    }
}
