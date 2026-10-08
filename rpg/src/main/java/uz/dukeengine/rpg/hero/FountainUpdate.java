package uz.dukeengine.rpg.hero;

import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.ModuleGroups;
import uz.dukeengine.core.module.UpdateModule;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.rpg.Seal;
import uz.dukeengine.rpg.skill.SkillBook;

/**
 * Water that mends whoever stands near it: every so often, every living thing within its reach is given back a
 * share of its health and of its mana — the heroes, and the dungeon's own as well, so a fight beside the fountain
 * is one the monsters get their breath back in too.
 *
 * <p>A share of each one's own figures rather than a number of points, so it is worth the same on the first floor
 * as on the tenth, to a rogue as to a knight. And a pulse rather than a trickle: the numbers the client pops over a
 * mended head are how the player learns what the fountain does.
 *
 * <p>Like every mending, it does not cross a seal (see {@link uz.dukeengine.rpg.Seal}): it reaches only those on its
 * own side of it — see {@link Seal}. The way-in fountain stands far from any keep, but that is only where a floor puts
 * it today, and the water asks all the same.
 *
 * <p>Deterministic: counted on the world's own frame, and each gift a whole number of points worked out from the
 * thing's own figures, the same on every machine.
 */
@ModuleGroup({ModuleGroups.EFFECT, ModuleGroups.BODY})
public final class FountainUpdate extends UpdateModule {

    /**
     * @param radius        how far from its middle it reaches, in world units
     * @param everyFrames   how often it mends, in logic frames (30 = once a second)
     * @param healthPercent what share of each one's maximum health a pulse gives back
     * @param manaPercent   and of each one's maximum mana
     */
    public record Data(float radius, int everyFrames, int healthPercent, int manaPercent) implements ModuleData {

        /** What a block leaves out. */
        static final Data DEFAULTS = new Data(36f, 30, 5, 5);
    }

    private final Data data;
    private final Seal seal;

    public FountainUpdate(GameObject owner, Data data, Seal seal) {
        super(owner);
        this.data = data;
        this.seal = seal;
    }

    @Override
    public void update() {
        var owner = getOwner();
        var world = owner.getWorld();
        if (world == null || data.everyFrames() <= 0 || world.getFrame() % data.everyFrames() != 0) {
            return;
        }
        for (var near : world.objectsInRange(owner.getPosition(), data.radius(),
                thing -> thing != owner && thing.getBody() != null && !thing.isEffectivelyDead()
                        && !seal.parts(world, owner.getPosition(), thing.getPosition()))) {
            var body = near.getBody();
            if (data.healthPercent() > 0 && body.getHealth() < body.getMaxHealth()) {
                body.heal(Math.max(1, Math.round(body.getMaxHealth() * data.healthPercent() / 100f)));
            }
            var book = near.findModule(SkillBook.class);
            if (data.manaPercent() > 0 && book != null && book.getMana() < book.getMaxMana()) {
                book.restoreMana(Math.max(1, book.getMaxMana() * data.manaPercent() / 100));
            }
        }
    }
}
