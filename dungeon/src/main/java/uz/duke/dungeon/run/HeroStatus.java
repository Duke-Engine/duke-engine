package uz.duke.dungeon.run;

import uz.duke.core.module.MoveUpdate;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.ThingTemplate;
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

    /** The line, or the empty string if there is no hero to describe. */
    static String of(GameObject hero, HeroProgress progress, int depth,
            DungeonSettings settings, PowerChoice powers, int frame, String look) {
        if (hero == null || hero.getBody() == null) {
            return "";
        }
        int level = progress.getLevel();
        var line = new StringBuilder()
                .append("name=").append(nameOf(hero))
                .append("|rank=").append(level).append(settings.hudRankSuffix())
                .append("|hp=").append(Math.round(hero.getBody().getHealth()))
                .append('/').append(Math.round(hero.getBody().getMaxHealth()))
                .append("|xp=").append(progress.getExperienceIntoLevel())
                .append('/').append(progress.getExperienceForNextLevel())
                .append("|depth=").append(howFarDown(depth, settings))
                .append("|depthWord=").append(settings.hudDepthWord());
        appendStats(line, hero, progress, powers, settings);
        var book = hero.findModule(SkillBook.class);
        if (book != null) {
            line.append(Skills.slots(book, level, settings.hudRankSuffix(), settings::hudIcon));
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
        int armour = Math.round(
                (1f - rules.damageTakenWith(level, found.armourPercent())) * 100f);
        float speed = walkingSpeed(hero.getTemplate())
                * (powers == null ? 1f : powers.getBook().moveSpeedMultiplier());
        line.append("|stat=").append(settings.hudAttackWord()).append(',')
                .append(Math.round(attack))
                .append("|stat=").append(settings.hudArmourWord()).append(',').append(armour)
                .append("|stat=").append(settings.hudSpeedWord()).append(',')
                .append(Math.round(speed));
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
     */
    private static String howFarDown(int depth, DungeonSettings settings) {
        return settings.finalDepth() > 0
                ? roman(depth) + " / " + roman(settings.finalDepth())
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
