package uz.duke.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * What the hero's panel will and will not draw.
 *
 * <p>The drawing itself needs a window and cannot be tested here. What can be —
 * and what actually breaks — is the line the game sends: the panel is the only
 * thing that reads it, the game is the only thing that writes it, and neither
 * compiler sees the other. So this holds the format still from the reading end,
 * and {@code uz.duke.dungeon.run.HeroStatusTest} holds it from the writing end;
 * change one side and one of the two goes red.
 *
 * <p>The other half is the promise to every game that is not a dungeon. Three
 * other apps launch this client, and the status channel is free-form — a line
 * this panel does not understand has to leave it hidden rather than half-drawn.
 */
class HeroPanelTest {

    /** A line of the kind the dungeon sends, with all three skill states in it. */
    private static final String LINE =
            "name=Erika|rank=7-daraja|hp=128/200|xp=38/100|depth=III|depthWord=CHUQURLIK"
                    + "|skill=Q,icons/skills/arrowhead.png,ready"
                    + "|skill=W,icons/skills/arrow_cluster.png,cool,72,165"
                    + "|skill=E,icons/skills/sprint.png,ready"
                    + "|skill=R,icons/skills/hood.png,lock,5-daraja";

    @Test
    void aDungeonLineIsRead() {
        var reading = HeroPanel.Reading.parse(LINE);

        assertNotNull(reading, "the panel should have understood its own game's line");
        assertEquals("Erika", reading.name());
        assertEquals("7-daraja", reading.rank());
        assertEquals(128f, reading.health(), 0.001f);
        assertEquals(200f, reading.maxHealth(), 0.001f);
        assertEquals(38f, reading.experience(), 0.001f);
        assertEquals("III", reading.depth());
        assertEquals("CHUQURLIK", reading.depthWord());
        assertEquals(4, reading.skills().size());
    }

    /** Each of the three states a slot can be in, and what it needs to draw it. */
    @Test
    void eachSkillStateIsRead() {
        var skills = HeroPanel.Reading.parse(LINE).skills();

        assertEquals(HeroPanel.Reading.State.READY, skills.get(0).state());
        assertEquals('Q', skills.get(0).key());

        var cooling = skills.get(1);
        assertEquals(HeroPanel.Reading.State.COOLING, cooling.state());
        // 72 frames of 30 is 2.4 seconds, and 72 of 165 is how much shadow is left.
        assertEquals("2.4", cooling.label());
        assertEquals(72f / 165f, cooling.left(), 0.001f);

        var locked = skills.get(3);
        assertEquals(HeroPanel.Reading.State.LOCKED, locked.state());
        assertEquals("5-daraja", locked.label(),
                "the level it waits for is the game's words, not the client's");
    }

    /**
     * Long cooldowns lose the decimal. "27.3" seconds is a number nobody reads at
     * a glance, and the slot it has to fit inside is sixty pixels wide.
     */
    @Test
    void aLongCooldownIsRoundedToWholeSeconds() {
        var skills = HeroPanel.Reading.parse(
                "name=E|rank=1|hp=1/1|xp=0/1|depth=I|depthWord=D|skill=R,,cool,820,900").skills();

        assertEquals("27", skills.get(0).label());
    }

    /** Anything that is not this format leaves the panel out of it. */
    @Test
    void anotherGamesStatusIsNotTakenOver() {
        assertNull(HeroPanel.Reading.parse(null));
        assertNull(HeroPanel.Reading.parse(""));
        assertNull(HeroPanel.Reading.parse("Wave 4    2 bases left"));
        assertNull(HeroPanel.Reading.parse("name=Erika|morale=high"),
                "a field this panel cannot draw means the line was never meant for it");
    }

    /** A malformed line of the right shape is refused rather than half-drawn. */
    @Test
    void aBrokenLineIsRefused() {
        assertNull(HeroPanel.Reading.parse("name=Erika|hp=lots/200"));
        assertNull(HeroPanel.Reading.parse("name=Erika|hp=200"));
        assertNull(HeroPanel.Reading.parse("name=Erika|skill=Q,,melted"));
        assertNull(HeroPanel.Reading.parse("name=Erika|skill=Q,,cool,72"),
                "a cooldown without its total has no fraction to sweep");
    }

