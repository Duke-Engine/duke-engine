package uz.dukeengine.client3d.rpg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import uz.dukeengine.client3d.Canvas;
import uz.dukeengine.client3d.MenuStyle;
import uz.dukeengine.core.GameConstants;
import uz.dukeengine.core.content.Effect;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.rpg.skill.Skill;
import uz.dukeengine.rpg.skill.SkillBook;
import uz.dukeengine.rpg.skill.SkillEffect;

/**
 * What is on a creature the player picks, as Dota draws it: over its bar, a small picture for each aura that reaches
 * it and for a haste while one burns on it, framed in the colour its own look starts in, and the pointer on one says
 * what it adds.
 *
 * <p>The creature selected alone shows its row always; the one the pointer rests on, while the pointer is on it, on
 * its row or on its bar — and while it is on the row, the row stays that one's, whatever the pick names behind it. A
 * pointer on something drawn over the rows, or over the panel at the foot of the window — where the canvas says it
 * stands, and nowhere while it is hidden — names nothing; a bar behind the panel has no row over it, and one at the top
 * of the window has its row kept on the window.
 *
 * <p>What is on them is read after every frame, on the simulation's thread — {@link SkillBook#aurasOn}, the one rule
 * the circle under a creature asks too, and the haste on its book's list — and handed over whole, so the window never
 * reads a book in the middle of being written. Painted by whatever paints the game's canvas ({@link BagScreen} does),
 * which draws the card of the picture the pointer is on and takes a press on a picture.
 */
public final class BuffScreen {

    /**
     * One thing on a creature: the skill it is, what it is worth there — a percent, or a mana aura's tenths of a point
     * a second — and, for a haste, the frames it has left; an aura lasts while it reaches, and has none.
     */
    public record Buff(Skill skill, int figure, int framesLeft) {
    }

    /** A card to draw: what it says, the hints under it, and where its top left goes. */
    public record Card(List<String> said, List<String> hints, float x, float y) {
    }

    /** What each kind adds is called, in the game's language. */
    public record Words(String attack, String manaRegen, String lifesteal, String attackSpeed, String seconds) {
    }

    private final MenuStyle look;
    private final PanelLook panel;
    private final float iconSize;
    private final Words words;

    /**
     * The skill each kind is shown as: the first of that kind among the skills given.
     *
     * <p>ponytail: the first of each kind; two of a kind would want the card to name the one whose figure it shows,
     * which only its bearer's book knows.
     */
    private final Map<SkillEffect, Skill> kinds = new EnumMap<>(SkillEffect.class);

    /** The colour each look starts in — the first of its layers', as the file writes it — by the look's name. */
    private final Map<String, Integer> starts = new HashMap<>();

    /** What is on the creatures picked, by id: read after every frame on the simulation's thread, handed over whole. */
    private volatile Map<Integer, List<Buff>> buffs = Map.of();

    /** The last thing the pointer rested on, kept when it leaves for the row over it: the simulation's thread's own. */
    private int lastPointed = -1;

    /**
     * The creature whose row — or bar, or the stretch between — the pointer was on when last painted, or -1: the
     * window's word to the simulation's thread, so a row the pointer is reading stays that one's.
     */
    private volatile int reading = -1;

    /** Where each picture was drawn this frame: the window's own, so a press on one is known. */
    private final List<Canvas.Box> drawn = new ArrayList<>();

    /**
     * @param skills   every skill of the game, the first of each kind being what that kind is shown as
     * @param effects  every effect of the game, for the colour a skill's look starts in
     * @param look     the stone and the colours it is painted in
     * @param panel    the hero bar's look, whose scale its pictures are drawn at
     * @param iconSize how big a picture is, in the bar's design pixels
     */
    public BuffScreen(List<Skill> skills, List<Effect> effects, MenuStyle look, PanelLook panel, float iconSize,
            Words words) {
        this.look = look;
        this.panel = panel;
        this.iconSize = iconSize;
        this.words = words;
        for (var skill : skills) {
            kinds.putIfAbsent(skill.effect(), skill);
        }
        for (var effect : effects) {
            if (!effect.layers().isEmpty() && !effect.layers().getFirst().colour().isEmpty()) {
                starts.put(effect.name(), effect.layers().getFirst().colour().getFirst());
            }
        }
    }

