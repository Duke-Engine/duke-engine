package uz.dukeengine.rts;

import java.util.logging.Logger;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.message.Command;
import uz.dukeengine.core.player.PlayerList;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;
import uz.dukeengine.rts.message.GameMessage;
import uz.dukeengine.rts.module.RtsModules;
import uz.dukeengine.rts.player.RtsPlayer;
import uz.dukeengine.rts.player.Upgrade;

/**
 * A {@link GameLogic} that speaks the RTS command set — the base every RTS
 * simulation extends.
 *
 * <p>The engine hands commands back as the genre-neutral {@link Command}; this
 * narrows them to {@link GameMessage} once, here, so subclasses get an
 * exhaustive {@code switch} over a sealed hierarchy instead of repeating the
 * cast.
 *
 * <p>Anything that is not an RTS command goes to {@link #onOtherCommand}, which
 * by default logs it as input fed to the wrong simulation. That default is
 * usually right — but not always, and a game that overrides it is the reason the
 * hook exists. {@link Command} says a game declares its own command set, and
 * {@link GameMessage} is <em>this library's</em> set, not every set a game built
 * on it could want: a roguelike's "cast the third ability" is not an RTS order
 * and never will be, yet it belongs in the same stream, because that stream is
 * what makes input replayable and network-safe. Without the hook such a game had
 * to reach around the command pipeline entirely, which is exactly the property
 * the pipeline exists to provide.
 *
 * <p>It also installs the RTS module set by default, so INI can reference
 * {@code WeaponUpdate}, {@code ProductionUpdate} and friends without extra
 * wiring.
 */
public abstract class RtsSimulation extends GameLogic {

    private static final Logger LOG = Logger.getLogger(RtsSimulation.class.getName());

    protected RtsSimulation() {
        this(new ThingFactory(RtsModules.withDefaults()));
    }

    protected RtsSimulation(ThingFactory thingFactory) {
        super(thingFactory, new PlayerList(RtsPlayer::new));
    }

    @Override
    protected final void onCommand(Command command) {
        if (command instanceof GameMessage message) {
            onRtsCommand(message);
            return;
        }
        onOtherCommand(command);
    }

    /** Apply one RTS command. Implementations switch over the sealed hierarchy. */
    protected abstract void onRtsCommand(GameMessage command);

    /**
     * A crusher runs over a thing whose crushable level is below its crusher level, never an ally's, and is not held up
     * by it — the reference's {@code canCrushOrSquish} in {@code AIUpdateInterface::blockedBy}.
     */
    @Override
    public boolean runsOver(uz.dukeengine.core.thing.GameObject mover, uz.dukeengine.core.thing.GameObject other) {
        var crusher = mover.findModule(uz.dukeengine.rts.module.CrushUpdate.class);
        var crushable = other.findModule(uz.dukeengine.rts.module.Crushable.class);
        return crusher != null && crushable != null && crushable.getLevel() < crusher.getLevel()
                && mover.getPlayerIndex() != other.getPlayerIndex()
                && getRelationship(mover.getPlayerIndex(), other.getPlayerIndex())
                        != uz.dukeengine.core.player.Relationship.ALLIES;
    }

    /**
     * Apply a command that is not part of the RTS set — a command the game built
     * on this library declared for itself.
     *
     * <p>The default assumes there is no such set and says so, because for most
     * simulations a foreign command really is a mistake and silence would hide it.
     * A game with commands of its own overrides this and dispatches over its own
     * sealed hierarchy, exactly as {@link #onRtsCommand} does over this one.
     *
     * <p>Whatever it does must be deterministic: this runs inside the frame, from
     * the same queue, on every peer.
     */
    protected void onOtherCommand(Command command) {
        LOG.warning(() -> "ignoring non-RTS command: " + command.getClass().getName());
    }

    private final java.util.List<java.util.function.BiConsumer<uz.dukeengine.core.thing.GameObject,
            uz.dukeengine.core.thing.GameObject>> producedWatchers = new java.util.ArrayList<>();
    private final java.util.List<java.util.function.BiConsumer<uz.dukeengine.core.thing.GameObject,
            uz.dukeengine.core.thing.GameObject>> constructedWatchers = new java.util.ArrayList<>();

