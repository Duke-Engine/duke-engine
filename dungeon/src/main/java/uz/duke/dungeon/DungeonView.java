package uz.duke.dungeon;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.Timer;
import uz.duke.core.event.ObjectDied;
import uz.duke.core.math.Coord3D;
import uz.duke.core.thing.ObjectId;
import uz.duke.game.DukeGame;
import uz.duke.game.view.UnitView;
import uz.duke.game.view.WorldSnapshot;

/**
 * The dungeon as the player sees it, and the mouse as the game hears it.
 *
 * <p>Deliberately not the engine's built-in Swing panel. That one is an RTS
 * view — select a unit, then right-click to order it — and a dungeon crawler
 * reads differently: one click, and the hero deals with whatever was clicked.
 * The whole difference is input, so it costs a small window of its own rather
 * than a change to the engine.
 *
 * <p>Nothing here touches the simulation. It reads an immutable
 * {@link WorldSnapshot} and sends commands back, which is the only seam the
 * engine offers across threads — and the only one it should.
 */
final class DungeonView extends JPanel {

    /** How long a skeleton's remains are drawn after it dies, in repaints. */
    private static final int DEATH_MARK_LIFE = 45;

    private final DukeGame game;
    private final List<int[]> deathMarks = new ArrayList<>(); // x, y, life left

    /** The snapshot whose events have already been read, so none is counted twice. */
    private WorldSnapshot lastSeen = WorldSnapshot.EMPTY;

    private float scale = 1f;
    private float originX;
    private float originY;