    /** The match it is drawn over, told before it starts: what is on those picked is read after its every frame. */
    public void show(DukeGame game) {
        game.onTick(ticked -> buffs = pickedIn(ticked));
    }

    /** What was last handed over, by the id of the creature it is on. */
    public Map<Integer, List<Buff>> buffs() {
        return buffs;
    }

    /**
     * What is on the creature selected alone and on the one the pointer rests on, or last rested on, by id — the one
     * whose row the pointer is on before whatever the pick names behind that row.
     */
    private Map<Integer, List<Buff>> pickedIn(DukeGame game) {
        var selection = game.getSelection();
        int onRow = reading;
        int pointed = game.getPointedAt();
        if (onRow >= 0 || pointed >= 0) {
            lastPointed = onRow >= 0 ? onRow : pointed;
        }
        var found = new LinkedHashMap<Integer, List<Buff>>();
        int frame = game.getLogic().getFrame();
        for (int id : new int[] {selection.size() == 1 ? selection.getFirst() : -1, lastPointed}) {
            var creature = id < 0 ? null : game.getLogic().findObject(new ObjectId(id));
            var on = creature == null ? List.<Buff>of() : on(creature, frame);
            if (!on.isEmpty()) {
                found.put(id, on);
            }
        }
        return found.isEmpty() ? Map.of() : Collections.unmodifiableMap(found);
    }

    /**
     * What is on {@code creature}, in {@link SkillEffect} order: a haste while one holds on it, and then each aura that
     * reaches it. Nothing on one that has fallen.
     *
     * <p>Read after frame {@code frame} has run, so what is left of a haste is what is still to run: the strongest that
     * holds on the next frame, and the frames to the last it holds — of the buffs worth that figure, not of the longest
     * on the list, which may be a weaker one's.
     */
    List<Buff> on(GameObject creature, int frame) {
        var book = creature.findModule(SkillBook.class);
        if (book == null || creature.isEffectivelyDead()) {
            return List.of();
        }
        var on = new ArrayList<Buff>();
        int haste = book.buffOf(SkillEffect.HASTE, frame + 1);
        if (haste > 0) {
            int last = book.getBuffs().stream()
                    .filter(held -> held.kind() == SkillEffect.HASTE && held.value() == haste)
                    .mapToInt(held -> held.until()).max().orElse(frame);
            on.add(new Buff(kinds.get(SkillEffect.HASTE), haste, last - frame));
        }
        for (var kind : SkillBook.aurasOn(creature)) {
            on.add(new Buff(kinds.get(kind), SkillBook.auraOn(creature, kind), 0));
        }
        return List.copyOf(on);
    }

    /** What it adds, as the pointer says it: {@code +50% Zarba}, {@code +5 Mana/s}, {@code +75% Hujum tezligi · 4s}. */
    public String addsOf(Buff buff) {
        int figure = buff.figure();
        return switch (buff.skill().effect()) {
            case DAMAGE_AURA -> "+" + figure + "% " + words.attack();
            case MANA_AURA -> "+" + java.math.BigDecimal.valueOf(figure, 1).stripTrailingZeros().toPlainString() + " "
                    + words.manaRegen();
            case LIFESTEAL_AURA -> figure + "% " + words.lifesteal();
            case HASTE -> "+" + figure + "% " + words.attackSpeed() + " · "
                    + Math.ceilDiv(buff.framesLeft(), GameConstants.LOGICFRAMES_PER_SECOND) + words.seconds();
            default -> "";
        };
    }