    /**
     * Game code told whenever a factory releases a unit, as {@code (factory, unit)}: on the simulation's thread,
     * the frame it happens, after the factory's own {@link uz.dukeengine.rts.module.ProductionListener}s, and in
     * the order watchers were registered — a computer player's {@code onUnitProduced}.
     */
    public final void onProduced(java.util.function.BiConsumer<uz.dukeengine.core.thing.GameObject,
            uz.dukeengine.core.thing.GameObject> watcher) {
        producedWatchers.add(watcher);
    }

    /** Game code told whenever a building is finished, as {@code (builder, building)} — see {@link #onProduced}. */
    public final void onConstructed(java.util.function.BiConsumer<uz.dukeengine.core.thing.GameObject,
            uz.dukeengine.core.thing.GameObject> watcher) {
        constructedWatchers.add(watcher);
    }

    private final java.util.List<java.util.function.Consumer<uz.dukeengine.core.thing.GameObject>> placedWatchers =
            new java.util.ArrayList<>();

    /**
     * Game code told whenever a building is put down — a site, the frame it is placed, or a building a game's own code
     * puts down whole and says so ({@link #placed}) — with it: on the simulation's thread, in the order watchers were
     * registered, for the game to clear what it stands over at once, as the reference clears what may be removed for
     * construction ({@code BuildAssistant::clearRemovableForConstruction}).
     */
    public final void onPlaced(java.util.function.Consumer<uz.dukeengine.core.thing.GameObject> watcher) {
        placedWatchers.add(watcher);
    }

    /** {@code building} has been put down: every watcher told — what the engine calls for each site it places. */
    public final void placed(uz.dukeengine.core.thing.GameObject building) {
        for (var watcher : placedWatchers) {
            watcher.accept(building);
        }
    }

    private final java.util.List<java.util.function.BiConsumer<uz.dukeengine.core.thing.GameObject, Upgrade>>
            researchedWatchers = new java.util.ArrayList<>();

    /**
     * Game code told whenever research finishes at a building or a unit, as {@code (researcher, upgrade)}: on the
     * simulation's thread, the frame it finishes, after the upgrade has taken effect, in the order watchers were
     * registered — as the reference tells the researcher's owner ({@code ProductionUpdate::update}: its line, its
     * radar event, its {@code ResearchSound}). Not for an upgrade bought at once ({@link #purchaseUpgrade}), nor for
     * research called off.
     */
    public final void onResearched(java.util.function.BiConsumer<uz.dukeengine.core.thing.GameObject, Upgrade>
            watcher) {
        researchedWatchers.add(watcher);
    }

    /** Research at {@code researcher} has finished: {@code upgrade} takes effect, then every watcher is told. */
    public final void researched(uz.dukeengine.core.thing.GameObject researcher, Upgrade upgrade) {
        upgradeCompleted(researcher, upgrade);
        for (var watcher : researchedWatchers) {
            watcher.accept(researcher, upgrade);
        }
    }

    /**
     * {@code factory} has released {@code unit}: its {@code ProductionListener}s are told, then every watcher.
     * What a factory's own queue does, and what game code that hands out a unit of its own from a building — a
     * free harvester from a supply centre as it is finished — calls to say the building made it.
     */
    public final void produced(uz.dukeengine.core.thing.GameObject factory, uz.dukeengine.core.thing.GameObject unit) {
        for (var module : factory.getModules()) {
            if (module instanceof uz.dukeengine.rts.module.ProductionListener listener) {
                listener.onProduced(unit);
            }
        }
        for (var watcher : producedWatchers) {
            watcher.accept(factory, unit);
        }
    }

    /**
     * {@code builder} has finished {@code building}: the {@code ConstructionListener}s on the builder, then on the
     * building, then every watcher.
     */
    public final void constructed(uz.dukeengine.core.thing.GameObject builder,
            uz.dukeengine.core.thing.GameObject building) {
        for (var told : builder == null ? java.util.List.of(building) : java.util.List.of(builder, building)) {
            for (var module : told.getModules()) {
                if (module instanceof uz.dukeengine.rts.construction.ConstructionListener listener) {
                    listener.onConstructed(builder, building);
                }
            }
        }
        for (var watcher : constructedWatchers) {
            watcher.accept(builder, building);
        }
    }

    private uz.dukeengine.rts.construction.PlacementRules placementRules =
            uz.dukeengine.rts.construction.PlacementRules.DEFAULTS;

