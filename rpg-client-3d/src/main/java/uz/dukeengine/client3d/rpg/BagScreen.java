package uz.dukeengine.client3d.rpg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import uz.dukeengine.client3d.AimOutcome;
import uz.dukeengine.client3d.Canvas;
import uz.dukeengine.client3d.CanvasInput;
import uz.dukeengine.client3d.Duke3D;
import uz.dukeengine.client3d.MenuStyle;
import uz.dukeengine.client3d.Painter;
import uz.dukeengine.core.message.Command;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.view.CommandButton;
import uz.dukeengine.core.view.WorldSnapshot;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.rpg.item.DropItem;
import uz.dukeengine.rpg.item.GroundItem;
import uz.dukeengine.rpg.item.ItemUse;
import uz.dukeengine.rpg.item.Loot;
import uz.dukeengine.rpg.item.LootBag;
import uz.dukeengine.rpg.item.UseItem;
import uz.dukeengine.rpg.mission.GateUpdate;

/**
 * The hero's bag as the player handles it, on the game's canvas: its slots on a slab of stone at the right of the
 * window, what a thing gives when the pointer rests on it — in the bag or lying on the floor — and a thing taken in
 * hand with the right button and put down on the floor with the left; or, a thing that does something, taken in hand
 * with the left and used on the thing the next click is on. Under it, the rows of what is on a creature picked
 * ({@link BuffScreen}), whose cards it draws and whose pictures take a press.
 *
 * <p>Nothing here touches the world. Picking up is the click the client already sends on a thing the game names a
 * word for, and so is going up to a gate ({@link Words#pickUp}, {@link Words#toTheGate}); putting down is the client's
 * aim at the ground, whose place comes back as a {@link DropItem} order, and using a thing is its aim at a thing,
 * whose id comes back as a {@link UseItem} order — each handed to the game's wire first, so all of them go down the
 * road every order goes.
 *
 * <p>On the window's thread, but for what is handed to the match and run on the simulation's: the rule that names the
 * pickup, and a look after every frame at what lies on the floor. Both only read.
 */
public final class BagScreen implements Painter, CanvasInput {

    /**
     * The words it says, in the game's language, and the words of its orders.
     *
     * @param items     the bag's heading
     * @param dropHint  under a thing in hand to put down: where it goes
     * @param takeHint  how a thing is taken in hand
     * @param useHint   how a thing that does something is used
     * @param pickUp    the order word a click on a thing lying holding an item is
     * @param toTheGate the order word a click on a gate is
     * @param use       the order word a thing in hand pressed on a thing is answered as
     */
    public record Words(String items, String dropHint, String takeHint, String useHint, String pickUp,
            String toTheGate, String use) {
    }

    /** The id of the aim that puts {@code slot} down. */
    private static final String DROP = "drop:";
    /** The id of the aim that takes {@code slot} to a thing, to be used on it. */
    private static final String USE = "use:";
    /** The pointer while a thing is in hand — a Cursor block of that name. */
    private static final String HOLDING = "Drop";
    /** How long the world may stand still before the bag is taken for a menu over it, and put away. */
    private static final long STILL_NANOS = 500_000_000L;

    private final MenuStyle look;
    private final Words words;
    /** What a thing gives, a line each — its figure, and its extra past the first level — in the game's words. */
    private final Function<Loot, List<String>> gives;
    /** How a thing taken in hand is armed: the button, and who is told how its aim ended. */
    private final BiConsumer<CommandButton, Consumer<AimOutcome>> arm;
    /** What is on the creature he picks, over its bar: drawn under the bag, its card over everything. */
    private final BuffScreen buffs;
    /** Where the time is read, in nanoseconds: the window's own clock, or one a test holds still. */
    private final LongSupplier clock;

    /**
     * The match being played, its bag, how its orders go down the wire, and what is painted under the bag while it is
     * shown.
     */
    private record Match(DukeGame game, Supplier<LootBag> bag, UnaryOperator<Command> wire, Painter under) {
    }

    private volatile Match match;

    /** What lies on the floor, by the id of the thing it lies in: read after every frame, handed over whole. */
    private volatile Map<Integer, Loot> lying = Map.of();

    private int mouseX = -1;
    private int mouseY = -1;
    /** The slot in hand, or -1; and which aim put it there, so the end of an older aim does not let go of it. */
    private int held = -1;
    private int aims;
    private Match heldIn;
    /** Whether the thing in hand is to be used rather than put down: no word beside it about the floor. */
    private boolean using;
    /** Whether the left button went down on the bag, so its letting go stays with it. */
    private boolean pressedHere;

