package uz.dukeengine.client3d.rpg;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import uz.dukeengine.core.GameConstants;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.rpg.skill.Skill;
import uz.dukeengine.rpg.skill.SkillBook;
import uz.dukeengine.rpg.skill.SkillRanks;

/**
 * The hero card, written: what {@link HeroPanel} reads off the status line, built a field at a time on the simulation's
 * thread by the game, which decides what goes on it — its words, its pictures, whose card it is. The format lives here
 * beside its reader, so the two cannot drift.
 *
 * <p>Every word on it is finished — in the game's language, with its suffixes — since the client has other games to
 * serve and no business knowing which language this one speaks; the panel picks only the colours. No word may hold
 * the two characters the line is cut on, {@code |} and {@code ,} where a field has parts.
 */
public final class HeroCard {

    /** The words under a skill's pips and on its card, in the game's language. */
    public record Words(String rankSuffix, String master, String locked, TipWords tip) {
    }

    /** The words on a skill's card: its rows' labels, its foot, and what a rank and a second are called. */
    public record TipWords(String damage, String cooldown, String radius, String range,
            String boost, String raise, String raiseKey, String maxed, String noPoints,
            String rankWord, String seconds, String mana) {
    }

    private final StringBuilder line = new StringBuilder();

    private HeroCard(String name) {
        line.append("name=").append(name);
    }

    /** A card for {@code name}; empty for the card of nobody, which shows only what was never about a creature. */
    public static HeroCard named(String name) {
        return new HeroCard(name);
    }

    public HeroCard title(String title) {
        return field("title", title);
    }

    /** The drawing in the portrait's frame where no creature is drawn live: a name the panel's glyphs know. */
    public HeroCard face(String face) {
        return field("face", face);
    }

    /** His level, as it is written: {@code 7-daraja}. A card with none is not his. */
    public HeroCard rank(String rank) {
        return field("rank", rank);
    }

    public HeroCard health(int now, int most) {
        return field("hp", now + "/" + most);
    }

    public HeroCard experience(int intoLevel, int forNextLevel) {
        return field("xp", intoLevel + "/" + forNextLevel);
    }

    public HeroCard mana(int now, int most) {
        return field("mana", now + "/" + most);
    }

    /** The frame a cast was last refused for want of mana: a moment, stamped so it is heard once. */
    public HeroCard refusedForMana(int frame) {
        return field("noMana", Integer.toString(frame));
    }

    /** How far into the game, and what that is called: {@code III / IV}, {@code DEPTH}. */
    public HeroCard depth(String depth, String word) {
        return field("depth", depth).field("depthWord", word);
    }

    /** The headings over the skills and the bag, drawn whether or not anything is under them. */
    public HeroCard headings(String skills, String items) {
        return field("itWord", items).field("skWord", skills);
    }

    /** Something that has just happened, said for a while: what he picked up. */
    public HeroCard note(String note) {
        return field("note", note);
    }

    /** One figure under the bars, what it is {@code now}, and against what he would be {@code bare} what he borrows. */
    public HeroCard stat(String word, int now, int bare, String icon) {
        return figure("stat", word, now, bare, icon);
    }

    /** One attribute, the same four fields as a figure, his {@code primary} — the one that is his blow — marked. */
    public HeroCard attribute(String word, int now, int bare, String icon, boolean primary) {
        figure("attr", word, now, bare, icon);
        if (primary) {
            line.append(",primary");
        }
        return this;
    }

    private HeroCard figure(String field, String word, int now, int bare, String icon) {
        line.append('|').append(field).append('=').append(word).append(',').append(now).append(',');
        if (now != bare) {
            line.append(now > bare ? "+" : "").append(now - bare);
        }
        line.append(',').append(icon);
        return this;
    }

    /** The card over attribute {@code place}: its name, a line under it, a blurb, its rows and its foot. */
    public HeroCard attributeTip(int place, String name, String at, String text, List<Row> rows, String foot) {
        line.append("|atTipName=").append(place).append(',').append(name);
        line.append("|atTipAt=").append(place).append(',').append(at);
        if (!text.isEmpty()) {
            line.append("|atTipText=").append(place).append(',').append(text);
        }
        for (var row : rows) {
            line.append("|atTipRow=").append(place).append(',').append(row.label())
                    .append(',').append(row.now()).append(',').append(row.next());
        }
        if (!foot.isEmpty()) {
            line.append("|atTipFoot=").append(place).append(',').append(foot);
        }
        return this;
    }

