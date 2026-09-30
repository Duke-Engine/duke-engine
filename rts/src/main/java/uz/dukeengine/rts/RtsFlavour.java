package uz.dukeengine.rts;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import uz.dukeengine.combat.message.CombatOrder;
import uz.dukeengine.combat.message.GameOrder;
import uz.dukeengine.core.Flavour;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.message.Command;
import uz.dukeengine.core.network.PacketCodec;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ThingTemplateLoader;
import uz.dukeengine.core.thing.Titled;
import uz.dukeengine.core.view.ViewParts;
import uz.dukeengine.rts.message.GameMessage;
import uz.dukeengine.rts.module.ProductionUpdate;
import uz.dukeengine.rts.network.CommandCodec;
import uz.dukeengine.rts.player.RtsPlayer;
import uz.dukeengine.rts.player.Upgrade;

/**
 * The RTS as a kind of game the runtime runs ({@link Flavour}): its world, {@link RtsLogic} and the RTS's command set;
 * its templates, {@code Object} blocks read as {@link RtsTemplate}; its wire, {@link CommandCodec}; its parts of the
 * picture — a side's money and power, rally points, what a factory makes, what a hold carries, how far a site is built
 * and how turrets stand — and the RTS's own API beside the runtime's: what a factory makes and a builder finishes, what
 * research and selling set off, a side's upgrades and starting money, a factory's build menu.
 *
 * <p>Found on the classpath as a service, so a game on the RTS library runs on it without naming it; a game that wants
 * its API makes one and hands it to the runtime, or asks the runtime for the one it runs on. One a match.
 */
public final class RtsFlavour implements Flavour {

    private final List<BiConsumer<GameObject, GameObject>> produced = new ArrayList<>();
    private final List<BiConsumer<GameObject, GameObject>> constructed = new ArrayList<>();
    private final List<Consumer<GameObject>> placed = new ArrayList<>();
    private final List<BiConsumer<GameObject, Upgrade>> researched = new ArrayList<>();
    private final List<Consumer<GameObject>> sold = new ArrayList<>();
    private final List<Upgrade> upgrades = new ArrayList<>();
    private final List<Money> money = new ArrayList<>();
    private RtsLogic logic;

    private record Money(IntSupplier player, int amount) {
    }

    /**
     * Runs on the simulation thread whenever a factory releases a unit, as {@code (factory, unit)} — the frame it
     * happens, in the order registered. See {@link RtsSimulation#onProduced}.
     */
    public RtsFlavour onProduced(BiConsumer<GameObject, GameObject> callback) {
        produced.add(callback);
        if (logic != null) {
            logic.onProduced(callback);
        }
        return this;
    }

    /** Runs on the simulation thread whenever a building is finished, as {@code (builder, building)}. */
    public RtsFlavour onConstructed(BiConsumer<GameObject, GameObject> callback) {
        constructed.add(callback);
        if (logic != null) {
            logic.onConstructed(callback);
        }
        return this;
    }

    /**
     * Runs on the simulation thread whenever a building is put down — a site, the frame it is placed — with it, for the
     * game to clear what it stands over. See {@link RtsSimulation#onPlaced}.
     */
    public RtsFlavour onPlaced(Consumer<GameObject> callback) {
        placed.add(callback);
        if (logic != null) {
            logic.onPlaced(callback);
        }
        return this;
    }

    /**
     * Runs on the simulation thread whenever research finishes at a building or a unit, as {@code (researcher,
     * upgrade)} — the frame it finishes, after the upgrade has taken effect, in the order registered; not for an
     * upgrade bought at once, nor research called off. See {@link RtsSimulation#onResearched}.
     */
    public RtsFlavour onResearched(BiConsumer<GameObject, Upgrade> callback) {
        researched.add(callback);
        if (logic != null) {
            logic.onResearched(callback);
        }
        return this;
    }

    /**
     * Told when a building its side sold is down and gone — taken down for its worth, not destroyed by an enemy — on
     * the simulation thread, the frame it goes, its refund paid.
     */
    public RtsFlavour onSold(Consumer<GameObject> callback) {
        sold.add(callback);
        if (logic != null) {
            logic.onSold(callback);
        }
        return this;
    }

    /** The game's upgrades — what research gives, by name. */
    public RtsFlavour addUpgrades(Collection<Upgrade> more) {
        upgrades.addAll(more);
        if (logic != null) {
            logic.addUpgrades(more);
        }
        return this;
    }

    /**
     * Give a side starting money as the match begins — in its balance, neither earned nor spent. {@code player} is
     * asked then, as a side is known by its index only once the match has its players: {@code me::getIndex}.
     */
    public RtsFlavour money(IntSupplier player, int amount) {
        money.add(new Money(player, amount));
        return this;
    }

    /** The RTS world this flavour runs, once a match has made it. */
    public RtsSimulation logic() {
        if (logic == null) {
            throw new IllegalStateException("no match has begun on this flavour");
        }
        return logic;
    }

    /** One entry of a factory's build menu: what it makes, as it is called, and what {@code player} pays for it. */
    public record BuildOption(String templateName, String displayName, int cost) {
    }

