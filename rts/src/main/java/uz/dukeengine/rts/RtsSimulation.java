package uz.dukeengine.rts;

import java.util.logging.Logger;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.message.Command;
import uz.dukeengine.core.player.PlayerList;
import uz.dukeengine.core.thing.ThingFactory;
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

    /** Apply a {@code Construct} order; see {@link uz.dukeengine.rts.construction.Construction#order}. */
    protected final boolean construct(GameMessage.Construct order) {
        return uz.dukeengine.rts.construction.Construction.order(this, order, placementRules);
    }

    /** Apply a {@code CancelConstruction} order. */
    protected final boolean cancelConstruction(GameMessage.CancelConstruction order) {
        return uz.dukeengine.rts.construction.Construction.cancel(this, order);
    }

    /**
     * Whether {@code template} may stand at {@code place}, as an order to build it there would be judged —
     * for a client to show, never to decide.
     */
    public final uz.dukeengine.rts.construction.Placement.Fit fits(String template,
            uz.dukeengine.core.math.Coord3D place, float facing) {
        var thing = findTemplate(template);
        if (thing == null || getPathGrid() == null) {
            return uz.dukeengine.rts.construction.Placement.Fit.OFF_THE_MAP;
        }
        return uz.dukeengine.rts.construction.Placement.check(this, getPathGrid(), thing, place, facing,
                placementRules);
    }

    /** The RTS player at {@code index}, or {@code null} if there is none. */
    public final RtsPlayer getRtsPlayer(int index) {
        return getPlayerList().getPlayer(index) instanceof RtsPlayer player ? player : null;
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
        player.addUpgrade(upgrade.name());
        // Name order, so every machine compounds the same bonuses in the same
        // sequence — the effects map is sorted for exactly this reason.
        for (var effect : upgrade.effects().entrySet()) {
            player.multiplyBonus(effect.getKey(), effect.getValue());
        }
        return true;
    }
}
