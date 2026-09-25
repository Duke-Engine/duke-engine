package uz.dukeengine.rts.player;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import uz.dukeengine.core.player.Player;
import uz.dukeengine.core.thing.World;

/**
 * An RTS side: the engine's {@link Player} plus the things an RTS runs on —
 * a treasury, the upgrades it has bought, and the bonuses they add up to.
 *
 * <p>{@code RtsSimulation} installs {@code RtsPlayer::new} as its roster's
 * factory, so every player in an RTS game is one of these. Modules reach it
 * through {@link #of(World, int)} rather than casting by hand.
 *
 * <p>Bonuses are <b>named</b> rather than enumerated. There used to be exactly
 * one — a weapon damage multiplier — which quietly decided that the only thing an
 * upgrade could improve was damage; an armoury that toughens infantry or a forge
 * that lengthens range had nowhere to go. The engine does not need to know what
 * {@code "WeaponDamage"} means to carry it, so a game names its own.
 *
 * <p>Both collections are sorted, and that is not tidiness. Anything iterated on
 * the simulation path has to come out in the same order on every machine, and a
 * hash-ordered set only stays safe while every caller remembers to sort it —
 * which is a promise made once and relied on forever. Sorting at the source
 * makes the guarantee the type's, not the caller's.
 */
public final class RtsPlayer extends Player {

    /** The bonus a weapon consults. Named so a game can add its own beside it. */
    public static final String WEAPON_DAMAGE = "WeaponDamage";

    private final Set<String> upgrades = new TreeSet<>();
    /** Words granted to the side — a science it chose — beside the upgrades it researched. */
    private final Set<String> granted = new TreeSet<>();
    private boolean computer;
    private final Map<String, Float> bonuses = new TreeMap<>();
    private int money;
    /** What the side took in, and what it paid out and kept paid — see {@link #getEarned}, {@link #getSpent}. */
    private long earned;
    private long spent;

    public RtsPlayer(int index, String name) {
        super(index, name);
    }

    /**
     * The RTS player at {@code index}, or {@code null} if the world has no such
     * player — or is not running an RTS roster.
     */
    public static RtsPlayer of(World world, int index) {
        return world != null && world.getPlayer(index) instanceof RtsPlayer player ? player : null;
    }

    /**
     * Its money, what it has earned and spent, the words granted it and the upgrades it has, mixed into the frame's
     * checksum, as the reference sums every player's money, sciences and upgrades ({@code GameLogic::getCRC}): two
     * machines that part company over them are told on the next checked frame.
     */
    @Override
    public long checksum(long hash) {
        hash = hash * 31 + getMoney();
        hash = hash * 31 + Long.hashCode(getEarned());
        hash = hash * 31 + Long.hashCode(getSpent());
        for (var word : granted) {
            hash = hash * 31 + word.hashCode();
        }
        for (var upgrade : upgrades) {
            hash = hash * 31 + upgrade.hashCode();
        }
        return hash;
    }

    public int getMoney() {
        return money;
    }

    /** A change to what the side pays for things of a kind, and how many times it has been given. */
    private record PriceChange(uz.dukeengine.core.thing.Kind kind, float percent, int count) {
    }

    /** What its prices are changed by, in the order given; read from any thread, changed on the simulation's. */
    private final java.util.List<PriceChange> priceChanges = new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * Change what the side pays for things of {@code kind} by {@code percent} — -0.1 is a tenth cheaper — the
     * reference's CostModifierUpgrade (a captured oil refinery: vehicles 10% off). The same kind and percent given
     * twice counts once, until it is taken away twice.
     */
    public void addPriceChange(uz.dukeengine.core.thing.Kind kind, float percent) {
        for (int i = 0; i < priceChanges.size(); i++) {
            var change = priceChanges.get(i);
            if (change.kind().equals(kind) && change.percent() == percent) {
                priceChanges.set(i, new PriceChange(kind, percent, change.count() + 1));
                return;
            }
        }
        priceChanges.add(new PriceChange(kind, percent, 1));
    }

    /** Take away a change {@link #addPriceChange} gave: it holds until taken away as many times as it was given. */
    public void removePriceChange(uz.dukeengine.core.thing.Kind kind, float percent) {
        for (int i = 0; i < priceChanges.size(); i++) {
            var change = priceChanges.get(i);
            if (change.kind().equals(kind) && change.percent() == percent) {
                if (change.count() > 1) {
                    priceChanges.set(i, new PriceChange(kind, percent, change.count() - 1));
                } else {
                    priceChanges.remove(i);
                }
                return;
            }
        }
        java.util.logging.Logger.getLogger(RtsPlayer.class.getName()).warning(
                "no price change of " + percent + " for " + kind + " to take away from " + getName());
    }