    /**
     * Skills are drawn in the order they arrive, since that is the order the file
     * lists them in and the order the keys sit on the keyboard.
     */
    @Test
    void slotsKeepTheOrderTheyWereSentIn() {
        var skills = HeroPanel.Reading.parse(LINE).skills();

        var keys = new StringBuilder();
        skills.forEach(skill -> keys.append(skill.key()));
        assertEquals("QWER", keys.toString());
    }

    /**
     * A field the panel draws nothing for still has to be a field it knows.
     *
     * <p>The line is one line. It carries which look the floor wears as well as
     * what the hero is, and the panel has no picture for that -- but a parser that
     * refuses what it cannot draw would refuse the whole line and take the panel
     * with it.
     */
    @Test
    void aFieldThePanelDrawsNothingForIsStillReadable() {
        var reading = HeroPanel.Reading.parse(LINE + "|look=SciFi,Bare");

        assertNotNull(reading, "the panel went blank over a field meant for somebody else");
        assertEquals("Erika", reading.name());
        assertEquals(4, reading.skills().size(), "and read everything it does draw");
    }

    // ---- the picture in the slot ----

    /**
     * Which picture goes in which slot is the game's answer, carried down the
     * line — so changing it in the file changes what the panel draws.
     *
     * <p>The point of the whole arrangement. The client serves three other games
     * and cannot be the place that knows a dungeon's ultimate is an explosion; a
     * fifth skill has to be a fifth block of INI and no Java at all. This is that
     * promise from the reading end: the same line with a different name in it comes
     * out as a different picture.
     */
    @Test
    void thePictureForASlotComesDownTheLine() {
        var skills = HeroPanel.Reading.parse(LINE).skills();

        assertEquals("icons/skills/arrowhead.png", skills.get(0).icon());
        assertEquals("icons/skills/arrow_cluster.png", skills.get(1).icon(),
                "a slot on cooldown still knows what it is a picture of");
        assertEquals("icons/skills/hood.png", skills.get(3).icon(),
                "and so does one that is still locked");

        var renamed = HeroPanel.Reading.parse(
                LINE.replace("icons/skills/arrowhead.png", "Some/Other/picture.png")).skills();
        assertEquals("Some/Other/picture.png", renamed.get(0).icon(),
                "the file said a different picture, so the slot gets a different picture");
    }

    /**
     * A slot with no picture named is a slot, not a hole.
     *
     * <p>Every other game this client serves sends no icons at all, and this one
     * sent none until there were any. The empty field has to survive.
     */
    @Test
    void aSlotWithNoPictureIsStillRead() {
        var skills = HeroPanel.Reading.parse(
                "name=E|rank=1|hp=1/1|xp=0/1|depth=I|depthWord=D|skill=Q,,ready").skills();

        assertEquals(1, skills.size());
        assertEquals("", skills.get(0).icon());
        assertEquals(HeroPanel.Reading.State.READY, skills.get(0).state());
    }

    /**
     * A picture the client cannot find costs the panel a slot's carving, not the
     * game.
     *
     * <p>The game names its own art and nothing checks the spelling until the file
     * is asked for. So the miss has to end in a fallback rather than in an
     * exception: no name, a name nothing answers to, and no asset manager at all
     * are all "draw the letter instead".
     */
    @Test
    void aPictureThatWillNotLoadFallsBackInsteadOfThrowing() {
        var assets = new com.jme3.asset.DesktopAssetManager(true);
        var missing = new java.util.HashSet<String>();

        assertNull(HeroPanel.iconTexture(assets, "icons/skills/no-such-icon.png", missing),
                "a name nothing answers to");
        assertNull(HeroPanel.iconTexture(assets, "", missing), "no name at all");
        assertNull(HeroPanel.iconTexture(assets, null, missing));
        assertNull(HeroPanel.iconTexture(null, "Common/Textures/dot.png", missing),
                "and no asset manager, which is what a test harness has");

        assertEquals(1, missing.size(),
                "the miss is worth saying once; a slot is redrawn many times a second");
        assertNotNull(HeroPanel.iconTexture(assets, "Common/Textures/dot.png", missing),
                "and something that is really there still loads");
    }

