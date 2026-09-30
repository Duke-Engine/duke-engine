package uz.dukeengine.rts.construction;

import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.module.UpdateModule;
import uz.dukeengine.core.thing.Footprint;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.core.thing.Solid;
import uz.dukeengine.core.thing.ThingTemplate;
import uz.dukeengine.rts.player.RtsPlayer;

/**
 * A builder on its way to put something down: what, where, and what it has already cost.
 *
 * <p>Carried by the builder rather than kept in a list beside the world, so it lives and dies with the unit
 * carrying it and is ticked in the same place and order as everything else the unit does. It is added when
 * a {@code Construct} order is accepted and does nothing once its errand is over, one way or the other.
 *
 * <p><b>Arriving</b> is being within a cell of the footprint's outline, from outside — the band. It is judged
 * the moment the order is accepted, before a step is taken, because a player very often puts a building
 * down right beside the builder, and a builder already in the band must not walk anywhere: it used to take
 * one step first, and that step carried it into the footprint. It is judged on every step after, and a step
 * that carries the builder across the band into the footprint counts as arriving — it is set back on its
 * own step to where it crossed the outline — rather than as having started inside.
 *
 * <p><b>Walking in</b>, the builder is sent at the middle of the footprint and stops the moment it reaches
 * the band: walked at the middle it crosses the band whatever the shape, where a spot worked out in
 * advance can be a cell too far along one axis. <b>Stepping out</b>, a builder that starts inside the
 * footprint is sent to a spot beyond it on its own side; a step that ends early — the mover gave up on it —
 * is judged again rather than taken as the player's change of mind, and tried again, three times at most.
 *
 * <p><b>Giving up</b> — sent somewhere else, told to stop on the way in, stopped short and unable to get
 * closer, or something that does not move put there meanwhile — gives the money back in full, because
 * nothing was built.
 *
 * <p><b>A site put down at the order</b> ({@link PlacementRules#siteAtOrder}) already stands: the builder only goes to
 * it. Giving up then leaves the site standing, with the money in it; calling the site off gives back what calling a
 * site off gives.
 *
 * <p><b>At work</b> — beside its site, put down when it arrived or standing already — the builder keeps to it until the
 * site is whole, as the reference's dozer keeps its build task ({@code DOZER_TASK_BUILD}): busy, so it keeps the place
 * it stands on and an ally's route does not ask it aside; moved off by anything but an order — pushed, stepped aside —
 * it walks back beside the site and goes on building. Another order for it, the site gone, whole, or taken up by
 * another builder, and its work is over.
 */
public final class BuildOrder extends UpdateModule implements uz.dukeengine.rts.module.OrderListener {

    /** How many times a builder standing on its own site is sent out of it before the order is given up. */
    private static final int STEPS_OUT = 3;

    private final ThingTemplate template;
    private final Coord3D place;
    private final float facing;
    private final int cost;
    private final PlacementRules rules;
    /** The side the builder was given the order for: handed to another, it gives the order up. */
    private final int side;
    /** Where this order last sent the builder, to tell its own errand from an order someone else gave. */
    private Coord3D goal;
    /** Where the builder stood, and how far from the outline, when this order last looked. */
    private Coord3D lastPosition;
    private float lastGap = -1f;
    private int stepsOut;
    private boolean over;
    /** The site it is building: put down at its order, or when it arrived; null until there is one. */
    private GameObject site;
    /** Whether it is at work on its site, which it keeps to until the site is whole. */
    private boolean atWork;

    BuildOrder(GameObject builder, ThingTemplate template, Coord3D place, float facing, int cost,
            PlacementRules rules, GameObject site) {
        super(builder);
        this.template = template;
        this.place = place;
        this.facing = facing;
        this.cost = cost;
        this.rules = rules;
        this.goal = place;
        this.side = builder.getPlayerIndex();
        this.site = site;
    }

    /**
     * Put a site down: the real template at {@code place}, under construction and at the rules' start share of its
     * health, made that way so the side's upgrades and its own modules first hear of it as a site, with its builder's
     * work to be done on it.
     */
    static GameObject putDown(GameObject builder, ThingTemplate template, Coord3D place, float facing, int cost,
            PlacementRules rules) {
        var site = builder.getWorld().spawn(template, place, builder.getPlayerIndex(), made -> {
            made.setOrientation((float) StrictMath.toRadians(facing));
            made.setStatus(ObjectStatus.UNDER_CONSTRUCTION);
        });
        var body = site.getBody();
        if (body != null) {
            body.setHealth(body.getMaxHealth() * rules.startShare());
        }
        site.addModule(new ConstructionSite(site, builder.getId(), cost, rules));
        if (builder.getWorld() instanceof uz.dukeengine.rts.RtsSimulation rts) {
            rts.placed(site); // for the game to clear what it stands over, at once
        }
        return site;
    }

