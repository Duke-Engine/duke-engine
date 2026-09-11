package uz.duke.dungeon.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.duke.dungeon.Dungeon;
import uz.duke.dungeon.content.Content;
import uz.duke.dungeon.content.DungeonSettings;

/**
 * The line the hero's panel is drawn from, written by a real run.
 *
 * <p>Its reader is {@code uz.duke.client3d.HeroPanel}, which this module cannot
 * see and which cannot see this. Nothing but a test connects them, so this holds
 * the writing end of the format and {@code HeroPanelTest} holds the reading end.
 *
 * <p>What is checked is the shape and where each part came from — that the name
 * is the one in the creature file, the words are the ones in {@code dungeon.ini},
 * and there is a slot for every skill the file gives him. Not the values: the
 * hero's health and the length of his cooldowns are balance, and balance is meant
 * to be tuned without a test objecting.
 */
class HeroStatusTest {

    /**
     * One frame of a real dungeon with the hero picked out, which is what the
     * player does before he reads anything about him.
     *
     * <p>Selecting him first is not scaffolding: the bar describes whatever is
     * selected, and with nothing selected it describes nobody on purpose. See
     * {@link #withNothingSelectedTheBarIsEmpty}.
     */
    private static String lineFrom(long seed) {
        var session = Dungeon.newSession(seed);
        var game = session.game();
        game.runHeadless(1);
        var hero = game.getLogic().getObjects().stream()
                .filter(o -> o.getTemplate().getName().equals("Hero"))
                .findFirst().orElseThrow();
        session.orders().watch(hero.getPlayerIndex(), hero.getId());
        game.runHeadless(1);
        return game.getSnapshot().status();
    }

    /** Pick the hero out, the way a player does before reading anything about him. */
    private static void pickOutTheHero(Dungeon.Session session) {
        var hero = session.game().getLogic().getObjects().stream()
                .filter(o -> o.getTemplate().getName().equals("Hero"))
                .findFirst().orElseThrow();
        session.orders().watch(hero.getPlayerIndex(), hero.getId());
    }

    /**
     * With nothing selected the bar is about the floor and nobody else.
     *
     * <p>Which is the first thing a run ever says: nothing is selected when one
     * begins. So the line has to carry what belongs to the screen rather than to a
     * creature -- and in particular the floor's LOOK, because this is the line the
     * client learns which stone to build the first floor out of from.
     */
    @Test
    void withNothingSelectedTheBarIsEmpty() {
        var session = Dungeon.newSession(4321L);
        session.game().runHeadless(1);
        var line = session.game().getSnapshot().status();

        assertTrue(line.startsWith("name="), "the panel reads nothing else: " + line);
        assertTrue(line.startsWith("name=|") || line.equals("name="),
                "nobody is selected, so nobody is named: " + line);
        assertFalse(line.contains("|hp="), "nothing has health: " + line);
        assertFalse(line.contains("|skill="), "nothing has skills: " + line);
        // The buttons stay -- they are furniture, like the empty sockets beside
        // them -- but nothing is selected, so nothing may be pressed and nothing
        // is doing anything for them to show.
        assertTrue(line.contains("|cmds=theirs"),
                "there is nothing to give orders to: " + line);
        assertFalse(line.contains(",on"), "and nothing is doing anything: " + line);
        assertTrue(line.contains("|depth="), "the floor is still the floor: " + line);
        assertTrue(line.contains("|look="),
                "and the client learns the floor's stone from this line: " + line);
    }

    @Test
    void aRunDescribesItsHero() {
        var line = lineFrom(4321L);

        assertTrue(line.startsWith("name="), "the panel reads nothing else: " + line);
        for (var field : new String[] {"rank=", "hp=", "xp=", "depth=", "depthWord="}) {
            assertTrue(line.contains("|" + field) || line.startsWith(field),
                    field + " missing from " + line);
        }
    }

    /**
     * The name is the one the file gives him, not the one the code calls him.
     *
     * <p>He is the {@code Hero} template everywhere in the game — the spawner, the
     * skill blocks, every test above this one — and {@code DisplayName} is the only
     * place that says what the player should call him.
     */
    @Test
    void heIsCalledWhateverTheCreatureFileCallsHim() {
        assertTrue(Content.read(Content.CREATURES).contains("DisplayName = Erika"),
                "the shipped hero should have a name of his own");
        assertTrue(lineFrom(11L).startsWith("name=Erika"), lineFrom(11L));
        assertFalse(lineFrom(11L).startsWith("name=Hero"),
                "that is his template, not his name");
    }

