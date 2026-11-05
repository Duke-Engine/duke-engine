package uz.dukeengine.rpg.skill;

import java.util.List;
import java.util.function.ObjIntConsumer;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.ModuleGroups;
import uz.dukeengine.core.module.UpdateModule;
import uz.dukeengine.core.save.SaveIn;
import uz.dukeengine.core.save.SaveOut;
import uz.dukeengine.core.save.Saveable;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.World;
import uz.dukeengine.rpg.hero.LevelBonus;
import uz.dukeengine.combat.module.ExperienceModule;

/**
 * A rift in the floor that one of the dungeon's own is about to climb out of.
 *
 * <p>The bargain the meteor's mark and the healer's light strike: it lies where it was
 * opened for a moment, a thing in the world, and when it lands the creature rises there
 * -- so the client draws the column going up on the frame the creature appears, and the
 * fog hides both.
 *
 * <p>What climbs out is marked as called up ({@link Summoned}): it falls down again when
 * its time is out, it stands at its caller's level, it is worth the share of experience
 * the skill says of what its kind is worth there, and it counts against its caller's
 * {@code MaxSummoned} for as long as it stands.
 */
@ModuleGroup({ModuleGroups.COMBAT, ModuleGroups.EFFECT})
public final class SummoningUpdate extends UpdateModule implements Saveable {

    /** It reads no fields; the block only says the unit has one. */
    public record Data() implements ModuleData {
    }

    /** The game's one step that makes a creature placed in the world the level it is given. */
    private final ObjIntConsumer<GameObject> atLevel;
    private ObjectId caller;
    private String creature;
    private int lasts;
    private int experiencePercent;
    private int level = 1;
    private int opensIn;
    private boolean opened;

    public SummoningUpdate(GameObject owner, ObjIntConsumer<GameObject> atLevel) {
        super(owner);
        this.atLevel = atLevel;
    }

    /**
     * Open it: in {@code frames} a {@code creature} climbs out, for {@code lasts} frames,
     * worth {@code experiencePercent} of its own kind.
     *
     * <p>Its caller's level is read now rather than when it rises, as every shot here is
     * settled when it is thrown: the caller may be dead by then.
     */
    public void open(GameObject from, String creature, int lasts, int experiencePercent,
            int frames) {
        this.caller = from.getId();
        this.creature = creature;
        this.lasts = lasts;
        this.experiencePercent = experiencePercent;
        this.level = LevelBonus.levelOf(from);
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
        // Its caller's level, by the one step that makes anything placed on the floor its
        // level -- and then its share of what that level makes it worth.
        atLevel.accept(risen, level);
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

    @Override
    public void save(SaveOut out) {
        out.writeId(caller).writeString(creature).writeInt(lasts).writeInt(experiencePercent).writeInt(level)
                .writeInt(opensIn).writeBoolean(opened);
    }

    @Override
    public void load(SaveIn in) {
        caller = in.readId();
        creature = in.readString();
        lasts = in.readInt();
        experiencePercent = in.readInt();
        level = in.readInt();
        opensIn = in.readInt();
        opened = in.readBoolean();
    }
}
