package uz.dukeengine.client3d;

import com.jme3.input.InputManager;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.view.WorldSnapshot;
import uz.dukeengine.game.DukeGame;

/**
 * The game's own keys — its skills and the orders it put on letters: pressed, armed and waiting to be pointed at
 * something, held to show how far they reach and cast as they are let go, or a level spent on them — and the ring
 * that shows the reach of the one armed.
 *
 * <p>All any of this may do in the end is post a command: the render thread has no business in the simulation.
 */
final class SkillKeys {

    private final Hotkeys hotkeys;
    private final Visuals visuals;
    private final List<HudPart> parts;
    private final Orders orders;
    private final WorldPointer pointer;
    /** Controls of a kind the client has not got itself, which may aim and cast on their own terms. */
    private final Supplier<ControlScheme> scheme;
    /** Every noise the game makes, once there are any. */
    private final Supplier<GameSounds> noises;
    private final Supplier<DukeGame> game;
    private final Supplier<WorldSnapshot> snapshot;
    /** What is on the screen now. */
    private final Supplier<Screen> screen;
    private final InputManager inputManager;
    private final com.jme3.system.Timer timer;
    private final WorldMoments.Floor floor;
    /**
     * The game key that has been pressed and is waiting to be pointed at something.
     */
    private Character arming;

    /**
     * Whether it was armed by clicking its slot rather than by pressing its key.
     *
     * <p>Only a held skill cares, and it cares a great deal: what casts it is
     * letting go of the same thing that armed it. Without this, arming with the
     * keyboard and then releasing a mouse button that happened to be down would
     * fire it.
     */
    private boolean armedByMouse;

    /**
     * Draws how far an armed skill reaches. See RangeRings.
     */
    private final RangeRings rangeRings;
    /** The HUD's pixels: the panels a click is tested against are laid out in them. */
    private final HudScale hud;
    /** Whether the aim is a quick cast's, where a creature near the pointer counts as under it. */
    private boolean quickCasting;

    SkillKeys(Hotkeys hotkeys, Visuals visuals, List<HudPart> parts, Orders orders, WorldPointer pointer,
            Supplier<ControlScheme> scheme, RangeRings rangeRings, HudScale hud, Supplier<GameSounds> noises,
            Supplier<DukeGame> game, Supplier<WorldSnapshot> snapshot, Supplier<Screen> screen,
            InputManager inputManager, com.jme3.system.Timer timer, WorldMoments.Floor floor) {
        this.hotkeys = hotkeys;
        this.visuals = visuals;
        this.parts = parts;
        this.orders = orders;
        this.pointer = pointer;
        this.scheme = scheme;
        this.rangeRings = rangeRings;
        this.hud = hud;
        this.noises = noises;
        this.game = game;
        this.snapshot = snapshot;
        this.screen = screen;
        this.inputManager = inputManager;
        this.timer = timer;
        this.floor = floor;
    }

    /** Whether a key is armed, waiting for the next click. */
    boolean armed() {
        return arming != null;
    }

    /** Whether {@code key} is the key armed. */
    boolean armedWith(char key) {
        return arming != null && arming == key;
    }

    /** What the armed key aims at — see {@link Hotkeys.Aim} — or null where none is armed. */
    Hotkeys.Aim armedAim() {
        var binding = arming == null ? null : hotkeys.all().get(arming);
        return binding == null ? null : binding.aim();
    }

    /** The first answer of the parts that has one, or {@code null}. */
    private <T> T firstOfTheParts(Function<HudPart, T> asked) {
        return parts.stream().map(asked).filter(Objects::nonNull).findFirst().orElse(null);
    }

