package uz.duke.rts;

import java.util.logging.Logger;
import uz.duke.core.GameLogic;
import uz.duke.core.message.Command;
import uz.duke.core.player.PlayerList;
import uz.duke.core.thing.ThingFactory;
import uz.duke.rts.message.GameMessage;
import uz.duke.rts.module.RtsModules;
import uz.duke.rts.player.RtsPlayer;
import uz.duke.rts.player.Upgrade;

/**
 * A {@link GameLogic} that speaks the RTS command set — the base every RTS
 * simulation extends.
 *
 * <p>The engine hands commands back as the genre-neutral {@link Command}; this
 * narrows them to {@link GameMessage} once, here, so subclasses get an
 * exhaustive {@code switch} over a sealed hierarchy instead of repeating the
 * cast. A command that is not an RTS command is logged rather than dropped
 * silently — it means something is feeding the wrong game's input into this
 * simulation.
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
        LOG.warning(() -> "ignoring non-RTS command: " + command.getClass().getName());
    }

    /** Apply one RTS command. Implementations switch over the sealed hierarchy. */
    protected abstract void onRtsCommand(GameMessage command);

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
        player.multiplyWeaponDamageBonus(upgrade.weaponDamageMultiplier());
        return true;
    }
}
