package uz.dukeengine.client3d;

import com.jme3.input.InputManager;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

import uz.dukeengine.combat.message.CombatOrder;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.view.UnitView;
import uz.dukeengine.core.view.WorldSnapshot;
import uz.dukeengine.game.DukeGame;

/**
 * What the player has selected and what he orders it to do: a click or a box choosing, a click on a thing or the
 * ground ordering, each order answered — marked where it was given, the thing flashed, a voice — and the game kept
 * told who is selected and who his orders go to.
 */
final class Orders {

    private final Supplier<DukeGame> game;
    private final Supplier<WorldSnapshot> snapshot;
    /** What is selected, in the order it was selected: the order the game is told it in. */
    private final Set<Integer> selected;
    private final Visuals visuals;
    private final Hotkeys hotkeys;
    /** The client's own controls on the game's keys — see {@link KeyMap} and {@link Controls}. */
    private final Controls controls;
    private final List<HudPart> parts;
    /** Every noise the game makes, once there are any. */
    private final Supplier<GameSounds> noises;
    /** Which button selects and which commands — see {@link Duke3D#mouse}. */
    private final Supplier<Mouse> mouse;
    private final WorldPointer pointer;
    private final UnitScene unitScene;
    /** Whether the minimap takes a press at a point of the window, which is then no selection. */
    private final Predicate<Vector2f> minimapTakes;
    /**
     * Brief flashes acknowledging orders.
     */
    private final OrderMarkers orderMarkers;
    /** Orders answered with the game's own model, where it names one — see {@link OrderMark#model}. */
    private final ModelMarks modelMarks;
    private final RallyMarks rallyMarks;
    /**
     * The box the player is dragging, drawn over the world while the button is down.
     */
    private final DragBox dragRectangle;
    private final Camera cam;
    private final InputManager inputManager;
    private final com.jme3.system.Timer timer;
    /** What is on the screen now. */
    private final Supplier<Screen> screen;
    private final WorldMoments.Floor floor;
    /** The selection kept in step with the game's, both ways — see {@link SelectionLink}. */
    private final SelectionLink selectionLink = new SelectionLink();
    /**
     * Where the left button went down, or {@code null} when it is not down.
     */
    private Vector2f dragFrom;
    /** Whether a new world is still waiting for its hero to be picked out. */
    private boolean findHimInTheNewWorld;
    /** The unit the game was last told about, so it is only told when it changes. */
    private int watching = -1;
    /** Who his orders went to when the game was last told, so it is told only when that changes. */
    private List<Integer> orderedTold = List.of();

    Orders(Supplier<DukeGame> game, Supplier<WorldSnapshot> snapshot, Set<Integer> selected, Visuals visuals,
            Hotkeys hotkeys, Controls controls, List<HudPart> parts, Supplier<GameSounds> noises, Supplier<Mouse> mouse,
            WorldPointer pointer, UnitScene unitScene, Predicate<Vector2f> minimapTakes, OrderMarkers orderMarkers,
            ModelMarks modelMarks, RallyMarks rallyMarks, DragBox dragRectangle, Camera cam, InputManager inputManager,
            com.jme3.system.Timer timer, Supplier<Screen> screen, WorldMoments.Floor floor) {
        this.game = game;
        this.snapshot = snapshot;
        this.selected = selected;
        this.visuals = visuals;
        this.hotkeys = hotkeys;
        this.controls = controls;
        this.parts = parts;
        this.noises = noises;
        this.mouse = mouse;
        this.pointer = pointer;
        this.unitScene = unitScene;
        this.minimapTakes = minimapTakes;
        this.orderMarkers = orderMarkers;
        this.modelMarks = modelMarks;
        this.rallyMarks = rallyMarks;
        this.dragRectangle = dragRectangle;
        this.cam = cam;
        this.inputManager = inputManager;
        this.timer = timer;
        this.screen = screen;
        this.floor = floor;
    }

    /** A new world: the hero he had selected is not this world's, and this world's is picked out once it comes. */
    void newWorld() {
        selected.clear();
        findHimInTheNewWorld = true;
    }

    /** Whether the left button is down on the world, a box perhaps being dragged. */
    boolean dragging() {
        return dragFrom != null;
    }