    /**
     * A game key was pressed.
     *
     * <p>Most act at once. One that needs pointing at something instead goes
     * quiet and waits for the next click — pressed again it thinks better of it,
     * and so does a right-click or escape. All any of this may do in the end is
     * post a command: the render thread has no business in the simulation.
     */
    void pressHotkey(char key, boolean byMouse) {
        var binding = hotkeys.all().get(key);
        if (binding == null) {
            return;
        }
        var range = visuals.getSkillRange(key);
        if (ordersSelection(key) && !parts.stream().allMatch(HudPart::mayOrder)) {
            // The four orders belong to whatever is selected, and what is selected
            // is not his -- so there is nothing to give the order to. The panel
            // draws them dim for the same reason; this is the half that stops the
            // key going round the outside of the dim button.
            return;
        }
        if (range != null && !ordersSelection(key) && orders.selectedIds().isEmpty()) {
            // A skill is something one of his creatures does, so it needs that
            // creature picked out — pressing Q with nothing selected, or with a
            // skeleton selected to look at it, used to cast anyway. The orders
            // beside them are deliberately not like this: they are the player
            // talking to whoever he owns, and are meant to work with an empty
            // selection.
            //
            // ★ Which is why the order has to be asked about by name. It used to
            // be enough that only a skill had a reach to draw — until the attack
            // order was given one too, and it is worth having: the ring says how
            // far he hits from, which is the question a player is asking when he
            // reaches for that key.
            return;
        }
        if (binding.aim() == Hotkeys.Aim.NOW) {
            if (range != null && range.castOnRelease()) {
                // Held rather than spent. A skill with nothing to point at used to
                // go off the instant the key went down, which left the player no
                // way to ask how far it reaches except by spending it. Holding
                // shows him; letting go casts, so a tap is still a cast and all
                // that has changed is that looking is now free.
                if (ready(key)) {
                    arm(key, byMouse);
                } else {
                    sayWhyNot(key);
                }
                return;
            }
            disarm();
            hotkeys.pressNow(game.get(), key);
            return;
        }
        if (arming != null && arming == key) {
            if (scheme.get().aiming()) {
                aimArmedKey(); // aimed by the scheme: the key again casts
                return;
            }
            disarm();
            return;
        }
        // Refusing here rather than arming and going quiet: a slot the panel is
        // already showing as spent would take a click and do nothing with it.
        if (!ready(key)) {
            sayWhyNot(key);
            return;
        }
        arm(key, byMouse);
        if (scheme.get().armed(key, byMouse, range)) {
            return; // the scheme aims it
        }
        if (scheme.get().castsAtOnce(byMouse)) {
            quickCasting = true;
            try {
                aimArmedKey(); // cast at once, as if its aim were clicked where the pointer is
            } finally {
                quickCasting = false;
            }
        }
    }

    /** Answer a key that was refused, where a part's panel does not already show why. */
    private void sayWhyNot(char key) {
        for (var part : parts) {
            part.refused(key, timer.getTimeInSeconds());
        }
    }

    /** Whether a key orders what is selected, as a part's panel says. */
    private boolean ordersSelection(char key) {
        return parts.stream().anyMatch(part -> part.ordersSelection(key));
    }

    /** Whether a key may be pressed now, as every part's panel says. */
    private boolean ready(char key) {
        return parts.stream().allMatch(part -> part.ready(key));
    }


    /**
     * Arm a key: the panel lights its slot, and its reach is drawn on the floor.
     */
    private void arm(char key, boolean byMouse) {
        arming = key;
        armedByMouse = byMouse;
        tellThePartsWhatIsArmed(key);
    }

    /**
     * Letting go of a held skill casts it.
     *
     * <p>Whichever way it was armed and only that way: the key it was pressed
     * with, or the mouse, if he pressed its slot on the bar. Otherwise releasing
     * the mouse after arming from the keyboard would fire it before the player had
     * aimed his eyes, never mind his hand.
     */
    void letGoOfHeldSkill(boolean byMouse) {
        if (!holdingASkill() || armedByMouse != byMouse) {
            return;
        }
        var binding = hotkeys.all().get(arming);
        disarm();
        if (binding != null) {
            binding.run().accept(game.get(), null);
        }
    }

    /**
     * Whether what is armed is drawn while held and cast when let go.
     */
    boolean holdingASkill() {
        if (arming == null) {
            return false;
        }
        var range = visuals.getSkillRange(arming);
        return range != null && range.castOnRelease();
    }

