package uz.dukeengine.rpg.skill;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.core.save.SaveIn;
import uz.dukeengine.core.save.SaveOut;
import uz.dukeengine.core.thing.GameObject;

/**
 * What holds on a creature for a while: what others have lent it, and the {@code EMPOWER} and {@code GUARD} it cast on
 * itself -- each counted in frames, never seconds.
 */
final class Buffs {

    /**
     * What others have lent it and not yet let go of: one buff for each kind and lender, in the order they were first
     * given -- an aura, given again on each of its bearer's pulses (see {@link Auras#lend}), or a haste, given when
     * it is cast (see {@link Allies#haste}). Plain state, the same on every machine: {@link #of} reads it on the frame a
     * figure is wanted, and {@link #countDown} lets go of what has passed.
     */
    private final List<Buff> lent = new ArrayList<>();

    /** Frames of {@code EMPOWER} left, and what it is worth while it lasts. */
    private int boostFrames;
    private int boostPercent;

    /** Frames of {@code GUARD} left, and how much of a blow it turns aside. */
    private int guardFrames;
    private int guardPercent;

    /** Health a {@code HEAL} buff has paid toward its next whole point, counted as a mana pool's carry is. */
    private int healCarry;

    /** See {@link SkillBook#give}. */
    void give(SkillEffect kind, int source, int value, int until) {
        var buff = new Buff(kind, source, value, until);
        for (int at = 0; at < lent.size(); at++) {
            var held = lent.get(at);
            if (held.kind() == kind && held.source() == source) {
                lent.set(at, buff);
                return;
            }
        }
        lent.add(buff);
    }

    /** See {@link SkillBook#buffOf}. */
    int of(SkillEffect kind, int frame) {
        int largest = 0;
        for (var buff : lent) {
            if (buff.kind() == kind && buff.until() >= frame) {
                largest = Math.max(largest, buff.value());
            }
        }
        return largest;
    }

    List<Buff> held() {
        return List.copyOf(lent);
    }

    /**
     * Re-casting refreshes rather than stacking: two overlapping copies of the same buff is a question with no obvious
     * answer, and the cooldown already decides how often it can be had.
     */
    void empower(Skill skill, int level) {
        boostFrames = skill.durationFrames();
        boostPercent = skill.boostAt(level);
    }

    /**
     * The same bargain as {@link #empower}, and the same refresh. What it is worth reaches his body through
     * HeroProgress, which is the one place that computes armour out of everything that goes into it — his level, what
     * he has found, what he was born in, and this.
     */
    void guard(Skill skill, int level) {
        guardFrames = skill.durationFrames();
        guardPercent = Math.clamp(skill.boostAt(level), 0, 100);
    }

    float empowered() {
        return boostFrames > 0 ? 1f + boostPercent / 100f : 1f;
    }

    int boostFrames() {
        return boostFrames;
    }

    int guardPercent() {
        return guardFrames > 0 ? guardPercent : 0;
    }

    int guardFrames() {
        return guardFrames;
    }

    /**
     * A meal's health: the strongest {@code HEAL} buff on its list — tenths of a point a second, as a {@code MANA_AURA}
     * is for mana — paid in whole points against a frame carry while it holds.
     */
    void digest(GameObject owner) {
        var body = owner.getBody();
        var world = owner.getWorld();
        int tenths = body == null || world == null || body.isDead() ? 0 : of(SkillEffect.HEAL, world.getFrame());
        if (tenths <= 0) {
            healCarry = 0;
            return;
        }
        healCarry += tenths;
        int whole = healCarry / ManaPool.A_SECOND;
        if (whole > 0) {
            healCarry -= whole * ManaPool.A_SECOND;
            body.heal(whole);
        }
    }

    /**
     * A frame gone by: the {@code EMPOWER} and {@code GUARD} counted down, and every buff whose last frame has gone by
     * let go of, the rest keeping their places. Nothing hangs on the last -- {@link #of} counts a buff by the frame it
     * is asked on -- but it keeps the list to what is still held, which is what whatever draws it has to show.
     */
    void countDown(GameObject owner) {
        if (boostFrames > 0) {
            boostFrames--;
        }
        if (guardFrames > 0) {
            guardFrames--;
        }
        var world = owner.getWorld();
        if (world != null) {
            int frame = world.getFrame();
            lent.removeIf(buff -> buff.until() < frame);
        }
    }

    void save(SaveOut out) {
        out.writeInt(lent.size());
        lent.forEach(buff -> out.writeEnum(buff.kind()).writeInt(buff.source()).writeInt(buff.value())
                .writeInt(buff.until()));
        out.writeInt(boostFrames).writeInt(boostPercent).writeInt(guardFrames).writeInt(guardPercent)
                .writeInt(healCarry);
    }

    void load(SaveIn in) {
        lent.clear();
        for (int count = in.readInt(); count > 0; count--) {
            lent.add(new Buff(in.readEnum(SkillEffect.class), in.readInt(), in.readInt(), in.readInt()));
        }
        boostFrames = in.readInt();
        boostPercent = in.readInt();
        guardFrames = in.readInt();
        guardPercent = in.readInt();
        healCarry = in.readInt();
    }
}