    /** A selection answered as a click's is: with the select voice of {@code unit}, a thing it took in. */
    void answerSelection(int unit) {
        var view = viewOf(unit);
        if (view != null) {
            noises.get().selected(view, game.get().getLocalPlayerIndex(), timer.getTimeInSeconds());
        }
    }

    /**
     * Click a unit to select it — anyone's, not only the player's own.
     *
     * <p>Clicking something that is not his is <em>inspecting</em> it: the panel
     * describes whatever is selected, and being able to read a monster's health
     * and what it hits for is most of what makes a dungeon's bar worth looking at.
     * Nothing can be ordered with it — see {@link #selectedIds} — so an enemy in
     * the selection is a thing being looked at rather than a thing being
     * commanded.
     *
     * <p>Which is also why it never joins a group: shift-clicking a skeleton onto
     * a selection of his own units would make "what is selected" mean two
     * different things at once, and every order after it would have to decide
     * which half it applied to. One enemy on its own, or his own units.
     */
    void select(boolean add) {
        var hit = pointer.pickUnit();
        boolean mine = hit != null && hit.view.playerIndex() == game.get().getLocalPlayerIndex();
        var clicked = SelectionBox.click(hit == null ? null
                : new SelectionBox.Candidate(hit.view.id(), 0f, 0f, mine, hit.view.selectable()),
                List.copyOf(selected), add);
        selected.clear();
        selected.addAll(clicked.selection());
        controls.forgetGroupPress();
        if (clicked.tookIn() >= 0) {
            if (mine) {
                noises.get().moment("vo.select", timer.getTimeInSeconds());
            }
            noises.get().selected(hit.view, game.get().getLocalPlayerIndex(), timer.getTimeInSeconds());
        }
    }

    /**
     * Select his own unit once, when a world he has just arrived in produces one.
     *
     * <p>The other half of "a skill needs its caster selected" — see
     * pressing a skill's key. On its own that rule is a trap rather than a rule: a
     * run begins with nothing selected and every new floor hands him a <em>new</em>
     * hero whose id the old selection does not name, so his keys would quietly
     * stop working until he remembered to click himself, on every floor, with no
     * hint that clicking was what was wanted.
     *
     * <p><b>Once per world, and only then.</b> Anything more would be a client
     * that refuses to be told: pressing escape to clear the selection is
     * deliberate, and so is clicking a skeleton to read its card — the panel's
     * empty state and its creature card both exist because the player asked for
     * them, and a frame later this would have taken both away.
     */
    void keepHisOwnSelected() {
        if (!findHimInTheNewWorld || screen.get() != Screen.PLAYING) {
            return;
        }
        for (var view : snapshot.get().units()) {
            if (view.playerIndex() == game.get().getLocalPlayerIndex() && view.selectable()) {
                selected.clear();
                selected.add(view.id());
                findHimInTheNewWorld = false;
                return;
            }
        }
    }


    /**
     * Say which single unit the player has picked out, when that changes.
     *
     * <p>Selection belongs here and the simulation has none — but what a creature
     * is worth does not belong here and the simulation is the only thing that
     * knows it. A monster's damage is its template times what this floor
     * multiplies by, and this side has never seen a template; reading one across
     * the thread that is mutating it would be worse than not knowing.
     *
     * <p>So the fact travels the way every other fact from this side does: as a
     * command, on a frame boundary. Sent on change rather than every frame,
     * because forty a second of "still that one" is a command stream nobody can
     * read and a replay nobody can search.
     *
     * <p>One unit only. A panel that describes a creature cannot describe nine,
     * and {@code -1} — nothing, or a whole box of them — puts it back to
     * describing the player's own.
     */
    void tellTheGameWhatHeIsLookingAt() {
        if (!hotkeys.watches()) {
            return;
        }
        int single = selected.size() == 1 ? selected.iterator().next() : -1;
        if (single != watching) {
            watching = single;
            hotkeys.watch(game.get(), single);
        }
    }

    void beginDrag() {
        if (minimapTakes.test(inputManager.getCursorPosition())) {
            return; // the minimap took the press; not a selection
        }
        var cursor = inputManager.getCursorPosition();
        dragFrom = new Vector2f(cursor.x, cursor.y);
    }

