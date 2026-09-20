package uz.dukeengine.dungeon.level;

import uz.dukeengine.core.GameConstants;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.ModuleGroups;
import uz.dukeengine.core.module.UpdateModule;
import uz.dukeengine.core.thing.GameObject;

/**
 * Health coming back on its own, counted exactly the way mana is.
 *
 * <p>The rate is <b>tenths of a point a second</b> and {@link #carry} holds what has been
 * earned towards the next whole point: each frame adds the rate, and every time the carry
 * reaches a second's worth one point is healed. 15 tenths is exactly 1.5 a second, on
 * every machine, for ever — where {@code heal(1.5f / 30f)} thirty times a second is a
 * float sum two peers drift apart on.
 *
 * <p>Nothing until {@code HeroProgress} names a rate: the figure is his block's, not his
 * creature's, and a creature spawned without a hero's progress behind it heals nothing.
 */
@ModuleGroup(ModuleGroups.BODY)
public final class Recovery extends UpdateModule {

    /** It reads no fields; the block only says the unit has one. */
    public record Data() implements ModuleData {
    }

    private static final int A_SECOND = 10 * GameConstants.LOGICFRAMES_PER_SECOND;

    private int tenthsPerSecond;
    private int carry;

    public Recovery(GameObject owner) {
        super(owner);
    }

    /** How fast, in tenths of a point a second. */
    public void rate(int tenthsPerSecond) {
        this.tenthsPerSecond = Math.max(0, tenthsPerSecond);
    }

    public int getRate() {
        return tenthsPerSecond;
    }

    @Override
    public void update() {
        var owner = getOwner();
        var body = owner.getBody();
        if (tenthsPerSecond <= 0 || body == null || owner.isEffectivelyDead()
                || body.getHealth() >= body.getMaxHealth()) {
            // Nothing is banked while he is full, so a wound taken later does not
            // start half healed.
            carry = 0;
            return;
        }
        carry += tenthsPerSecond;
        while (carry >= A_SECOND && body.getHealth() < body.getMaxHealth()) {
            carry -= A_SECOND;
            body.heal(1f);
        }
    }
}
