package uz.dukeengine.client3d.rpg;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.client3d.Canvas;
import uz.dukeengine.client3d.HudPart;
import uz.dukeengine.core.view.WorldSnapshot;

/**
 * The RPG's part of the window: the hero's bar at its foot — his card, his skills on their keys, the minimap in its
 * socket — and the live creature in its frame, read off the hero card the game writes on the status line
 *. Its keys are refused while they are not ready or not his, and its card's moments are heard.
 */
public final class RpgHud implements HudPart {

    private Window window;
    private HeroPanel panel;

    /**
     * The live creature in the panel's frame. Held here rather than by the panel because of the resize: the panel is
     * thrown away and built again at every new window size, and a render target that went with it would be a new
     * frame buffer every time the window was dragged.
     */
    private HeroPortrait portrait = HeroPortrait.none();
    private CardSounds sounds;

    @Override
    public void open(Window window, int width, int height) {
        this.window = window;
        panel = panel(width, height);
        // Built the same way units are, so the face in the frame is the creature on the floor and not a second one.
        portrait = HeroPortrait.open(window.renderManager(), window.visuals(), window::body);
        sounds = new CardSounds(window::sound);
    }

    private HeroPanel panel(int width, int height) {
        var visuals = window.visuals();
        var looks = HeroLooks.of(visuals);
        return new HeroPanel(window.assets(), window.font(), window.titleFont(), window.gui(), width, height,
                visuals.getPanelSkin(), visuals.getRangeLook(), looks.getIconLook(), looks.getStatLook(),
                looks.getPanelLook());
    }

    /**
     * Everything drawn out of quads is built again rather than moved: laid out again at the new size it stays in the
     * scene, in the right place, unculled — and not on the screen.
     */
    @Override
    public void resized(int width, int height) {
        var armed = panel.armedKey();
        panel.destroy();
        panel = panel(width, height);
        panel.arm(armed);
    }

    @Override
    public void close() {
        portrait.close();
    }

    @Override
    public void newWorld() {
        sounds.forget();
    }

    /**
     * The level the portrait is told is the one the panel read last frame, and that is deliberate: nothing here draws
     * a level, the portrait only notices it changed, and a change noticed a frame late is noticed exactly once.
     */
    @Override
    public boolean show(Frame frame) {
        var snapshot = frame.snapshot();
        var reading = panel.reading();
        sounds.status(reading);
        portrait.show(frame.picked(), reading == null ? "" : reading.rank());
        portrait.update(frame.tpf(), frame.drawing());
        panel.live(portrait.texture());
        boolean drawn = panel.show(snapshot.hasStatus() ? snapshot.status() : null, window.now());
        // A key pressed that could not be paid for: the panel knows when, the game's sound bank what.
        if (panel.takeRefusal()) {
            window.sound("no_mana");
        }
        panel.hover(frame.playing() ? panel.slotAt(frame.cursorX(), frame.cursorY()) : null);
        panel.hoverAttribute(frame.playing() ? panel.attributeAt(frame.cursorX(), frame.cursorY()) : null);
        return drawn;
    }

    @Override
    public void hide() {
        panel.hide();
        portrait.rest(); // a viewport nobody switches off goes on drawing
    }

    @Override
    public void died(int unitId) {
        portrait.died(unitId); // told outright: a dead creature is gone from the next snapshot
    }

    @Override
    public Canvas.Box panel() {
        return panel == null ? null : panel.standing();
    }

    @Override
    public float[] minimapSocket() {
        return panel != null && panel.isShowing() ? panel.minimapRect() : null;
    }

    @Override
    public float panelHeight() {
        return panel == null ? 0f : panel.heightPixels();
    }

    @Override
    public boolean covers(float x, float y) {
        return panel != null && panel.contains(x, y);
    }

    @Override
    public boolean ordersSelection(char key) {
        return panel.isAnOrder(key);
    }

    @Override
    public boolean mayOrder() {
        return panel.ordersAreHis();
    }

    @Override
    public boolean ready(char key) {
        return panel.readyToCast(key);
    }

    /**
     * Only for want of mana: a slot reloading is swept and counting, one not bought says so across its face, and being
     * broke is written at the other end of the panel — so it is the one refusal said where he pressed.
     */
    @Override
    public void refused(char key, float now) {
        if (panel.refusedForMana(key)) {
            panel.denyForMana(now);
        }
    }

    @Override
    public void armed(Character key) {
        panel.arm(key);
    }

    @Override
    public boolean raises(char key) {
        return panel.canRaise(key);
    }

    @Override
    public Character raiseAt(float x, float y) {
        return panel.badgeAt(x, y);
    }

    @Override
    public Character keyAt(float x, float y) {
        return panel.slotAt(x, y);
    }

    /** How deep the run is and the hero's rank, off the card, which the panel has put away by the time a menu is up. */
    @Override
    public List<String[]> pauseFacts(WorldSnapshot snapshot) {
        var run = snapshot.hasStatus() ? HeroPanel.Reading.parse(snapshot.status()) : null;
        var facts = new ArrayList<String[]>();
        if (run != null) {
            facts.add(new String[] {run.depthWord().isEmpty() ? "DEPTH" : run.depthWord(), run.depth()});
            facts.add(new String[] {"RANK", run.rank()});
        }
        return facts;
    }
}
