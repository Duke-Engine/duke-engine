package uz.dukeengine.rpg.skill;

import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.World;

/** A {@code MOUNT}: the one he rides, or is mounting, and the frame his mounting ends on. */
final class Mount {

    private final GameObject owner;
    private Skill riding;
    private Skill mounting;
    private int mountedAt;
    private Coord3D mountedFrom;

    Mount(GameObject owner) {
        this.owner = owner;
    }

    /** The mount he rides, or null. */
    Skill riding() {
        return riding;
    }

    /** Whether he rides, or is mounting. */
    boolean isUp() {
        return riding != null || mounting != null;
    }

    /** See {@link SkillBook#dismount}. */
    void dismount() {
        if (riding == null && mounting == null) {
            return;
        }
        riding = null;
        mounting = null;
        owner.drawAs(null, -1);
    }

    /** A blow takes him off, or calls his mounting off. */
    void onHurt() {
        if (riding != null && riding.endsOnHurt() || mounting != null) {
            dismount();
        }
    }

    /** Mounting begins: on at once where it takes no time. */
    boolean begin(Skill skill, World world) {
        mounting = skill;
        mountedFrom = owner.getPosition();
        mountedAt = world.getFrame() + Math.max(0, skill.windUpFrames());
        if (!skill.castAnim().isBlank()) {
            owner.busy(skill.castAnim(), Math.max(1, skill.windUpFrames()));
        }
        mountOn();
        return true;
    }

    /** The mounting under way, finished or called off. */
    void mountOn() {
        var world = owner.getWorld();
        if (mounting == null || world == null) {
            return;
        }
        if (owner.getPosition().distance(mountedFrom) > 0.5f) {
            mounting = null; // a step calls it off
            return;
        }
        if (world.getFrame() >= mountedAt) {
            riding = mounting;
            mounting = null;
            if (riding.look() != null && !riding.look().isBlank()) {
                owner.drawAs(riding.look(), -1);
            }
        }
    }
}