    /** A hero with no skills at all is still a hero with health. */
    @Test
    void aPanelWithoutSkillsIsStillAPanel() {
        var reading = HeroPanel.Reading.parse(
                "name=Erika|rank=1-daraja|hp=550/550|xp=0/30|depth=I|depthWord=CHUQURLIK");

        assertNotNull(reading);
        assertTrue(reading.skills().isEmpty());
    }

    // ---- the parts a level-up run adds ----

    /**
     * The fuller line: the same hero, plus the figures under his bars, the powers
     * he has picked up, and three cards waiting to be chosen from.
     */
    private static final String FULL = LINE
            + "|stat=Zarba,34|stat=Zirh,12|stat=Tezlik,52"
            + "|pwWord=Kuchlar|pw=shot,2|pw=boot,1"
            + "|note=O'tkir tig'"
            + "|offer=3,8-daraja,Bittasini tanlang"
            + "|opt=shot,O'tkir uch,Q zarari +25%"
            + "|opt=clock,Tez qo'l,W kuluari -20%"
            + "|opt=heart,Qon ichuvchi,Zarbadan 10% jon qaytadi";

    @Test
    void theFiguresUnderTheBarsAreRead() {
        var stats = HeroPanel.Reading.parse(FULL).stats();

        assertEquals(3, stats.size());
        assertEquals("Zarba", stats.get(0).word());
        assertEquals("34", stats.get(0).value());
        assertEquals("Tezlik", stats.get(2).word());
    }

    @Test
    void theStripOfPowersIsRead() {
        var reading = HeroPanel.Reading.parse(FULL);

        assertEquals("Kuchlar", reading.powersWord());
        assertEquals(2, reading.powers().size());
        assertEquals("shot", reading.powers().get(0).icon());
        assertEquals(2, reading.powers().get(0).count(),
                "three of one card is one mark reading three");
        assertEquals(1, reading.powers().get(1).count());
    }

    @Test
    void theLevelUpCardsAreRead() {
        var offer = HeroPanel.Reading.parse(FULL).offer();

        assertNotNull(offer);
        assertEquals(3, offer.id(),
                "which offer this is, so a late click cannot spend it twice");
        assertEquals("8-daraja", offer.title());
        assertEquals("Bittasini tanlang", offer.hint());
        assertEquals(3, offer.cards().size());
        assertEquals("shot", offer.cards().get(0).icon());
        assertEquals("O'tkir uch", offer.cards().get(0).name());
        // The description is the rest of the field, so a percentage sign or a
        // dash in it is words rather than punctuation the parser has to survive.
        assertEquals("Q zarari +25%", offer.cards().get(0).description());
        assertEquals("Zarbadan 10% jon qaytadi", offer.cards().get(2).description());
    }

    /**
     * What he just picked up, said once and then not.
     *
     * <p>A line rather than the banner: the banner interrupts and belongs to
     * dying and to going down a floor, and finding a sword is news rather than an
     * interruption. The game stops sending it when it has been read long enough.
     */
    @Test
    void thePickupNoteIsRead() {
        assertEquals("O'tkir tig'", HeroPanel.Reading.parse(FULL).note());
        assertEquals("", HeroPanel.Reading.parse(LINE).note(),
                "an ordinary frame has nothing to announce");
    }

    @Test
    void aLineWithNoOfferHasNoCards() {
        assertNull(HeroPanel.Reading.parse(LINE).offer(),
                "an ordinary frame must not put a level-up screen on the player");
        assertTrue(HeroPanel.Reading.parse(LINE).powers().isEmpty());
    }

    @Test
    void anOfferWithoutCardsIsNotAnOffer() {
        assertNull(HeroPanel.Reading.parse(LINE + "|offer=3,8-daraja,tanlang").offer(),
                "a heading with nothing under it would be an empty screen with no way out");
    }

    /** The glyph vocabulary is by name, and an unknown name is a shape, not a gap. */
    @Test
    void everyIconNameDrawsSomething() {
        for (var icon : new String[] {"shot", "burst", "dash", "star", "clock", "heart",
            "boot", "plus", "times", "Q", "W", "E", "R", "no-such-icon"}) {
            var mesh = HeroPanel.glyph(icon, 24f);
            assertNotNull(mesh, icon);
            assertTrue(mesh.getVertexCount() > 0, icon + " drew nothing at all");
        }
    }
}