    /**
     * Finish a press: a box if the mouse travelled, the old single-unit pick if it
     * did not.
     *
     * <p>Falling back to the click matters — a click is a drag of zero pixels and a
     * real hand never quite manages zero, so without the distinction every click
     * would become a tiny empty box that selects nothing.
     */
    void endDrag(boolean add) {
        var from = dragFrom;
        dragFrom = null;
        dragRectangle.hide();
        if (from == null) {
            return; // the press was taken by the minimap or a menu
        }
        var cursor = inputManager.getCursorPosition();
        if (!isBox(visuals, from.x, from.y, cursor.x, cursor.y)) {
            var hit = pointer.pickUnit();
            boolean onOwn = hit != null && hit.view.selectable()
                    && hit.view.playerIndex() == game.get().getLocalPlayerIndex();
            // The force-attack key held: a click on his own thing is an attack on it, not a pick.
            boolean picks = onOwn && !controls.isHeld(KeyMap.Control.FORCE_ATTACK);
            if (leftClickOrders(mouse.get(), picks, snapshot.get().contextOrder() != null, add, !selectedIds().isEmpty())) {
                order(); // the reference's left-click mouse: a click off his own things commands them
                return;
            }
            select(add);
            return;
        }
        var before = List.copyOf(selected);
        boolean hisUnits = snapshot.get().units().stream().filter(view -> selected.contains(view.id()))
                .allMatch(view -> view.playerIndex() == game.get().getLocalPlayerIndex() && !view.structure());
        var after = SelectionBox.after(SelectionBox.inside(from.x, from.y, cursor.x, cursor.y, onScreenUnits()),
                before, hisUnits, add);
        selected.clear();
        selected.addAll(after);
        controls.forgetGroupPress();
        var first = after.stream().filter(id -> !before.contains(id)).findFirst();
        // One answer for the box, as for a click: the first thing it took in.
        first.ifPresent(id -> noises.get().selected(viewOf(id), game.get().getLocalPlayerIndex(), timer.getTimeInSeconds()));
    }

    /**
     * Whether a left click, not a drag, orders what is selected rather than choosing what it landed on: under the
     * left-click mouse, a click off the player's own things, or on one of them a click gives the game's word on — a
     * transport to board — unless the add-to-selection key is held, which always chooses, adding the thing, as the
     * reference's shift-click does whatever the context command ({@code isInPreferSelectionMode}).
     */
    static boolean leftClickOrders(Mouse mouse, boolean onOwnSelectable, boolean worded, boolean add,
            boolean ownSelected) {
        return mouse.leftClickOrders(onOwnSelectable && (add || !worded), ownSelected);
    }

    /** The order a click on the ground gives where the game has a word for it ({@code DukeGame.groundOrder}), or null. */
    static uz.dukeengine.combat.message.GameOrder groundOrder(int local, List<ObjectId> units, String word, Vector3f ground) {
        return word == null ? null
                : new uz.dukeengine.combat.message.GameOrder(local, word, units, new Coord3D(ground.x, ground.z, ground.y), null, 0);
    }

    /** What the last snapshot says of one thing, or null where it said nothing. */
    uz.dukeengine.core.view.UnitView viewOf(int id) {
        for (var view : snapshot.get().units()) {
            if (view.id() == id) {
                return view;
            }
        }
        return null;
    }

    /** A click that gave the game's word answered as the game names: its moment, none, or a move's. */
    void answerWord(String word, List<ObjectId> units) {
        var answer = visuals.orderAnswerFor(word, null);
        if (answer != null) {
            answerOrder(answer, units);
        }
    }

    /** An order given on {@code thing}, answered: its mark laid there, the thing flashed, the selection's voice. */
    void answer(ThingAnswer answer, uz.dukeengine.core.view.UnitView thing, List<ObjectId> units) {
        if (answer.mark() != null) {
            markOrder(thing.x(), thing.y(), thing.id(), answer.mark());
        }
        if (answer.flash()) {
            unitScene.flash(thing.id(), visuals.getSelectionFlash() != null
                    ? visuals.getSelectionFlash() : Visuals.SelectionFlashLook.REFERENCE);
        }
        if (answer.voice() != null) {
            answerOrder(answer.voice(), units);
        }
    }

    /** An order the player gave the selection: {@code ordered.<order>.<template>} for the first of it. */
    void answerOrder(String order, List<ObjectId> units) {
        if (!units.isEmpty()) {
            noises.get().ordered(order, viewOf(units.getFirst().value()), game.get().getLocalPlayerIndex(),
                    timer.getTimeInSeconds());
        }
    }