    /** One slot per skill the file gives him, in the file's order. */
    @Test
    void thereIsASlotForEverySkill() {
        var line = lineFrom(77L);
        var settings = DungeonSettings.load();

        // His skills, not the file's: the panel describes whoever is being played,
        // and the file holds a second hero's four as well now.
        var his = settings.skillsFor(settings.playedHero());
        assertEquals(his.size(), line.split("\\|skill=", -1).length - 1,
                "a slot each, no more and no fewer: " + line);
        for (var skill : his) {
            assertTrue(line.contains("|skill=" + skill.key() + ","),
                    skill.key() + " has no slot in " + line);
        }
    }

    /**
     * A locked slot says what it is waiting for, in the game's words.
     *
     * <p>An ultimate is the one thing on the panel the player cannot use yet, so
     * it is the one slot that has to explain itself — and it explains itself in
     * whatever language {@code RankSuffix} is written in.
     */
    @Test
    void alockedSlotSaysWhatItWaitsFor() {
        var settings = DungeonSettings.load();
        var waiting = settings.skills().stream()
                .filter(skill -> !skill.unlockedAt(1))
                .findFirst().orElseThrow(() ->
                        new AssertionError("nothing is locked at level one any more"));

        assertTrue(lineFrom(5L).contains(
                        "|skill=" + waiting.key() + "," + settings.hudIcon(waiting.icon())
                                + ",lock," + waiting.unlockLevel() + settings.hudRankSuffix()),
                "the locked slot should name its level the way the panel names them");
    }

    /**
     * Each slot carries the picture the file gave it, found where the file says to
     * look for it.
     *
     * <p>The whole point of naming icons in INI: the client is handed a path and
     * draws whatever is at it, so a fifth skill is a fifth block of the file rather
     * than a line of Java. This is that from the writing end — {@code HeroPanelTest}
     * holds the other.
     *
     * <p>A skill that names <em>no</em> icon is not a fault: the slot draws the
     * letter of its key, which is what every slot did before there were any
     * pictures, and a hero whose art has not arrived yet is playable that way on
     * purpose. So what is held here is that a named picture arrives — not that one
     * was named.
     */
    @Test
    void everySlotCarriesThePictureTheFileGaveIt() {
        var line = lineFrom(77L);
        var settings = DungeonSettings.load();

        for (var skill : settings.skillsFor(settings.playedHero())) {
            var icon = settings.hudIcon(skill.icon());
            if (icon.isBlank()) {
                continue; // no picture named: the slot draws the letter, as it always did
            }
            assertTrue(line.contains("|skill=" + skill.key() + "," + icon + ","),
                    skill.key() + " should carry " + icon + " in " + line);
        }
    }

    /**
     * And the archer's four, who have had pictures for a long time, still have them.
     *
     * <p>The half of the check above that was worth keeping once a hero was allowed
     * to have none. Losing an icon is silent — the slot falls back to its letter —
     * so the hero who has them needs somebody to say so.
     */
    @Test
    void theArcherStillHasAllFourOfHisPictures() {
        var settings = DungeonSettings.load();

        for (var skill : settings.skillsFor("Hero")) {
            assertFalse(settings.hudIcon(skill.icon()).isBlank(),
                    "the archer's " + skill.key() + " lost the Icon it had");
        }
    }

    /** The panel's words are the file's — the client writes none of its own. */
    @Test
    void changingTheFileChangesTheWords() {
        var settings = DungeonSettings.parse("""
                DungeonHud Panel
                  DepthWord = FLOOR
                  RankSuffix = th level
                End
                """);

        assertEquals("FLOOR", settings.hudDepthWord());
        assertEquals("th level", settings.hudRankSuffix());
    }

    /**
     * Depth is a numeral, being the one number in the game that only goes up.
     *
     * <p>And anything a numeral cannot say comes back as a digit, because a wrong
     * numeral on the screen is worse than a plain number.
     */
    @Test
    void depthIsRoman() {
        assertEquals("I", HeroStatus.roman(1));
        assertEquals("IV", HeroStatus.roman(4));
        assertEquals("IX", HeroStatus.roman(9));
        assertEquals("XLII", HeroStatus.roman(42));
        assertEquals("MMMCMXCIX", HeroStatus.roman(3999));
        assertEquals("0", HeroStatus.roman(0));
        assertEquals("4000", HeroStatus.roman(4000));
    }

    // ---- what a levelling run adds to the line ----

