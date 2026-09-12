package uz.duke.dungeon.run;

import uz.duke.core.module.MoveUpdate;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.ThingTemplate;
import uz.duke.dungeon.ai.Doing;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.dungeon.level.HeroProgress;
import uz.duke.dungeon.power.PowerChoice;
import uz.duke.dungeon.skill.SkillBook;
import uz.duke.dungeon.skill.Skills;

/**
 * Everything the hero's panel shows, as one line of the status channel.
 *
 * <p>The channel is a string the engine carries and never reads, so this is the
 * game talking to its own client and the format is theirs to agree on. It is read
 * by {@code uz.duke.client3d.HeroPanel}, and the two have to be changed together.
 *
 * <pre>
 * name=Erika|rank=7-daraja|hp=128/200|xp=38/100|depth=III|depthWord=CHUQURLIK
 *   |skill=Q,icons/skills/arrowhead.png,ready|skill=W,icons/skills/arrow_cluster.png,cool,72,165
 *   |skill=E,icons/skills/sprint.png,ready|skill=R,icons/skills/hood.png,lock,5-daraja
 *   |pwWord=Kuchlar|pw=shot,2|pw=boot,1
 *   |offer=3,8-daraja,Bittasini tanlang
 *   |opt=shot,O'tkir uch,Q zarari +25%
 * </pre>
 *
 * <p>{@code offer} is the level-up screen: which offer it is, then the two lines
 * of its heading, then one {@code opt} per card. The number is there so a click
 * arriving late cannot spend the next offer's card on the last one's picture —
 * the world is held still behind that screen and the client goes on drawing the
 * snapshot it already has. It counts up through the session rather than naming
 * the level, because a new run starts the levels again.
 *
 * <p>The split between the two halves is: whatever is <em>words</em> is finished
 * here, and whatever is <em>drawn</em> is sent as numbers. So the client never
 * writes a word of its own — it has three other games to serve and no business
 * knowing which language this one speaks — and this class never decides how wide
 * a bar is or how much of a slot is still in shadow.
 *
 * <p>Cooldowns cross as frames, not seconds. Frames are what the simulation
 * counts; the conversion is a presentation decision and belongs on the far side
 * with the rest of them.
 */
final class HeroStatus {

    private HeroStatus() {
    }

    /**
     * Where his last cast wants drawing, and as what.
     *
     * <p>The one thing on this line that is not about the panel. It is here
     * because this IS the game's channel to its own client -- a string the engine
     * carries and never reads -- and because the alternative was widening the
     * engine's own {@code WeaponFired} event, which is shared with games that have
     * never heard of a skill. That event says who fired and where, which is all a
     * muzzle flash needs; it cannot tell a nova from a blink, and the whole point
     * of the effects is that a player can.
     *
     * <p>The frame travels with it so that one cast is drawn once. The line is
     * rebuilt and sent every frame whether or not anything happened, so without
     * it a nova would open its ring thirty times a second for as long as nothing
     * else was cast.
     *
     * <p>Only the hero's. A skeleton mage's fire is drawn by the fire, which is a
     * thing in the world and needs nobody to say so.
     *
     * <p>The last field is WHOSE it is, or 0 for a mark that belongs to the floor.
     * A spot is enough for most of them -- a nova went off there and there it
     * stays -- but not for the ones that draw a state rather than an event: a
     * guard is round the man for as long as it lasts, and one pinned to the
     * flagstone he cast it from stays behind the moment he walks away.
     */
    private static void appendCast(StringBuilder line, SkillBook book) {
        for (var mark : book.getCastMarks()) {
            line.append("|cast=").append(mark.look())
                    .append(',').append(book.getCastMarkFrame())
                    .append(',').append(mark.x())
                    .append(',').append(mark.y())
                    .append(',').append(mark.radius())
                    .append(',').append(mark.on().value());
        }
    }