    /**
     * What {@code template} costs this side: its price times one plus each change it is of the kind of, rounded
     * down, as the reference's {@code calcCostToBuild} works it out — what a factory charges, a builder pays, a cancel
     * gives back and a build menu shows.
     */
    public int priceOf(uz.dukeengine.core.thing.ThingTemplate template) {
        float factor = 1f;
        for (var change : priceChanges) {
            if (uz.dukeengine.core.thing.Classified.of(template).contains(change.kind())) {
                factor *= 1f + change.percent();
            }
        }
        return (int) (uz.dukeengine.rts.Buildable.costOf(template) * factor);
    }

    /** Money the side earned — a supply truck's load, a bounty, a hack: in the balance, and in what it earned. */
    public void deposit(int amount) {
        if (amount > 0) {
            money += amount;
            earned += amount;
        }
    }

    /** Withdraw up to {@code amount}; returns true if the player could afford it — and then it is spent. */
    public boolean withdraw(int amount) {
        if (amount < 0 || money < amount) {
            return false;
        }
        money -= amount;
        spent += amount;
        return true;
    }

    /**
     * Money coming back from what was spent: an order called off, a building sold for its worth. Taken off what the
     * side spent rather than counted as earned, as the reference's score keeps a sale out of what a side collected.
     */
    public void refund(int amount) {
        if (amount > 0) {
            money += amount;
            spent = Math.max(0L, spent - amount);
        }
    }

    /**
     * Money handed to the side rather than earned by it — the money it starts with, a script's gift: in the balance,
     * and in neither total.
     */
    public void give(int amount) {
        if (amount > 0) {
            money += amount;
        }
    }

    /**
     * All its money handed away — to an ally, as it leaves: out of its balance and in neither total, as {@link #give}
     * puts it into the ally's.
     *
     * @return how much it had
     */
    public int handOver() {
        int all = money;
        money = 0;
        return all;
    }

    /**
     * All the money the side has taken in — supply returns, bounties, hacks, whatever was deposited — for a score
     * screen: what one frame both earns and spends, the balance hides, and this does not.
     */
    public long getEarned() {
        return earned;
    }

    /** All the money the side has paid out and not had back — see {@link #refund}. */
    public long getSpent() {
        return spent;
    }

    /** The side's money and its books as a save wrote them down. */
    public void restoreBooks(int money, long earned, long spent) {
        this.money = Math.max(0, money);
        this.earned = Math.max(0L, earned);
        this.spent = Math.max(0L, spent);
    }

    public boolean hasUpgrade(String upgradeName) {
        return upgrades.contains(upgradeName);
    }

    /** Every upgrade bought, in name order. */
    public Set<String> getUpgrades() {
        return java.util.Collections.unmodifiableSortedSet(new TreeSet<>(upgrades));
    }

    /** Mark an upgrade as completed for this player. */
    public void addUpgrade(String upgradeName) {
        upgrades.add(upgradeName);
    }

    /** Give the side a word to hold — a science it chose — for what needs it ({@code Prerequisites}). */
    public void grant(String word) {
        granted.add(word);
    }

    /** Whether the side holds a word: one granted it, or an upgrade of the side's it has. */
    public boolean holds(String word) {
        return granted.contains(word) || upgrades.contains(word);
    }

    /** The words granted the side, in name order. */
    public Set<String> getGranted() {
        return java.util.Collections.unmodifiableSortedSet(new TreeSet<>(granted));
    }

    /** Whether a computer plays this side: what it may make only a computer may ({@code ONLY_BY_COMPUTER}). */
    public boolean isComputer() {
        return computer;
    }

    public void setComputer(boolean computer) {
        this.computer = computer;
    }

    // ---- player-wide bonuses ----

    /**
     * A named multiplier this side has earned, or 1.0 if it has earned none.
     *
     * <p>Unknown names answer 1.0 rather than failing, so a unit can ask for a
     * bonus its game never defines and simply be unaffected.
     */
    public float getBonus(String name) {
        return bonuses.getOrDefault(name, 1.0f);
    }

    /** Set a bonus outright — what to use when the value is known. */
    public void setBonus(String name, float value) {
        bonuses.put(name, value);
    }

    /**
     * Compound a bonus, for an upgrade that stacks with what came before.
     *
     * <p>{@link #setBonus} is the one to reach for when the caller knows the
     * figure it wants. Multiplying was once the only way in, which forced anything
     * needing an exact value to divide out whatever was already there.
     */
    public void multiplyBonus(String name, float factor) {
        bonuses.put(name, getBonus(name) * factor);
    }

    /** Every bonus this side holds, in name order. */
    public Map<String, Float> getBonuses() {
        return java.util.Collections.unmodifiableSortedMap(new TreeMap<>(bonuses));
    }

    /** Player-wide multiplier applied to all owned units' weapon damage. */
    public float getWeaponDamageBonus() {
        return getBonus(WEAPON_DAMAGE);
    }

    public void setWeaponDamageBonus(float value) {
        setBonus(WEAPON_DAMAGE, value);
    }

    public void multiplyWeaponDamageBonus(float factor) {
        multiplyBonus(WEAPON_DAMAGE, factor);
    }
}
