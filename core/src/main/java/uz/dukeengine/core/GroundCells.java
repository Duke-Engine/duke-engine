package uz.dukeengine.core;

import java.util.function.Predicate;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.pathfind.Block;
import uz.dukeengine.core.pathfind.MoverCells;
import uz.dukeengine.core.pathfind.Pathfinder;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.core.thing.Solid;

/**
 * The ground movers on the grid's cells, as the reference's pathfinder places them ({@code AIPathfind.cpp}): the block
 * each covers where it stands, the block each is going to, and what a move to a place becomes — the nearest block the
 * mover may have ({@code Pathfinder::adjustDestination}) — and what the movers on a cell add to a route over it.
 */
final class GroundCells {

    /** How many cells round a place are tried for a block: the reference's {@code MAX_CELLS_TO_TRY}. */
    private static final int MOST_TRIED = 400;
    /** What a cell an ally holds still, or one an ally is passing near the start, adds to a step: 3 diagonal steps. */
    private static final int ALLY_COST = 42;
    /** How near the start, in cells, an ally passing a cell makes it dearer. */
    private static final int NEAR_START = 10;

    private final GameLogic world;

    GroundCells(GameLogic world) {
        this.world = world;
    }

    /** Whether {@code mover} keeps cells: a body walking on the grid's ground, alive and not carried or in the air. */
    boolean keepsCells(GameObject mover) {
        return world.getPathGrid() != null
                && mover.getLocomotor() instanceof MoveUpdate
                && !Solid.of(mover.getTemplate()).isPoint()
                && mover.getFloor() == 0
                && !mover.isDestroyed()
                && !mover.isEffectivelyDead()
                && !mover.isContained()
                && !mover.hasStatus(ObjectStatus.AIRBORNE);
    }

    /** The block {@code mover} covers standing at {@code at}. */
    Block blockAt(GameObject mover, Coord3D at) {
        return Block.of(Solid.of(mover.getTemplate()).footprintRadius(), world.getPathGrid().getCellSize(),
                world.getPathGrid().cellsPerMapCell(), at.x(), at.y());
    }

    private MoverCells cells() {
        return world.getPathGrid().movers();
    }

    /**
     * Where {@code mover} sent to {@code place} goes: the nearest block round it that it may have and can walk to,
     * held as its own from now on — or, where there is none within the reference's 400 cells, the place's own block.
     */
    Coord3D take(GameObject mover, Coord3D place) {
        if (!keepsCells(mover)) {
            forget(mover.getId());
            return place;
        }
        var block = nearest(mover, place, any -> true);
        if (block == null) {
            block = blockAt(mover, place);
        }
        cells().claimGoal(mover.getId().value(), block);
        return pointOf(block);
    }

    /** How many cells a group's walk to a place is searched over before it counts as too far: the reference's 500. */
    private static final int MOST_COSTED = 500;

    /**
     * Where {@code mover}, one of a group sent to {@code near}, goes when it wants {@code place}: the nearest block round
     * the place it may have and can walk to — pulled along the line toward {@code near} onto the free block on it
     * nearest {@code near} ({@code Pathfinder::tightenPath}) — whose walk from {@code near} is under 1.4 × (|dx| + |dy|),
     * so not behind a wall from it ({@code checkForAdjust} given a group's destination, and {@code checkPathCost}).
     * Where there is none, as {@link #take(GameObject, Coord3D)}.
     */
    Coord3D take(GameObject mover, Coord3D place, Coord3D near) {
        if (!keepsCells(mover)) {
            forget(mover.getId());
            return place;
        }
        var chosen = new Block[1];
        nearest(mover, place, candidate -> {
            var pulled = pulledToward(mover, candidate, near);
            var at = pointOf(pulled);
            int dx = (int) Math.abs(near.x() - at.x());
            int dy = (int) Math.abs(near.y() - at.y());
            int finer = world.getPathGrid().cellsPerMapCell();
            if (Pathfinder.walkCost(world.getPathGrid(), near, at, MOST_COSTED * finer * finer) > 1.4f * (dx + dy)) {
                return false;
            }
            chosen[0] = pulled;
            return true;
        });
        if (chosen[0] == null) {
            return take(mover, place);
        }
        cells().claimGoal(mover.getId().value(), chosen[0]);
        return pointOf(chosen[0]);
    }