    /**
     * Hand {@code things} to player {@code to}: the reference's {@code Player::transferAssetsFromThat}, for a side that
     * quits or surrenders with a living ally, or anything a game gives away. Call it on the simulation thread — from
     * {@code onOrder}, or the frame a player leaves — so every machine hands over the same things on the same frame.
     * Whatever the engine keeps for an owner follows the thing, being read off its owner each frame: a factory's queue,
     * its research and its rally point carry on for the new owner, its power counts for them, it sees and is seen for
     * them, and its experience and the words its own upgrades put on it stay with it. A shared hold's passengers stay
     * in the old side's list.
     */
    public final void handOver(java.util.Collection<uz.dukeengine.core.thing.GameObject> things, int to) {
        for (var thing : things) {
            if (thing != null && !thing.isDestroyed()) {
                thing.setPlayerIndex(to);
            }
        }
    }

    /** Everything player {@code from} has, handed to {@code to} — and, where {@code money}, all its money too. */
    public final void handOverAll(int from, int to, boolean money) {
        var theirs = getObjects().stream()
                .filter(thing -> thing.getPlayerIndex() == from && !thing.isEffectivelyDead()).toList();
        handOver(theirs, to);
        var giver = getRtsPlayer(from);
        var taker = getRtsPlayer(to);
        if (money && giver != null && taker != null) {
            taker.give(giver.handOver());
        }
    }

    /** The passengers of every side's shared holds, by side and then network — see {@code ContainModule}. */
    private final java.util.Map<Integer, java.util.Map<String, java.util.List<uz.dukeengine.core.thing.ObjectId>>>
            sharedHolds = new java.util.TreeMap<>();

    /**
     * The one list of passengers a side's network of shared holds carries, in the order they got in — every thing of
     * the side whose {@code ContainModule} is {@code SharedBy} that network loads to and unloads from it. The list
     * itself, for {@code ContainModule} to change; anything else reads it.
     */
    public final java.util.List<uz.dukeengine.core.thing.ObjectId> sharedHold(int player, String network) {
        return sharedHolds.computeIfAbsent(player, side -> new java.util.TreeMap<>())
                .computeIfAbsent(network, name -> new java.util.ArrayList<>());
    }

    /** Where this game's buildings may stand and what calling one off gives back — the game's numbers. */
    public final void setPlacementRules(uz.dukeengine.rts.construction.PlacementRules rules) {
        this.placementRules = rules == null ? uz.dukeengine.rts.construction.PlacementRules.DEFAULTS : rules;
    }

    public final uz.dukeengine.rts.construction.PlacementRules getPlacementRules() {
        return placementRules;
    }

    private final java.util.Map<String, uz.dukeengine.rts.module.Weapon> weapons = new java.util.LinkedHashMap<>();

    /**
     * The weapons this game's {@link uz.dukeengine.rts.module.WeaponSlot}s link by name — one block a weapon,
     * however many units carry it. A later weapon of a name replaces an earlier. Data, like the templates: a
     * new game in the same world keeps them.
     */
    public final void addWeapons(java.util.Collection<uz.dukeengine.rts.module.Weapon> more) {
        for (var weapon : more) {
            if (weapon != null && weapon.name() != null) {
                weapons.put(weapon.name(), weapon);
            }
        }
    }

    /** The weapon of this name, or {@code null} where the game gave none. */
    public final uz.dukeengine.rts.module.Weapon findWeapon(String name) {
        return name == null ? null : weapons.get(name);
    }

    private java.util.List<uz.dukeengine.rts.module.TargetRule> targetRules = java.util.List.of();

    /**
     * What each thing is, to a weapon: the game's lines, in order, the first that matches a thing deciding its
     * classes — see {@link uz.dukeengine.rts.module.TargetRule}. None, the default, gives no thing a class,
     * which matters only to a weapon that names classes: a weapon that names none fires at anything, as every
     * weapon did before this existed.
     */
    public final void setTargetRules(java.util.List<uz.dukeengine.rts.module.TargetRule> rules) {
        this.targetRules = rules == null ? java.util.List.of() : java.util.List.copyOf(rules);
    }

    public final java.util.List<uz.dukeengine.rts.module.TargetRule> getTargetRules() {
        return targetRules;
    }

    private int targetScanFrames = 1;
    private int idleTargetScanFrames;