    /** Where the bag was last drawn; nothing is on the screen when {@link #shown} is false. */
    private boolean shown;
    private float left;
    private float top;
    private float wide;
    private float high;
    private float slot;
    private float gap;
    private float pad;
    private float heading;
    private int columns;

    private int lastFrame = -1;
    private long lastFrameAt;

    /** A bag whose things taken in hand are armed by {@code duke}, on the window's own clock. */
    public static BagScreen on(Duke3D duke, MenuStyle look, Words words, Function<Loot, List<String>> gives,
            BuffScreen buffs) {
        return new BagScreen(look, words, gives, buffs, (button, ended) -> duke.aim(button, 0f, HOLDING, ended),
                System::nanoTime);
    }

    /** The same, arming what is taken in hand by {@code arm}: for a test to see what it aims with, with no window. */
    public BagScreen(MenuStyle look, Words words, Function<Loot, List<String>> gives, BuffScreen buffs,
            BiConsumer<CommandButton, Consumer<AimOutcome>> arm, LongSupplier clock) {
        this.look = look;
        this.words = words;
        this.gives = gives;
        this.buffs = buffs;
        this.arm = arm;
        this.clock = clock;
    }

    /**
     * The match now being played, told before it starts: a click on a thing lying on the floor is a pickup in it, a
     * click on a gate is the walk up to it, a place the drop aim was given becomes the order to put the thing down
     * there, and a thing the use aim was given becomes the order to take it there.
     *
     * @param bag   this machine's hero's bag, or {@code null} before the match has numbered its players
     * @param wire  what an order is turned into to be sent — the game's own wrapping, or the order itself
     * @param under painted under the bag while it is shown — the game's own words over the world — or {@code null}
     */
    public void show(DukeGame game, Supplier<LootBag> bag, UnaryOperator<Command> wire, Painter under) {
        game.contextOrder((selection, target) -> {
            var item = target.findModule(GroundItem.class);
            if (item != null && item.getHolding() != null) {
                return words.pickUp();
            }
            return target.findModule(GateUpdate.class) == null ? null : words.toTheGate();
        });
        game.onTick(ticked -> lying = lyingIn(ticked));
        buffs.show(game);
        game.onCommandPressed(press -> {
            int put = slotOf(DROP, press.id());
            if (put >= 0 && press.place() != null) {
                game.postCommand(wire.apply(new DropItem(game.getLocalPlayerIndex(), put, press.place())));
            }
            int used = slotOf(USE, press.id());
            if (used >= 0 && press.target() >= 0) {
                game.postCommand(wire.apply(new UseItem(game.getLocalPlayerIndex(), used,
                        new ObjectId(press.target()))));
            }
        });
        match = new Match(game, bag, wire, under);
    }

    /** Everything lying on the floor of {@code game}, by the id of the thing it lies in. */
    private static Map<Integer, Loot> lyingIn(DukeGame game) {
        var found = new HashMap<Integer, Loot>();
        for (var thing : game.getLogic().getObjects()) {
            var item = thing.findModule(GroundItem.class);
            if (item != null && item.getHolding() != null) {
                found.put(thing.getId().value(), item.getHolding());
            }
        }
        return Map.copyOf(found);
    }

    /** The slot a drop aim's id names, or -1 for an id that is not one. */
    public static int slotOf(String id) {
        return slotOf(DROP, id);
    }