    /**
     * The order accepted: judge where the builder already stands, and set it off only if it must move.
     *
     * <p>Called as the order is applied, before the frame's modules run — so before the builder's own mover
     * takes the step that would otherwise come first.
     */
    void begin() {
        var builder = getOwner();
        var walking = builder.findModule(MoveUpdate.class);
        if (site != null) {
            goToTheSite(builder, walking);
            return;
        }
        float gap = gapAt(builder.getPosition());
        remember(builder.getPosition(), gap);
        if (inTheBand(gap)) {
            if (walking != null) {
                walking.stop(); // here already: whatever it was doing, it is building now
            }
            goal = null;
            return;
        }
        if (gap < 0f) {
            goal = stepOut(builder);
            stepsOut++;
        }
        if (walking != null) {
            walking.moveExactlyTo(goal);
        }
    }

    /** What it is on its way to raise. */
    public ThingTemplate template() {
        return template;
    }

    /** Whether it is still on its errand: on its way, or at work on its site until it is whole; not given up. */
    public boolean isUnderWay() {
        return !over;
    }

    /** Whether it is on its way to put down a site that does not stand yet — what a side's caps count as a building. */
    public boolean awaitsItsSite() {
        return !over && site == null;
    }

    /** Whether it is on its way to, or at work on, {@code building}. */
    public boolean isBuilding(GameObject building) {
        return !over && site == building;
    }

    boolean isOver() {
        return over;
    }

    /** On its way to build, or building, it is at work: not asked to step aside. */
    @Override
    public boolean keepsBusy() {
        return !over;
    }

    /** At work, any order but a build order ends its work — a stop as well as a move: the player's last word goes. */
    @Override
    public void onOrder(uz.dukeengine.core.message.Command order) {
        if (atWork && !(order instanceof uz.dukeengine.rts.message.GameMessage.Construct)) {
            over = true;
        }
    }

    /** Give it up, the money back in full to the side that paid: nothing has been built — unless a site already stands. */
    void giveUp() {
        if (over) {
            return;
        }
        over = true;
        if (site != null) {
            return; // the site stands, the money in it: calling the site off is what gives some back
        }
        var player = RtsPlayer.of(getOwner().getWorld(), side);
        if (player != null) {
            player.refund(cost);
        }
    }

    @Override
    public void update() {
        if (over) {
            return;
        }
        var builder = getOwner();
        if (builder.getPlayerIndex() != side) {
            giveUp(); // handed to another side: it builds nothing more for the old one, and nothing for the new
            return;
        }
        if (atWork) {
            work(builder);
            return;
        }
        if (site != null) {
            walkToTheSite(builder);
            return;
        }
        var here = builder.getPosition();
        float gap = gapAt(here);
        if (lastGap >= 0f && gap < 0f && lastPosition != null) {
            builder.setPosition(whereItCrossed(lastPosition, here));
            arrive(builder); // across the band inside one step: that is arriving, not starting inside
            return;
        }
        if (inTheBand(gap)) {
            arrive(builder);
            return;
        }
        remember(here, gap);
        var walking = builder.findModule(MoveUpdate.class);
        if (walking == null) {
            giveUp();
            return;
        }
        var heading = walking.getGoal();
        if (heading != null && !heading.equals(goal)) {
            giveUp(); // sent somewhere else: the player changed his mind
            return;
        }
        boolean walkingIn = place.equals(goal);
        if (gap < 0f) {
            // Standing on the spot. Sent out of it — or, a step out having ended early, sent again.
            if (walkingIn || heading == null || !walking.isMoving()) {
                if (stepsOut >= STEPS_OUT) {
                    giveUp();
                    return;
                }
                stepsOut++;
                goal = stepOut(builder);
                walking.moveExactlyTo(goal);
            }
            return;
        }
        if (heading == null || !walking.isMoving()) {
            if (walkingIn) {
                giveUp(); // stopped on the way in — told to, or it can get no closer
                return;
            }
            goal = place; // stepped out past the band: back in toward the middle
            walking.moveExactlyTo(goal);
        }
    }

    /** Off to the site its order put down: out of its footprint first where it stands in it. */
    private void goToTheSite(GameObject builder, MoveUpdate walking) {
        var world = builder.getWorld();
        if (world.isBeside(builder, site)) {
            if (walking != null) {
                walking.stop(); // here already: the site's own module does the work
            }
            atWork = true;
            goal = null;
            return;
        }
        goal = gapAt(builder.getPosition()) < 0f ? stepOut(builder) : world.standingNextTo(builder, site);
        if (walking != null) {
            walking.moveExactlyTo(goal);
        }
    }