    /**
     * Every unit in the snapshot, projected to where it is drawn on screen.
     */
    private List<SelectionBox.Candidate> onScreenUnits() {
        int local = game.get().getLocalPlayerIndex();
        var candidates = new java.util.ArrayList<SelectionBox.Candidate>();
        for (var view : snapshot.get().units()) {
            var node = unitScene.node(view.id());
            var screen = cam.getScreenCoordinates(drawnCentre(node == null ? null : node.root.getWorldBound(),
                    view.x(), view.y(), floor.at(view.x(), view.y())));
            candidates.add(new SelectionBox.Candidate(view.id(), screen.x, screen.y,
                    view.playerIndex() == local, view.selectable(), view.structure()));
        }
        return candidates;
    }

    /**
     * Where a box tests a thing: the middle of its drawn body, as a click picks it by the body — or, with nothing drawn
     * of it, the ground under it, not the foot of the world at height 0.
     */
    static Vector3f drawnCentre(com.jme3.bounding.BoundingVolume drawn, float x, float y, float floor) {
        return drawn != null && drawn.getCenter() != null ? drawn.getCenter().clone() : new Vector3f(x, floor, y);
    }

    /**
     * Whether a press from one point let go at the other is a box: never where the game says a drag selects nothing
     * ({@link Visuals#dragSelects}).
     */
    static boolean isBox(Visuals visuals, float fromX, float fromY, float toX, float toY) {
        return visuals.isDragSelecting() && SelectionBox.isDrag(fromX, fromY, toX, toY, visuals.getDragDistance());
    }

    /**
     * Redraw the box while the button is held, as an outline over the world.
     */
    void syncDragRectangle() {
        if (dragFrom == null) {
            return;
        }
        var cursor = inputManager.getCursorPosition();
        if (!isBox(visuals, dragFrom.x, dragFrom.y, cursor.x, cursor.y)) {
            dragRectangle.hide();
            return;
        }
        dragRectangle.lay(dragFrom.x, dragFrom.y, cursor.x, cursor.y);
    }

