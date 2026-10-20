package uz.dukeengine.game;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import uz.dukeengine.combat.module.WeaponUpdate;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.view.AimAnswer;

/**
 * What the window's pointer is on and what an armed button is aimed at, and the game's rules for what a click there
 * would order. All of it is asked on the simulation thread as a snapshot is built; the window only writes the pointer.
 */
final class Pointer {

    /** What the window last reported of an armed button: which, where, and facing which way. */
    private record Aim(String button, Coord3D place, float facing) {
    }

    /**
     * What the window's pointer is on: a thing, or -1 and the point of the ground under it (null for none), and
     * whether the local player has ever seen that ground.
     */
    private record Pointed(int unit, Coord3D ground, boolean seen) {
    }

    private final DukeGame game;
    private final Roster roster;
    private final Selection selection;

    private volatile Aim aim;
    private DukeGame.AimAnswering aimAnswering;
    private volatile Pointed pointed = new Pointed(-1, null, true);
    private DukeGame.ContextOrder contextOrder;
    private DukeGame.SeenGroundOrder groundOrder;
    private final List<Runnable> eachSnapshot = new CopyOnWriteArrayList<>();

    Pointer(DukeGame game, Roster roster, Selection selection) {
        this.game = game;
        this.roster = roster;
        this.selection = selection;
    }

    void aimAnswering(DukeGame.AimAnswering answering) {
        this.aimAnswering = answering;
    }

    void setAim(String buttonId, Coord3D place, float facing) {
        this.aim = buttonId == null || place == null ? null : new Aim(buttonId, place, facing);
    }

    AimAnswer aimAnswerNow() {
        var now = aim;
        return now == null || aimAnswering == null ? AimAnswer.YES
                : aimAnswering.answer(now.button(), now.place(), now.facing());
    }

    void setPointedAt(int unitId, Coord3D ground, boolean seen) {
        this.pointed = new Pointed(unitId, unitId >= 0 ? null : ground, seen);
    }

    int pointedAt() {
        return pointed.unit();
    }

    void contextOrder(DukeGame.ContextOrder rule) {
        this.contextOrder = rule;
    }

    /** A rule asked beside the ones already given, after them: the first to name a word is the word. */
    void addContextOrder(DukeGame.ContextOrder rule) {
        var before = contextOrder;
        this.contextOrder = before == null ? rule : (selected, target) -> {
            var word = before.orderOn(selected, target);
            return word != null ? word : rule.orderOn(selected, target);
        };
    }

    void groundOrder(DukeGame.SeenGroundOrder rule) {
        this.groundOrder = rule;
    }

    boolean namesGroundOrders() {
        return groundOrder != null;
    }

    void eachSnapshot(Runnable work) {
        eachSnapshot.add(work);
    }

    /** The word of the order a click where the pointer is would give — with each snapshot, as the pointer's word is. */
    String snapshotWord() {
        eachSnapshot.forEach(Runnable::run);
        var world = game.getLogic();
        if (world == null) {
            return null;
        }
        var now = pointed;
        if (now.unit() >= 0) {
            var target = world.findObject(new ObjectId(now.unit()));
            return target == null ? null : contextWord(world, target);
        }
        return now.ground() == null ? null : groundWord(world, now.ground(), now.seen());
    }

    /** The local player's own things his orders go to, selected, in order. */
    List<GameObject> orderedOwn() {
        var world = game.getLogic();
        return world == null ? List.of() : selectedOwn(world, null);
    }

    String contextWordOn(GameObject thing) {
        var world = game.getLogic();
        return world == null ? null : contextWord(world, thing);
    }

    String groundWordAt(Coord3D place) {
        var world = game.getLogic();
        return world == null ? null : groundWord(world, place, true);
    }

    private String contextWord(GameLogic world, GameObject target) {
        var rule = contextOrder;
        if (rule == null) {
            return null;
        }
        var selected = selectedOwn(world, target);
        return selected.isEmpty() ? null : rule.orderOn(selected, target);
    }

    /** {@code seenIfUnknown}: whether the point counts as seen where the simulation keeps no cells of its own. */
    private String groundWord(GameLogic world, Coord3D place, boolean seenIfUnknown) {
        var rule = groundOrder;
        if (rule == null) {
            return null;
        }
        var selected = selectedOwn(world, null);
        if (selected.isEmpty()) {
            return null;
        }
        var cells = world.getSightCells(); // the simulation's cells, where it keeps them, over the client's word
        return rule.orderAt(selected, place, cells == null ? seenIfUnknown : cells.everSeen(roster.localIndex(), place));
    }

    /**
     * Whether an attack on what the pointer is on would be taken: refused only where the selection holds something
     * armed of the local player's and not one of its weapons may be fired at it. Nothing under the pointer, or nothing
     * armed selected, is no refusal.
     */
    boolean attackableNow() {
        int at = pointed.unit();
        var world = game.getLogic();
        var victim = at < 0 || world == null ? null : world.findObject(new ObjectId(at));
        if (victim == null) {
            return true;
        }
        boolean armed = false;
        for (var unit : ownOf(world, selection.orderedTo(), null)) {
            var weapon = unit.findModule(WeaponUpdate.class);
            if (weapon == null) {
                continue;
            }
            armed = true;
            if (weapon.canFireAt(victim)) {
                return true;
            }
        }
        return !armed;
    }

    /** The local player's own things his orders go to, but {@code except}. */
    private List<GameObject> selectedOwn(GameLogic world, GameObject except) {
        return ownOf(world, selection.orderedTo(), except);
    }

    private List<GameObject> ownOf(GameLogic world, List<Integer> ids, GameObject except) {
        var own = new ArrayList<GameObject>();
        int local = roster.localIndex();
        for (var id : ids) {
            var unit = world.findObject(new ObjectId(id));
            if (unit != null && unit != except && unit.getPlayerIndex() == local) {
                own.add(unit);
            }
        }
        return List.copyOf(own);
    }
}