    /** A row of a card: what it is, what it is now, and what the next point makes it — either may be empty. */
    public record Row(String label, String now, String next) {
    }

    /** One socket of his bag: the picture, and how many of it he carries. */
    public HeroCard item(String icon, int count) {
        return field("it", icon + "," + count);
    }

    /** Whether the orders beside the map are the player's to press: what is selected is his. */
    public HeroCard ordersAreHis(boolean his) {
        return field("cmds", his ? "mine" : "theirs");
    }

    /** One order beside the map: its key, its picture, its word, and whether it is the standing order now. */
    public HeroCard order(char key, String icon, String word, boolean on) {
        return field("cmd", key + "," + icon + "," + word + "," + (on ? "on" : "off"));
    }

    /** Points not yet spent, and what they are called. */
    public HeroCard points(int unspent, String word) {
        return field("pts", unspent + "," + word);
    }

    /**
     * Every skill in {@code book} on its key: what it costs and whether it can be paid, its picture and its state —
     * ready, cooling, or locked and for want of what — its pips and whether a point may go there, and its card.
     */
    /**
     * The same, each skill drawn and pressed on the letter {@code letterOf} gives its key — W's skill pressed on E in a
     * scheme that moves them — told as a {@code letter} field where it differs from the key.
     */
    public HeroCard skills(SkillBook book, SkillRanks ranks, int heroLevel, Words words,
            java.util.function.UnaryOperator<Character> letterOf) {
        skills(book, ranks, heroLevel, words);
        for (var skill : book.getSkills()) {
            Character letter = letterOf == null ? null : letterOf.apply(skill.key());
            if (letter != null && letter != skill.key()) {
                line.append("|letter=").append(skill.key()).append(',').append(letter);
            }
        }
        return this;
    }

    public HeroCard skills(SkillBook book, SkillRanks ranks, int heroLevel, Words words) {
        for (var skill : book.getSkills()) {
            int rank = ranks.rankOf(skill.key());
            line.append("|cost=").append(skill.key()).append(',')
                    .append(skill.manaAt(Math.max(1, rank))).append(',')
                    .append(book.canAfford(skill.key(), Math.max(1, rank)) ? "yes" : "no");
            line.append("|skill=").append(skill.key()).append(',')
                    .append(Objects.requireNonNullElse(skill.icon(), "")).append(',');
            if (rank <= SkillRanks.UNLEARNT) {
                // An ultimate says what it is waiting for; an ordinary skill waits for nothing but a point.
                line.append("lock");
                if (skill.isUltimate()) {
                    line.append(',').append(skill.levelForRank(1)).append(words.rankSuffix());
                }
            } else {
                int left = book.cooldownOf(skill.key());
                if (skill.effect().isPassive()) {
                    line.append("passive"); // never cast: a framed picture, not a button
                } else if (left <= 0) {
                    line.append("ready");
                } else {
                    line.append("cool,").append(left).append(',').append(skill.cooldownAt(rank));
                }
            }
            boolean canRaise = ranks.canRaise(skill.key(), heroLevel);
            line.append("|srank=").append(skill.key()).append(',').append(rank)
                    .append(',').append(skill.maxRank())
                    .append(',').append(canRaise ? "up" : "no")
                    .append(',').append(rankWord(skill, rank, canRaise, words));
            // Every card, every frame: the panel knows which is hovered and the simulation does not.
            tip(skill, rank, canRaise, words.tip());
        }
        return this;
    }

    private static String rankWord(Skill skill, int rank, boolean canRaise, Words words) {
        if (canRaise) {
            return rank + " → " + (rank + 1);
        }
        if (rank >= skill.maxRank()) {
            return words.master();
        }
        return rank > 0 ? rank + words.rankSuffix() : words.locked();
    }