    /**
     * How often a weapon with no target looks for one, in frames, where its template does not say ({@code
     * WeaponUpdate.Data.targetScanFrames}) — the reference's {@code MoodAttackCheckRate}, 2 seconds where a template
     * leaves it out. Each thing on its own clock: a look moves its next on by the rate, its first by up to half the
     * rate more, drawn from the world's random numbers, so a crowd does not look all at once; and a thing that falls
     * idle looks soon after ({@link #setIdleTargetScanFrames}). 1, the default, is every frame, as always.
     */
    public final void setTargetScanFrames(int frames) {
        this.targetScanFrames = Math.max(1, frames);
    }

    public final int getTargetScanFrames() {
        return targetScanFrames;
    }

    /**
     * How soon a thing that falls idle looks for a target, in frames — its order done or stopped, or what it fought
     * gone: its next look set to then, whatever its clock said — the reference's {@code resetNextMoodCheckTime}, now
     * and its {@code AIData}'s {@code ForceIdleFramesCount}, which its idle state and an idle turret run. 0, the
     * default, at once.
     */
    public final void setIdleTargetScanFrames(int frames) {
        this.idleTargetScanFrames = Math.max(0, frames);
    }

    public final int getIdleTargetScanFrames() {
        return idleTargetScanFrames;
    }

    private java.util.List<uz.dukeengine.core.thing.Kind> shownWhenHidden = java.util.List.of();
    private boolean hiddenShotsToOwnerOnly;

    /**
     * The kinds of thing whose shots are shown though the thing is hidden, filtered by where they are fired alone as
     * any other's — the reference's mines ({@code KINDOF_MINE}), which always show their blast. A weapon may say so of
     * itself ({@link uz.dukeengine.rts.module.Weapon#shownWhenHidden}). None, the default.
     */
    public final void setShownWhenHidden(java.util.List<uz.dukeengine.core.thing.Kind> kinds) {
        this.shownWhenHidden = kinds == null ? java.util.List.of() : java.util.List.copyOf(kinds);
    }

    public final java.util.List<uz.dukeengine.core.thing.Kind> getShownWhenHidden() {
        return shownWhenHidden;
    }

    /**
     * Whether a hidden thing's shots are kept from its allies too, shown to its own player alone — the reference's
     * {@code Weapon::fireWeaponTemplate}, a stealthed shooter's firing seen by the player who controls it and nobody
     * else. Off, the default: shown to whoever it is not hidden from.
     */
    public final void setHiddenShotsToOwnerOnly(boolean ownerOnly) {
        this.hiddenShotsToOwnerOnly = ownerOnly;
    }

    public final boolean isHiddenShotsToOwnerOnly() {
        return hiddenShotsToOwnerOnly;
    }

    /**
     * Apply a {@code Construct} order; see {@link uz.dukeengine.rts.construction.Construction#order}. One taken is
     * told to the builder's {@link uz.dukeengine.rts.module.OrderListener}s after, as the other standard orders are:
     * the reference's worker drops his supply work when he is told to build.
     */
    protected final boolean construct(GameMessage.Construct order) {
        boolean taken = uz.dukeengine.rts.construction.Construction.order(this, order, placementRules);
        if (taken) {
            tellOrder(order, java.util.List.of(order.builder()), order.playerIndex());
        }
        return taken;
    }

    /**
     * Apply a {@code ResumeConstruction} order; see {@link uz.dukeengine.rts.construction.Construction#resume}. One
     * taken is told to the builder's {@link uz.dukeengine.rts.module.OrderListener}s after.
     */
    protected final boolean resumeConstruction(GameMessage.ResumeConstruction order) {
        boolean taken = uz.dukeengine.rts.construction.Construction.resume(this, order, placementRules);
        if (taken) {
            tellOrder(order, java.util.List.of(order.builder()), order.playerIndex());
        }
        return taken;
    }

    /** Apply a {@code CancelConstruction} order. */
    protected final boolean cancelConstruction(GameMessage.CancelConstruction order) {
        return uz.dukeengine.rts.construction.Construction.cancel(this, order);
    }

    /**
     * Whether {@code template} may stand at {@code place} for no side in particular: as an order to build it there
     * would be judged, but for what the side has seen, which only {@link #fits(int, String,
     * uz.dukeengine.core.math.Coord3D, float) a side's} asks — for a client to show, never to decide.
     */
    public final uz.dukeengine.rts.construction.Placement.Fit fits(String template,
            uz.dukeengine.core.math.Coord3D place, float facing) {
        return fits(-1, template, place, facing);
    }

