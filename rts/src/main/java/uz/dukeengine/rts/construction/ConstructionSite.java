package uz.dukeengine.rts.construction;

import uz.dukeengine.core.GameConstants;
import uz.dukeengine.core.module.UpdateModule;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.rts.Buildable;
import uz.dukeengine.rts.player.RtsPlayer;

/**
 * A building still going up: the one part of it that runs while it is {@code UNDER_CONSTRUCTION}, and what
 * makes it whole.
 *
 * <p>The site <em>is</em> the building — the real template, standing, owned, seen and able to be shot — only
 * inert until it is finished, as SAGE's is. Each frame its builder stands beside it, it gains its share of
 * health and a frame of work; after the template's build time of such frames it is whole, the status comes
 * off, and every other module it has starts doing what it is for. A builder called away, or killed, leaves
 * it standing half-built until one comes back.
 *
 * <p>Health grows by adding, not by being set: a site shot while it goes up stays that much behind, and is
 * finished hurt rather than finished whole. In whole frames and the same additions on every machine, so two
 * peers reach the same health on the same frame.
 *
 * <p>Destroyed half-built, it leaves nothing: it is taken away like anything that dies, and there is no
 * refund for what the enemy knocked down. Cancelled by its owner, it gives back the share the game says.
 */
public final class ConstructionSite extends UpdateModule {

    private final ObjectId builder;
    private final int cost;
    private final PlacementRules rules;
    /** Whole frames of a builder's work it takes, from the template's build time. */
    private final int frames;
    private int worked;

    ConstructionSite(GameObject site, ObjectId builder, int cost, PlacementRules rules) {
        super(site);
        this.builder = builder;
        this.cost = cost;
        this.rules = rules;
        float seconds = site.getTemplate() instanceof Buildable buildable ? buildable.buildTime() : 0f;
        this.frames = Math.max(1, Math.round(seconds * GameConstants.LOGICFRAMES_PER_SECOND));
    }

    @Override
    public boolean runsWhileUnderConstruction() {
        return true; // the one thing that does: it is what is building it
    }

    /** How far along, from 0 to 1. */
    public float progress() {
        return worked / (float) frames;
    }

    public boolean isFinished() {
        return !getOwner().hasStatus(ObjectStatus.UNDER_CONSTRUCTION);
    }

    @Override
    public void update() {
        var site = getOwner();
        // Dead is dead. A site knocked down this frame is still in the world until the frame ends, and a
        // frame's work added to it then would stand it back up — a building that cannot be destroyed while a
        // builder is next to it.
        if (isFinished() || site.isEffectivelyDead()) {
            return;
        }
        var world = site.getWorld();
        var who = world.findObject(builder);
        if (who == null || who.isEffectivelyDead() || !world.isBeside(who, site)) {
            return; // nobody at work on it: it waits
        }
        worked++;
        var body = site.getBody();
        if (body != null) {
            body.heal(body.getMaxHealth() * (1f - rules.startShare()) / frames);
        }
        if (worked >= frames) {
            site.clearStatus(ObjectStatus.UNDER_CONSTRUCTION);
            if (world instanceof uz.dukeengine.rts.RtsSimulation rts) {
                rts.constructed(who, site);
            }
        }
    }

    /**
     * Its owner calls it off: the share the game says comes back, and the site is gone.
     *
     * @return whether there was anything to call off — a finished building is not a site
     */
    boolean cancel() {
        var site = getOwner();
        if (isFinished() || site.isEffectivelyDead()) {
            return false;
        }
        var player = RtsPlayer.of(site.getWorld(), site.getPlayerIndex());
        if (player != null) {
            player.refund(Math.round(cost * rules.refundShare()));
        }
        site.markDestroyed();
        return true;
    }
}
