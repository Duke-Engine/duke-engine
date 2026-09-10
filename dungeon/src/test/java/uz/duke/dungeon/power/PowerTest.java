package uz.duke.dungeon.power;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.duke.core.thing.GameObject;
import uz.duke.dungeon.Dungeon;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.game.DukeGame;
import uz.duke.rts.module.ExperienceModule;

/**
 * The cards a level puts on the table: where they come from, that the same seed
 * deals the same ones, and that taking one is felt.
 *
 * <p>Nothing here asserts a balance figure. Which powers exist and what they are
 * worth is written in {@code dungeon.ini}, so a re-tune should move these tests
 * with it rather than break them. What is held still is the mechanism: that the
 * file decides the catalogue, that a seed decides the draw, that a limit is a
 * limit, and that a chosen card changes the hero.
 */
class PowerTest {

    private static final DungeonSettings SHIPPED = DungeonSettings.load();

    /** Two powers and nothing else, so a draw has a known answer. */
    private static final DungeonSettings TWO = DungeonSettings.parse("""
            DungeonPowers Draft
              OfferCount = 2
              MinCooldownPercent = 30
            End

            DungeonPower Sharper
              Name = Sharper
              Desc = Q hits harder
              Icon = shot
              Effect = SKILL_DAMAGE
              Skill = Q
              Value = 40
              Weight = 10
              MaxStacks = 2
            End

            DungeonPower Later
              Name = Later
              Desc = only deeper in
              Icon = star
              Effect = MOVE_SPEED
              Value = 10
              Weight = 10
              MaxStacks = 1
              MinLevel = 5
            End
            """);

    private static PowerBook book(DungeonSettings settings) {
        return new PowerBook(settings.powerMinCooldownPercent());
    }

    private static List<String> idsOf(List<Power> offer) {
        return offer.stream().map(Power::id).toList();
    }

    private static Power named(DungeonSettings settings, String id) {
        return settings.powers().stream().filter(power -> power.id().equals(id)).findFirst()
                .orElseThrow(() -> new AssertionError("no power called " + id));
    }

    /**
     * Only the powers this test declared.
     *
     * <p>A partial file overrides what it names and keeps the rest, the way every
     * other setting in {@code dungeon.ini} behaves — so a test that wants a
     * catalogue of two has to say which two. {@link PowerDraft} takes the
     * catalogue as an argument for exactly this reason.
     */
    private static List<Power> only(DungeonSettings settings, String... ids) {
        return java.util.Arrays.stream(ids).map(id -> named(settings, id)).toList();
    }

    // ---- what the file says ----

    @Test
    void theFileIsTheCatalogue() {
        assertEquals(2, TWO.powerOfferCount(), "the file decides how many cards a level deals");
        assertEquals(30, TWO.powerMinCooldownPercent());
        assertFalse(SHIPPED.powers().isEmpty(), "the shipped game offers something");
        assertEquals(40, named(TWO, "Sharper").value());
        assertEquals('Q', named(TWO, "Sharper").skillKey());
        assertEquals(PowerEffect.MOVE_SPEED, named(TWO, "Later").effect());
        // A file that names some powers keeps the rest, as every other block here
        // does: overriding one card must not quietly empty the deck.
        assertTrue(idsOf(TWO.powers()).containsAll(idsOf(SHIPPED.powers())),
                "the shipped powers survive a file that adds its own");
    }

    @Test
    void aBrokenPowerIsRefusedWhereItIsWritten() {
        // A name carrying a comma would arrive at the panel as two half-cards, so
        // it is refused at load time, naming the power rather than failing later.
        var thrown = assertThrows(IllegalArgumentException.class, () ->
                DungeonSettings.parse("""
                        DungeonPower Bad
                          Name = one, two
                          Effect = LIFESTEAL
                          Value = 5
                        End
                        """));
        assertTrue(thrown.getMessage().contains("Bad"), thrown.getMessage());
    }

    // ---- the draw ----

    @Test
    void theSameSeedDealsTheSameCards() {
        var first = PowerDraft.offer(4242L, 3, SHIPPED.powers(), book(SHIPPED), 3);
        var again = PowerDraft.offer(4242L, 3, SHIPPED.powers(), book(SHIPPED), 3);
        assertEquals(idsOf(first), idsOf(again), "a seed is a whole run, the offers included");
        assertEquals(3, first.size());
    }