    /**
     * On its way to the site: done once beside it; given up — the site left standing — if it is sent elsewhere, the
     * site is gone, or it stops short three times over.
     */
    private void walkToTheSite(GameObject builder) {
        var world = builder.getWorld();
        var walking = builder.findModule(MoveUpdate.class);
        if (site.isDestroyed() || site.isEffectivelyDead() || walking == null) {
            giveUp();
            return;
        }
        if (world.isBeside(builder, site)) {
            walking.stop();
            atWork = true;
            goal = null;
            return;
        }
        var heading = walking.getGoal();
        if (heading != null && !heading.equals(goal)) {
            giveUp(); // sent somewhere else: the site waits for another builder
            return;
        }
        if (heading == null || !walking.isMoving()) {
            if (stepsOut >= STEPS_OUT) {
                giveUp(); // it can get no nearer
                return;
            }
            stepsOut++;
            goToTheSite(builder, walking);
        }
    }

    /**
     * At work: beside its site, standing; moved off it by anything but an order, back beside it once it stands; over
     * once the site is whole, gone or another builder's, or the builder is sent elsewhere.
     */
    private void work(GameObject builder) {
        var building = site.findModule(ConstructionSite.class);
        var walking = builder.findModule(MoveUpdate.class);
        if (site.isDestroyed() || site.isEffectivelyDead() || building == null || building.isFinished()
                || !builder.getId().equals(building.builder()) || walking == null) {
            over = true; // whole, gone, or taken up by another: its work here is done
            return;
        }
        var heading = walking.getGoal();
        if (heading != null && !heading.equals(goal)) {
            over = true; // sent somewhere else: the site waits for a builder
            return;
        }
        var world = builder.getWorld();
        if (world.isBeside(builder, site)) {
            if (walking.isMoving()) {
                walking.stop(); // back beside it
            }
            goal = null;
            return;
        }
        if (!walking.isMoving()) {
            goal = world.standingNextTo(builder, site); // moved off it, and not by an order: back to work
            walking.moveExactlyTo(goal);
        }
    }

    private boolean inTheBand(float gap) {
        return gap >= 0f && gap <= getOwner().getWorld().cellSize();
    }

    private void remember(Coord3D position, float gap) {
        lastPosition = position;
        lastGap = gap;
    }

    private Footprint footprint() {
        return new Footprint(Solid.of(template), place, (float) StrictMath.toRadians(facing));
    }

    /** How far the builder would be from the footprint's outline, standing at {@code position}. */
    private float gapAt(Coord3D position) {
        return Footprint.of(getOwner(), position).separation(footprint());
    }

    /**
     * The point of a step at which the builder crossed onto the footprint: the last point of it still
     * outside, found by halving. A straight step and a convex outline cross once, so twelve halvings put it
     * within a four-thousandth of the step of the outline.
     */
    private Coord3D whereItCrossed(Coord3D from, Coord3D to) {
        float outside = 0f;
        float inside = 1f;
        for (int halving = 0; halving < 12; halving++) {
            float middle = (outside + inside) / 2f;
            if (gapAt(along(from, to, middle)) >= 0f) {
                outside = middle;
            } else {
                inside = middle;
            }
        }
        return along(from, to, outside);
    }

    private static Coord3D along(Coord3D from, Coord3D to, float share) {
        return new Coord3D(from.x() + (to.x() - from.x()) * share, from.y() + (to.y() - from.y()) * share,
                from.z() + (to.z() - from.z()) * share);
    }

    /** A spot beyond the footprint on the builder's own side of it — or its east side, from the very middle. */
    private Coord3D stepOut(GameObject builder) {
        var at = builder.getPosition();
        float across = at.x() - place.x();
        float along = at.y() - place.y();
        float span = (float) Math.sqrt(across * across + along * along);
        if (span < 0.0001f) {
            across = 1f;
            along = 0f;
            span = 1f;
        }
        float out = Solid.of(template).footprintRadius() + Solid.of(builder.getTemplate()).footprintRadius()
                + builder.getWorld().cellSize() / 2f;
        return new Coord3D(place.x() + across / span * out, place.y() + along / span * out, place.z());
    }

    private void arrive(GameObject builder) {
        var walking = builder.findModule(MoveUpdate.class);
        if (walking != null) {
            walking.stop();
        }
        rise(builder);
    }

    /** The builder is here: put the site down, if the place is still clear. */
    private void rise(GameObject builder) {
        var world = builder.getWorld();
        var print = footprint();
        for (var other : world.getObjects()) {
            if (Placement.refuses(other, rules) && Footprint.of(other).overlaps(print)) {
                giveUp(); // something was put here while he walked
                return;
            }
        }
        site = putDown(builder, template, place, facing, cost, rules);
        atWork = true;
        goal = null;
    }
}
