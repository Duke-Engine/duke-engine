package uz.dukeengine.client3d.rpg;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import uz.dukeengine.core.GameConstants;

/**
 * The status line, read. Nothing here knows what a skill does — only what the
 * slot has to look like — which is the whole reason the game sends finished
 * words and the client sends none.
 */
record PanelReading(String name, String title, String face, String rank,
        float health, float maxHealth,
        float experience, float needed, String depth, String depthWord,
        String skillsWord, String itemsWord, String note,
        List<Stat> stats, List<SkillReading> skills,
        List<ItemReading> items, List<OrderReading> orders, boolean ordersAreHis,
        List<RankReading> ranks, int points, String pointsWord,
        Map<Character, SkillTip.Reading> tips,
        float mana, float maxMana, int refusedForManaAt, List<CostReading> costs,
        List<Stat> attributes, Map<Integer, SkillTip.Reading> attributeTips) {

    /**
     * What a slot costs to cast, and whether he can pay it.
     *
     * <p>Its own field for the reason the rank beside it is: the slot's own
     * fields are positional and already end in two optional ones, so a fourth
     * thing inside them would be a fourth shape to get wrong. A game that
     * charges nothing sends none of these and the slots are drawn as they
     * always were.
     */
    record CostReading(char key, int cost, boolean affordable) {
    }

    /**
     * What is in a slot, what fits in it, and whether the next point may go
     * there.
     *
     * <p>Its own field on the line rather than three more on the slot's,
     * which already has three shapes. Nothing here knows what a rank DOES —
     * only what the pips under the socket have to look like.
     */
    record RankReading(char key, int rank, int max, boolean canRaise, String word) {
    }

    enum State { READY, COOLING, LOCKED, PASSIVE }

    /**
     * One slot: its key, the picture to draw in it, its state, the words on it,
     * and how much shadow is left.
     *
     * <p>{@code icon} is a path to an image, given by the game, and empty when
     * the game named none — which is not a failure: a slot with no picture is
     * drawn with the letter of its key, as every slot was before there were any
     * pictures. The client never invents one, because it has three other games
     * to serve and no idea where any of them keeps its art.
     */
    record SkillReading(char key, String icon, State state, String label, float left) {
    }

    /**
     * One figure under the bars: what it is called, what it is, and how much
     * of that he borrowed.
     *
     * <p>{@code bonus} is empty when nothing is lent. It is a finished word
     * rather than a number because the sign is part of it and the panel is not
     * in the business of deciding whether a thing that went down is good news.
     *
     * <p>{@code icon} is a path to a picture, and empty for a game that names
     * none. It comes down the wire with the figure rather than being chosen
     * here by which figure this is, which is what the panel used to do — and
     * which quietly decided that a game's third statistic is a lightning bolt.
     *
     * <p>{@code primary} marks the one attribute that is also his blow, and is
     * drawn in gold. A game with no such idea never sends the mark.
     */
    record Stat(String word, String value, String bonus, String icon, boolean primary) {

        Stat(String word, String value, String bonus, String icon) {
            this(word, value, bonus, icon, false);
        }
    }

    /** One socket in his bag: which drawing, and how many of it he carries. */
    record ItemReading(String icon, int count) {
    }

    /**
     * One button beside the map: its key, its drawing, its word, and whether
     * it is a standing order that is currently on.
     */
    record OrderReading(char key, String icon, String word, boolean on) {
    }

    static PanelReading parse(String status) {
        if (status == null || !status.startsWith("name=")) {
            return null;
        }
        String name = "";
        String title = "";
        String face = "";
        String rank = "";
        String depth = "";
        String depthWord = "";
        String skillsWord = "";
        String itemsWord = "";
        String note = "";
        var health = new float[] {0f, 0f};
        var experience = new float[] {0f, 0f};
        var skills = new ArrayList<SkillReading>();
        var stats = new ArrayList<Stat>();
        var items = new ArrayList<ItemReading>();
        var orders = new ArrayList<OrderReading>();
        var ordersAreHis = new boolean[] {false};
        var ranks = new ArrayList<RankReading>();
        var costs = new ArrayList<CostReading>();
        var tips = new java.util.LinkedHashMap<Character, SkillTip.Reading>();
        var attributes = new ArrayList<Stat>();
        var attributeTips = new java.util.LinkedHashMap<Integer, SkillTip.Reading>();
        float[] mana = null;
        var refusedAt = new int[] {0};
        int points = 0;
        String pointsWord = "";
        for (var field : status.split("\\|")) {
            int split = field.indexOf('=');
            if (split < 0) {
                return null;
            }
            var value = field.substring(split + 1);
            switch (field.substring(0, split)) {
                case "name" -> name = value;
                case "title" -> title = value;
                case "face" -> face = value;
                case "rank" -> rank = value;
                case "depth" -> depth = value;
                case "depthWord" -> depthWord = value;
                case "skWord" -> skillsWord = value;
                case "itWord" -> itemsWord = value;
                case "it" -> items.add(item(value));
                case "cmd" -> orders.add(order(value));
                // Whether the player may press them at all. One field for the
                // four of them, because it is a fact about whose creature is
                // selected rather than about any one button.
                case "cmds" -> ordersAreHis[0] = "mine".equals(value);
                case "note" -> note = value;
                case "hp" -> health = pair(value);
                case "xp" -> experience = pair(value);
                case "skill" -> skills.add(skill(value));
                case "mana" -> mana = pair(value);
                case "noMana" -> refusedAt[0] = whole(value);
                case "cost" -> costs.add(cost(value));
                case "stat" -> stats.add(stat(value));
                // An attribute: the four fields a figure has, and a mark on his
                // primary. A field of its own because the block draws the two apart
                // -- figures down the left, attributes down the right.
                case "attr" -> attributes.add(stat(value));
                // "rank" above is the HERO's -- "7-daraja". This is a slot's,
                // which is a different thing on a different row, so it gets a
                // name of its own rather than a cleverness.
                case "srank" -> ranks.add(skillRank(value));
                // The card over a slot, in five kinds of field. Five rather
                // than one long one because they are five different shapes,
                // and packing them into a single string would want an escape
                // scheme for a saving nobody asked for.
                case "tipName" -> tip(tips, value, PanelReading::tipNamed);
                case "tipAt" -> tip(tips, value, PanelReading::tipAt);
                case "tipText" -> tip(tips, value, PanelReading::tipBlurb);
                case "tipFoot" -> tip(tips, value, PanelReading::tipFoot);
                case "tipRow" -> tip(tips, value, PanelReading::tipRow);
                // The same card over an attribute, in the same five kinds of field,
                // keyed by the attribute's place in the game's list: an attribute
                // has no key he presses.
                case "atTipName" -> attributeTip(attributeTips, value, PanelReading::tipNamed);
                case "atTipAt" -> attributeTip(attributeTips, value, PanelReading::tipAt);
                case "atTipText" -> attributeTip(attributeTips, value, PanelReading::tipBlurb);
                case "atTipRow" -> attributeTip(attributeTips, value, PanelReading::tipRow);
                case "atTipFoot" -> attributeTip(attributeTips, value, PanelReading::tipFoot);
                case "pts" -> {
                    var halves = value.split(",", 2);
                    points = Integer.parseInt(halves[0]);
                    pointsWord = halves.length > 1 ? halves[1] : "";
                }
                // How the floor is drawn. Nothing on the panel, but the line
                // is one line: a field this panel has no picture for still has
                // to be a field it recognises, or it would refuse the whole
                // thing as somebody else's.
                // What a skill looked like going off. Nothing on the panel, but
                // the line is one line: see "look" above.
                case "cast" -> { }
                case "look" -> { }
                // The floor, and what is over everybody else's head. Nothing
                // on the panel either, and read by UnitBars off the same
                // line: see "look" above. The panel has to KNOW them all the
                // same, since one field it does not recognise is the whole
                // line refused as somebody else's game.
                case "deep" -> { }
                case "boss" -> { }
                case "hero" -> { }
                case "who" -> { }
                case "letter" -> { } // the slot's letter, read by the panel itself: see letters
                default -> {
                    return null; // a field this client does not know: not ours
                }
            }
            if (health == null || experience == null || skills.contains(null)
                    || costs.contains(null)
                    || stats.contains(null) || attributes.contains(null)
                    || items.contains(null) || orders.contains(null)) {
                return null;
            }
        }
        return new PanelReading(name, title, face, rank, health[0], health[1],
                experience[0], experience[1], depth, depthWord, skillsWord,
                itemsWord, note, List.copyOf(stats), List.copyOf(skills),
                List.copyOf(items), List.copyOf(orders),
                ordersAreHis[0], List.copyOf(ranks),
                points, pointsWord, withRaising(tips, ranks),
                mana == null ? 0f : mana[0], mana == null ? 0f : mana[1],
                refusedAt[0], List.copyOf(costs), List.copyOf(attributes),
                withFootLit(attributeTips));
    }

    /**
     * An attribute's footer is neither an offer nor a refusal — it says how fast he
     * is now — so it is drawn in the card's gold whenever there is one.
     */
    private static Map<Integer, SkillTip.Reading> withFootLit(
            Map<Integer, SkillTip.Reading> tips) {
        var out = new java.util.LinkedHashMap<Integer, SkillTip.Reading>();
        for (var entry : tips.entrySet()) {
            var was = entry.getValue();
            out.put(entry.getKey(), new SkillTip.Reading(was.name(), was.at(), was.blurb(),
                    was.rows(), was.foot(), !was.foot().isEmpty()));
        }
        return Map.copyOf(out);
    }

    private static SkillTip.Reading tipNamed(SkillTip.Reading was, String rest) {
        return new SkillTip.Reading(rest, was.at(), was.blurb(), was.rows(), was.foot(),
                was.canRaise());
    }

    private static SkillTip.Reading tipAt(SkillTip.Reading was, String rest) {
        return new SkillTip.Reading(was.name(), rest, was.blurb(), was.rows(), was.foot(),
                was.canRaise());
    }

    private static SkillTip.Reading tipBlurb(SkillTip.Reading was, String rest) {
        return new SkillTip.Reading(was.name(), was.at(), rest, was.rows(), was.foot(),
                was.canRaise());
    }

    private static SkillTip.Reading tipFoot(SkillTip.Reading was, String rest) {
        return new SkillTip.Reading(was.name(), was.at(), was.blurb(), was.rows(), rest,
                was.canRaise());
    }

    private static SkillTip.Reading tipRow(SkillTip.Reading was, String rest) {
        var parts = rest.split(",", 3);
        if (parts.length < 3) {
            return was;
        }
        var rows = new ArrayList<>(was.rows());
        rows.add(new SkillTip.Reading.Row(parts[0], parts[1], parts[2]));
        return new SkillTip.Reading(was.name(), was.at(), was.blurb(), List.copyOf(rows),
                was.foot(), was.canRaise());
    }

    /** An attribute's card field: {@code <place>,<the rest>}. */
    private static void attributeTip(Map<Integer, SkillTip.Reading> tips, String value,
            java.util.function.BiFunction<SkillTip.Reading, String, SkillTip.Reading> change) {
        var halves = value.split(",", 2);
        if (halves.length < 2) {
            return;
        }
        int place;
        try {
            place = Integer.parseInt(halves[0].trim());
        } catch (NumberFormatException notAPlace) {
            return;
        }
        tips.put(place, change.apply(tips.getOrDefault(place, SkillTip.Reading.NONE),
                halves[1]));
    }

    /**
     * {@code Q,22,yes} — the key, what it costs at its present rank, and
     * whether the pool has that much in it right now.
     */
    private static CostReading cost(String value) {
        var parts = value.split(",");
        if (parts.length < 3 || parts[0].isEmpty()) {
            return null;
        }
        try {
            return new CostReading(parts[0].charAt(0), Integer.parseInt(parts[1].trim()),
                    "yes".equals(parts[2].trim()));
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    /** A single whole number, or 0 for one that will not parse. */
    private static int whole(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException notANumber) {
            return 0;
        }
    }

    private static float[] pair(String value) {
        var halves = value.split("/");
        if (halves.length != 2) {
            return null;
        }
        try {
            return new float[] {Float.parseFloat(halves[0]), Float.parseFloat(halves[1])};
        } catch (NumberFormatException _) {
            return null;
        }
    }

    /**
     * {@code Q,icon,ready} — {@code W,icon,cool,72,165} — {@code R,icon,lock,5-daraja}.
     *
     * <p>The icon comes second because it belongs to the slot rather than to
     * the state: a skill's picture does not change when it goes on cooldown,
     * and putting it before the state keeps the three states the same shape as
     * each other. It may be empty.
     */
    private static SkillReading skill(String value) {
        var parts = value.split(",", -1);
        if (parts.length < 3 || parts[0].length() != 1) {
            return null;
        }
        char key = parts[0].charAt(0);
        String icon = parts[1];
        try {
            return switch (parts[2]) {
                case "ready" -> new SkillReading(key, icon, State.READY, "", 0f);
                case "passive" -> new SkillReading(key, icon, State.PASSIVE, "", 0f);
                // An ordinary skill nobody has bought says "lock" and stops
                // there: it waits for a POINT rather than for a level, so
                // there is no level to name. Only an ultimate has one.
                case "lock" -> parts.length < 4
                        ? new SkillReading(key, icon, State.LOCKED, "", 0f)
                        : new SkillReading(key, icon, State.LOCKED, parts[3], 1f);
                case "cool" -> parts.length < 5 ? null : cooling(key, icon,
                        Integer.parseInt(parts[3]), Integer.parseInt(parts[4]));
                default -> null;
            };
        } catch (NumberFormatException _) {
            return null;
        }
    }

    /** {@code Zarba,34} — a word and whatever the game counts under it. */
    private static Stat stat(String value) {
        var parts = value.split(",", -1);
        return parts.length < 2 ? null
                : new Stat(parts[0], parts[1], parts.length > 2 ? parts[2] : "",
                        parts.length > 3 ? parts[3] : "",
                        parts.length > 4 && "primary".equals(parts[4]));
    }

    /** {@code flask,3} — which drawing is in the socket, and how many of it. */
    private static ItemReading item(String value) {
        var parts = value.split(",");
        if (parts.length < 2) {
            return null;
        }
        try {
            return new ItemReading(parts[0], Integer.parseInt(parts[1]));
        } catch (NumberFormatException _) {
            return null;
        }
    }

    /** {@code A,march,Yur,off} — key, drawing, word, and whether it is lit. */
    private static OrderReading order(String value) {
        var parts = value.split(",", -1);
        if (parts.length < 4 || parts[0].length() != 1) {
            return null;
        }
        return new OrderReading(parts[0].charAt(0), parts[1], parts[2],
                "on".equals(parts[3]));
    }

    /**
     * Change the card for one key, whatever the field was carrying.
     *
     * <p>Every tip field is {@code <key>,<the rest>}, so the key comes off
     * the front and what is left belongs to whichever part of the card the
     * field was. A key with no card yet gets an empty one to build on, since
     * the fields arrive in whatever order the game wrote them.
     */
    private static void tip(Map<Character, SkillTip.Reading> tips, String value,
            java.util.function.BiFunction<SkillTip.Reading, String, SkillTip.Reading> change) {
        var halves = value.split(",", 2);
        if (halves.length < 2 || halves[0].isEmpty()) {
            return;
        }
        char key = halves[0].charAt(0);
        tips.put(key, change.apply(tips.getOrDefault(key, SkillTip.Reading.NONE), halves[1]));
    }

    /**
     * Whether each card's footer is an offer or a refusal.
     *
     * <p>Known from the slot's own rank field rather than said twice: the
     * card and the badge are answering one question, and two fields that
     * could disagree about it would eventually disagree about it.
     */
    private static Map<Character, SkillTip.Reading> withRaising(
            Map<Character, SkillTip.Reading> tips, List<RankReading> ranks) {
        var out = new java.util.LinkedHashMap<Character, SkillTip.Reading>();
        for (var entry : tips.entrySet()) {
            boolean raising = ranks.stream().anyMatch(rank -> rank != null
                    && rank.key() == entry.getKey() && rank.canRaise());
            var was = entry.getValue();
            out.put(entry.getKey(), new SkillTip.Reading(was.name(), was.at(), was.blurb(),
                    was.rows(), was.foot(), raising));
        }
        return Map.copyOf(out);
    }

    /** {@code <key>,<rank>,<max>,up|no} — what the pips and the badge need. */
    private static RankReading skillRank(String value) {
        var parts = value.split(",");
        if (parts.length < 5 || parts[0].isEmpty()) {
            return null;
        }
        return new RankReading(parts[0].charAt(0), Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]), "up".equals(parts[3]), parts[4]);
    }

    /**
     * Frames on the wire, seconds on the screen. The simulation counts frames
     * and must, but a player reading a number off a slot thinks in seconds —
     * and the conversion is the engine's own constant, not a second copy of it.
     */
    private static SkillReading cooling(char key, String icon, int left, int total) {
        float seconds = left / (float) GameConstants.LOGICFRAMES_PER_SECOND;
        var label = seconds >= 10f
                ? String.valueOf(Math.round(seconds))
                : String.format(java.util.Locale.ROOT, "%.1f", seconds);
        return new SkillReading(key, icon, State.COOLING, label,
                total <= 0 ? 0f : left / (float) total);
    }
}