    /**
     * The build menu of a production structure's template, priced for {@code player} — a side's own prices, or the
     * template's cost for a player with no side. For UIs; the templates are fixed once loaded.
     */
    public List<BuildOption> getBuildOptions(String factoryTemplateName, int player) {
        var world = logic();
        var factory = world.getThingFactory().findTemplate(factoryTemplateName);
        if (factory == null) {
            return List.of();
        }
        for (var entry : factory.modules()) {
            if (entry instanceof ProductionUpdate.Data data) {
                var options = new ArrayList<BuildOption>();
                var side = world.getRtsPlayer(player);
                for (var name : data.builds()) {
                    var unit = world.getThingFactory().findTemplate(name);
                    if (unit != null) {
                        options.add(new BuildOption(name, Titled.of(unit),
                                side == null ? Buildable.costOf(unit) : side.priceOf(unit)));
                    }
                }
                return options;
            }
        }
        return List.of();
    }

    @Override
    public GameLogic newWorld() {
        logic = new RtsLogic();
        produced.forEach(logic::onProduced);
        constructed.forEach(logic::onConstructed);
        placed.forEach(logic::onPlaced);
        researched.forEach(logic::onResearched);
        sold.forEach(logic::onSold);
        logic.addUpgrades(upgrades);
        return logic;
    }

    @Override
    public void templates(ThingTemplateLoader loader) {
        RtsTemplate.register(loader);
    }

    @Override
    public PacketCodec codec() {
        return CommandCodec.INSTANCE;
    }

    @Override
    public boolean carries(Command command) {
        return command instanceof GameMessage || command instanceof CombatOrder || command instanceof GameOrder;
    }

    @Override
    public void began(GameLogic world) {
        for (var gift : money) {
            var side = RtsPlayer.of(world, gift.player().getAsInt());
            if (side != null) {
                side.give(gift.amount());
            }
        }
    }

    @Override
    public ViewParts views() {
        return RtsViewParts.INSTANCE;
    }

    /**
     * A small, balanced starter faction so a first game needs no data files:
     * a power plant, a barracks that builds riflemen, a rifleman and a tank.
     */
    public static final String STARTER_UNITS = """
            Object
              Name = PowerPlant
              DisplayName = Power Plant
              KindOf = [STRUCTURE, SELECTABLE, POWERED]
              Geometry = Box
                MajorRadius = 18
                MinorRadius = 14
                Height = 16
              End
              BuildCost = 600
              BuildTime = 4.0
              VisionRange = 30
              Modules = [
                ActiveBody
                  MaxHealth = 400
                End,
                PowerModule
                  Produces = 10
                End
              ]
            End
            Object
              Name = Barracks
              DisplayName = Barracks
              KindOf = [STRUCTURE, SELECTABLE]
              Geometry = Box
                MajorRadius = 20
                MinorRadius = 16
                Height = 14
              End
              BuildCost = 500
              BuildTime = 5.0
              VisionRange = 35
              Modules = [
                ActiveBody
                  MaxHealth = 600
                End,
                ProductionUpdate
                  Builds = [Rifleman, Tank]
                End,
                PowerModule
                  Consumes = 3
                End,
                ; Stall the line when the base outgrows its plants. Asked for here
                ; rather than assumed by the engine — a game with no notion of
                ; capacity simply leaves this off.
                CapacityGate
                End
              ]
            End
            Object
              Name = Rifleman
              DisplayName = Rifleman
              KindOf = [INFANTRY, SELECTABLE, CAN_ATTACK]
              Geometry = Cylinder
                Radius = 3
                Height = 9
              End
              BuildCost = 120
              BuildTime = 1.5
              VisionRange = 40
              Modules = [
                ActiveBody
                  MaxHealth = 80
                End,
                MoveUpdate
                  Speed = 14
                End,
                WeaponUpdate
                  Damage = 9
                  AttackRange = 22
                  ReloadFrames = 12
                End,
                ExperienceModule
                  ExperienceValue = 30
                  ExperienceRequired = [60, 180, 360]
                  LevelDamageBonus = [1.1, 1.2, 1.3]
                  HealOnPromotion = Yes
                End
              ]
            End
            Object
              Name = Tank
              DisplayName = Battle Tank
              KindOf = [VEHICLE, SELECTABLE, CAN_ATTACK]
              Geometry = Box
                MajorRadius = 8
                MinorRadius = 5
                Height = 6
              End
              BuildCost = 700
              BuildTime = 6.0
              VisionRange = 45
              Modules = [
                ActiveBody
                  MaxHealth = 300
                End,
                MoveUpdate
                  Speed = 20
                  TurnRate = 120
                End,
                WeaponUpdate
                  Damage = 40
                  AttackRange = 30
                  ReloadFrames = 45
                  SplashRadius = 6
                  DamageType = EXPLOSION
                End,
                ExperienceModule
                  ExperienceValue = 100
                  ExperienceRequired = [200, 500, 1000]
                  LevelDamageBonus = [1.1, 1.2, 1.3]
                  HealOnPromotion = Yes
                End
              ]
            End
            """;
}