    /**
     * Whether {@code template} may stand at {@code place} as a site of {@code player}'s, exactly as his order to build
     * it there would be judged — what his side has seen as well, where the rules ask it ({@link
     * uz.dukeengine.rts.construction.PlacementRules#seenGround}). For his ghost to show, and a computer's own search
     * to ask, never to decide.
     */
    public final uz.dukeengine.rts.construction.Placement.Fit fits(int player, String template,
            uz.dukeengine.core.math.Coord3D place, float facing) {
        var thing = findTemplate(template);
        if (thing == null || getPathGrid() == null) {
            return uz.dukeengine.rts.construction.Placement.Fit.OFF_THE_MAP;
        }
        return uz.dukeengine.rts.construction.Placement.check(this, getPathGrid(), thing, place, facing,
                placementRules, player);
    }

    /** The RTS player at {@code index}, or {@code null} if there is none. */
    public final RtsPlayer getRtsPlayer(int index) {
        return getPlayerList().getPlayer(index) instanceof RtsPlayer player ? player : null;
    }

    // ---- the piles a harvester chooses ----

    private java.util.function.BiPredicate<uz.dukeengine.core.thing.GameObject, uz.dukeengine.core.thing.GameObject>
            pileRule = (harvester, pile) -> true;

    /**
     * Which piles a harvester may choose for itself, asked of each it could, as {@code (harvester, pile)} — the
     * reference never lets a person's harvester take a pile his side has never seen ({@code
     * ActionManager::canTransferSuppliesAt}). A pile its player sent it to is its player's choice. Every pile, unless
     * the game says otherwise.
     */
    public final void setPileRule(java.util.function.BiPredicate<uz.dukeengine.core.thing.GameObject,
            uz.dukeengine.core.thing.GameObject> rule) {
        this.pileRule = rule == null ? (harvester, pile) -> true : rule;
    }

    /** Whether the game lets {@code harvester} choose {@code pile} — see {@link #setPileRule}. */
    public final boolean mayChoosePile(uz.dukeengine.core.thing.GameObject harvester,
            uz.dukeengine.core.thing.GameObject pile) {
        return pileRule.test(harvester, pile);
    }

    // ---- a group's move ----

    private GroupLayout groupLayout = GroupMove.REFERENCE;

    /** How the units one {@code MoveTo} names are sent to its point — see {@link GroupMove}, the default. */
    public final void setGroupLayout(GroupLayout layout) {
        this.groupLayout = layout == null ? GroupMove.REFERENCE : layout;
    }

    public final GroupLayout getGroupLayout() {
        return groupLayout;
    }

    // ---- selling, guarding ----

    private uz.dukeengine.rts.construction.SellRules sellRules = uz.dukeengine.rts.construction.SellRules.DEFAULT;
    private uz.dukeengine.rts.module.GuardRules guardRules = uz.dukeengine.rts.module.GuardRules.DEFAULT;
    private final java.util.List<java.util.function.Consumer<uz.dukeengine.core.thing.GameObject>> soldWatchers =
            new java.util.ArrayList<>();

    /** How a sold building comes down and what it gives back — see {@code Selling}. */
    public final void setSellRules(uz.dukeengine.rts.construction.SellRules rules) {
        this.sellRules = rules == null ? uz.dukeengine.rts.construction.SellRules.DEFAULT : rules;
    }

    public final uz.dukeengine.rts.construction.SellRules getSellRules() {
        return sellRules;
    }

    /** How a guard guards and an attack-move chases — see {@code GuardOrder}. */
    public final void setGuardRules(uz.dukeengine.rts.module.GuardRules rules) {
        this.guardRules = rules == null ? uz.dukeengine.rts.module.GuardRules.DEFAULT : rules;
    }

    public final uz.dukeengine.rts.module.GuardRules getGuardRules() {
        return guardRules;
    }

    /**
     * Told when a building its side sold is down and gone — taken down, not destroyed by anyone — on the simulation
     * thread, the frame it goes, its refund already paid.
     */
    public final void onSold(java.util.function.Consumer<uz.dukeengine.core.thing.GameObject> watcher) {
        soldWatchers.add(watcher);
    }