    /** The card over one skill's slot: its name, its key — always, bought or not — its blurb, rows and foot. */
    private void tip(Skill skill, int rank, boolean canRaise, TipWords words) {
        char key = skill.key();
        line.append("|tipName=").append(key).append(',').append(skill.name());
        line.append("|tipAt=").append(key).append(',')
                .append(rank > 0 ? rank + words.rankWord() + " · " + key : String.valueOf(key));
        if (!skill.blurb().isEmpty()) {
            line.append("|tipText=").append(key).append(',').append(skill.blurb());
        }
        rows(skill, rank, canRaise, words);
        line.append("|tipFoot=").append(key).append(',').append(
                rank >= skill.maxRank() ? words.maxed()
                        : canRaise ? words.raiseKey() + key + " · " + words.raise()
                                : words.noPoints());
    }

    /**
     * What a skill is now and what the next point makes it. Unbought, "now" is blank and the arrow points at what the
     * first point buys; a figure a point does not move is drawn with one value, a fact rather than a purchase.
     */
    private void rows(Skill skill, int rank, boolean canRaise, TipWords words) {
        int next = rank + 1;
        boolean unlearnt = rank < 1;
        boolean shows = canRaise && next <= skill.maxRank();
        char key = skill.key();
        if (skill.damage() > 0f) {
            row(key, words.damage(), unlearnt ? "" : round(skill.damageAt(rank)),
                    shows ? round(skill.damageAt(next)) : "");
        }
        if (skill.boostPercent() > 0) {
            row(key, words.boost(), unlearnt ? "" : skill.boostAt(rank) + "%",
                    shows ? skill.boostAt(next) + "%" : "");
        }
        if (skill.radius() > 0f) {
            row(key, words.radius(), round(skill.radius()), "");
        }
        float reach = skill.range() > 0f ? skill.range() : skill.distance();
        if (reach > 0f) {
            row(key, words.range(), round(reach), "");
        }
        if (skill.manaAt(Math.max(1, rank)) > 0) {
            row(key, words.mana(), unlearnt ? "" : String.valueOf(skill.manaAt(rank)),
                    shows && skill.manaCostPerLevel() != 0 ? String.valueOf(skill.manaAt(next)) : "");
        }
        // Seconds, not frames: frames are what the simulation counts, seconds what a player feels.
        String now = unlearnt ? "" : seconds(skill.cooldownAt(rank), words);
        String then = shows && skill.cooldownPerLevel() != 0 ? seconds(skill.cooldownAt(next), words) : "";
        row(key, words.cooldown(), now, then);
    }

    private void row(char key, String label, String now, String next) {
        if (now.isEmpty() && next.isEmpty()) {
            return;
        }
        line.append("|tipRow=").append(key).append(',').append(label)
                .append(',').append(now).append(',').append(next);
    }

    private static String seconds(int frames, TipWords words) {
        float value = frames / (float) GameConstants.LOGICFRAMES_PER_SECOND;
        return String.format(Locale.ROOT, "%.1f", value) + words.seconds();
    }

    /** Whole numbers: a player reads 48, not 48.0, and never 47.99999. */
    private static String round(float value) {
        return String.valueOf(Math.round(value));
    }

    /**
     * Where {@code caster}'s last cast wants drawing, and as what: each of its book's marks, with the frame it was
     * put there so one cast is drawn once, where, how wide, whose the mark is — 0 for the floor's — and who cast it.
     * The client's own field for what it knocks the camera with ({@code SkillEffects}), not the panel's.
     */
    public HeroCard casts(SkillBook book, GameObject caster) {
        for (var mark : book.getCastMarks()) {
            line.append("|cast=").append(mark.look())
                    .append(',').append(book.getCastMarkFrame())
                    .append(',').append(mark.x())
                    .append(',').append(mark.y())
                    .append(',').append(mark.radius())
                    .append(',').append(mark.on().value())
                    .append(',').append(caster == null ? 0 : caster.getId().value());
        }
        return this;
    }

    /**
     * A field of the line that is not the card's — the bars' {@code deep}, {@code boss}, {@code hero} and
     * {@code who}, the floor's {@code look} — which the panel knows and passes over, and the client reads elsewhere.
     */
    public HeroCard field(String name, String value) {
        line.append('|').append(name).append('=').append(value);
        return this;
    }

    /** The line, as the snapshot carries it. */
    @Override
    public String toString() {
        return line.toString();
    }
}