    /** The slot an aim's id names after {@code kind} — a drop's or a use's — or -1 for an id that is not one. */
    private static int slotOf(String kind, String id) {
        if (id == null || !id.startsWith(kind)) {
            return -1;
        }
        try {
            return Integer.parseInt(id.substring(kind.length()));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public void paint(Canvas canvas) {
        var now = match;
        var bag = now == null ? null : now.bag().get();
        shown = bag != null && live(now.game().getSnapshot());
        if (!shown) {
            return;
        }
        if (now.under() != null) {
            now.under().paint(canvas);
        }
        if (heldIn != now) {
            held = -1; // a match that is over took what was in hand with it
        }
        var slots = bag.slots();
        layOut(canvas, slots.size());
        var card = buffs.paint(canvas, now.game(), mouseX, mouseY, onTheBag(mouseX, mouseY));
        canvas.fillRect(left, top, wide, high, 0xE0000000 | look.stoneColour());
        canvas.openRect(left, top, wide, high, 2f, 0xFF000000 | look.stoneEdgeColour());
        canvas.drawText(small(), words.items(), left + pad, top + pad, 0xFF000000 | look.boneColour());
        int over = slotAt(mouseX, mouseY);
        for (int at = 0; at < slots.size(); at++) {
            float x = slotX(at);
            float y = slotY(at);
            canvas.fillRect(x, y, slot, slot, 0xFF000000 | look.stoneDeepColour());
            boolean lit = at == held || at == over && slots.get(at) != null;
            canvas.openRect(x, y, slot, slot, lit ? 2f : 1f,
                    0xFF000000 | (lit ? look.torchColour() : look.stoneLitColour()));
            var item = slots.get(at);
            if (item != null) {
                float inset = slot * 0.14f;
                canvas.drawImage(Canvas.Image.of(item.icon()), x + inset, y + inset, x + slot - inset,
                        y + slot - inset, at == held ? 0x55FFFFFF : 0xFFFFFFFF, Canvas.Blend.ALPHA);
                if (item.level() > 1) {
                    var mark = "I".repeat(item.level());
                    var size = canvas.measure(small(), mark);
                    canvas.drawText(small(), mark, x + slot - size.width() - 3f, y + slot - size.lineHeight() - 1f,
                            0xFF000000 | look.torchColour());
                }
            }
        }
        var inHand = held >= 0 && held < slots.size() ? slots.get(held) : null;
        if (inHand != null) {
            float size = slot * 0.7f;
            canvas.drawImage(Canvas.Image.of(inHand.icon()), mouseX + 10, mouseY + 10, mouseX + 10 + size,
                    mouseY + 10 + size, 0xD0FFFFFF, Canvas.Blend.ALPHA);
            if (!using) {
                tip(canvas, List.of(), List.of(words.dropHint()), mouseX + 14, mouseY + 14 + size, false);
            }
        } else if (over >= 0 && slots.get(over) != null) {
            var item = slots.get(over);
            tip(canvas, lines(item), hintsOf(item, true), slotX(over) - gap, slotY(over), true);
        } else if (card != null) {
            tip(canvas, card.said(), card.hints(), card.x(), card.y(), false);
        } else if (!onTheBag(mouseX, mouseY) && lying.get(now.game().getPointedAt()) instanceof Loot under) {
            tip(canvas, lines(under), hintsOf(under, false), mouseX + 20, mouseY + 20, false);
        }
    }

    /** Its name, and what it gives if it gives anything. */
    private List<String> lines(Loot item) {
        var said = new ArrayList<String>();
        said.add(LootBag.nameOf(item));
        for (var line : gives.apply(item)) {
            if (!line.isEmpty()) {
                said.add(line);
            }
        }
        return said;
    }

    /** How to take it and, on its card in the bag, how to use it if it does something; on the floor, how to take it. */
    private List<String> hintsOf(Loot item, boolean inBag) {
        if (inBag && item.use() != ItemUse.NONE) {
            return List.of(words.takeHint(), words.useHint());
        }
        return List.of(words.takeHint());
    }

    /**
     * A card: {@code said}, the first line a name and then what it gives, and under it {@code hints} — a card of hints
     * alone has its first as its heading. Beside {@code (x, y)}, to its left when {@code leftOf}, and on the screen.
     */
    private void tip(Canvas canvas, List<String> said, List<String> hints, float x, float y, boolean leftOf) {
        var lines = new ArrayList<>(said);
        lines.addAll(hints);
        float across = 0f;
        float down = 0f;
        for (int i = 0; i < lines.size(); i++) {
            var font = i == 0 ? big() : small();
            var measure = canvas.measure(font, lines.get(i));
            across = Math.max(across, measure.width());
            down += measure.lineHeight() + (i == 0 ? 0f : 2f);
        }
        float w = across + pad * 2f;
        float h = down + pad * 2f;
        float at = leftOf ? x - w : x;
        at = Math.clamp(at, 4f, Math.max(4f, canvas.width() - w - 4f));
        float from = Math.clamp(y, 4f, Math.max(4f, canvas.height() - h - 4f));
        canvas.fillRect(at, from, w, h, 0xF0000000 | look.stoneDeepColour());
        canvas.openRect(at, from, w, h, 1.5f, 0xFF000000 | look.stoneEdgeColour());
        float line = from + pad;
        for (int i = 0; i < lines.size(); i++) {
            var font = i == 0 ? big() : small();
            int colour = i == 0 ? look.torchColour() : i >= said.size() ? look.hintColour() : look.boneColour();
            canvas.drawText(font, lines.get(i), at + pad, line, 0xFF000000 | colour);
            line += canvas.measure(font, lines.get(i)).lineHeight() + 2f;
        }
    }

    private Canvas.Font big() {
        return Canvas.Font.of("Georgia", Math.round(slot * 0.34f)).bold(true);
    }

    private Canvas.Font small() {
        return Canvas.Font.of("Georgia", Math.round(slot * 0.27f));
    }

    /** Two columns at the right of the window, a little above its middle, sized to its height. */
    private void layOut(Canvas canvas, int count) {
        slot = Math.clamp(Math.round(canvas.height() * 0.05f), 34, 72);
        gap = Math.max(3f, slot / 10f);
        pad = Math.max(6f, slot / 6f);
        heading = Math.round(slot * 0.27f) * 1.4f;
        columns = 2;
        int rows = (count + columns - 1) / columns;
        wide = pad * 2f + columns * slot + (columns - 1) * gap;
        high = pad * 2f + heading + gap + rows * slot + (rows - 1) * gap;
        left = canvas.width() - wide - 16f;
        top = (canvas.height() - high) / 2f - canvas.height() * 0.06f;
    }

    private float slotX(int at) {
        return left + pad + (at % columns) * (slot + gap);
    }

    private float slotY(int at) {
        return top + pad + heading + gap + (at / columns) * (slot + gap);
    }

    /** The slot under {@code (x, y)}, or -1. */
    private int slotAt(int x, int y) {
        if (!shown) {
            return -1;
        }
        var now = match;
        var bag = now == null ? null : now.bag().get();
        int count = bag == null ? 0 : bag.slots().size();
        for (int at = 0; at < count; at++) {
            if (x >= slotX(at) && x < slotX(at) + slot && y >= slotY(at) && y < slotY(at) + slot) {
                return at;
            }
        }
        return -1;
    }

    private boolean onTheBag(int x, int y) {
        return shown && x >= left && x < left + wide && y >= top && y < top + high;
    }

    /** Whether the world is being played: frames coming, and not paused under a menu. */
    private boolean live(WorldSnapshot snapshot) {
        long now = clock.getAsLong();
        if (snapshot.frame() != lastFrame) {
            lastFrame = snapshot.frame();
            lastFrameAt = now;
        }
        return snapshot.frame() > 0 && !snapshot.paused() && now - lastFrameAt < STILL_NANOS;
    }

    @Override
    public boolean take(Event event) {
        switch (event) {
            case Pointer pointer -> {
                mouseX = pointer.x();
                mouseY = pointer.y();
                return false;
            }
            case Button button -> {
                mouseX = button.x();
                mouseY = button.y();
                return click(button);
            }
            default -> {
                return false;
            }
        }
    }

    /**
     * A button on the bag stays on the bag; the right one on a thing in it takes that thing in hand to put down, and
     * the left one on a thing that does something takes it in hand to use.
     */
    private boolean click(Button button) {
        if (button.button() == Mouse.LEFT && !button.down()) {
            boolean mine = pressedHere;
            pressedHere = false;
            return mine;
        }
        if (shown && button.button() == Mouse.LEFT && !onTheBag(button.x(), button.y())
                && buffs.pictureAt(button.x(), button.y())) {
            pressedHere = true;
            return true;
        }
        if (!onTheBag(button.x(), button.y())) {
            return false;
        }
        var now = match;
        int at = slotAt(button.x(), button.y());
        var bag = now == null ? null : now.bag().get();
        var item = bag == null || at < 0 ? null : bag.slots().get(at);
        if (button.button() == Mouse.LEFT) {
            pressedHere = true;
            if (item != null && item.use() != ItemUse.NONE) {
                hold(now, at, item, USE, CommandButton.Aim.UNIT);
            }
            return true;
        }
        if (button.button() != Mouse.RIGHT || !button.down()) {
            return button.button() != Mouse.RIGHT;
        }
        if (item == null) {
            return held < 0;
        }
        hold(now, at, item, DROP, CommandButton.Aim.GROUND);
        return true;
    }

    /**
     * Take the thing in {@code at} in hand: to be put down where the next click on the ground is, or — a thing that
     * does something — taken to the thing the next click is on. The client arms the aim, and says when it is over.
     *
     * <p>A press on a thing is answered as the game's own order, {@link Words#use}: marked in the ring of the game's
     * orders and voiced as they are, and not as an attack on it. A drop aims at the floor, and says nothing of it.
     */
    private void hold(Match now, int at, Loot item, String kind, CommandButton.Aim aim) {
        int armed = ++aims;
        held = at;
        heldIn = now;
        using = aim == CommandButton.Aim.UNIT;
        var button = new CommandButton(kind + at, item.icon(), item.name(), null, true, aim, null);
        arm.accept(using ? button.answeredAs(words.use()) : button, outcome -> {
            if (aims == armed) {
                held = -1;
            }
        });
    }
}