    /**
     * The figures under the bars come from the creature file and his level, and
     * are named by {@code dungeon.ini}.
     */
    @Test
    void theFiguresUnderTheBarsAreThere() {
        var settings = DungeonSettings.load();
        var line = lineFrom(4321L);

        for (var word : new String[] {settings.hudAttackWord(), settings.hudArmourWord(),
            settings.hudSpeedWord()}) {
            assertFalse(word.isBlank(), "the shipped file should name its own figures");
            assertTrue(line.contains("|stat=" + word + ","), word + " missing from " + line);
        }
    }

    /** With nothing picked up yet, the strip is labelled and empty. */
    @Test
    void theStripOfPowersStartsEmpty() {
        var line = lineFrom(4321L);
        assertTrue(line.contains("|pwWord="), line);
        assertFalse(line.contains("|pw="), "he has taken nothing yet: " + line);
    }

    /**
     * Everything the design asks for is on the line the game really sends.
     *
     * <p>The panel and this class are two halves of one format that no compiler
     * checks, so every field the design added is worth naming: a heading the game
     * forgets is a heading the panel draws as an empty gold wash, which looks
     * deliberate and is not.
     */
    @Test
    void theLineCarriesEveryPartOfTheDesign() {
        var session = Dungeon.newSession(7L);
        var game = session.game();
        game.runHeadless(2);
        pickOutTheHero(session);
        game.runHeadless(30);
        var line = game.getSnapshot().status();

        assertTrue(line.contains("|title="), "what he is, under his name: " + line);
        assertTrue(line.contains("|itWord="), "the heading over his bag: " + line);
        assertTrue(line.contains("|skWord="), "the heading over his skills: " + line);
        // Four orders, each with a key, a drawing, a word and a state.
        int orders = line.split(java.util.regex.Pattern.quote("|cmd="), -1).length - 1;
        assertEquals(4, orders, "the four buttons beside the map: " + line);
        assertTrue(line.contains("|cmd=F,shield,"), "the one order the engine has no word for");
        assertTrue(line.contains(",off"), "and an order that is not on says so");
    }

    /**
     * A thing picked up shows in his bag and in green under the bars.
     *
     * <p>Two fields from one event, and the pair is the point: the socket says he
     * has it and the green says what it was worth. Neither is worth much alone.
     */
    @Test
    void whatHeFindsReachesTheBagAndTheFigures() {
        var session = Dungeon.newSession(11L);
        var game = session.game();
        game.runHeadless(2);
        pickOutTheHero(session);
        game.runHeadless(1);
        var before = game.getSnapshot().status();
        assertFalse(before.contains("|it="), "he starts with nothing: " + before);

        var settings = DungeonSettings.load();
        var blade = settings.loot().stream()
                .filter(item -> item.kind() == uz.duke.dungeon.loot.LootKind.ATTACK)
                .findFirst().orElseThrow();
        session.progress().getLoot().take(blade, game.getLogic().getFrame(), 60);
        game.runHeadless(2);

        var line = game.getSnapshot().status();
        assertTrue(line.contains("|it=" + blade.icon() + ",1"),
                blade.id() + " should be in his bag: " + line);
        assertTrue(line.contains(",+"), "and what it is worth should be in green: " + line);
    }

    /**
     * A level puts the cards on the line, headed by the level they belong to.
     *
     * <p>The heading's number is what the client answers with, so it has to be
     * there and it has to be the offer's own level.
     */
    @Test
    void aLevelPutsItsCardsOnTheLine() {
        var session = Dungeon.newSession(4321L);
        var game = session.game();
        game.runHeadless(1);
        pickOutTheHero(session);
        var hero = game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().getName().equals("Hero"))
                .findFirst().orElseThrow();
        hero.findModule(uz.duke.rts.module.ExperienceModule.class)
                .addExperience(DungeonSettings.load().levelling().totalXpFor(2));
        game.runHeadless(3);

        var line = game.getSnapshot().status();
        assertTrue(line.contains("|offer=" + session.powers().getOfferId() + ","), line);
        int cards = line.split("\\|opt=", -1).length - 1;
        assertEquals(session.powers().getOffer().size(), cards, line);
        // Every card's own field is icon, name and description, and neither the
        // name nor the description may carry the separators.
        for (var power : session.powers().getOffer()) {
            assertTrue(line.contains("|opt=" + power.icon() + "," + power.name() + ","
                    + power.description()), power.id() + " missing from " + line);
        }
    }
}