    void order() {
        var units = selectedIds();
        if (units.isEmpty()) {
            return;
        }
        int local = game.get().getLocalPlayerIndex();
        var enemy = pointer.pickUnit();
        if (enemy != null && snapshot.get().contextOrder() != null) {
            // The order the game said a click on this thing gives — see DukeGame.contextOrder.
            game.get().postCommand(new uz.dukeengine.combat.message.GameOrder(local, snapshot.get().contextOrder(), units,
                    new Coord3D(enemy.view.x(), enemy.view.y(), 0f), new ObjectId(enemy.view.id()), 0));
            answer(ThingAnswer.toTheWord(snapshot.get().contextOrder(), visuals, AnswerTarget.of(enemy.view, local)),
                    enemy.view, units);
            return;
        }
        // With the force-attack key held, any thing is fired on — his own and an ally's too, as the reference's
        // MSG_DO_FORCE_ATTACK_OBJECT — and open ground is shelled (MSG_DO_FORCE_ATTACK_GROUND).
        boolean forced = controls.isHeld(KeyMap.Control.FORCE_ATTACK);
        if (forced && enemy == null) {
            var place = pointer.pickGround();
            if (place != null) {
                game.get().postCommand(new CombatOrder.AttackGround(local, units, new Coord3D(place.x, place.z, place.y),
                        true));
                answerOrder("attack", units);
                markOrder(place.x, place.z, OrderMarkers.Kind.ATTACK);
                return;
            }
        }
        if (forced && enemy != null && !units.contains(new ObjectId(enemy.view.id()))) {
            if (!snapshot.get().attackable()) {
                return;
            }
            game.get().postCommand(new CombatOrder.AttackObject(local, units, new ObjectId(enemy.view.id()), true));
            answerOrder("attack", units);
            markOrder(enemy.view.x(), enemy.view.y(), enemy.view.id(), OrderMarkers.Kind.ATTACK);
            noises.get().moment("vo.attack", timer.getTimeInSeconds());
            return;
        }
        if (enemy != null && enemy.view.playerIndex() != local && enemy.view.playerIndex() != 0) {
            if (!snapshot.get().attackable()) {
                return; // nothing selected may be fired at it: refused, as the pointer already said
            }
            game.get().postCommand(new CombatOrder.AttackObject(local, units, new ObjectId(enemy.view.id()), forced));
            answerOrder("attack", units);
            markOrder(enemy.view.x(), enemy.view.y(), enemy.view.id(), OrderMarkers.Kind.ATTACK);
            // His own orders only. In a game with more than one player at it,
            // each hears his own hero answer and nobody hears anyone else's.
            noises.get().moment("vo.attack", timer.getTimeInSeconds());
            return;
        }
        var ground = pointer.pickGround();
        if (ground == null) {
            return;
        }
        var click = groundClick(local, selectedOwn(), enemy == null ? snapshot.get().contextOrder() : null,
                game.get().namesGroundOrders(), ground, parts);
        if (click instanceof CombatOrder.MoveTo plain && controls.isHeld(KeyMap.Control.QUEUE_WAYPOINTS)) {
            click = plain.appended(); // the waypoint key held: a place added to the walk (MSG_ADD_WAYPOINT)
        }
        switch (click) {
            case null -> {
                // nothing selected may take it: the pointer already said so
            }
            case uz.dukeengine.combat.message.GameOrder steer -> {
                game.get().postCommand(steer); // the game's word for a click here — see DukeGame.groundOrder
                if (visuals.wordMarkFor(steer.word()) == Visuals.WordMark.MARK) {
                    markOrder(ground.x, ground.z, OrderMarkers.Kind.MOVE); // no thing on open ground to flash
                }
                answerWord(steer.word(), units);
            }
            case CombatOrder.MoveTo move -> {
                if (!move.units().isEmpty()) {
                    game.get().postCommand(move); // none of them can move: answered as a move all the same, and none sent
                }
                // One mark for the order, not one per unit: it was a single decision. And
                // put WHERE HE CLICKED rather than where they will end up. The mark is an
                // answer to the click -- "that, understood" -- and moving it to the place
                // they can reach answers a question the player did not ask and hides the
                // one thing he wants to see, which is whether he clicked where he meant
                // to. How far they actually get is theirs to work out on the way.
                markOrder(ground.x, ground.z, OrderMarkers.Kind.MOVE);
                hintMove(ground.x, ground.z);
                // And one answer, for the same reason. His own orders only: in a game
                // with more than one player at it each hears his own hero and nobody
                // hears anyone else's.
                if (!move.units().isEmpty()) {
                    noises.get().moment("vo.move", timer.getTimeInSeconds());
                    answerOrder("move", move.units());
                }
            }
            default -> {
                game.get().postCommand(click); // a building's rally point, as its kind of game names it
                markOrder(ground.x, ground.z, OrderMarkers.Kind.MOVE);
            }
        }
    }

    /**
     * What a click on open ground orders the local player's selected things: the game's word for it, where it has one;
     * else one move for those of them that can move — placed as a group by the simulation, and the player's own click,
     * which gathers a group clicked in its middle; else the rally point of a lone production building of his, only in a
     * game that names no ground orders of its own, as the first of {@code parts} to have one sends it; else nothing for one structure of his selected alone, and for more a
     * move of none of them — answered as a move, sent to nobody — as the reference issues its move whatever the
     * selection ({@code CommandTranslator::evaluateContextCommand}).
     */
    static uz.dukeengine.core.message.Command groundClick(int local, List<uz.dukeengine.core.view.UnitView> own,
            String word, boolean gameDecides, Vector3f ground, List<HudPart> parts) {
        var steer = groundOrder(local, own.stream().map(unit -> new ObjectId(unit.id())).toList(), word, ground);
        if (steer != null) {
            return steer;
        }
        var place = new Coord3D(ground.x, ground.z, 0f);
        var movers = own.stream().filter(uz.dukeengine.core.view.UnitView::mobile)
                .map(unit -> new ObjectId(unit.id())).toList();
        if (!movers.isEmpty()) {
            return new CombatOrder.MoveTo(local, movers, place, true);
        }
        var rally = rallies(own, gameDecides) ? rallyOf(local, own.getFirst(), place, parts) : null;
        if (rally != null) {
            return rally;
        }
        return own.isEmpty() || loneStructure(own) ? null : new CombatOrder.MoveTo(local, List.of(), place, true);
    }

