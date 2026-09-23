package uz.dukeengine.rts.construction;

import java.util.Objects;
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
 * <p><b>Arriving.</b> The builder is sent toward the middle of the footprint and stops the moment it comes
 * within a cell of its outline from outside. Walked at the middle, it crosses that band whatever the shape —
 * a long thin building is reached from its side as surely as from its end, where a spot worked out in
 * advance would have been a cell too far along one axis and never close enough. One that starts on the
 * spot steps out first. Either way the site rises beside it, never on top of it.
 *
 * <p><b>Giving up.</b> Sent somewhere else or told to stop, the order is given up and the money comes back
 * in full, because nothing was built; so too if it stopped short and can get no closer, or something that
 * does not move was put there while it walked.
 */
public final class BuildOrder extends UpdateModule {

    private final ThingTemplate template;
    private final Coord3D place;
    private final float facing;
    private final int cost;
    private final PlacementRules rules;
    /** Where this order last sent the builder, to tell its own errand from an order someone else gave. */
    private Coord3D goal;
    private boolean over;

    BuildOrder(GameObject builder, ThingTemplate template, Coord3D place, float facing, int cost,
            PlacementRules rules) {
        super(builder);
        this.template = template;
        this.place = place;
        this.facing = facing;
        this.cost = cost;
        this.rules = rules;
        this.goal = place;
    }

    /** Send the builder on its way; called once, as the order is accepted. */
    void begin() {
        var walking = getOwner().findModule(MoveUpdate.class);
        if (walking != null) {
            walking.moveTo(goal);
        }
    }

    boolean isOver() {
        return over;
    }

    /** Give it up, the money back in full: nothing has been built. */
    void giveUp() {
        if (over) {
            return;
        }
        over = true;
        var player = RtsPlayer.of(getOwner().getWorld(), getOwner().getPlayerIndex());
        if (player != null) {
            player.deposit(cost);
        }
    }

    @Override
    public void update() {
        if (over) {
            return;
        }
        var builder = getOwner();
        var walking = builder.findModule(MoveUpdate.class);
        if (walking == null || !Objects.equals(walking.getGoal(), goal)) {
            giveUp(); // stopped, or sent somewhere else: the player changed his mind
            return;
        }
        var print = footprint();
        float gap = Footprint.of(builder).separation(print);
        float cell = builder.getWorld().cellSize();
        if (gap >= 0f && gap <= cell) {
            walking.stop();
            rise(builder, print);
            return;
        }
        if (gap < 0f && goal.equals(place)) {
            goal = stepOut(builder, print, cell); // standing on the spot: out of it first
            walking.moveTo(goal);
            return;
        }
        if (!walking.isMoving()) {
            giveUp(); // stopped short, and will get no closer
        }
    }

    private Footprint footprint() {
        return new Footprint(Solid.of(template), place, (float) StrictMath.toRadians(facing));
    }

    /** A spot beyond the footprint on the builder's own side of it — or its east side, from the very middle. */
    private Coord3D stepOut(GameObject builder, Footprint print, float cell) {
        var at = builder.getPosition();
        float across = at.x() - place.x();
        float along = at.y() - place.y();
        float span = (float) Math.sqrt(across * across + along * along);
        if (span < 0.0001f) {
            across = 1f;
            along = 0f;
            span = 1f;
        }
        float out = print.shape().footprintRadius() + Solid.of(builder.getTemplate()).footprintRadius() + cell;
        return new Coord3D(place.x() + across / span * out, place.y() + along / span * out, place.z());
    }

    /** The builder is here: put the site down, if the place is still clear. */
    private void rise(GameObject builder, Footprint print) {
        var world = builder.getWorld();
        for (var other : world.getObjects()) {
            if (!other.isMobile() && !other.isEffectivelyDead() && other.getGeometry() != null
                    && other.getGeometry().footprintRadius() > 0f && Footprint.of(other).overlaps(print)) {
                giveUp(); // something was put here while he walked
                return;
            }
        }
        over = true;
        var site = world.spawn(template, place, builder.getPlayerIndex());
        site.setOrientation((float) StrictMath.toRadians(facing));
        site.setStatus(ObjectStatus.UNDER_CONSTRUCTION);
        var body = site.getBody();
        if (body != null) {
            body.setHealth(body.getMaxHealth() * rules.startShare());
        }
        site.addModule(new ConstructionSite(site, builder.getId(), cost, rules));
    }
}