    /** {@code block} pulled along the line from it toward {@code near}, onto the last block on it {@code mover} may have. */
    private Block pulledToward(GameObject mover, Block block, Coord3D near) {
        var from = pointOf(block);
        float dx = near.x() - from.x();
        float dy = near.y() - from.y();
        int samples = (int) Math.ceil(Math.sqrt(dx * dx + dy * dy) / (world.getPathGrid().getCellSize() / 4f));
        var pulled = block;
        for (int s = 1; s <= samples; s++) {
            float t = (float) s / samples;
            var there = blockAt(mover, new Coord3D(from.x() + dx * t, from.y() + dy * t, 0f));
            if (!there.equals(pulled) && mayHold(mover, there) && reachable(mover, there)) {
                pulled = there;
            }
        }
        return pulled;
    }

    /**
     * {@code mover} holding the block it stands on as its own, as a mover does once it has stopped — where it may: not
     * where an ally is going, nor an enemy still.
     */
    boolean hold(GameObject mover) {
        if (!keepsCells(mover)) {
            return false;
        }
        var block = blockAt(mover, mover.getPosition());
        if (!mayHold(mover, block)) {
            return false;
        }
        cells().claimGoal(mover.getId().value(), block);
        return true;
    }

    /** The block {@code mover} was going to, let go. */
    void letGo(GameObject mover) {
        if (world.getPathGrid() != null) {
            cells().releaseGoal(mover.getId().value());
        }
    }