    /**
     * The card for nobody: the floor, and nothing else.
     *
     * <p>What the bar says when the player has selected nothing. It is not a
     * shorter hero's card — it has no hero on it at all, and the panel answers by
     * taking the portrait, the figures, the bag and the skills off the bar and
     * closing the gap. What is left is what was never about a creature: the map,
     * how far down he is, and anything that has just happened.
     *
     * <p><b>The floor's look has to be here.</b> It is the first line the client
     * ever reads — nothing is selected when a run begins — and it is the line that
     * says which stone this floor is built from. A card that left it out would
     * have the first floor drawn in the wrong kit until something was clicked.
     */
    static String nothing(int depth, int lastDepth, DungeonSettings settings, String note,
            String look) {
        var line = new StringBuilder()
                .append("name=")
                .append("|depth=").append(howFarDown(depth, lastDepth))
                .append("|depthWord=").append(settings.hudDepthWord())
                // The headings belong to the furniture rather than to whoever is
                // selected: the sockets under them are drawn empty, and an empty
                // grid with no heading over it is a hole rather than a bag.
                .append("|itWord=").append(settings.hudItemsWord())
                .append("|skWord=").append(settings.hudSkillsWord());
        // The buttons are furniture too, and stay for the same reason the sockets
        // do. Nothing is selected, so nothing is doing anything and none of them
        // may be pressed -- but four dim buttons are a bar, and a gap is a hole.
        appendOrders(line, settings, null, false);
        if (note != null && !note.isEmpty()) {
            // Finding a sword is worth saying whether or not he is selected.
            line.append("|note=").append(note);
        }
        if (look != null && !look.isBlank()) {
            line.append("|look=").append(look);
        }
        return line.toString();
    }

    /**
     * The card for a creature that is not his: name, what is left of it, and what
     * it hits for.
     *
     * <p>Deliberately shorter than the hero's, and not because it was easier. A
     * skeleton has no experience the player is earning, no skills he can cast and
     * no bag — writing those fields with a monster's numbers in them would be
     * inventing a second hero. It does have ORDERS, which is not the same thing:
     * it is walking or fighting or standing whatever side it is on, and {@code his}
     * is only whether the player may change that. What is left is what is actually worth
     * knowing about a thing across the room: how much of it there is and how hard
     * it hits.
     *
     * <p>The floor's own line stays. Depth belongs to the dungeon rather than to
     * whatever happens to be selected, and a panel whose corner went blank when
     * the player clicked a skeleton would look broken.
     *
     * <p>Worked out here rather than read off the creature because the engine's
     * weapon and locomotor do not hand their numbers back — the same reason the
     * hero's three are worked out — and because what a floor multiplies a monster
     * by is this game's arithmetic. See {@code Spawner.scale}.
     */
    static String creature(GameObject creature, int depth, int lastDepth,
            DungeonSettings settings, String look, boolean his) {
        if (creature == null || creature.getBody() == null) {
            return "";
        }
        var line = new StringBuilder()
                .append("name=").append(nameOf(creature))
                .append("|hp=").append(Math.round(creature.getBody().getHealth()))
                .append('/').append(Math.round(creature.getBody().getMaxHealth()))
                .append("|depth=").append(howFarDown(depth, lastDepth))
                .append("|depthWord=").append(settings.hudDepthWord())
                // Headings again: a skeleton has no bag and no skills, and the
                // empty sockets under these words say so better than a gap would.
                .append("|itWord=").append(settings.hudItemsWord())
                .append("|skWord=").append(settings.hudSkillsWord());
        // It is not his, so he cannot order it -- and it is still DOING something,
        // and that is worth as much across the room as it is under his own feet.
        // Reading a skeleton off the bar is how a player learns to read the bar.
        appendOrders(line, settings, Doing.of(creature, false), his);
        if (!settings.hudMonsterFace().isBlank()) {
            line.append("|face=").append(settings.hudMonsterFace());
        }
        float damage = weaponDamage(creature.getTemplate()) * settings.monsterDamageAt(depth);
        if (damage > 0f) {
            line.append("|stat=").append(settings.hudAttackWord()).append(',')
                    .append(Math.round(damage));
        }
        float speed = walkingSpeed(creature.getTemplate());
        if (speed > 0f) {
            line.append("|stat=").append(settings.hudSpeedWord()).append(',')
                    .append(Math.round(speed));
        }
        if (look != null && !look.isBlank()) {
            line.append("|look=").append(look);
        }
        return line.toString();
    }