    void disarm() {
        scheme.get().disarmed();
        arming = null;
        armedByMouse = false;
        tellThePartsWhatIsArmed(null);
        rangeRings.hide();
    }

    private void tellThePartsWhatIsArmed(Character key) {
        for (var part : parts) {
            part.armed(key);
        }
    }


    /**
     * The click that completes an armed key. Clicking nothing usable drops it,
     * rather than leaving the player armed and wondering why nothing happened.
     */
    void aimArmedKey() {
        // ★ Read BEFORE disarming, which nulls it. The clamp below asks what the
        // armed key reaches, and asking after the disarm got null every time --
        // so the mark it was written to put in the right place was left at the
        // cursor, which is the one place it was written not to be.
        char aimed = arming == null ? 0 : arming;
        var binding = hotkeys.all().get(arming);
        var ahead = pointer.schemeAim(); // read before the disarm lets the scheme's aim go
        disarm();
        if (binding == null) {
            return;
        }
        boolean mayBeACreature = binding.aim() == Hotkeys.Aim.UNIT
                || binding.aim() == Hotkeys.Aim.UNIT_OR_GROUND;
        if (mayBeACreature) {
            // The creature nearest where it aims, within six.
            var unit = ahead != null ? pointer.nearTheGround(ahead) : pointer.pickUnit();
            if (unit == null && quickCasting) {
                var under = pointer.pickGround();
                unit = under == null ? null : pointer.nearTheGround(under);
            }
            if (unit != null) {
                binding.run().accept(game.get(), new Hotkeys.Aimed(unit.view.id(), null));
                orders.markOrder(unit.view.x(), unit.view.y(), unit.view.id(),
                        OrderMarkers.Kind.ATTACK);
                noises.get().moment("vo.attack", timer.getTimeInSeconds());
                return;
            }
            if (binding.aim() == Hotkeys.Aim.UNIT) {
                return; // it had to be a creature, and the click found none
            }
        }
        var ground = ahead != null ? ahead : pointer.pickGround();
        if (ground == null) {
            return;
        }
        if (binding.aim() == Hotkeys.Aim.OPEN_GROUND && !pointer.isOpenAndSeen(ground)) {
            return; // stone, or somewhere he has never been — nothing is sent
        }
        var spot = new Coord3D(ground.x, ground.z, 0f);
        binding.run().accept(game.get(), new Hotkeys.Aimed(0, spot));
        // ★ The mark goes where the SKILL goes, not where the mouse was. A click
        // past the ring is read by the simulation as "as far that way as I can"
        // -- it clamps to the edge -- so a mark left at the cursor would stand a
        // long way from where the thing actually landed and the picture would be
        // a lie in exactly the case the player most needs it to be true.
        //
        // An ORDER is not clamped, and the difference is real rather than an
        // exception: the ring on an order says how far he HITS from, which is
        // worth knowing and is not a fence. He may be sent to fight his way
        // across the whole floor.
        var landing = ordersSelection(aimed) ? spot : clampedToReach(aimed, spot);
        boolean attackMove = binding.aim() == Hotkeys.Aim.UNIT_OR_GROUND;
        orders.markOrder(landing.x(), landing.y(),
                attackMove ? OrderMarkers.Kind.ATTACK_MOVE : OrderMarkers.Kind.MOVE);
        if (attackMove) {
            orders.hintMove(landing.x(), landing.y());
        }
    }

    /**
     * A spot pulled back to the edge of what the armed skill can reach.
     *
     * <p>The same rule {@code SkillBook.withinReach} applies on the far side,
     * drawn from the same {@link SkillRange} the ring on the floor is drawn from
     * -- so the mark, the ring and the cast all agree about where "as far that
     * way as I can" is. A skill with no ring, or a click already inside it, is
     * left exactly where it was.
     */
    private Coord3D clampedToReach(char key, Coord3D wanted) {
        var range = key == 0 ? null : visuals.getSkillRange(key);
        var hero = whereHisHeroIs();
        if (range == null || hero == null) {
            return wanted;
        }
        return range.within(hero, wanted);
    }


