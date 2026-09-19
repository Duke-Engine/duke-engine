package uz.duke.dungeon.skill;

import java.util.List;
import uz.duke.core.module.ModuleData;
import uz.duke.core.module.ModuleGroup;
import uz.duke.core.module.ModuleGroups;
import uz.duke.core.module.UpdateModule;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.ObjectId;
import uz.duke.core.thing.World;
import uz.duke.dungeon.combat.DepthBonus;
import uz.duke.dungeon.level.GrowableBody;
import uz.duke.rts.module.ExperienceModule;

/**
 * A rift in the floor that one of the dungeon's own is about to climb out of.
 *
 * <p>The bargain the meteor's mark and the healer's light strike: it lies where it was
 * opened for a moment, a thing in the world, and when it lands the creature rises there
 * -- so the client draws the column going up on the frame the creature appears, and the
 * fog hides both.
 *
 * <p>What climbs out is marked as called up ({@link Summoned}): it falls down again when
 * its time is out, it is worth the share of experience the skill says, it was found as
 * deep as its caller, and it counts against its caller's {@code MaxSummoned} for as long
 * as it stands.
 */
@ModuleGroup({ModuleGroups.COMBAT, ModuleGroups.EFFECT})
public final class SummoningUpdate extends UpdateModule {

    /** It reads no fields; the block only says the unit has one. */
    public record Data() implements ModuleData {
    }

    private ObjectId caller;
    private String creature;
    private int lasts;
    private int experiencePercent;
    private float damageBonus = 1f;
    private float healthBonus = 1f;
    private int opensIn;
    private boolean opened;

    public SummoningUpdate(GameObject owner, ModuleData ignored) {
        super(owner);
    }

    /**
     * Open it: in {@code frames} a {@code creature} climbs out, for {@code lasts} frames,
     * worth {@code experiencePercent} of its own kind.
     *
     * <p>How deep its caller was found is read now rather than when it rises, as every
     * shot here is settled when it is thrown: the caller may be dead by then.
     */
    public void open(GameObject from, String creature, int lasts, int experiencePercent,
            int frames) {
        this.caller = from.getId();
        this.creature = creature;
        this.lasts = lasts;
        this.experiencePercent = experiencePercent;
        var depth = from.findModule(DepthBonus.class);
        if (depth != null) {
            this.damageBonus = depth.damageMultiplier();
            this.healthBonus = depth.healthMultiplier();
        }
        this.opensIn = Math.max(1, frames);
        this.opened = true;
    }

    @Override
    public void update() {
        var owner = getOwner();
        var world = owner.getWorld();
        if (world == null || !opened) {
            owner.markDestroyed(); // never opened: a rift with nothing behind it
            return;
        }
        if (--opensIn > 0) {
            return;
        }
        rise(world, owner);
        owner.markDestroyed();
    }

    private void rise(World world, GameObject rift) {
        var template = world.findTemplate(creature);
        if (template == null) {
            return;
        }
        var risen = world.spawn(template, rift.getPosition(), rift.getPlayerIndex());
        risen.addModule(new Summoned(risen, lasts));
        // Its depth, the way the spawner gives it to anything placed on the floor.
        if (risen.getBody() instanceof GrowableBody body && healthBonus > 1f) {
            body.growMaxHealth(body.getMaxHealth() * (healthBonus - 1f));
        }
        if (damageBonus != 1f || healthBonus != 1f) {
            risen.addModule(new DepthBonus(risen, damageBonus, healthBonus));
        }
        var worth = risen.findModule(ExperienceModule.class);
        if (worth != null && experiencePercent != 100) {
            risen.replaceModule(worth, new ExperienceModule(risen, new ExperienceModule.Data(
                    worth.getExperienceValue() * experiencePercent / 100, List.of(), false)));
        }
        var caster = world.findObject(caller);
        var book = caster == null ? null : caster.findModule(SkillBook.class);
        if (book != null) {
            book.risenInPlaceOf(rift.getId(), risen.getId());
        }
    }
}
