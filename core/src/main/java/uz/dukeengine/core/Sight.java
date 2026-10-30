package uz.dukeengine.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.BiPredicate;
import java.util.function.Supplier;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.partition.Lookers;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.Footprint;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.core.thing.Sighted;
import uz.dukeengine.core.thing.Solid;

/**
 * What each player sees: the fog of war from his things' and his allies' fog ranges, the sight others lend him, the
 * map revealed to him or marked seen, and — where the game keeps them — his cells ({@link SightCells}), looked at as
 * each frame ends. {@code GameLogic}'s fog, which it answers its questions of sight from.
 */
final class Sight {

    /** How two players stand to each other, as their world says. */
    @FunctionalInterface
    interface Relations {
        Relationship between(int a, int b);
    }

    private final Roster roster;
    private final Sleepers sleepers;
    private final Supplier<PathGrid> grid;
    private final Relations relations;
    /** Whose things lend a player their sight beyond its own and its allies'; null for none. */
    private BiPredicate<Integer, GameObject> shared;
    /** The players the whole map has been revealed to, for good. Sorted. */
    private final TreeSet<Integer> revealedTo = new TreeSet<>();
    /** The players whose whole map was marked seen. Sorted. */
    private final TreeSet<Integer> markedSeen = new TreeSet<>();
    private SightCells cells;
    private float cellSize;
    private int linger;
    /** Whether stone and the floors above a looker hide the ground from it. */
    private boolean hiddenByStone;
    /** The sight cells things that block sight stand on this frame — see {@link Solid#blocksSight}. */
    private final Set<Long> blocked = new HashSet<>();
    /** The places looked at this frame with no thing there, until the look at the frame's end: see {@link #glance}. */
    private final List<Glance> glances = new ArrayList<>();

    private record Glance(int player, Coord3D at, float reach) {
    }

    Sight(Roster roster, Sleepers sleepers, Supplier<PathGrid> grid, Relations relations) {
        this.roster = roster;
        this.sleepers = sleepers;
        this.grid = grid;
        this.relations = relations;
    }