    /**
     * The same, told whether he has been ordered to hold his ground.
     *
     * <p>The one thing on the panel that is neither a number on the hero nor a
     * word in the file: a standing order lives beside the brain that obeys it —
     * see {@code uz.duke.dungeon.ai.Orders} — and has to be carried in from there.
     */
    static String of(GameObject hero, HeroProgress progress, int depth, int lastDepth,
            DungeonSettings settings, PowerChoice powers, int frame, String look,
            boolean holding, uz.duke.dungeon.skill.SkillRanks learnt) {
        if (hero == null || hero.getBody() == null) {
            return "";
        }
        int level = progress.getLevel();
        var line = new StringBuilder()
                .append("name=").append(nameOf(hero))
                .append("|title=").append(titleOf(hero, settings))
                .append("|rank=").append(level).append(settings.hudRankSuffix())
                .append("|hp=").append(Math.round(hero.getBody().getHealth()))
                .append('/').append(Math.round(hero.getBody().getMaxHealth()))
                .append("|xp=").append(progress.getExperienceIntoLevel())
                .append('/').append(progress.getExperienceForNextLevel())
                .append("|depth=").append(howFarDown(depth, lastDepth))
                .append("|depthWord=").append(settings.hudDepthWord());
        appendStats(line, hero, progress, powers, settings);
        appendOrders(line, settings, Doing.of(hero, holding), true);
        appendItems(line, progress, settings);
        var book = hero.findModule(SkillBook.class);
        if (book != null) {
            line.append("|skWord=").append(settings.hudSkillsWord());
            line.append(Skills.slots(book, learnt, level,
                    new Skills.Words(settings.hudRankSuffix(), settings.hudMasterWord(),
                            settings.hudLockedWord(),
                            new uz.duke.dungeon.skill.SkillTip.Words(
                                    settings.hudDamageWord(), settings.hudCooldownWord(),
                                    settings.hudRadiusWord(), settings.hudRangeWord(),
                                    settings.hudBoostWord(), settings.hudRaiseWord(),
                                    settings.hudRaiseKeyWord(),
                                    settings.hudMaxedWord(), settings.hudNoPointsWord(),
                                    settings.hudRankSuffix(), settings.hudSecondsWord())),
                    settings::hudIcon));
            // How many levels he has not spent yet, and the word for them. Beside
            // the heading rather than on a slot, because it belongs to none of
            // them: it is what the four are competing for.
            line.append("|pts=").append(learnt.unspent(level))
                    .append(',').append(settings.hudPointsWord());
            appendCast(line, book);
        }
        appendPowers(line, powers, settings);
        appendOffer(line, powers, settings);
        // What he just picked up, for as long as it is worth saying. A line rather
        // than a banner: the banner interrupts, and finding a sword is news, not
        // an interruption.
        var found = progress.getLoot().noteAt(frame);
        if (!found.isEmpty()) {
            line.append("|note=").append(found);
        }
        // How this floor is drawn: a theme and one of its variations, by name. The
        // client keeps every look it was given at launch and is only ever told
        // which of them is current -- see Visuals.theme.
        if (look != null && !look.isBlank()) {
            line.append("|look=").append(look);
        }
        return line.toString();
    }

    /**
     * The strip of what he has picked up: one field per power, in the order they
     * were taken, each an icon and how many of it he holds.
     *
     * <p>Sent as a count rather than as repeated entries so that three of the same
     * card is one mark reading three, which is what the strip has room for.
     */
    private static void appendPowers(StringBuilder line, PowerChoice powers,
            DungeonSettings settings) {
        if (powers == null) {
            return;
        }
        line.append("|pwWord=").append(settings.hudPowersWord());
        // Insertion-ordered, so the strip lists them in the order he took them
        // and not in whatever order a hash happens to produce.
        var held = new java.util.LinkedHashMap<String, int[]>();
        var icons = new java.util.LinkedHashMap<String, String>();
        for (var power : powers.getBook().getTaken()) {
            held.computeIfAbsent(power.id(), id -> new int[1])[0]++;
            icons.putIfAbsent(power.id(), power.icon());
        }
        for (var entry : held.entrySet()) {
            line.append("|pw=").append(icons.get(entry.getKey()))
                    .append(',').append(entry.getValue()[0]);
        }
    }

