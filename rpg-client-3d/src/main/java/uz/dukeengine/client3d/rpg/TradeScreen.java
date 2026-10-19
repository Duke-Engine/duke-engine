package uz.dukeengine.client3d.rpg;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import uz.dukeengine.client3d.Canvas;
import uz.dukeengine.client3d.CanvasInput;
import uz.dukeengine.client3d.MenuStyle;
import uz.dukeengine.client3d.Painter;
import uz.dukeengine.rpg.item.Loot;
import uz.dukeengine.rpg.item.LootBag;
import uz.dukeengine.rpg.story.Merchant;

/**
 * The trade window, and the purse. The window's alone and only asking: it is up while the game says a merchant is
 * being traded with, lists his stock and the hero's bag, and a click is an order handed to {@link Deals} — that the
 * simulation checks and carries out on every machine. Esc closes it; the hero walking away is the game's to see.
 */
public final class TradeScreen implements Painter, CanvasInput {

    /**
     * The words it says, in the game's language.
     *
     * @param goldWord  the purse's word, before the gold: {@code Gold: 120}
     * @param stockWord the merchant's column
     * @param bagWord   the hero's column
     * @param closeHint under the window: how it is put away
     * @param goldIcon  the coins drawn before a purse, a path from the resource root; blank for none
     */
    public record Words(String goldWord, String stockWord, String bagWord, String closeHint, String goldIcon) {

        public static final Words DEFAULTS = new Words("Gold", "For sale", "Your bag", "Esc — close", "");

        public Words {
            goldIcon = goldIcon == null ? "" : goldIcon;
        }
    }

    /**
     * The merchant now traded with, as the window should show him.
     *
     * @param player   this machine's player
     * @param merchant the id of the person
     * @param name     what he is called on the window
     * @param wares    his stock as it stands
     * @param bag      the hero's bag, or {@code null} before there is one
     */
    public record Open(int player, int merchant, String name, List<Merchant.Ware> wares, LootBag bag) {

        public Open {
            wares = wares == null ? List.of() : List.copyOf(wares);
        }
    }

    /**
     * What the game knows of things.
     *
     * @param resolve a ware's item by its id, or {@code null} for one the game does not know
     * @param worth   what a thing sells for, or below 0 for one that cannot be sold
     * @param colour  a thing's name as it is written, {@code 0xRRGGBB}
     * @param stacked whether a thing is one of a stack, its count drawn after its name
     */
    public record Goods(Function<String, Loot> resolve, ToIntFunction<Loot> worth, ToIntFunction<Loot> colour,
            Predicate<Loot> stacked) {
    }

    /** The orders a click gives, which the game posts as its own commands. */
    public interface Deals {

        /** "I'll take one": {@code player} buys the ware in place {@code ware} of {@code merchant}'s stock. */
        void buy(int player, int merchant, int ware);

        /** "Take this": {@code player} sells what is in {@code slot} of his bag to {@code merchant}. */
        void sell(int player, int merchant, int slot);

        /** Esc: the window is put away. */
        void close();
    }

    /** Where each row was drawn this frame, for a click: a ware by its place, or a bag slot. */
    public record Row(float x, float y, float width, float height, boolean ware, int index) {

        boolean holds(int px, int py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }

    private final MenuStyle look;
    private final Words words;
    private final Supplier<Open> open;
    /** The hero's gold, or below 0 while the match has not numbered its players yet. */
    private final IntSupplier gold;
    private final Goods goods;
    private final Deals deals;

    private final List<Row> rows = new ArrayList<>();
    /** What each row is, in the order of {@link #rows}: for its card. */
    private final List<Loot> things = new ArrayList<>();

    public TradeScreen(MenuStyle look, Words words, Supplier<Open> open, IntSupplier gold, Goods goods,
            Deals deals) {
        this.look = look;
        this.words = words;
        this.open = open;
        this.gold = gold;
        this.goods = goods;
        this.deals = deals;
    }

    /** The purse, at the bottom right above the bar: {@code Gold: 120}. */
    public void purse(Canvas canvas) {
        int have = gold.getAsInt();
        if (have < 0) {
            return;
        }
        var font = font(canvas);
        var said = words.goldWord() + ": " + have;
        var size = canvas.measure(font, said);
        var panel = canvas.bottomPanel();
        float floor = panel == null ? canvas.height() - 8f : panel.y() - 8f;
        float x = canvas.width() - size.width() - 16f;
        float y = floor - size.lineHeight();
        canvas.drawText(font, said, x + 1f, y + 1f, 0xC0000000);
        coins(canvas, x, y, size.lineHeight());
        canvas.drawText(font, said, x, y, 0xFF000000 | look.torchColour());
    }

    /** The coins before a purse whose words start at {@code x}, as tall as a line and a third, centred on it. */
    private void coins(Canvas canvas, float x, float y, float line) {
        if (words.goldIcon().isBlank()) {
            return;
        }
        float face = line * 1.35f;
        float top = y + (line - face) / 2f;
        canvas.drawImage(Canvas.Image.of(words.goldIcon()), x - face - 4f, top, x - 4f, top + face,
                0xFFFFFFFF, Canvas.Blend.ALPHA);
    }

    private static Canvas.Font font(Canvas canvas) {
        return Canvas.Font.of("Georgia", Math.clamp(Math.round(canvas.height() * 0.02f), 12, 24));
    }