    /** A sold building is down: its watchers told, and the moment posted. */
    public final void sold(uz.dukeengine.core.thing.GameObject building, int refund) {
        for (var watcher : soldWatchers) {
            watcher.accept(building);
        }
        post(new uz.dukeengine.rts.event.StructureSold(getFrame(), building.getId(), building.getTemplate().name(),
                building.getPlayerIndex(), refund, building.getPosition()));
    }

    // ---- the game's own orders ----

    private final java.util.List<java.util.function.Consumer<uz.dukeengine.rts.message.GameMessage.GameOrder>>
            orderWatchers = new java.util.ArrayList<>();

    /**
     * Told every order of the game's own as it is applied — the same frame and the same order on every machine, and
     * again in a replay — on the simulation thread, where the game does what the order means: grant a science, fire a
     * power. What it does there must be deterministic, as everything on this thread is.
     */
    public final void onOrder(java.util.function.Consumer<uz.dukeengine.rts.message.GameMessage.GameOrder> watcher) {
        orderWatchers.add(watcher);
    }

    /** A game's order applied: every watcher told, in the order they were added. */
    public final void ordered(uz.dukeengine.rts.message.GameMessage.GameOrder order) {
        for (var watcher : orderWatchers) {
            watcher.accept(order);
        }
    }

    // ---- weapon bonuses ----

    private java.util.List<uz.dukeengine.rts.module.WeaponBonus> weaponBonuses = java.util.List.of();

    /**
     * The game's weapon bonus table: every line whose word a thing holds multiplies its weapons' damage, range, rate
     * of fire or blast — see {@link uz.dukeengine.rts.module.WeaponBonus}. Kept in the order of their words, sorted,
     * so the product is the same on every machine.
     */
    public final void setWeaponBonuses(java.util.Collection<uz.dukeengine.rts.module.WeaponBonus> lines) {
        this.weaponBonuses = lines.stream()
                .sorted(java.util.Comparator.comparing(uz.dukeengine.rts.module.WeaponBonus::word)
                        .thenComparing(uz.dukeengine.rts.module.WeaponBonus::kind))
                .toList();
    }

    /** What the table makes {@code kind} for {@code thing}, as its words stand now — see the overload below. */
    public final float weaponBonus(uz.dukeengine.core.thing.GameObject thing,
            uz.dukeengine.rts.module.WeaponBonus.Kind kind) {
        return weaponBonus(thing, kind, java.util.List.of());
    }

    /**
     * What the table and a weapon's own lines make {@code kind} for {@code thing}, as its words stand now: 1, and for
     * every line whose word it holds, what that line adds over 1 — {@code 1 + Σ(multiplier − 1)}, as the reference's
     * {@code WeaponBonus::appendBonuses} sums the game's set and then the weapon's extra one. Added in a fixed order,
     * the table's lines by their words and then the weapon's, so it comes to the same bits on every machine.
     */
    public final float weaponBonus(uz.dukeengine.core.thing.GameObject thing,
            uz.dukeengine.rts.module.WeaponBonus.Kind kind,
            java.util.List<uz.dukeengine.rts.module.WeaponBonus> own) {
        float sum = 1f;
        for (var line : weaponBonuses) {
            if (line.kind() == kind && thing.hasCondition(line.word())) {
                sum += line.multiplier() - 1f;
            }
        }
        for (var line : own) {
            if (line.kind() == kind && thing.hasCondition(line.word())) {
                sum += line.multiplier() - 1f;
            }
        }
        return sum;
    }

    // ---- what a side may make ----

    private java.util.function.BiPredicate<String, String> countsAs = String::equals;
    private final java.util.Map<String, Integer> caps = new java.util.TreeMap<>();

    /**
     * Whether a thing of template {@code owned} counts as one of {@code wanted} — for a requirement, and for a limit
     * of how many — the game's answer, as the reference's {@code isEquivalentTo} is for build variations and reskins.
     * The same template and no other, where the game says nothing.
     */
    public final void countsAs(java.util.function.BiPredicate<String, String> rule) {
        this.countsAs = rule == null ? String::equals : rule;
    }

    /**
     * How many things with this link key a side may have at once in this match, whatever their templates say — a
     * game's superweapon option. A negative number lifts it.
     */
    public final void setCap(String linkKey, int most) {
        if (most < 0) {
            caps.remove(linkKey);
        } else {
            caps.put(linkKey, most);
        }
    }