    boolean canSee(int viewerPlayer, Coord3D position) {
        if (revealedTo.contains(viewerPlayer)) {
            return true; // the whole map, for good
        }
        if (cells != null) {
            return cells.inSight(viewerPlayer, position); // by its cells, the while after counted
        }
        for (var glance : glances) {
            if (glance.player == viewerPlayer && glance.at.distance(position) <= glance.reach) {
                return true;
            }
        }
        for (var watchers : mayShow(viewerPlayer)) {
            for (var watcher : watchers) {
                float reach = reachFor(viewerPlayer, watcher);
                if (reach >= 0f && watcher.getPosition().distance(position) <= reach) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * {@code player} looks at the disc of {@code reach} round {@code at}, with no thing of his there: this frame, and —
     * where the game keeps cells — the while after, as a thing's look does. The reference's {@code doShroudReveal} and
     * at once its {@code undoShroudReveal}.
     */
    void glance(int player, Coord3D at, float reach) {
        glances.add(new Glance(player, at, reach));
    }

    /**
     * The things that may show {@code viewerPlayer} anything, a list for each kind: his own, each ally's, those every
     * player sees round, and — where the game lends sight — every thing, since any may lend its. Every thing
     * {@link #reachFor} gives a reach is among them; one may be twice, which a question of any of them answers alike.
     */
    private List<List<GameObject>> mayShow(int viewerPlayer) {
        if (shared != null) {
            return List.of(roster.list());
        }
        var lists = new ArrayList<List<GameObject>>();
        for (var owner : roster.byPlayer().entrySet()) {
            int player = owner.getKey();
            if (player == viewerPlayer || relations.between(viewerPlayer, player) == Relationship.ALLIES) {
                lists.add(owner.getValue());
            }
        }
        lists.add(roster.seenByAll());
        return lists;
    }

    /** The lookers that show {@code viewerPlayer} anything, by the ground their reach covers. */
    private Lookers lookersFor(int viewerPlayer) {
        var lookers = new Lookers();
        for (var glance : glances) {
            if (glance.player == viewerPlayer) {
                lookers.add(glance.at, glance.reach);
            }
        }
        for (var watchers : mayShow(viewerPlayer)) {
            for (var watcher : watchers) {
                float reach = reachFor(viewerPlayer, watcher);
                if (reach >= 0f) {
                    lookers.add(watcher.getPosition(), reach);
                }
            }
        }
        return lookers;
    }

    /**
     * How far {@code watcher} shows {@code viewerPlayer} round it: its fog range where it is one of that player's eyes,
     * the range every player sees round it where it is not hidden from him, the more of the two; negative for none.
     */
    private float reachFor(int viewerPlayer, GameObject watcher) {
        if (watcher.isEffectivelyDead() || watcher.hasStatus(ObjectStatus.HIDDEN)
                || watcher.isContained() && !watcher.seesOut()) {
            return -1f; // a thing not there sees nothing either, nor one shut in a hold
        }
        boolean goingUp = watcher.hasStatus(ObjectStatus.UNDER_CONSTRUCTION);
        float fog = goingUp ? watcher.getGeometry().footprintRadius() : watcher.getFogRange();
        float byAll = goingUp ? 0f : Sighted.seenByAllOf(watcher.getTemplate());
        if (fog <= 0f && byAll <= 0f) {
            return -1f;
        }
        float reach = byAll > 0f && !watcher.isHiddenFrom(viewerPlayer) ? byAll : -1f;
        boolean eye = watcher.getPlayerIndex() == viewerPlayer
                || relations.between(viewerPlayer, watcher.getPlayerIndex()) == Relationship.ALLIES
                || shared != null && shared.test(viewerPlayer, watcher);
        return eye ? Math.max(reach, fog) : reach;
    }

    void setShared(BiPredicate<Integer, GameObject> rule) {
        this.shared = rule;
    }

    void reveal(int player) {
        revealedTo.add(player);
        if (cells != null) {
            cells.reveal(player);
        }
    }

    boolean isRevealedTo(int player) {
        return revealedTo.contains(player);
    }

    SortedSet<Integer> revealedTo() {
        return Collections.unmodifiableSortedSet(revealedTo);
    }

    void markSeen(int player) {
        markedSeen.add(player);
        if (cells != null) {
            cells.markSeen(player);
        }
    }

    boolean isMarkedSeen(int player) {
        return markedSeen.contains(player);
    }

    SortedSet<Integer> markedSeen() {
        return Collections.unmodifiableSortedSet(markedSeen);
    }

    /** Nobody has seen anything of a new match's map, nor had it revealed. */
    void forget() {
        revealedTo.clear();
        markedSeen.clear();
        lay();
    }

    /** Every object {@code viewerPlayer} can currently see, in creation order — as {@link #canSee} says of each. */
    List<GameObject> visibleTo(int viewerPlayer) {
        boolean all = revealedTo.contains(viewerPlayer);
        var lookers = all || cells != null ? null : lookersFor(viewerPlayer);
        // His own things, and the things standing where his lookers' reach falls: a look at those alone, where the
        // partition finds them for less than a look at every thing.
        var near = lookers == null ? null : roster.partition().standingWhereSeen(lookers);
        var candidates = near == null ? roster.list()
                : CreationOrder.merged(roster.byPlayer().getOrDefault(viewerPlayer, List.of()), near);
        var visible = new ArrayList<GameObject>();
        for (var object : candidates) {
            if (object.isHiddenFrom(viewerPlayer)) {
                continue;
            }
            if (object.getPlayerIndex() == viewerPlayer || all
                    || (lookers != null ? lookers.see(object.getPosition())
                            : cells.inSight(viewerPlayer, object.getPosition()))) {
                visible.add(object);
            }
        }
        return visible;
    }

    // ---- what each player has seen, by cells ----

    void setCells(float cellSize, int linger) {
        this.cellSize = cellSize;
        this.linger = linger;
        this.cells = null;
        lay();
    }

    SightCells cells() {
        return cells;
    }

    void setHiddenByStone(boolean hidden) {
        this.hiddenByStone = hidden;
        if (cells != null) {
            cells.setGround(hidden ? groundUnderTheCells(cells, grid.get(), blocked) : null);
        }
    }

    /**
     * The map's stone and storeys, each sight cell asked of the ground under its middle — and the cells a thing that
     * blocks sight stands on, as {@code blocked} holds them this frame, stone too.
     */
    private static SightCells.Ground groundUnderTheCells(SightCells cells, PathGrid grid, Set<Long> blocked) {
        float size = cells.cellSize();
        float ground = grid.getCellSize();
        return new SightCells.Ground() {
            @Override
            public boolean stone(int cx, int cy) {
                return blocked.contains(cellKey(cx, cy)) || grid.isTerrainBlocked(under(cx), under(cy));
            }

            @Override
            public int storey(int cx, int cy) {
                return grid.level(under(cx), under(cy));
            }

            private int under(int cell) {
                return (int) Math.floor((cell + 0.5f) * size / ground);
            }
        };
    }

    SightCells.Memory memory(int player) {
        return cells == null ? null : cells.remember(player);
    }

    void recall(int player, SightCells.Memory memory) {
        if (cells != null && memory != null) {
            cells.recall(player, memory);
        }
    }

    /** Each player's cells laid afresh for the ground as it is — none where the game keeps none, or there is no grid. */
    void lay() {
        var pathGrid = grid.get();
        if (cellSize <= 0f || pathGrid == null) {
            cells = null;
            return;
        }
        cells = new SightCells(cellSize, linger, pathGrid.getWidth() * pathGrid.getCellSize(),
                pathGrid.getHeight() * pathGrid.getCellSize());
        if (hiddenByStone) {
            cells.setGround(groundUnderTheCells(cells, pathGrid, blocked));
        }
        for (int player : markedSeen) {
            cells.markSeen(player); // what was marked is marked on the new grid's cells too
        }
        for (int player : revealedTo) {
            cells.reveal(player);
        }
    }

    private static long cellKey(int cx, int cy) {
        return ((long) cy << 32) | (cx & 0xFFFFFFFFL);
    }

    /**
     * Lay the cells every standing thing that blocks sight covers: those its footprint overlaps ({@link
     * Footprint#cellsOverlapped}). Laid again each look, so one gone or swapped for another template uncovers them at
     * once. Every thing is asked, a sleeping one too — a shut gate hides the court whether or not anyone is near it.
     */
    private void layWhatBlocksSight() {
        blocked.clear();
        float size = cells.cellSize();
        for (var thing : roster.list()) {
            if (thing.isDestroyed() || !(thing.getTemplate() instanceof Solid solid) || !solid.blocksSight()) {
                continue;
            }
            Footprint.of(thing).cellsOverlapped(size, cells.width(), cells.height(),
                    (cx, cy) -> blocked.add(cellKey(cx, cy)));
        }
    }

    /** As the frame ends: every player's lookers cover their discs, as {@link #canSee} has them look. */
    void look(int frame, int players) {
        if (cells == null) {
            glances.clear();
            return;
        }
        if (hiddenByStone) {
            layWhatBlocksSight();
        }
        cells.begin(frame);
        var watchers = sleepers.awakeOr(roster.list()); // a sleeping thing looks at nothing
        for (int player = 0; player < players; player++) {
            for (var watcher : watchers) {
                float reach = reachFor(player, watcher);
                if (reach >= 0f) {
                    cells.look(player, watcher.getPosition(), reach);
                }
            }
        }
        for (var glance : glances) {
            cells.look(glance.player, glance.at, glance.reach);
        }
        glances.clear();
    }
}