    /** Whether the block {@code mover} is going to is still its own: no other has claimed any of its cells since. */
    boolean holds(GameObject mover) {
        if (world.getPathGrid() == null) {
            return true;
        }
        int id = mover.getId().value();
        var block = cells().goalOf(id);
        if (block == null) {
            return false;
        }
        for (int cy = block.minY(); cy <= block.maxY(); cy++) {
            for (int cx = block.minX(); cx <= block.maxX(); cx++) {
                int holder = cells().goalAt(cx, cy);
                if (holder != id && world.getPathGrid().inBounds(cx, cy)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Where {@code mover} stands, marked on its cells; off the ground, its cells let go. */
    void mark(GameObject mover) {
        if (world.getPathGrid() == null) {
            return;
        }
        if (!keepsCells(mover)) {
            forget(mover.getId());
            return;
        }
        cells().stand(mover.getId().value(), blockAt(mover, mover.getPosition()));
    }

    /** A thing gone from the ground for good. */
    void forget(ObjectId id) {
        if (world.getPathGrid() != null) {
            cells().forget(id.value());
        }
    }

    /** The goal point of a block, on the ground under it. */
    Coord3D pointOf(Block block) {
        var flat = block.point(world.getPathGrid().getCellSize(), 0f);
        return new Coord3D(flat.x(), flat.y(), world.groundHeight(flat));
    }

    /**
     * The nearest block round {@code place} that {@code mover} may have, can walk to and {@code also} takes, spiralling
     * out from the place's own as the reference does — 1 east, 1 north, 2 west, 2 south, 3 east… — over 400 cells;
     * null for none. Where the place itself is out of its reach, a block the place is joined to will do as well, and it
     * goes as near that as it can ({@code checkForAdjust}'s path from the destination).
     */
    Block nearest(GameObject mover, Coord3D place, Predicate<Block> also) {
        var start = blockAt(mover, place);
        var reach = reachFor(mover, place);
        int i = start.x();
        int j = start.y();
        if (fits(mover, start, reach, also)) {
            return start;
        }
        int tried = 1;
        int delta = 1;
        int finer = world.getPathGrid().cellsPerMapCell();
        while (tried < MOST_TRIED * finer * finer) {
            for (int n = 0; n < delta; n++, tried++) {
                i++;
                if (fits(mover, start.at(i, j), reach, also)) {
                    return start.at(i, j);
                }
            }
            for (int n = 0; n < delta; n++, tried++) {
                j++;
                if (fits(mover, start.at(i, j), reach, also)) {
                    return start.at(i, j);
                }
            }
            delta++;
            for (int n = 0; n < delta; n++, tried++) {
                i--;
                if (fits(mover, start.at(i, j), reach, also)) {
                    return start.at(i, j);
                }
            }
            for (int n = 0; n < delta; n++, tried++) {
                j--;
                if (fits(mover, start.at(i, j), reach, also)) {
                    return start.at(i, j);
                }
            }
            delta++;
        }
        return null;
    }

    private boolean fits(GameObject mover, Block block, Predicate<Block> reach, Predicate<Block> also) {
        return mayHold(mover, block) && reach.test(block) && also.test(block);
    }

    /**
     * The blocks {@code mover} sent to {@code place} may be given, as the grid's zones join them: those it can walk to,
     * and — where the place itself is out of its reach — those the place is joined to, beyond whatever is in the way.
     */
    private Predicate<Block> reachFor(GameObject mover, Coord3D place) {
        var grid = world.gridFor(mover);
        var zones = world.zonesOf(grid);
        int fromX = grid.toCellX(mover.getPosition());
        int fromY = grid.toCellY(mover.getPosition());
        if (zones == null || zones.zoneOf(fromX, fromY) < 0) {
            return any -> true;
        }
        int placeX = grid.toCellX(place);
        int placeY = grid.toCellY(place);
        boolean inReach = zones.connected(fromX, fromY, placeX, placeY);
        return block -> zones.connected(fromX, fromY, block.x(), block.y())
                || !inReach && zones.connected(placeX, placeY, block.x(), block.y());
    }

    /**
     * Whether {@code mover} may have {@code block}: every cell of it on the map, open — no obstacle, stone or cliff — and
     * held as a goal by no ally, nor stood on still by an enemy it cannot run over ({@code Pathfinder::checkDestination}).
     */
    boolean mayHold(GameObject mover, Block block) {
        var grid = world.gridFor(mover); // its classes of ground open to it
        int id = mover.getId().value();
        for (int cy = block.minY(); cy <= block.maxY(); cy++) {
            for (int cx = block.minX(); cx <= block.maxX(); cx++) {
                if (!grid.inBounds(cx, cy) || grid.isBlocked(cx, cy) || grid.isCliff(cx, cy)) {
                    return false;
                }
                int holder = cells().goalAt(cx, cy);
                if (holder == 0 || holder == id) {
                    continue;
                }
                var other = world.findObject(new ObjectId(holder));
                if (other == null || other.isDestroyed()) {
                    continue; // a claim left by something gone
                }
                if (allied(mover, other)) {
                    return false; // an ally's goal is not taken from it
                }
                if (cells().standingAt(cx, cy) == holder && !world.runsOver(mover, other)) {
                    return false; // an enemy still on it, and not one to drive over
                }
            }
        }
        // Scenery and round still things close no cell: a block is kept clear of them by where the mover would stand.
        var standing = block.point(grid.getCellSize(), 0f);
        return grid.clearOfCircles(standing.x(), standing.y(), Solid.of(mover.getTemplate()).footprintRadius());
    }

    /** Whether {@code mover} can walk to the block, as the grid's zones say; a mover standing in stone can go anywhere. */
    private boolean reachable(GameObject mover, Block block) {
        var grid = world.gridFor(mover);
        var zones = world.zonesOf(grid);
        var at = mover.getPosition();
        int fromX = grid.toCellX(at);
        int fromY = grid.toCellY(at);
        if (zones == null || zones.zoneOf(fromX, fromY) < 0) {
            return true;
        }
        return zones.connected(fromX, fromY, block.x(), block.y());
    }

    /** Whether two things are on one side: the same player, or allies. */
    boolean allied(GameObject a, GameObject b) {
        return a.getPlayerIndex() == b.getPlayerIndex()
                || world.getRelationship(a.getPlayerIndex(), b.getPlayerIndex()) == Relationship.ALLIES;
    }

    /**
     * What the movers on the ground add to a step of {@code mover}'s route onto a cell — the reference's {@code
     * checkForMovement} over the block it would cover there: 42 where an ally stands still on it, and 42 more where an
     * ally is passing over it within 10 cells of the start; closed where an enemy it cannot run over stands still; and
     * legs pass legs, still or moving. The movers in {@code closedToo} close their cells outright: those it is stuck
     * behind.
     */
    Pathfinder.Traffic trafficFor(GameObject mover, java.util.Set<Integer> closedToo) {
        var grid = world.getPathGrid();
        var shape = blockAt(mover, mover.getPosition());
        int startX = grid.toCellX(mover.getPosition());
        int startY = grid.toCellY(mover.getPosition());
        int id = mover.getId().value();
        boolean legs = MoveUpdate.walksOnLegs(mover);
        var stuckBehind = new java.util.ArrayList<GameObject>();
        for (int other : closedToo) {
            var thing = world.findObject(new ObjectId(other));
            if (thing != null) {
                stuckBehind.add(thing);
            }
        }
        float reach = Solid.of(mover.getTemplate()).footprintRadius();
        int nearStart = NEAR_START * grid.cellsPerMapCell();
        return new Pathfinder.Traffic() {
            @Override
            public int costOf(int cx, int cy) {
                var there = shape.at(cx, cy);
                if (!stuckBehind.isEmpty() && reachesAny(there)) {
                    return Pathfinder.Traffic.CLOSED;
                }
                boolean allyStill = false;
                boolean allyPassing = false;
                for (int y = there.minY(); y <= there.maxY(); y++) {
                    for (int x = there.minX(); x <= there.maxX(); x++) {
                        int standing = cells().standingAt(x, y);
                        if (standing == 0 || standing == id) {
                            continue;
                        }
                        if (closedToo.contains(standing)) {
                            return Pathfinder.Traffic.CLOSED;
                        }
                        var other = world.findObject(new ObjectId(standing));
                        if (other == null || legs && MoveUpdate.walksOnLegs(other)) {
                            continue;
                        }
                        boolean still = cells().goalAt(x, y) == standing;
                        if (still) {
                            if (allied(mover, other)) {
                                allyStill = true;
                            } else if (!world.runsOver(mover, other)) {
                                return Pathfinder.Traffic.CLOSED;
                            }
                        } else if (allied(mover, other) && Math.abs(cx - startX) < nearStart
                                && Math.abs(cy - startY) < nearStart) {
                            allyPassing = true;
                        }
                    }
                }
                return (allyStill ? ALLY_COST : 0) + (allyPassing ? ALLY_COST : 0);
            }

            @Override
            public boolean allyStill(int cx, int cy) {
                return !stillAlliesAt(mover, shape.at(cx, cy)).isEmpty();
            }

            /**
             * Whether the mover's body, standing at the block's point, would touch one it is stuck behind: their bodies,
             * not only the block they stand on, since one that stopped where it was walking straddles its cells.
             */
            private boolean reachesAny(Block there) {
                var at = pointOf(there);
                for (var other : stuckBehind) {
                    float room = reach + Solid.of(other.getTemplate()).footprintRadius();
                    float dx = other.getPosition().x() - at.x();
                    float dy = other.getPosition().y() - at.y();
                    if (dx * dx + dy * dy < room * room) {
                        return true;
                    }
                }
                return false;
            }
        };
    }

    /**
     * The allies standing still on the block {@code mover} would cover — what the reference's {@code moveAllies} asks
     * off a route; legs leave legs be.
     */
    java.util.List<GameObject> stillAlliesAt(GameObject mover, Block there) {
        int id = mover.getId().value();
        boolean legs = MoveUpdate.walksOnLegs(mover);
        var found = new java.util.ArrayList<GameObject>();
        for (int y = there.minY(); y <= there.maxY(); y++) {
            for (int x = there.minX(); x <= there.maxX(); x++) {
                int standing = cells().standingAt(x, y);
                if (standing == 0 || standing == id || cells().goalAt(x, y) != standing) {
                    continue;
                }
                var other = world.findObject(new ObjectId(standing));
                if (other != null && !found.contains(other) && allied(mover, other)
                        && !(legs && MoveUpdate.walksOnLegs(other))) {
                    found.add(other);
                }
            }
        }
        return found;
    }

    /** The allies standing still on the cells {@code mover} would cover along {@code way}, walked a quarter cell at a time. */
    java.util.List<GameObject> stillAlliesOn(GameObject mover, java.util.List<Coord3D> way) {
        var grid = world.getPathGrid();
        float step = grid.getCellSize() / 4f;
        var found = new java.util.ArrayList<GameObject>();
        for (int i = 1; i < way.size(); i++) {
            var a = way.get(i - 1);
            var b = way.get(i);
            float length = (float) Math.sqrt((b.x() - a.x()) * (b.x() - a.x()) + (b.y() - a.y()) * (b.y() - a.y()));
            int samples = Math.max(1, (int) Math.ceil(length / step));
            for (int s = 1; s <= samples; s++) {
                float t = (float) s / samples;
                var there = blockAt(mover, new Coord3D(a.x() + (b.x() - a.x()) * t, a.y() + (b.y() - a.y()) * t, 0f));
                for (var other : stillAlliesAt(mover, there)) {
                    if (!found.contains(other)) {
                        found.add(other);
                    }
                }
            }
        }
        return found;
    }
}
