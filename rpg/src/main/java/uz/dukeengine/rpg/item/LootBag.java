package uz.dukeengine.rpg.item;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * What the hero carries: a few slots, each empty or holding one thing he picked up off the floor, and what it all
 * comes to.
 *
 * <p>Session state, beside {@link uz.dukeengine.rpg.hero.HeroProgress} and for the
 * same reasons: a floor
 * gives him a new body, and a sword he found has to survive that; a death is
 * meant to take everything, and is told rather than left to infer.
 *
 * <p>What is in it counts for as long as it is in it. He picks a thing up by being sent to it, and puts one down
 * the same way — see {@link ItemErrand} — and a full bag leaves the next thing lying where it was. Three of the
 * same thing at the same level join into one of the next, up to the top level: see {@link #take}.
 *
 * <p>Written on the simulation thread only. The window reads {@link #slots()}, a copy taken each time the bag
 * changes, and {@link #noteAt}, whose words and frame are one value — so it never sees either half-changed.
 *
 * <p>It also carries the words for the last thing he found, because something has
 * to say so and the run loop is what writes the line the panel reads. The same words, for the same frames, are what
 * the window says in a bubble over his head.
 */
public final class LootBag {

    /** How many things a bag holds when nobody says otherwise. */
    public static final int SLOTS = 6;
    /** How many alike join into one of the next level, and the highest level they join to, likewise. */
    public static final int JOIN = 3;
    public static final int TOP_LEVEL = 3;

    private final Loot[] slots;
    private final int join;
    private final int topLevel;

    /** The slots as the window reads them: replaced whole on every change, never changed in place. */
    private volatile List<Loot> view;

    /** Counts every change, so whoever works figures out of the bag knows when to work them again. */
    private int version;

    /** What he is saying, and the frame it stops being said: one value, so both are always read together. */
    private record Note(String words, int untilFrame) {
    }

    /** What he just picked up, why he left it lying, what he makes of the gate: the window reads it too. */
    private volatile Note note = new Note("", 0);

    public LootBag() {
        this(SLOTS);
    }

    public LootBag(int slots) {
        this(slots, JOIN, TOP_LEVEL);
    }

    /**
     * @param join     how many alike join into one of the next level; less than two joins nothing
     * @param topLevel the highest level a thing is joined to
     */
    public LootBag(int slots, int join, int topLevel) {
        this.slots = new Loot[Math.max(1, slots)];
        this.join = join;
        this.topLevel = topLevel;
        changed();
    }

    /**
     * Take one in, and remember it long enough to say so; false when there is no room for it.
     *
     * <p>Where it makes up a set — the bag already holding one fewer than a join of the same thing at the same level
     * — the set becomes one of the next level in the first of their slots, and that may make up a set of its own in
     * turn. Such a thing needs no room: it takes some away. A thing that does not join — a key, see
     * {@link Loot#joins} — never makes up a set, however many there are, and takes a slot of its own.
     */
    public boolean take(Loot item, int frame, int noteFrames) {
        var coming = item;
        int freed = -1;
        while (join >= 2 && coming.level() < topLevel && coming.joins()) {
            var alike = new ArrayList<Integer>();
            for (int at = 0; at < slots.length && alike.size() < join - 1; at++) {
                if (coming.sameAs(slots[at])) {
                    alike.add(at);
                }
            }
            if (alike.size() < join - 1) {
                break;
            }
            for (int at : alike) {
                slots[at] = null;
            }
            freed = freed < 0 ? alike.getFirst() : Math.min(freed, alike.getFirst());
            coming = coming.joined(join);
        }
        int at = freed >= 0 ? freed : firstEmpty();
        if (at < 0) {
            return false;
        }
        slots[at] = coming;
        say(nameOf(coming), frame, noteFrames);
        changed();
        return true;
    }

    /** What the panel calls a thing: its name, and past the first level which level it is. */
    public static String nameOf(Loot item) {
        return item.level() <= 1 ? item.name() : item.name() + " " + "I".repeat(item.level());
    }

    private int firstEmpty() {
        for (int at = 0; at < slots.length; at++) {
            if (slots[at] == null) {
                return at;
            }
        }
        return -1;
    }

    /** Take what is in {@code slot} out of the bag: what it was, or {@code null} for an empty or unknown slot. */
    public Loot remove(int slot) {
        if (slot < 0 || slot >= slots.length || slots[slot] == null) {
            return null;
        }
        var out = slots[slot];
        slots[slot] = null;
        changed();
        return out;
    }

    /** What is in {@code slot}, or {@code null}. */
    public Loot at(int slot) {
        return slot < 0 || slot >= slots.length ? null : slots[slot];
    }

    /** Whether the next thing picked up would have nowhere to go. */
    public boolean isFull() {
        return Arrays.stream(slots).allMatch(item -> item != null);
    }

    /** Whether he carries anything used as {@code use}: a key, say. */
    public boolean holds(ItemUse use) {
        return Arrays.stream(slots).anyMatch(item -> item != null && item.use() == use);
    }

    /** Take everything used as {@code use} out of the bag, in slot order: every key, say, once its place is behind. */
    public void removeAll(ItemUse use) {
        for (int slot = 0; slot < slots.length; slot++) {
            if (slots[slot] != null && slots[slot].use() == use) {
                remove(slot);
            }
        }
    }

    /**
     * Say something for a while, on the panel and in a bubble over his head — what he found, why he left it lying,
     * what he makes of the gate. It is said for {@code noteFrames} frames from {@code frame}.
     */
    public void say(String words, int frame, int noteFrames) {
        note = new Note(words, frame + noteFrames);
    }

    /** Everything he carries, in slot order. */
    public List<Loot> getFound() {
        var found = new ArrayList<Loot>();
        for (var item : slots) {
            if (item != null) {
                found.add(item);
            }
        }
        return List.copyOf(found);
    }

    /** Every slot in order, {@code null} where empty — from any thread. */
    public List<Loot> slots() {
        return view;
    }

    /** Goes up with every change. */
    public int version() {
        return version;
    }

    /** Forget everything: a run has ended. */
    public void clear() {
        Arrays.fill(slots, null);
        note = new Note("", 0);
        changed();
    }

    /** What he is saying at {@code frame}, or "" once it has been said — from any thread. */
    public String noteAt(int frame) {
        var now = note;
        return frame < now.untilFrame() ? now.words() : "";
    }

    private void changed() {
        version++;
        view = Collections.unmodifiableList(Arrays.asList(slots.clone()));
    }

    // ---- what it all comes to ----

    /** Percent added to his weapon damage by everything he carries. */
    public int attackPercent() {
        return total(Stat.ATTACK);
    }

    /** Flat maximum health added by everything he carries. */
    public int health() {
        return total(Stat.HEALTH);
    }

    /** Flat maximum mana added by everything he carries. */
    public int mana() {
        return total(Stat.MANA);
    }

    /** Percent of incoming damage removed by everything he carries. */
    public int armourPercent() {
        return total(Stat.ARMOUR);
    }

    /** Whole points of health a second that what he carries adds to what comes back on its own. */
    public int healthRegen() {
        return extra(Stat.HEALTH_REGEN);
    }

    /** Whole points of mana a second that what he carries adds to what comes back on its own. */
    public int manaRegen() {
        return extra(Stat.MANA_REGEN);
    }

    /** Percent faster between blows, for everything he carries. */
    public int attackSpeedPercent() {
        return extra(Stat.ATTACK_SPEED);
    }

    /** What everything he carries gives of {@code extra} beside its figure: a {@link Stat} word, or the game's own. */
    public int extra(String extra) {
        int total = 0;
        for (var item : slots) {
            if (item != null && item.extra().equals(extra)) {
                total += item.extraValue();
            }
        }
        return total;
    }

    /**
     * The attributes everything he carries adds, in the order {@code rules} lists them.
     *
     * <p>An item naming an attribute the rules do not know adds nothing; the settings
     * file refuses such an item when it is read, so this is only ever a test's.
     */
    public uz.dukeengine.rpg.hero.Attributes attributes(uz.dukeengine.rpg.hero.AttributeRules rules) {
        var points = new int[rules.attributes().size()];
        for (var item : slots) {
            if (item == null || !item.kind().equals(Stat.ATTRIBUTE)) {
                continue;
            }
            int at = rules.indexOf(item.attribute());
            if (at >= 0) {
                points[at] = Math.addExact(points[at], item.value());
            }
        }
        return uz.dukeengine.rpg.hero.Attributes.ofWhole(points);
    }

    /** What everything he carries adds to the figure {@code kind}: a {@link Stat} word, or the game's own. */
    public int total(String kind) {
        int total = 0;
        for (var item : slots) {
            if (item != null && item.kind().equals(kind)) {
                total += item.value();
            }
        }
        return total;
    }
}
