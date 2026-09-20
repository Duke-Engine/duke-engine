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