    /** Give a side a word to hold — a science it chose — which {@link Prerequisites#requiredWords} read. */
    public final void grant(int playerIndex, String word) {
        var side = getRtsPlayer(playerIndex);
        if (side != null) {
            side.grant(word);
        }
    }

    /** Whether the side may make a thing of this name now; see {@link #canBuild(int, ThingTemplate)}. */
    public final boolean canBuild(int playerIndex, String templateName) {
        var template = findTemplate(templateName);
        return template != null && canBuild(playerIndex, template);
    }

    /**
     * Whether the side may make {@code template} now, as the reference's {@code Player::canBuild} decides: never if it
     * is buildable by nobody; always if it ignores what it needs; only by a computer where it says so; otherwise
     * every requirement met — one of its templates owned, finished and alive — every word held, and fewer standing,
     * going up and queued than its limit. A template that says none of it may be made by anyone.
     */
    public final boolean canBuild(int playerIndex, ThingTemplate template) {
        var side = getRtsPlayer(playerIndex);
        if (side == null) {
            return false;
        }
        if (!(template instanceof Prerequisites rules)) {
            return true;
        }
        switch (rules.buildability()) {
            case NO -> {
                return false;
            }
            case IGNORING_PREREQUISITES -> {
                return true;
            }
            case ONLY_BY_COMPUTER -> {
                if (!side.isComputer()) {
                    return false;
                }
            }
            case YES -> {
            }
        }
        for (var requirement : rules.prerequisites()) {
            if (!ownsFinished(playerIndex, Prerequisites.alternatives(requirement))) {
                return false;
            }
        }
        for (var word : rules.requiredWords()) {
            if (!side.holds(word)) {
                return false;
            }
        }
        return underItsLimit(playerIndex, template, rules);
    }