    /**
     * The rows over the bars of those shown, the pointer at {@code (x, y)} — {@code covered} while it is on something
     * drawn over them — and the card of the picture it is on, or null, for the caller to draw last, over everything.
     */
    public Card paint(Canvas canvas, DukeGame game, int x, int y, boolean covered) {
        drawn.clear();
        float scale = Math.clamp(canvas.width() / panel.designWidth(), panel.minScale(), panel.maxScale());
        float size = iconSize * scale;
        float gap = Math.max(2f, size * 0.15f);
        var foot = canvas.bottomPanel();
        var selection = game.getSelection();
        int picked = selection.size() == 1 ? selection.getFirst() : -1;
        boolean free = !covered && (foot == null || !holds(foot, x, y));
        int pointed = free ? game.getPointedAt() : -1;
        int over = -1;
        Card card = null;
        for (var row : buffs.entrySet()) {
            var bar = canvas.barOf(row.getKey());
            float top = bar == null ? 0f : Math.max(4f, bar.y() - gap - size);
            if (bar == null || foot != null && top + size > foot.y()) {
                continue; // no bar placed this frame, or one behind the panel
            }
            var on = row.getValue();
            float right = Math.max(bar.x() + bar.width(), bar.x() + on.size() * (size + gap));
            float bottom = Math.max(top + size, bar.y() + bar.height());
            boolean onTheRow = free && x >= bar.x() && x < right && y >= top && y < bottom;
            if (onTheRow) {
                over = row.getKey();
            }
            if (row.getKey() != picked && row.getKey() != pointed && !onTheRow) {
                continue;
            }
            for (int at = 0; at < on.size(); at++) {
                var box = new Canvas.Box(bar.x() + at * (size + gap), top, size, size);
                draw(canvas, on.get(at), box);
                drawn.add(box);
                if (free && holds(box, x, y)) {
                    card = cardOf(on.get(at), box.x(), top + size + gap);
                }
            }
        }
        reading = over;
        return card;
    }

    /** Whether a picture was drawn under {@code (x, y)} this frame. */
    public boolean pictureAt(int x, int y) {
        return drawn.stream().anyMatch(box -> holds(box, x, y));
    }

    private static boolean holds(Canvas.Box box, float x, float y) {
        return x >= box.x() && x < box.x() + box.width() && y >= box.y() && y < box.y() + box.height();
    }

    /** One picture on stone, framed in the colour its look starts in; with no picture, its name's first letter. */
    private void draw(Canvas canvas, Buff buff, Canvas.Box box) {
        var skill = buff.skill();
        float size = box.width();
        canvas.fillRect(box.x(), box.y(), size, size, 0xF0000000 | look.stoneDeepColour());
        if (skill.icon().isBlank()) {
            var letter = skill.name().isEmpty() ? "" : skill.name().substring(0, 1);
            var font = Canvas.Font.of("Georgia", Math.round(size * 0.6f)).bold(true);
            var measure = canvas.measure(font, letter);
            canvas.drawText(font, letter, box.x() + (size - measure.width()) / 2f,
                    box.y() + (size - measure.lineHeight()) / 2f, 0xFF000000 | look.boneColour());
        } else {
            float inset = Math.max(2f, size * 0.1f);
            canvas.drawImage(Canvas.Image.of(skill.icon()), box.x() + inset, box.y() + inset,
                    box.x() + size - inset, box.y() + size - inset, 0xFFFFFFFF, Canvas.Blend.ALPHA);
        }
        canvas.openRect(box.x(), box.y(), size, size, 2f,
                0xFF000000 | starts.getOrDefault(skill.look(), look.stoneEdgeColour()));
    }

    /** Its name, what it adds, and under them its line on what it does. */
    private Card cardOf(Buff buff, float x, float y) {
        var skill = buff.skill();
        return new Card(List.of(skill.name(), addsOf(buff)),
                skill.blurb().isBlank() ? List.of() : List.of(skill.blurb()), x, y);
    }
}