    @Test
    void aDifferentSeedDealsDifferently() {
        boolean anyDifferent = false;
        for (int level = 2; level <= 8 && !anyDifferent; level++) {
            anyDifferent = !idsOf(PowerDraft.offer(1L, level, SHIPPED.powers(), book(SHIPPED), 3))
                    .equals(idsOf(PowerDraft.offer(2L, level, SHIPPED.powers(), book(SHIPPED), 3)));
        }
        assertTrue(anyDifferent, "two seeds should not deal the same run of offers");
    }

    @Test
    void everyCardInAnOfferIsADifferentOne() {
        var offer = PowerDraft.offer(77L, 6, SHIPPED.powers(), book(SHIPPED), 3);
        assertEquals(offer.size(), idsOf(offer).stream().distinct().count(),
                "the same card must not appear twice on the table");
    }

    @Test
    void aCardHeldBackWaitsForItsLevel() {
        var deck = only(TWO, "Sharper", "Later");
        assertEquals(List.of("Sharper"), idsOf(PowerDraft.offer(5L, 1, deck, book(TWO), 2)),
                "a power with MinLevel 5 is not on the table at level 1");
        assertEquals(2, PowerDraft.offer(5L, 5, deck, book(TWO), 2).size(),
                "and is at level 5");
    }

    @Test
    void aCardTakenToItsLimitStopsBeingOffered() {
        var deck = only(TWO, "Sharper", "Later");
        var held = book(TWO);
        var sharper = named(TWO, "Sharper");
        assertTrue(held.add(sharper));
        assertTrue(held.add(sharper));
        assertFalse(held.add(sharper), "MaxStacks is a limit, not a suggestion");
        assertTrue(idsOf(PowerDraft.offer(5L, 9, deck, held, 2)).contains("Later"));
        assertFalse(idsOf(PowerDraft.offer(5L, 9, deck, held, 2)).contains("Sharper"));
    }

    @Test
    void anEmptyTableIsNotAnError() {
        var deck = only(TWO, "Sharper", "Later");
        var held = book(TWO);
        for (var power : deck) {
            while (held.add(power)) {
                // take it as often as it may be taken
            }
        }
        assertTrue(PowerDraft.offer(5L, 9, deck, held, 2).isEmpty());
    }

    // ---- what a card comes to ----

    @Test
    void stacksAddRatherThanCompound() {
        var held = book(TWO);
        var sharper = named(TWO, "Sharper"); // +40% to Q
        held.add(sharper);
        assertEquals(1.4f, held.skillDamageMultiplier('Q'), 0.0001f);
        held.add(sharper);
        assertEquals(1.8f, held.skillDamageMultiplier('Q'), 0.0001f,
                "two of the same card is worth twice the first, not 1.4 squared");
        assertEquals(1f, held.skillDamageMultiplier('W'), 0.0001f, "and says nothing about W");
    }

    @Test
    void aCooldownIsSharpenedButNeverToNothing() {
        var settings = DungeonSettings.parse("""
                DungeonPowers Draft
                  OfferCount = 3
                  MinCooldownPercent = 40
                End

                DungeonPower Quick
                  Name = Quick
                  Desc = sooner
                  Effect = COOLDOWN
                  Skill = W
                  Value = 50
                  MaxStacks = 4
                End
                """);
        var held = book(settings);
        var quick = named(settings, "Quick");
        held.add(quick);
        assertEquals(0.5f, held.cooldownMultiplier('W'), 0.0001f);
        held.add(quick);
        assertEquals(0.4f, held.cooldownMultiplier('W'), 0.0001f,
                "the floor from the file holds, however many are stacked");
    }

    @Test
    void aStarMeansEverySkill() {
        var settings = DungeonSettings.parse("""
                DungeonPower All
                  Name = All
                  Desc = everything
                  Effect = SKILL_DAMAGE
                  Skill = *
                  Value = 15
                End
                """);
        var held = book(settings);
        held.add(named(settings, "All"));
        assertEquals(1.15f, held.skillDamageMultiplier('Q'), 0.0001f);
        assertEquals(1.15f, held.skillDamageMultiplier('R'), 0.0001f);
    }

    // ---- in the game ----