    /**
     * Whether a click on open ground with no word of the game's is taken — the move pointer — rather than refused: it
     * is refused only to one structure of his selected alone that takes it as no rally point, as the reference's
     * {@code InGameUI::createMouseoverHint} shows {@code GENERIC_INVALID} for a lone structure alone.
     */
    static boolean groundTakes(int local, List<uz.dukeengine.core.view.UnitView> own, boolean gameDecides,
            List<HudPart> parts) {
        return own.stream().anyMatch(uz.dukeengine.core.view.UnitView::mobile) || !loneStructure(own)
                || rallies(own, gameDecides) && rallyOf(local, own.getFirst(), new Coord3D(0f, 0f, 0f), parts) != null;
    }

    /** A building's rally point at {@code place}, as the first of {@code parts} with one sends it, or null. */
    private static uz.dukeengine.core.message.Command rallyOf(int local, uz.dukeengine.core.view.UnitView building,
            Coord3D place, List<HudPart> parts) {
        for (var part : parts) {
            var rally = part.rally(local, building, place);
            if (rally != null) {
                return rally;
            }
        }
        return null;
    }

    private static boolean loneStructure(List<uz.dukeengine.core.view.UnitView> own) {
        return own.size() == 1 && own.getFirst().structure();
    }

    /** Whether the client's own rule takes a click on open ground as the rally point of a lone building of his. */
    private static boolean rallies(List<uz.dukeengine.core.view.UnitView> own, boolean gameDecides) {
        return !gameDecides && own.size() == 1 && own.getFirst().producer() && !own.getFirst().mobile();
    }

    /**
     * Acknowledge an order where the player clicked. Presentation only.
     *
     * <p>The tick goes here rather than beside each of the four places an order
     * can be given, so a mark and its sound cannot come apart — they are one
     * acknowledgement, and a game that names no {@code order_mark} sound simply
     * gets the silent half.
     */
    void markOrder(float worldX, float worldY, OrderMarkers.Kind kind) {
        markOrder(worldX, worldY, OrderMarkers.NOBODY, kind);
    }

    void markOrder(float worldX, float worldY, int unitId, OrderMarkers.Kind kind) {
        float now = timer.getTimeInSeconds();
        orderMarkers.add(worldX, worldY, unitId, kind, now);
        noises.get().moment("order_mark", now);
    }

    /**
     * A move, or an attack-move, answered with the game's own model where it names one — the reference's move hint,
     * laid for those orders alone: a rally point, a power's place or an order the game names are not moves, and a
     * lone building told to go nowhere gets none, as a lone {@code KINDOF_IMMOBILE} thing does there.
     */
    void hintMove(float worldX, float worldY) {
        var look = visuals.getOrderMark();
        if (!hintsMove(look, selected, snapshot.get().units())) {
            return;
        }
        var selection = selectedIds().stream().map(ObjectId::value).toList();
        modelMarks.add(selection, worldX, worldY, floor.at(worldX, worldY), look, timer.getTimeInSeconds());
    }

    /** The rally points of the selected things of his that have one, and the flag of the only one — see RallyMarks. */
    void showRallies() {
        var own = ownSelected().stream().map(uz.dukeengine.core.view.UnitView::id).collect(java.util.stream.Collectors
                .toSet());
        var shown = snapshot.get().rallies().stream().filter(rally -> own.contains(rally.id())).toList();
        rallyMarks.show(visuals.getRally(), shown, RallyMarks.flagOf(shown, selected.size()),
                Materials.colour(game.get().getColor(game.get().getLocalPlayerIndex())), visuals.getHouseColour(), floor::at,
                cam.getLocation());
    }

    /**
     * Whether a move by {@code selected} is answered with the game's own model: where it named one, and not for a lone
     * structure, which cannot move ({@code InGameUI::createMoveHint}, {@code KINDOF_IMMOBILE}).
     */
    static boolean hintsMove(OrderMark look, java.util.Set<Integer> selected, List<UnitView> units) {
        return look.model() != null && !(selected.size() == 1
                && units.stream().anyMatch(view -> selected.contains(view.id()) && view.structure()));
    }

