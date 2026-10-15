package uz.dukeengine.core.pathfind;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;
import uz.dukeengine.core.math.Coord3D;

/**
 * The ways across a walker's route may take — see {@link TraversalLink}: a graph of where it stands, its goal and each
 * link's two ends, walked between ends the ground joins (as far as the crow flies) and crossed along each link (as far
 * as the walker goes in the link's frames), searched cheapest first. A route of several links, and a link taken where it
 * is the shorter way and not only the one way, are both its answer.
 */
public final class LinkPlanner {

    private LinkPlanner() {
    }

    /** The first link of the cheapest way: crossed from {@code entry} to {@code exit}. */
    public record Leg(TraversalLink link, Coord3D entry, Coord3D exit) {
    }

    /**
     * The first link the cheapest way from {@code from} to {@code goal} crosses, or null where walking there — at
     * {@code walk}, the length of the route the ground gives, or none for no route — is no dearer.
     *
     * @param joined whether the ground joins two points — the grid's zones
     * @param step   how far the walker goes in a frame, which prices a link's frames
     */
    public static Leg first(List<TraversalLink> links, Coord3D from, Coord3D goal, BiPredicate<Coord3D, Coord3D> joined,
            float step, float walk) {
        if (links.isEmpty()) {
            return null;
        }
        // Points: 0 where it stands, 1 its goal, then for each way a link is crossed its entry and its exit.
        var points = new ArrayList<Coord3D>(List.of(from, goal));
        var legs = new ArrayList<Leg>();
        for (var link : links) {
            for (int way = 0; way < (link.bothWays() ? 2 : 1); way++) {
                var entry = way == 0 ? link.a() : link.b();
                var exit = way == 0 ? link.b() : link.a();
                legs.add(new Leg(link, entry, exit));
                points.add(entry);
                points.add(exit);
            }
        }
        int n = points.size();
        var cost = new float[n];
        var came = new int[n];
        var done = new boolean[n];
        Arrays.fill(cost, Float.MAX_VALUE);
        Arrays.fill(came, -1);
        cost[0] = 0f;
        while (true) {
            int at = -1;
            for (int i = 0; i < n; i++) {
                if (!done[i] && cost[i] < Float.MAX_VALUE && (at < 0 || cost[i] < cost[at])) {
                    at = i; // the cheapest open point, the lowest index on a tie: the same on every machine
                }
            }
            if (at < 0 || at == 1) {
                break;
            }
            done[at] = true;
            for (int to = 0; to < n; to++) {
                if (done[to] || to == at) {
                    continue;
                }
                float through = Float.MAX_VALUE;
                boolean isLeg = at >= 2 && at % 2 == 0 && to == at + 1;
                if (isLeg) {
                    var leg = legs.get((at - 2) / 2);
                    through = cost[at] + Math.max(across(leg.entry(), leg.exit()), leg.link().frames() * step);
                } else if (!(at >= 2 && at % 2 == 0) && !(at == 0 && to == 1)
                        && joined.test(points.get(at), points.get(to))) {
                    // Walked: from where it stands or a link's far end, to the goal or a link's near end — the walk
                    // straight to the goal priced by its route, below.
                    if (to == 1 || to >= 2 && to % 2 == 0) {
                        through = cost[at] + across(points.get(at), points.get(to));
                    }
                }
                if (through < cost[to]) {
                    cost[to] = through;
                    came[to] = at;
                }
            }
        }
        if (cost[1] == Float.MAX_VALUE || cost[1] >= walk) {
            return null; // no link reaches it, or walking there is no dearer
        }
        int at = 1;
        int firstEntry = -1;
        while (at > 0) {
            if (at >= 2 && at % 2 == 0) {
                firstEntry = at;
            }
            at = came[at];
        }
        return firstEntry < 0 ? null : legs.get((firstEntry - 2) / 2);
    }

    private static float across(Coord3D a, Coord3D b) {
        float dx = a.x() - b.x();
        float dy = a.y() - b.y();
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