    private static GameObject heroOf(DukeGame game) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().getName().equals("Hero"))
                .findFirst().orElse(null);
    }

    /** Killing, handed over rather than fought for. */
    private static void grantExperience(DukeGame game, int amount) {
        heroOf(game).findModule(ExperienceModule.class).addExperience(amount);
    }

    /** Exactly one level's worth, whatever the file says a level costs. */
    private static int oneLevel(DungeonSettings settings) {
        return settings.levelling().totalXpFor(2);
    }

    @Test
    void aLevelPutsCardsOnTheTable() {
        var session = Dungeon.newSession(31L);
        var game = session.game();
        game.runHeadless(1);
        assertFalse(session.powers().hasOffer(), "level one is where he starts, not a level up");

        grantExperience(game, oneLevel(SHIPPED));
        game.runHeadless(2);
        assertTrue(session.powers().hasOffer(), "levelling offers a choice");
        assertEquals(SHIPPED.powerOfferCount(), session.powers().getOffer().size());
        assertTrue(session.powers().getOfferLevel() > 1);
        assertTrue(session.powers().getOfferId() > 0, "and it has a name of its own");
    }

    @Test
    void choosingIsFeltAndTheTableIsCleared() {
        var session = Dungeon.newSession(31L);
        var game = session.game();
        game.runHeadless(1);
        grantExperience(game, oneLevel(SHIPPED));
        game.runHeadless(2);

        var chosen = session.powers().getOffer().get(0);
        game.postCommand(new ChoosePower(game.getLocalPlayerIndex(), 0,
                session.powers().getOfferId()));
        game.runHeadless(2);

        assertFalse(session.powers().hasOffer(), "the card was taken; the table is cleared");
        assertEquals(1, session.powers().getBook().stacksOf(chosen.id()));
    }

    @Test
    void aChoiceForAnotherOfferIsRefused() {
        var session = Dungeon.newSession(31L);
        var game = session.game();
        game.runHeadless(1);
        grantExperience(game, oneLevel(SHIPPED));
        game.runHeadless(2);

        int offer = session.powers().getOfferId();
        game.postCommand(new ChoosePower(game.getLocalPlayerIndex(), 0, offer + 99));
        game.runHeadless(2);
        assertTrue(session.powers().hasOffer(),
                "a click answering some other offer must not spend this one");
    }

    @Test
    void twoLevelsAtOnceOweTwoCards() {
        var session = Dungeon.newSession(31L);
        var game = session.game();
        game.runHeadless(1);
        grantExperience(game, 5000); // several levels in one frame
        game.runHeadless(2);

        assertTrue(session.powers().hasOffer());
        game.postCommand(new ChoosePower(game.getLocalPlayerIndex(), 0,
                session.powers().getOfferId()));
        game.runHeadless(2);
        assertTrue(session.powers().hasOffer(),
                "the second level's card follows the first, rather than being lost");
    }

    @Test
    void aDeathTakesThePowersWithIt() {
        var session = Dungeon.newSession(31L);
        var game = session.game();
        game.runHeadless(1);
        grantExperience(game, oneLevel(SHIPPED));
        game.runHeadless(2);
        game.postCommand(new ChoosePower(game.getLocalPlayerIndex(), 0,
                session.powers().getOfferId()));
        game.runHeadless(2);
        assertFalse(session.powers().getBook().getTaken().isEmpty());

        game.getLogic().destroyObject(heroOf(game));
        game.runHeadless(SHIPPED.respawnDelayFrames() + 4);
        assertTrue(session.powers().getBook().getTaken().isEmpty(),
                "a new run starts with nothing, the cards included");
        assertFalse(session.powers().hasOffer());
    }

    @Test
    void fasterFeetReachTheHeroHimself() {
        // Every card on the table at once, so this test can pick the one it means
        // rather than hope the draw deals it.
        var settings = DungeonSettings.parse("""
                DungeonPowers Draft
                  OfferCount = 40
                  MinCooldownPercent = 25
                End

                DungeonPower Swift
                  Name = Swift
                  Desc = faster
                  Effect = MOVE_SPEED
                  Value = 50
                  Weight = 10
                End
                """);
        var session = Dungeon.newSession(31L, settings);
        var game = session.game();
        game.runHeadless(1);
        var before = heroOf(game).findModule(uz.duke.core.module.MoveUpdate.class);
        assertNotNull(before);

        grantExperience(game, oneLevel(settings));
        game.runHeadless(2);
        int swift = idsOf(session.powers().getOffer()).indexOf("Swift");
        assertTrue(swift >= 0, "the whole catalogue was offered, so Swift is on the table");
        game.postCommand(new ChoosePower(game.getLocalPlayerIndex(), swift,
                session.powers().getOfferId()));
        game.runHeadless(2);

        var after = heroOf(game).findModule(uz.duke.core.module.MoveUpdate.class);
        assertNotNull(after);
        assertNotEquals(before, after,
                "walking faster means a new locomotor: the engine's fixes its step when built");
    }

    /**
     * Two runs do not share an offer's name.
     *
     * <p>A death puts the hero back at level one, so both runs earn a "level 2"
     * offer. Naming an offer by its level made the second one indistinguishable
     * from the first — and the client, which remembers the last offer it dealt
     * with so a held-up screen does not come back, would then never show it. The
     * name counts up through the session instead.
     */
    @Test
    void aNewRunsOfferIsNotTheLastRunsOffer() {
        var session = Dungeon.newSession(31L);
        var game = session.game();
        game.runHeadless(1);
        grantExperience(game, oneLevel(SHIPPED));
        game.runHeadless(2);
        int first = session.powers().getOfferId();
        int atLevel = session.powers().getOfferLevel();
        game.postCommand(new ChoosePower(game.getLocalPlayerIndex(), 0, first));
        game.runHeadless(2);

        game.getLogic().destroyObject(heroOf(game));
        game.runHeadless(SHIPPED.respawnDelayFrames() + 4);
        grantExperience(game, oneLevel(SHIPPED));
        game.runHeadless(2);

        assertTrue(session.powers().hasOffer(), "the new run levels up too");
        assertEquals(atLevel, session.powers().getOfferLevel(), "at the same level as before");
        assertNotEquals(first, session.powers().getOfferId(),
                "but it must not answer to the same name as the offer already spent");
    }

    // ---- charges ----

    /** A hero alone in a room, so his skills are the only thing happening. */
    private static uz.duke.core.thing.GameObject aloneWith(DukeGame game) {
        game.runHeadless(1);
        return heroOf(game);
    }

    /**
     * A second charge is spent, not lent.
     *
     * <p>The first version handed the charge back the frame after it was used —
     * the slot was off cooldown, so the refill fired — and the skill became free
     * for the rest of the run. The cap is remembered now, and only a recharge or a
     * newly taken card puts anything back.
     */
    @Test
    void anExtraChargeIsSpentAndThenTheCooldownRuns() {
        var settings = DungeonSettings.parse("""
                DungeonPowers Draft
                  OfferCount = 40
                  MinCooldownPercent = 25
                End

                DungeonPower Twice
                  Name = Twice
                  Desc = E twice
                  Effect = EXTRA_CHARGE
                  Skill = E
                  Value = 1
                  Weight = 10
                End
                """);
        var session = Dungeon.newSession(31L, settings);
        var game = session.game();
        var hero = aloneWith(game);
        var book = hero.findModule(uz.duke.dungeon.skill.SkillBook.class);
        assertEquals(1, book.chargesOf('E'), "one cast, before any card");

        grantExperience(game, oneLevel(settings));
        game.runHeadless(2);
        int twice = idsOf(session.powers().getOffer()).indexOf("Twice");
        assertTrue(twice >= 0);
        game.postCommand(new ChoosePower(game.getLocalPlayerIndex(), twice,
                session.powers().getOfferId()));
        game.runHeadless(3);

        book = heroOf(game).findModule(uz.duke.dungeon.skill.SkillBook.class);
        assertEquals(2, book.chargesOf('E'), "the card is felt at once");

        int level = session.progress().getLevel();
        assertTrue(book.cast('E', level));
        game.runHeadless(1);
        assertEquals(1, book.chargesOf('E'), "one spent, one in hand");
        assertTrue(book.isReady('E'), "which is what a charge is for");

        assertTrue(book.cast('E', level));
        game.runHeadless(1);
        assertEquals(0, book.chargesOf('E'));
        assertFalse(book.isReady('E'), "and now it recharges, rather than going on for ever");
    }
}