    /** The window, over everything but the dialogue box. */
    @Override
    public void paint(Canvas canvas) {
        rows.clear();
        things.clear();
        var now = open.get();
        if (now == null) {
            return;
        }
        var font = font(canvas);
        float line = canvas.measure(font, "Ag").lineHeight() * 1.5f;
        float pad = font.pixelHeight();
        float wide = Math.min(canvas.width() - 32f, 820f);
        float column = (wide - pad * 3f) / 2f;
        var wares = now.wares();
        var bag = now.bag();
        // Only the backpack is offered: what he wears he takes off first.
        int bagRows = bag == null ? 0 : bag.backpackSize();
        float high = pad * 3f + line * (2 + Math.max(wares.size(), bagRows)) + line;
        float left = (canvas.width() - wide) / 2f;
        float top = Math.max(8f, (canvas.height() - high) / 2.5f);
        canvas.fillRect(left, top, wide, high, 0xF0000000 | look.stoneDeepColour());
        canvas.openRect(left, top, wide, high, 2f, 0xFF000000 | look.stoneEdgeColour());
        int torch = 0xFF000000 | look.torchColour();
        int dim = 0x90000000 | look.boneColour();

        canvas.drawText(font, now.name(), left + pad, top + pad, torch);
        var purse = words.goldWord() + ": " + Math.max(0, gold.getAsInt());
        var measure = canvas.measure(font, purse);
        float purseAt = left + wide - pad - measure.width();
        coins(canvas, purseAt, top + pad, measure.lineHeight());
        canvas.drawText(font, purse, purseAt, top + pad, torch);

        float y = top + pad + line * 1.3f;
        canvas.drawText(font, words.stockWord(), left + pad, y, torch);
        canvas.drawText(font, words.bagWord(), left + pad * 2f + column, y, torch);
        y += line;
        for (int i = 0; i < wares.size(); i++) {
            var ware = wares.get(i);
            var item = goods.resolve().apply(ware.item());
            var said = (item == null ? ware.item() : item.name()) + " — " + ware.price() + " (" + ware.count() + ")";
            int colour = item == null ? look.boneColour() : goods.colour().applyAsInt(item);
            row(canvas, font, left + pad, y + i * line, column, line, item == null ? null : item.icon(), said,
                    ware.count() <= 0 ? dim : 0xFF000000 | colour, true, i);
            things.add(item);
        }
        for (int slot = 0; slot < bagRows; slot++) {
            var thing = bag.at(slot);
            if (thing == null) {
                continue;
            }
            int worth = goods.worth().applyAsInt(thing);
            var stack = goods.stacked().test(thing) ? " x" + thing.value() : "";
            var said = LootBag.nameOf(thing) + stack + (worth < 0 ? "" : " — " + worth);
            row(canvas, font, left + pad * 2f + column, y + slot * line, column, line, thing.icon(), said,
                    worth < 0 ? dim : 0xFF000000 | goods.colour().applyAsInt(thing), false, slot);
            things.add(thing);
        }
        var hint = words.closeHint();
        canvas.drawText(font, hint, left + wide - pad - canvas.measure(font, hint).width(),
                top + high - pad - line * 0.6f, dim);
    }

    private void row(Canvas canvas, Canvas.Font font, float x, float y, float wide, float high, String icon,
            String said, int argb, boolean ware, int index) {
        float face = high * 0.8f;
        if (icon != null && !icon.isBlank()) {
            canvas.drawImage(Canvas.Image.of(icon), x, y, x + face, y + face, 0xFFFFFFFF, Canvas.Blend.ALPHA);
        }
        canvas.drawText(font, said, x + face + 6f, y + (face - canvas.measure(font, said).lineHeight()) / 2f, argb);
        rows.add(new Row(x, y, wide, high, ware, index));
    }

    /** The click, the key: a ware bought, a thing sold, Esc closing. Everything is taken while it is open. */
    @Override
    public boolean take(Event event) {
        var now = open.get();
        if (now == null) {
            return false;
        }
        switch (event) {
            case Key key -> {
                if (key.down() && key.code() == ESCAPE) {
                    deals.close();
                    return true;
                }
                return false; // his keys are still his: the bar's, the camera's
            }
            case Button button -> {
                if (button.down() && button.button() == Mouse.LEFT) {
                    for (var row : rows) {
                        if (!row.holds(button.x(), button.y())) {
                            continue;
                        }
                        if (row.ware()) {
                            deals.buy(now.player(), now.merchant(), row.index());
                        } else {
                            deals.sell(now.player(), now.merchant(), row.index());
                        }
                        return true;
                    }
                }
                return inside(button.x(), button.y()); // a click on the window is not an order to the world
            }
            default -> {
                return false;
            }
        }
    }

    private boolean inside(int x, int y) {
        if (rows.isEmpty()) {
            return false;
        }
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = 0f;
        float maxY = 0f;
        for (var row : rows) {
            minX = Math.min(minX, row.x());
            minY = Math.min(minY, row.y());
            maxX = Math.max(maxX, row.x() + row.width());
            maxY = Math.max(maxY, row.y() + row.height());
        }
        return x >= minX && x <= maxX && y >= minY - 80 && y <= maxY + 80;
    }

    /** What each row of {@link #rows} is: a ware (null for one the game does not know) or a thing of the bag. */
    public List<Loot> things() {
        return new ArrayList<>(things);
    }

    public List<Row> rows() {
        return List.copyOf(rows);
    }
}