    /**
     * A click on a skill slot casts it, exactly as pressing its key would.
     *
     * <p>Down the same road, deliberately: the click ends in {@link #pressHotkey},
     * so a skill that needs pointing at something arms and waits for the next
     * click just as the keyboard's does, and a skill that does not goes off at
     * once. Anything else would be a second copy of the rules for casting, and the
     * two would drift.
     *
     * <p>A slot that is cooling or locked eats the click and does nothing, which
     * is what a stone slot with a shadow over it looks like it should do.
     * Spend a level on a slot, from the keyboard.
     *
     * <p>Refused in silence when there is nothing to spend or nowhere to spend
     * it, and that is a requirement rather than an omission: Ctrl is held for
     * other reasons, and a key that made a noise every time the player happened
     * to be holding it would be a key he learns to dread. The panel is asked
     * rather than the simulation because the panel is what is showing him a
     * badge -- if there is no badge, the press means nothing and says nothing.
     *
     * @return whether the bar took the click
     */
    void raiseSkill(char key) {
        if (parts.stream().noneMatch(part -> part.raises(key))) {
            return;
        }
        hotkeys.raiseSkill(game.get(), key);
        noises.get().moment("power_taken", timer.getTimeInSeconds());
    }

    boolean clickedASkillSlot() {
        var cursor = hud.of(inputManager.getCursorPosition());
        // The badge before the slot, because it hangs over the slot's own corner
        // and a click there means the badge. It is only ever there when a point
        // may go into that slot, so the corner of a socket with nothing to buy
        // arms the skill exactly as it always did.
        Character raising = firstOfTheParts(part -> part.raiseAt(cursor.x, cursor.y));
        if (raising != null) {
            hotkeys.raiseSkill(game.get(), raising);
            noises.get().moment("power_taken", timer.getTimeInSeconds());
            return true;
        }
        Character key = firstOfTheParts(part -> part.keyAt(cursor.x, cursor.y));
        if (key != null) {
            pressHotkey(key, true);
            return true;
        }
        // The rest of a panel swallows clicks too. Without this, clicking its
        // portrait sends the hero walking to wherever the panel happens to cover.
        return parts.stream().anyMatch(part -> part.covers(cursor.x, cursor.y));
    }

    /**
     * Draw how far the armed skill reaches, and whether the click as it stands
     * would be obeyed.
     *
     * <p>Round the hero rather than round the selection: a skill is cast by the
     * man who has it whether or not the player has him clicked, so a ring drawn at
     * the selection would be in the wrong place exactly when he had picked a
     * monster out to look at.
     */
    void syncSkillRange(float now) {
        var range = arming == null ? null : visuals.getSkillRange(arming);
        if (range == null || screen.get() != Screen.PLAYING) {
            rangeRings.hide();
            return;
        }
        var hero = whereHisHeroIs();
        if (hero == null) {
            rangeRings.hide();
            return;
        }
        Coord3D aimedAt = null;
        boolean allowed = true;
        if (range.needsAiming()) {
            var at = inputManager.getCursorPosition();
            var ground = pointer.groundUnder(at.x, at.y);
            aimedAt = new Coord3D(ground.x, ground.z, 0f);
            // Out of reach is not a refusal -- the click is pulled back to the
            // edge, and the ring is what says so. What refuses is ground he
            // cannot aim at: stone, or somewhere he has never been.
            allowed = pointer.isOpenAndSeen(ground);
        }
        rangeRings.show(range, hero, aimedAt, allowed, now, floor::at);
    }

    /**
     * Where the player's own unit is standing, or null before there is one.
     */
    private Coord3D whereHisHeroIs() {
        int local = game.get().getLocalPlayerIndex();
        for (var view : snapshot.get().units()) {
            if (view.playerIndex() == local && view.selectable()) {
                return new Coord3D(view.x(), view.y(), 0f);
            }
        }
        return null;
    }
}