    /**
     * A move the game gave — a radar's press, its own {@code MoveTo} — answered as the player's own click there is: the
     * order mark and, where the game named a model, the move hint for the selection. See {@link Duke3D#answerMove}.
     */
    void answerMove(Coord3D place) {
        markOrder(place.x(), place.y(), OrderMarkers.Kind.MOVE);
        hintMove(place.x(), place.y());
    }

    /**
     * The selected unit's health, when exactly one is selected.
     *
     * <p>Read from the snapshot like everything else here. It is worth showing
     * because a hero's maximum health is not fixed in every game — a roguelike
     * hero's rises as he levels, and the number moving is the player seeing that
     * happen.
     */
    String selectedHealth() {
        if (selected.size() != 1) {
            return "";
        }
        for (var view : snapshot.get().units()) {
            if (selected.contains(view.id())) {
                return "    hp %.0f/%.0f".formatted(view.health(), view.maxHealth());
            }
        }
        return "";
    }

    /**
     * The single selected own production structure, or {@code null}.
     */
    UnitView selectedProducer() {
        if (selected.size() != 1) {
            return null;
        }
        for (var unit : snapshot.get().units()) {
            if (selected.contains(unit.id())) {
                return unit.producer() && unit.playerIndex() == game.get().getLocalPlayerIndex() ? unit : null;
            }
        }
        return null;
    }

    /**
     * Queue the {@code index}-th entry of the selected factory's build menu.
     */
    void queueBuild(int index) {
        var producer = selectedProducer();
        if (producer == null) {
            return;
        }
        for (var part : parts) {
            var build = part.build(game.get(), producer, index);
            if (build != null) {
                game.get().postCommand(build);
                return;
            }
        }
    }

    /**
     * The selected units an order may be given to: his own, and only his own.
     *
     * <p>The selection may hold something that is not his -- clicking a monster
     * is how its health and its damage are read off the bar -- and an order must
     * never reach it. Filtered here rather than at the click, so that inspecting
     * a creature and commanding one stay two different questions with two
     * different answers.
     */
    List<ObjectId> selectedIds() {
        return selectedOwn().stream().map(u -> new ObjectId(u.id())).toList();
    }

    /**
     * Who his orders go to: what he has selected of his own, or what a part says instead — a game of one hero's, the
     * hero, whatever is picked out to be looked at.
     */
    List<uz.dukeengine.core.view.UnitView> selectedOwn() {
        var own = ownSelected();
        for (var part : parts) {
            var ordered = part.ordered(snapshot.get(), game.get().getLocalPlayerIndex(), own);
            if (ordered != null) {
                return ordered;
            }
        }
        return own;
    }

    /** What the snapshot says of the selected things that are his. */
    private List<uz.dukeengine.core.view.UnitView> ownSelected() {
        return snapshot.get().units().stream()
                .filter(u -> selected.contains(u.id()) && u.playerIndex() == game.get().getLocalPlayerIndex())
                .toList();
    }


    /** The game told who his orders go to, where a part makes that other than what is selected. */
    private void tellTheGameWhoIsOrdered() {
        var own = ownSelected();
        var ordered = selectedOwn();
        var told = ordered.equals(own) ? null : ordered.stream().map(uz.dukeengine.core.view.UnitView::id).toList();
        if (!java.util.Objects.equals(told, orderedTold)) {
            orderedTold = told;
            game.get().setOrdered(told);
        }
    }


    /**
     * The game told what is selected whenever that changes, and the game's own pick taken up — in a match, whatever
     * draws the HUD: a game that draws its own bar hangs it off the selection just as the client's bar does.
     *
     * <p>Half of one conversation with the command bar, and deliberately a frame apart from it: the
     * selection goes out now, and the buttons come back in the <em>next</em> snapshot, because they are worked out
     * on the simulation thread where the state they are about lives — what a barracks can train, what this player
     * can afford. A frame is not worth racing for.
     */
    void keepTheSelectionInStep() {
        if (screen.get() != Screen.PLAYING && screen.get() != Screen.PAUSED) {
            return;
        }
        if (snapshot.get() != null) {
            selectionLink.takeUp(selected, game.get(), snapshot.get().units(), game.get().getLocalPlayerIndex(),
                    this::answerSelection);
        }
        selectionLink.tell(selected, game.get());
        selectionLink.tellGroups(controls.groups(), game.get());
        if (snapshot.get() != null) {
            tellTheGameWhoIsOrdered();
        }
    }
}
