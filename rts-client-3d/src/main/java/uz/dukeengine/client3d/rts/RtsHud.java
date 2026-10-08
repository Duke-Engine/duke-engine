package uz.dukeengine.client3d.rts;

import uz.dukeengine.client3d.HudPart;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.message.Command;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.view.UnitView;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.rts.RtsFlavour;
import uz.dukeengine.rts.message.GameMessage;

/**
 * The RTS's part of the window: a building's rally point set by a click on open ground, and its build menu written
 * along the top and queued from with the digit keys — the RTS's own orders, which the client has no words for.
 */
public final class RtsHud implements HudPart {

    @Override
    public Command rally(int localPlayer, UnitView building, Coord3D place) {
        return new GameMessage.SetRallyPoint(localPlayer, new ObjectId(building.id()), place);
    }

    @Override
    public String buildMenu(DukeGame game, UnitView building) {
        var text = new StringBuilder("BUILD (press number; right-click sets rally):\n");
        var options = optionsOf(game, building);
        for (int i = 0; i < options.size() && i < 9; i++) {
            var option = options.get(i);
            text.append("  [").append(i + 1).append("] ").append(option.displayName())
                    .append("  $").append(option.cost()).append('\n');
        }
        if (options.isEmpty()) {
            text.append("  (nothing — set Builds in this structure's Produce capability)\n");
        }
        return text.append("  queue: ").append(building.productionQueue()).toString();
    }

    @Override
    public Command build(DukeGame game, UnitView building, int index) {
        var options = optionsOf(game, building);
        return index < 0 || index >= options.size() ? null : new GameMessage.QueueProduction(
                game.getLocalPlayerIndex(), new ObjectId(building.id()), options.get(index).templateName());
    }

    /** A factory's build menu as the RTS prices it for this player; none in a game of another kind. */
    private static java.util.List<RtsFlavour.BuildOption> optionsOf(DukeGame game, UnitView building) {
        return game.flavour() instanceof RtsFlavour rts
                ? rts.getBuildOptions(building.templateName(), game.getLocalPlayerIndex()) : java.util.List.of();
    }
}