    /**
     * The four orders on the buttons beside the map: key, drawing, word, state.
     *
     * <p>Sent rather than assumed because three of the four are the engine's and
     * one is this game's, and the client has no way of knowing which orders a game
     * offers — the same reason the skill row is sent rather than assumed. The keys
     * match what {@code Main.controls} claims, and the words come out of the file
     * with every other word on the panel.
     *
     * <p><b>Every one of them has a state worth sending</b>, which is the thing
     * this used to miss. They were four things to press and nothing else, and that
     * is half a control: each is also a state the creature can be in, and exactly
     * one of the four is true at any moment -- see {@link Doing}. Nobody plays by
     * clicking them, so what earns them their space on the bar is what they can
     * tell him.
     *
     * <p>{@code his} is whether the player may press them at all. A creature that
     * is not his still SHOWS what it is doing -- reading a skeleton's intent off
     * the bar is worth as much as reading his own -- and the buttons go dim,
     * because a lit button that does nothing is worse than no button.
     */
    private static void appendOrders(StringBuilder line, DungeonSettings settings,
            Doing doing, boolean his) {
        var words = settings.hudOrderWords();
        // Drawn in this order and read in this order — walk, attack, stop, guard —
        // which is the order the words come in the file and the order Doing counts
        // its four states in. The LETTERS are not in that order and are not meant
        // to be: A S D are attack, stop and defend, under a hand that never leaves
        // them, and walking takes the letter left over because it is the one order
        // nobody uses the keyboard for.
        String[][] buttons = {
            {"F", "march"}, {"A", "blade"}, {"S", "halt"}, {"D", "shield"},
        };
        line.append("|cmds=").append(his ? "mine" : "theirs");
        for (int i = 0; i < buttons.length; i++) {
            String word = i < words.size() ? words.get(i) : "";
            if (word.isBlank()) {
                continue; // a game that does not name an order does not offer it
            }
            line.append("|cmd=").append(buttons[i][0]).append(',').append(buttons[i][1])
                    .append(',').append(word)
                    .append(',')
                    .append(doing != null && doing.button() == i ? "on" : "off");
        }
    }

    /**
     * What he is carrying, as one field per kind of thing with how many of it.
     *
     * <p>The bag is already there — every item he picks up goes into it and its
     * totals are what the figures under the bars are worked out from — so this
     * shows what the game already knows rather than inventing an inventory. He
     * cannot use or drop any of it yet; what the grid says today is "these are the
     * things that made you stronger", which is what finding them means.
     *
     * <p>Grouped and counted like the powers strip beside it, and for the same
     * reason: three of the same sword is one drawing reading three, which is what
     * six sockets have room for.
     */
    private static void appendItems(StringBuilder line, HeroProgress progress,
            DungeonSettings settings) {
        line.append("|itWord=").append(settings.hudItemsWord());
        var held = new java.util.LinkedHashMap<String, int[]>();
        var icons = new java.util.LinkedHashMap<String, String>();
        for (var item : progress.getLoot().getFound()) {
            held.computeIfAbsent(item.id(), id -> new int[1])[0]++;
            icons.putIfAbsent(item.id(), item.icon());
        }
        for (var entry : held.entrySet()) {
            line.append("|it=").append(icons.get(entry.getKey()))
                    .append(',').append(entry.getValue()[0]);
        }
    }

    /** The cards on the table, or nothing at all when none are. */
    private static void appendOffer(StringBuilder line, PowerChoice powers,
            DungeonSettings settings) {
        if (powers == null || !powers.hasOffer()) {
            return;
        }
        line.append("|offer=").append(powers.getOfferId())
                .append(',').append(powers.getOfferLevel()).append(settings.hudRankSuffix())
                .append(',').append(settings.hudChooseWord());
        for (var power : powers.getOffer()) {
            line.append("|opt=").append(power.icon())
                    .append(',').append(power.name())
                    .append(',').append(power.description());
        }
    }

    /**
     * The three figures under the bars: what he hits for, what he shrugs off, and
     * how fast he moves.
     *
     * <p>Worked out rather than read off the hero, because the engine's weapon and
     * locomotor do not hand their numbers back — and because what a level is worth
     * is this game's arithmetic anyway. The base of each comes from his template,
     * so {@code creatures.ini} stays the one place the starting hero is written.
     *
     * <p>Everything that moves them is counted: the level, the powers he chose and
     * what he found on the floor. A panel that showed only two of the three would
     * be a panel a player learns not to believe.
     */
    private static void appendStats(StringBuilder line, GameObject hero, HeroProgress progress,
            PowerChoice powers, DungeonSettings settings) {
        var rules = settings.levelling();
        var found = progress.getLoot();
        int level = progress.getLevel();
        float attack = weaponDamage(hero.getTemplate())
                * (rules.damageMultiplier(level) + found.attackPercent() / 100f);
        // What he was born in counts with what he has found, exactly as the body
        // counts it — otherwise the figure on the panel is not the one taking the
        // blows, and a knight reads as an archer in a shirt.
        int worn = settings.heroNamed(hero.getTemplate().getName()).armourPercent();
        int armour = Math.round(
                (1f - rules.damageTakenWith(level, worn + found.armourPercent())) * 100f);
        float speed = walkingSpeed(hero.getTemplate())
                * (powers == null ? 1f : powers.getBook().moveSpeedMultiplier());
        // What he would have without anything he found or chose. The difference is
        // the number in green, and it is worth showing on its own: a figure that
        // only goes up says nothing about whether the last thing he picked up was
        // worth picking up.
        float bareAttack = weaponDamage(hero.getTemplate()) * rules.damageMultiplier(level);
        // His own plate is not borrowed, so it belongs on both sides of the sum:
        // the green figure is what he picked up, not what he was made with.
        int bareArmour = Math.round((1f - rules.damageTakenWith(level, worn)) * 100f);
        float bareSpeed = walkingSpeed(hero.getTemplate());
        stat(line, settings.hudAttackWord(), Math.round(attack), Math.round(bareAttack));
        stat(line, settings.hudArmourWord(), armour, bareArmour);
        stat(line, settings.hudSpeedWord(), Math.round(speed), Math.round(bareSpeed));
    }

