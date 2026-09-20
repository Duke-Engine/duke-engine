package uz.dukeengine.dungeon.skill;

import java.util.List;

/**
 * What the player has put his levels into, and what he is allowed to put the
 * next one into.
 *
 * <p>The rules and nothing else — no game object, no simulation — for the same
 * reason {@link uz.dukeengine.dungeon.level.Levelling} and {@link Skill} are: "what can
 * he take at level 8?" should be answerable without starting a dungeon, and a
 * build is the part of a game that gets retuned most.
 *
 * <p><b>A skill is bought, not granted.</b> Before this, every skill a hero had
 * was as strong as he was: reaching level 7 made all four of them level 7, and
 * the only decision in the panel was which key to press. Now a level is a POINT
 * and the four slots compete for it, which is the whole of what makes two runs
 * with the same hero different from each other.
 *
 * <p>Four rules, and each is one sentence:
 *
 * <ul>
 *   <li>One point per level, so a hero of level 9 has spent at most nine.
 *   <li>A skill has a ceiling — four for an ordinary one, three for an ultimate.
 *   <li>An ultimate also waits for the hero: its first rank at level 4, its
 *       second at 8, its third at 12. A MINIMUM and not an appointment — pass on
 *       it at 8 and it is still there at 9.
 *   <li>The ordinary three may not drift more than {@code spread} apart, which
 *       is what stops one skill being taken four times running while the other
 *       two sit at nothing.
 * </ul>
 *
 * <p>The numbers work out exactly: fifteen levels, twelve ordinary ranks and
 * three ultimate ones. A hero who reaches the top has everything, and every
 * level before that is a decision about the order.
 *
 * <p><b>It outlives the hero's body.</b> A floor hands him a fresh
 * {@link SkillBook} — a new object, new cooldowns — so what he has learnt has to
 * be somewhere that survives that, exactly as what he has found is. A death
 * is the other way: {@link #startWith} wipes it, because a run is where a build
 * lives and a roguelike keeps nothing.
 */
public final class SkillRanks {

    /** Nothing learnt: the rank of a skill the player has not spent a point on. */
    public static final int UNLEARNT = 0;

    private final int spread;

    private List<Skill> skills = List.of();
    private int[] ranks = new int[0];

    /**
     * @param spread how far apart the ordinary skills may drift. Two: enough to
     *     favour one without abandoning the others, and the number the file owns
     */
    public SkillRanks(int spread) {
        this.spread = Math.max(1, spread);
    }

    /**
     * Begin again with this hero's skills, all of them unlearnt.
     *
     * <p>Called when a run starts and when one is started over, which are the
     * same thing: choosing a hero from the menu replaces the run rather than
     * swapping a body in, so a knight cannot inherit a mage's points.
     */
    public void startWith(List<Skill> his) {
        this.skills = List.copyOf(his);
        this.ranks = new int[skills.size()];
    }

    /** His four, in the order the file lists them. */
    public List<Skill> getSkills() {
        return skills;
    }

    /** What he has put into this one, or {@link #UNLEARNT}. */
    public int rankOf(char key) {
        int slot = slotOf(key);
        return slot < 0 ? UNLEARNT : ranks[slot];
    }

    /** Whether he has bought it at all, which is what lets it be cast. */
    public boolean isLearnt(char key) {
        return rankOf(key) > UNLEARNT;
    }

    /** Everything he has spent so far. */
    public int spent() {
        int total = 0;
        for (int rank : ranks) {
            total += rank;
        }
        return total;
    }

    /** What he has left to spend, which is one per level he has reached. */
    public int unspent(int heroLevel) {
        return Math.max(0, heroLevel - spent());
    }

    /**
     * Whether the next point may go here, this instant.
     *
     * <p>Every reason to say no, in the order they are cheapest to check. The
     * panel asks this of all four to decide which of them to offer a button on,
     * so it is asked a great deal and answers without allocating.
     */
    public boolean canRaise(char key, int heroLevel) {
        int slot = slotOf(key);
        if (slot < 0 || unspent(heroLevel) <= 0) {
            return false;
        }
        var skill = skills.get(slot);
        int next = ranks[slot] + 1;
        if (next > skill.maxRank()) {
            return false; // full
        }
        if (heroLevel < skill.levelForRank(next)) {
            return false; // an ultimate, waiting for him to catch up
        }
        return skill.isUltimate() || withinSpread(slot, next);
    }

    /**
     * Spend a point here.
     *
     * <p>Refuses rather than clamping, and says so, because the caller is a
     * command off the queue: a click that arrived a frame after the rule stopped
     * allowing it must do nothing at all rather than something nearly right.
     *
     * @return whether the point was actually spent
     */
    public boolean raise(char key, int heroLevel) {
        if (!canRaise(key, heroLevel)) {
            return false;
        }
        ranks[slotOf(key)]++;
        return true;
    }

    /**
     * The hero level at which this slot could next be raised, or 0 if the answer
     * is "as soon as you have a point".
     *
     * <p>For the panel, which has to tell a slot that is merely unaffordable from
     * one that is waiting for something — "you have no points" and "level 8" are
     * different things to read on a locked button.
     */
    public int waitingForLevel(char key) {
        int slot = slotOf(key);
        if (slot < 0) {
            return 0;
        }
        var skill = skills.get(slot);
        int next = ranks[slot] + 1;
        return next > skill.maxRank() ? 0 : skill.levelForRank(next);
    }

    /**
     * Whether raising this one would leave the ordinary three too far apart.
     *
     * <p>Measured across the ordinary skills ONLY. An ultimate sits at nothing
     * until level 4 while the others are climbing, so counting it would make the
     * spread impossible to satisfy for the first three levels of every run — and
     * it has a gate of its own, which is the same idea said better.
     */
    private boolean withinSpread(int raising, int to) {
        int highest = to;
        int lowest = to;
        for (int slot = 0; slot < ranks.length; slot++) {
            if (slot == raising || skills.get(slot).isUltimate()) {
                continue;
            }
            highest = Math.max(highest, ranks[slot]);
            lowest = Math.min(lowest, ranks[slot]);
        }
        return highest - lowest <= spread;
    }

    private int slotOf(char key) {
        for (int slot = 0; slot < skills.size(); slot++) {
            if (skills.get(slot).key() == key) {
                return slot;
            }
        }
        return -1;
    }
}