    /** Whether the side owns a finished, living thing counting as one of these. */
    private boolean ownsFinished(int playerIndex, java.util.List<String> anyOf) {
        for (var object : getObjects()) {
            if (object.getPlayerIndex() != playerIndex || object.isEffectivelyDead()
                    || object.hasStatus(uz.dukeengine.core.thing.ObjectStatus.UNDER_CONSTRUCTION)) {
                continue;
            }
            for (var wanted : anyOf) {
                if (countsAs.test(object.getTemplate().name(), wanted)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether one more would stay within its limit: the side's living things of the template, or of any template
     * sharing its link key — standing or going up — its builders' errands on their way to raise one, and what its
     * factories have queued.
     */
    private boolean underItsLimit(int playerIndex, ThingTemplate template, Prerequisites rules) {
        var key = rules.maxSimultaneousLinkKey();
        int most = key != null && caps.containsKey(key) ? caps.get(key) : rules.maxSimultaneous();
        if (most <= 0 && (key == null || !caps.containsKey(key))) {
            return true;
        }
        java.util.function.Predicate<ThingTemplate> counted = other -> countsAs.test(other.name(), template.name())
                || key != null && other instanceof Prerequisites them && key.equals(them.maxSimultaneousLinkKey());
        int count = 0;
        for (var object : getObjects()) {
            if (object.getPlayerIndex() != playerIndex || object.isEffectivelyDead()) {
                continue;
            }
            if (counted.test(object.getTemplate())) {
                count++;
            }
            for (var module : object.getModules()) {
                switch (module) {
                    case uz.dukeengine.rts.module.ProductionUpdate factory -> count += factory.countQueued(counted);
                    case uz.dukeengine.rts.construction.BuildOrder errand when errand.awaitsItsSite()
                            && counted.test(errand.template()) -> count++;
                    default -> {
                    }
                }
            }
        }
        return count < most;
    }

    /**
     * Purchase an upgrade for a player: charge its cost and apply its effect,
     * once. Returns false if already owned or unaffordable.
     */
    public final boolean purchaseUpgrade(int playerIndex, Upgrade upgrade) {
        var player = getRtsPlayer(playerIndex);
        if (player == null || player.hasUpgrade(upgrade.name())) {
            return false;
        }
        if (!player.withdraw(upgrade.cost())) {
            return false;
        }
        finishForTheSide(playerIndex, upgrade, null);
        return true;
    }

    // ---- upgrades researched ----

    private final java.util.Map<String, Upgrade> upgrades = new java.util.LinkedHashMap<>();

    /** The upgrades a building may research by name — {@code ProductionUpdate.Data.researches} links them. */
    public final void addUpgrades(java.util.Collection<Upgrade> more) {
        for (var upgrade : more) {
            upgrades.put(upgrade.name(), upgrade);
        }
    }

    /** The upgrade of this name, or {@code null}. */
    public final Upgrade findUpgrade(String name) {
        return name == null ? null : upgrades.get(name);
    }

    /** Whether the side has finished an upgrade of the side's by this name. */
    public final boolean hasUpgrade(int playerIndex, String name) {
        var player = getRtsPlayer(playerIndex);
        return player != null && player.hasUpgrade(name);
    }

    /** Whether any building of the side has this upgrade queued: what greys its button out everywhere. */
    public final boolean isUpgradeQueued(int playerIndex, String name) {
        for (var object : getObjects()) {
            if (object.getPlayerIndex() != playerIndex || object.isDestroyed()) {
                continue;
            }
            var production = object.findModule(uz.dukeengine.rts.module.ProductionUpdate.class);
            if (production != null && production.isResearching(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Research of {@code upgrade} is finished at {@code researcher}. The side's: recorded for the side with its
     * effects, as {@link #purchaseUpgrade} records one, and on every thing of it. Its own: a word on the
     * researcher alone. Either way the {@code UpgradeListener}s of what it reaches are told and an
     * {@code UpgradeCompleted} is posted.
     */
    public final void upgradeCompleted(uz.dukeengine.core.thing.GameObject researcher, Upgrade upgrade) {
        if (upgrade.scope() == Upgrade.Scope.PLAYER) {
            finishForTheSide(researcher.getPlayerIndex(), upgrade, researcher);
            return;
        }
        reach(researcher, upgrade.name());
        post(new uz.dukeengine.rts.event.UpgradeCompleted(getFrame(), researcher.getPlayerIndex(), upgrade.name(),
                researcher.getId(), false, researcher.getPosition()));
    }

    private void finishForTheSide(int playerIndex, Upgrade upgrade, uz.dukeengine.core.thing.GameObject researcher) {
        var player = getRtsPlayer(playerIndex);
        if (player == null || player.hasUpgrade(upgrade.name())) {
            return;
        }
        player.addUpgrade(upgrade.name());
        // Name order, so every machine compounds the same bonuses in the same
        // sequence — the effects map is sorted for exactly this reason.
        for (var effect : upgrade.effects().entrySet()) {
            player.multiplyBonus(effect.getKey(), effect.getValue());
        }
        for (var object : getObjects()) {
            if (object.getPlayerIndex() == playerIndex && !object.isDestroyed()) {
                reach(object, upgrade.name());
            }
        }
        post(new uz.dukeengine.rts.event.UpgradeCompleted(getFrame(), playerIndex, upgrade.name(),
                researcher == null ? null : researcher.getId(), true,
                researcher == null ? null : researcher.getPosition()));
    }

    /**
     * Tell the {@link uz.dukeengine.rts.module.OrderListener}s of each of {@code player}'s units named that it was
     * given {@code order} — after the engine's own handling of it, in the order named.
     */
    protected final void tellOrder(GameMessage order, java.util.List<uz.dukeengine.core.thing.ObjectId> units,
            int player) {
        for (var id : units) {
            var unit = findObject(id);
            if (unit == null || unit.getPlayerIndex() != player) {
                continue; // gone, or not the issuer's to command
            }
            for (var module : java.util.List.copyOf(unit.getModules())) {
                if (module instanceof uz.dukeengine.rts.module.OrderListener listener) {
                    listener.onOrder(order);
                }
            }
        }
    }

    /** An upgrade reaches a thing: its word holds for it, and its listeners are told. */
    private static void reach(uz.dukeengine.core.thing.GameObject thing, String upgrade) {
        thing.setCondition(upgrade);
        for (var module : thing.getModules()) {
            if (module instanceof uz.dukeengine.rts.module.UpgradeListener listener) {
                listener.onUpgrade(upgrade);
            }
        }
    }

    /**
     * A thing made for a side is made with the side's upgrades: their words, and its listeners told, in name
     * order — as the reference runs a new object's upgrade modules.
     */
    @Override
    protected void onSpawned(uz.dukeengine.core.thing.GameObject thing) {
        var player = getRtsPlayer(thing.getPlayerIndex());
        if (player != null) {
            for (var upgrade : player.getUpgrades()) {
                reach(thing, upgrade);
            }
        }
    }
}