    /** One figure: its word, what it is now, and how much of that is borrowed. */
    private static void stat(StringBuilder line, String word, int now, int bare) {
        line.append("|stat=").append(word).append(',').append(now);
        if (now != bare) {
            line.append(',').append(now > bare ? "+" : "").append(now - bare);
        }
    }

    private static float weaponDamage(ThingTemplate template) {
        for (var entry : template.getModules()) {
            if (entry.data() instanceof uz.duke.rts.module.WeaponUpdate.Data weapon) {
                return weapon.damage();
            }
        }
        return 0f;
    }

    private static float walkingSpeed(ThingTemplate template) {
        for (var entry : template.getModules()) {
            if (entry.data() instanceof MoveUpdate.Data move) {
                return move.speedPerSecond();
            }
        }
        return 0f;
    }

    /**
     * What he is, under his name.
     *
     * <p>His own block's word, and the panel's only if he has none. It used to be
     * the panel's outright — one line in {@code DungeonHud} — which was right
     * while there was one hero and became the archer's title on a knight the
     * moment there were two.
     */
    private static String titleOf(GameObject hero, DungeonSettings settings) {
        var his = settings.heroNamed(hero.getTemplate().getName()).title();
        return his == null || his.isBlank() ? settings.hudHeroTitle() : his;
    }

    /** What the player calls him, falling back to what the code calls him. */
    private static String nameOf(GameObject hero) {
        var display = hero.getTemplate().getDisplayName();
        return display == null || display.isBlank() ? hero.getTemplate().getName() : display;
    }

    private static final int[] VALUES = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
    private static final String[] NUMERALS = {
        "M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I",
    };

    /**
     * How far down he is, and — when the descent has a bottom — how far down it
     * goes.
     *
     * <p>Worth the second numeral. A floor with nothing under it is a floor the
     * player should be able to recognise <em>before</em> he beats it, and the
     * panel has no other way to say so: III on its own is a number that only ever
     * goes up, which is exactly the thing that is no longer true.
     *
     * <p>Composed here rather than sent as two fields, for the same reason every
     * other word on the panel is: the client draws four games and has no idea how
     * any of them counts its floors.
     *
     * <p>Told how deep it goes rather than reading it off the settings, because
     * that is a property of what is being played: a stage is one floor deep
     * however long the file's list of bosses is, and a panel promising nine more
     * floors that do not exist is a panel lying to the player. See {@code Floors}.
     */
    private static String howFarDown(int depth, int lastDepth) {
        return lastDepth > 0
                ? roman(depth) + " / " + roman(lastDepth)
                : roman(depth);
    }

    /**
     * Depth in Roman numerals, because it is the one number in the game that only
     * ever goes up, and a numeral says that in a way a digit does not.
     *
     * <p>Anything a numeral cannot say — nothing at all, or more floors than Rome
     * could count — is given back as a digit rather than as a wrong numeral.
     */
    static String roman(int depth) {
        if (depth < 1 || depth > 3999) {
            return String.valueOf(depth);
        }
        var numeral = new StringBuilder();
        int left = depth;
        for (int i = 0; i < VALUES.length; i++) {
            while (left >= VALUES[i]) {
                numeral.append(NUMERALS[i]);
                left -= VALUES[i];
            }
        }
        return numeral.toString();
    }
}
