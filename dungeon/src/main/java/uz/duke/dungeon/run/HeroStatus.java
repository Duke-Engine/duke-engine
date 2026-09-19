package uz.duke.dungeon.run;

import uz.duke.core.module.MoveUpdate;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.ThingTemplate;
import uz.duke.dungeon.ai.Doing;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.dungeon.level.Attribute;
import uz.duke.dungeon.level.AttributeRules;
import uz.duke.dungeon.level.HeroFigures;
import uz.duke.dungeon.level.HeroProgress;
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
 * </pre>
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
     *
     * <p>And then WHO cast it, which is not the same question. The field above
     * says where the mark belongs and is nobody for a meteor, since a meteor
     * belongs to the patch of floor it is going to land on -- so it cannot also
     * say which creature to draw making the gesture. They are two facts and they
     * are different for every skill that is aimed away from the caster.
     */
    private static void appendCast(StringBuilder line, SkillBook book, GameObject caster) {
        for (var mark : book.getCastMarks()) {
            line.append("|cast=").append(mark.look())
                    .append(',').append(book.getCastMarkFrame())
                    .append(',').append(mark.x())
                    .append(',').append(mark.y())
                    .append(',').append(mark.radius())
                    .append(',').append(mark.on().value())
                    .append(',').append(caster == null ? 0 : caster.getId().value());
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
                .append("|depthWord=").append(settings.hud().depthWord())
                // The headings belong to the furniture rather than to whoever is
                // selected: the sockets under them are drawn empty, and an empty
                // grid with no heading over it is a hole rather than a bag.
                .append("|itWord=").append(settings.hud().itemsWord())
                .append("|skWord=").append(settings.hud().skillsWord());
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
                .append("|depthWord=").append(settings.hud().depthWord())
                // Headings again: a skeleton has no bag and no skills, and the
                // empty sockets under these words say so better than a gap would.
                .append("|itWord=").append(settings.hud().itemsWord())
                .append("|skWord=").append(settings.hud().skillsWord());
        // It is not his, so he cannot order it -- and it is still DOING something,
        // and that is worth as much across the room as it is under his own feet.
        // Reading a skeleton off the bar is how a player learns to read the bar.
        appendOrders(line, settings, Doing.of(creature, false), his);
        if (!settings.hud().monsterFace().isBlank()) {
            line.append("|face=").append(settings.hud().monsterFace());
        }
        var pictures = settings.hud().statIcons();
        float damage = weaponDamage(creature.getTemplate()) * settings.monsterDamageAt(depth);
        if (damage > 0f) {
            stat(line, settings.hud().attackWord(), Math.round(damage), Math.round(damage),
                    pictures.get(0));
        }
        float speed = walkingSpeed(creature.getTemplate());
        if (speed > 0f) {
            stat(line, settings.hud().speedWord(), Math.round(speed), Math.round(speed),
                    pictures.get(2));
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
    /**
     * What the bars over everybody's heads need, which is not much.
     *
     * <p>Appended to whichever card is being sent, because it is about the FLOOR
     * rather than about whatever is selected: the bars are drawn over every
     * creature in sight whether or not the player has clicked on one.
     *
     * <p><b>Why it is here and not in {@code UnitView}.</b> The snapshot already
     * carries what a bar mostly needs — where a creature is, whose it is, and
     * what is left of it. What it does not carry is a level, a pool of mana, a
     * name worth printing, or which of them is the boss, and adding four fields
     * to the engine's own view would put one game's vocabulary in a record three
     * other games are handed. The status channel is the seam that exists for
     * exactly this: a string the engine carries and never reads.
     *
     * <p><b>And it collapses.</b> Written per creature this would be a line of
     * fifty entries rebuilt thirty times a second. It is not, because none of the
     * four facts is really per creature:
     *
     * <ul>
     * <li>the level is the DEPTH — one number for the whole floor. A monster is
     *     scaled by it (see {@code Spawner.scale}) and a stage's difficulty is
     *     defined as it (see {@code Stage}), so it is not a stand-in for a level,
     *     it <em>is</em> the level
     * <li>the boss is one id
     * <li>the name belongs to the TEMPLATE, so it is a dictionary of about a
     *     dozen rather than one entry a creature
     * <li>and mana belongs to a {@code SkillBook}'s pool, which in this game only a
     *     hero draws from — a monster's skills cost it nothing — so it is one entry
     * </ul>
     *
     * <p>The ring around the hero's medallion is his experience, and only his:
     * he is the only thing in the game that earns any. An empty ring on a
     * skeleton would be a promise that it could fill.
     */
    static String world(uz.duke.core.GameLogic logic, GameObject hero, HeroProgress progress,
            int depth, uz.duke.core.thing.ObjectId bossId, DungeonSettings settings) {
        var line = new StringBuilder("|deep=").append(depth);
        if (bossId != null) {
            line.append("|boss=").append(bossId.value());
        }
        if (hero != null && progress != null) {
            var book = hero.findModule(SkillBook.class);
            line.append("|hero=").append(hero.getId().value())
                    .append(',').append(progress.getLevel())
                    .append(',').append(book == null ? 0 : book.getMana())
                    .append(',').append(book == null ? 0 : book.getMaxMana())
                    .append(',').append(progress.getExperienceIntoLevel())
                    .append(',').append(progress.getExperienceForNextLevel());
        }
        // The dictionary. Every kind the file names rather than every creature in
        // sight: the same dozen entries whatever is on the floor, and no work at
        // all that depends on how busy the fight is.
        for (var kind : settings.monsters()) {
            named(line, logic, kind.name());
        }
        for (var look : settings.heroes()) {
            named(line, logic, look.name());
        }
        return line.toString();
    }

    /** One template's printed name, skipped when it has none worth printing. */
    private static void named(StringBuilder line, uz.duke.core.GameLogic logic,
            String template) {
        var found = logic.getThingFactory().findTemplate(template);
        if (found == null) {
            return;
        }
        var display = uz.duke.core.thing.Titled.of(found);
        if (display == null || display.isBlank() || display.equals(template)) {
            return; // nothing the client could not have worked out from the name
        }
        line.append("|who=").append(template).append(',').append(display);
    }

    static String of(GameObject hero, HeroProgress progress, int depth, int lastDepth,
            DungeonSettings settings, int frame, String look,
            boolean holding, uz.duke.dungeon.skill.SkillRanks learnt) {
        if (hero == null || hero.getBody() == null) {
            return "";
        }
        int level = progress.getLevel();
        var line = new StringBuilder()
                .append("name=").append(nameOf(hero))
                .append("|title=").append(titleOf(hero, settings))
                .append("|rank=").append(level).append(settings.hud().rankSuffix())
                .append("|hp=").append(Math.round(hero.getBody().getHealth()))
                .append('/').append(Math.round(hero.getBody().getMaxHealth()))
                .append("|xp=").append(progress.getExperienceIntoLevel())
                .append('/').append(progress.getExperienceForNextLevel())
                .append("|depth=").append(howFarDown(depth, lastDepth))
                .append("|depthWord=").append(settings.hud().depthWord());
        appendStats(line, hero, progress, settings);
        appendOrders(line, settings, Doing.of(hero, holding), true);
        appendItems(line, progress, settings);
        var book = hero.findModule(SkillBook.class);
        if (book != null && book.getMaxMana() > 0) {
            // Beside the health, and read the same way. A hero whose file gives
            // him no pool sends no field, and the panel draws no bar -- which is
            // every hero in the game until one of them was given one.
            line.append("|mana=").append(book.getMana())
                    .append('/').append(book.getMaxMana());
            // And the moment he asked for something he could not pay for. A
            // stamp rather than a flag, because the line is rebuilt and sent
            // every frame: without it the refusal would sound thirty times a
            // second for as long as nothing else happened.
            if (book.getRefusedForManaFrame() > 0) {
                line.append("|noMana=").append(book.getRefusedForManaFrame());
            }
        }
        if (book != null) {
            line.append("|skWord=").append(settings.hud().skillsWord());
            line.append(Skills.slots(book, learnt, level,
                    new Skills.Words(settings.hud().rankSuffix(), settings.hud().masterWord(),
                            settings.hud().lockedWord(),
                            new uz.duke.dungeon.skill.SkillTip.Words(
                                    settings.hud().damageWord(), settings.hud().cooldownWord(),
                                    settings.hud().radiusWord(), settings.hud().rangeWord(),
                                    settings.hud().boostWord(), settings.hud().raiseWord(),
                                    settings.hud().raiseKeyWord(),
                                    settings.hud().maxedWord(), settings.hud().noPointsWord(),
                                    settings.hud().rankSuffix(), settings.hud().secondsWord(),
                                    settings.hud().manaWord()))));
            // How many levels he has not spent yet, and the word for them. Beside
            // the heading rather than on a slot, because it belongs to none of
            // them: it is what the four are competing for.
            line.append("|pts=").append(learnt.unspent(level))
                    .append(',').append(settings.hud().pointsWord());
            appendCast(line, book, hero);
        }
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
        var words = settings.hud().orderWords();
        var pictures = settings.hud().orderIcons();
        // Drawn in this order and read in this order — walk, attack, stop, guard —
        // which is the order the words come in the file and the order Doing counts
        // its four states in. The LETTERS are not in that order and are not meant
        // to be: A S D are attack, stop and defend, under a hand that never leaves
        // them, and walking takes the letter left over because it is the one order
        // nobody uses the keyboard for.
        //
        // The letters are here because they are the game's claim about its own
        // controls — see Main.controls, which has to match. What each button LOOKS
        // like is not: that used to be a drawing named here by a word the client
        // held a mesh for, so changing how an order looked meant editing two
        // modules. It comes out of the file now, beside the word.
        String[] keys = {"F", "A", "S", "D"};
        line.append("|cmds=").append(his ? "mine" : "theirs");
        for (int i = 0; i < keys.length; i++) {
            String word = i < words.size() ? words.get(i) : "";
            if (word.isBlank()) {
                continue; // a game that does not name an order does not offer it
            }
            line.append("|cmd=").append(keys[i])
                    .append(',').append(i < pictures.size() ? pictures.get(i) : "")
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
     * <p>Grouped and counted: three of the same sword is one drawing reading three,
     * which is what six sockets have room for.
     */
    private static void appendItems(StringBuilder line, HeroProgress progress,
            DungeonSettings settings) {
        line.append("|itWord=").append(settings.hud().itemsWord());
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

    /**
     * His attributes, and the two figures beside them: what he hits for and what he
     * shrugs off.
     *
     * <p>The figures come from {@code HeroProgress} rather than being worked out here
     * again: it is the one place the attribute arithmetic is done and the one place it
     * is applied, so the number on the panel is the number taking the blows.
     *
     * <p>Everything that moves them is counted: the level and what he found on the
     * floor. What he would have without anything he found is the other half of each
     * figure — the difference is the number in green, and it is worth showing on its
     * own: a figure that only goes up says nothing about whether the last thing he
     * picked up was worth picking up.
     *
     * <p>Attributes are a field of their own rather than more figures, because the panel
     * lays them out apart and there are as many of them as the file lists, in its order.
     * His primary is marked, and each carries the card that says what a point of it is
     * worth. His speed is not a figure any more: it is what an attribute became, and it
     * is read on that one's card.
     */
    private static void appendStats(StringBuilder line, GameObject hero, HeroProgress progress,
            DungeonSettings settings) {
        var now = progress.figuresOf(hero, progress.found());
        var bare = progress.figuresOf(hero, HeroFigures.Found.NOTHING);
        var his = progress.getHero();
        if (his.attributes().hasPrimary()) {
            var rules = settings.attributeRules();
            var art = settings.attributeArt();
            for (int i = 0; i < art.size(); i++) {
                boolean primary = i == his.attributes().primary();
                figure(line, "attr", art.get(i).word(), now.attributes().whole(i),
                        bare.attributes().whole(i), art.get(i).icon());
                if (primary) {
                    line.append(",primary");
                }
                attributeCard(line, i, art.get(i).word(), rules.attributes().get(i), primary,
                        rules, Math.round(now.speed()), settings);
            }
        }
        var levelling = settings.levelling();
        int level = progress.getLevel();
        // What he was born in counts with what he has found, exactly as the body
        // counts it -- otherwise the figure on the panel is not the one taking the
        // blows, and a knight reads as an archer in a shirt. His own plate is not
        // borrowed, so it belongs on both sides of the sum.
        int worn = his.armourPercent();
        int armour = Math.round((1f - levelling.damageTakenWith(level,
                worn + progress.getLoot().armourPercent())) * 100f);
        int bareArmour = Math.round((1f - levelling.damageTakenWith(level, worn)) * 100f);
        var pictures = settings.hud().statIcons();
        stat(line, settings.hud().attackWord(), Math.round(now.attack()), Math.round(bare.attack()),
                pictures.get(0));
        stat(line, settings.hud().armourWord(), armour, bareArmour, pictures.get(1));
    }

    /**
     * The card over one attribute: its name, whether it is his primary, what a single
     * point of it gives, and — for one that gives speed — how fast he is now.
     *
     * <p>Sent in the same kinds of field a skill's card is, keyed by the attribute's place
     * in the file's list rather than by a key he presses. The numbers are the file's own,
     * written the way the file wrote them, and only a figure a point actually moves gets
     * a row, so an attribute the file adds next describes itself.
     */
    private static void attributeCard(StringBuilder line, int index, String word,
            Attribute attribute, boolean primary, AttributeRules rules, int speedNow,
            DungeonSettings settings) {
        line.append("|atTipName=").append(index).append(',').append(word);
        line.append("|atTipAt=").append(index).append(',')
                .append(primary ? settings.hud().primaryWord() : "");
        var rows = new StringBuilder();
        cardRow(rows, index, settings.hud().healthWord(), attribute.healthPerPoint().value());
        cardRow(rows, index, settings.hud().speedWord(), attribute.speedPerPoint().value());
        cardRow(rows, index, settings.hud().manaWord(), attribute.manaPerPoint().value());
        if (primary) {
            cardRow(rows, index, settings.hud().attackWord(), rules.damagePerPrimary());
        }
        if (!rows.isEmpty() && !settings.hud().eachPointWord().isEmpty()) {
            line.append("|atTipText=").append(index).append(',')
                    .append(settings.hud().eachPointWord());
        }
        line.append(rows);
        // His speed was a figure beside the others. It is what this attribute became,
        // so it is said here, under what a point of it is worth.
        if (attribute.speedPerPoint().value() > 0 && !settings.hud().speedNowWord().isEmpty()) {
            line.append("|atTipFoot=").append(index).append(',')
                    .append(settings.hud().speedNowWord()).append(' ').append(speedNow);
        }
    }

    /** One figure a point gives; nothing for a figure it does not move. */
    private static void cardRow(StringBuilder line, int index, String label, int hundredths) {
        if (hundredths == 0) {
            return;
        }
        line.append("|atTipRow=").append(index).append(',').append(label)
                .append(",+").append(hundredths(hundredths)).append(',');
    }

    /** A coefficient as the file wrote it: 15 is "0.15", 1200 is "12", 150 is "1.5". */
    static String hundredths(int value) {
        return java.math.BigDecimal.valueOf(value, 2).stripTrailingZeros().toPlainString();
    }

    /**
     * One figure: its word, what it is now, how much of that is borrowed, and the
     * picture beside it.
     *
     * <p>Four fields always, even where a field is empty. The picture is last and
     * the panel reads it by position, so a figure that lends nothing still has to
     * leave the gap where the lending would have gone.
     */
    private static void stat(StringBuilder line, String word, int now, int bare, String icon) {
        figure(line, "stat", word, now, bare, icon);
    }

    /** The same four fields under either name: a figure's, or an attribute's. */
    private static void figure(StringBuilder line, String field, String word, int now, int bare,
            String icon) {
        line.append('|').append(field).append('=').append(word).append(',').append(now).append(',');
        if (now != bare) {
            line.append(now > bare ? "+" : "").append(now - bare);
        }
        line.append(',').append(icon);
    }

    private static float weaponDamage(ThingTemplate template) {
        for (var entry : template.modules()) {
            if (entry instanceof uz.duke.rts.module.WeaponUpdate.Data weapon) {
                return weapon.damage();
            }
        }
        return 0f;
    }

    private static float walkingSpeed(ThingTemplate template) {
        for (var entry : template.modules()) {
            if (entry instanceof MoveUpdate.Data move) {
                return move.speed();
            }
        }
        return 0f;
    }

    /**
     * What he is, under his name.
     *
     * <p>His own block's word, and the panel's only if he has none. It used to be
     * the panel's outright — one line in {@code Hud} — which was right
     * while there was one hero and became the archer's title on a knight the
     * moment there were two.
     */
    private static String titleOf(GameObject hero, DungeonSettings settings) {
        var his = settings.heroNamed(hero.getTemplate().name()).title();
        return his == null || his.isBlank() ? settings.hud().heroTitle() : his;
    }

    /** What the player calls him, falling back to what the code calls him. */
    private static String nameOf(GameObject hero) {
        return uz.duke.core.thing.Titled.of(hero.getTemplate());
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