    DungeonView(DukeGame game) {
        this.game = game;
        setPreferredSize(new Dimension(1000, 640));
        setBackground(new Color(12, 12, 16));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                order(event.getX(), event.getY());
            }
        });
        new Timer(16, e -> repaint()).start();
    }

    // ---- input ----

    /**
     * One click, one decision: a skeleton under the cursor is something to kill,
     * anywhere else is somewhere to walk.
     *
     * <p>Attacking sends two orders, in this order and for a reason. A move
     * order clears the current target — the engine treats an explicit move as
     * "forget what you were doing" — so the attack has to come second or it would
     * be thrown away before the hero got there. Walking into range is the move's
     * job; the weapon only ever fires when it can already reach.
     */
    private void order(int screenX, int screenY) {
        var snapshot = game.getSnapshot();
        var hero = heroIn(snapshot);
        if (hero == null) {
            return; // no hero, no orders
        }
        int player = game.getLocalPlayerIndex();
        var units = List.of(new ObjectId(hero.id()));
        var target = enemyAt(snapshot, screenX, screenY);

        if (target != null) {
            game.postCommand(new uz.duke.rts.message.GameMessage.MoveTo(
                    player, units, new Coord3D(target.x(), target.y(), 0f)));
            game.postCommand(new uz.duke.rts.message.GameMessage.AttackObject(
                    player, units, new ObjectId(target.id())));
            return;
        }
        game.postCommand(new uz.duke.rts.message.GameMessage.MoveTo(
                player, units, new Coord3D(worldX(screenX), worldY(screenY), 0f)));
    }

    private UnitView heroIn(WorldSnapshot snapshot) {
        for (var unit : snapshot.units()) {
            if (unit.playerIndex() == game.getLocalPlayerIndex()) {
                return unit;
            }
        }
        return null;
    }

    /** The enemy under the cursor, if the click landed on one. */
    private UnitView enemyAt(WorldSnapshot snapshot, int screenX, int screenY) {
        float wx = worldX(screenX);
        float wy = worldY(screenY);
        for (var unit : snapshot.units()) {
            if (unit.playerIndex() == game.getLocalPlayerIndex()) {
                continue;
            }
            float dx = unit.x() - wx;
            float dy = unit.y() - wy;
            // A little wider than the body: clicking a small circle exactly is a
            // test of the mouse, not of the player.
            if (dx * dx + dy * dy <= 10f * 10f) {
                return unit;
            }
        }
        return null;
    }

    // ---- drawing ----

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        var g = (Graphics2D) graphics;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        var snapshot = game.getSnapshot();
        fitRoomToWindow();
        drawRoom(g);
        collectDeaths(snapshot);
        drawDeathMarks(g);
        for (var unit : snapshot.units()) {
            drawCreature(g, unit);
        }
        drawStatus(g, snapshot);
    }

    /** The room is small and fixed, so it is shown whole rather than scrolled. */
    private void fitRoomToWindow() {
        var terrain = game.getTerrain();
        if (terrain == null) {
            return;
        }
        float worldWidth = terrain.getWidth() * terrain.getCellSize();
        float worldHeight = terrain.getHeight() * terrain.getCellSize();
        scale = Math.min(getWidth() / worldWidth, getHeight() / worldHeight) * 0.95f;
        originX = (getWidth() - worldWidth * scale) / 2f;
        originY = (getHeight() - worldHeight * scale) / 2f;
    }

    private void drawRoom(Graphics2D g) {
        var terrain = game.getTerrain();
        if (terrain == null) {
            return;
        }
        float cell = terrain.getCellSize();
        for (int cy = 0; cy < terrain.getHeight(); cy++) {
            for (int cx = 0; cx < terrain.getWidth(); cx++) {
                // The map's own stone, not whatever happens to be standing on it:
                // creatures are drawn as creatures, not as scenery.
                g.setColor(terrain.isTerrainBlocked(cx, cy)
                        ? new Color(58, 54, 66) : new Color(26, 25, 31));
                g.fillRect(Math.round(originX + cx * cell * scale),
                        Math.round(originY + cy * cell * scale),
                        Math.round(cell * scale) + 1, Math.round(cell * scale) + 1);
            }
        }
    }

    private void drawCreature(Graphics2D g, UnitView unit) {
        boolean mine = unit.playerIndex() == game.getLocalPlayerIndex();
        float radius = 5f * scale;
        int x = Math.round(screenX(unit.x()));
        int y = Math.round(screenY(unit.y()));

        g.setColor(mine ? Dungeon.HERO_COLOUR : Dungeon.SKELETON_COLOUR);
        g.fillOval(Math.round(x - radius), Math.round(y - radius),
                Math.round(radius * 2), Math.round(radius * 2));

        if (mine) {
            g.setColor(new Color(255, 255, 255, 90));
            g.setStroke(new BasicStroke(2f));
            g.drawOval(Math.round(x - radius - 3), Math.round(y - radius - 3),
                    Math.round(radius * 2) + 6, Math.round(radius * 2) + 6);
        }
        if (unit.isDamaged()) {
            drawHealthBar(g, x, y - Math.round(radius) - 8, unit.healthFraction());
        }
    }

    private void drawHealthBar(Graphics2D g, int x, int y, float fraction) {
        int width = 26;
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRect(x - width / 2, y, width, 4);
        g.setColor(fraction > 0.5f ? new Color(110, 210, 110)
                : fraction > 0.25f ? new Color(230, 180, 70) : new Color(220, 80, 70));
        g.fillRect(x - width / 2, y, Math.round(width * fraction), 4);
    }

    /**
     * Deaths come from the simulation as events, not guessed from a creature
     * having vanished — a thing can leave the screen for more reasons than dying.
     */
    private void collectDeaths(WorldSnapshot snapshot) {
        if (snapshot != lastSeen) {
            lastSeen = snapshot;
            for (var event : snapshot.events()) {
                if (event instanceof ObjectDied died) {
                    deathMarks.add(new int[] {
                        Math.round(died.position().x()), Math.round(died.position().y()),
                        DEATH_MARK_LIFE,
                    });
                }
            }
        }
        deathMarks.removeIf(mark -> --mark[2] <= 0);
    }

    private void drawDeathMarks(Graphics2D g) {
        g.setStroke(new BasicStroke(2f));
        for (var mark : deathMarks) {
            int x = Math.round(screenX(mark[0]));
            int y = Math.round(screenY(mark[1]));
            g.setColor(new Color(200, 200, 210,
                    Math.max(0, 200 * mark[2] / DEATH_MARK_LIFE)));
            g.drawLine(x - 6, y - 6, x + 6, y + 6);
            g.drawLine(x - 6, y + 6, x + 6, y - 6);
        }
    }

    private void drawStatus(Graphics2D g, WorldSnapshot snapshot) {
        var hero = heroIn(snapshot);
        long skeletons = snapshot.units().stream()
                .filter(unit -> unit.playerIndex() != game.getLocalPlayerIndex())
                .count();

        g.setColor(new Color(220, 220, 230));
        g.drawString(hero == null
                ? "You have fallen."
                : "Hero %.0f/%.0f    skeletons: %d".formatted(
                        hero.health(), hero.maxHealth(), skeletons), 14, 22);
        g.setColor(new Color(150, 150, 165));
        g.drawString("click the floor to walk   .   click a skeleton to attack it",
                14, getHeight() - 14);

        if (snapshot.hasBanner()) {
            g.setColor(new Color(255, 225, 120));
            var metrics = g.getFontMetrics();
            g.drawString(snapshot.banner(),
                    (getWidth() - metrics.stringWidth(snapshot.banner())) / 2, getHeight() / 2);
        }
    }

    // ---- world <-> screen ----

    private float screenX(float worldX) {
        return originX + worldX * scale;
    }

    private float screenY(float worldY) {
        return originY + worldY * scale;
    }

    private float worldX(int screenX) {
        return (screenX - originX) / scale;
    }

    private float worldY(int screenY) {
        return (screenY - originY) / scale;
    }
}
