package uz.duke.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

    /**
     * A line carrying a cast is still the panel's line.
     *
     * <p>The field is nothing to do with the panel -- it is where a skill wants
     * its ring drawn, which {@code DukeRtsApp} reads straight off the snapshot --
     * but the line is ONE line, and a field this parser does not recognise means
     * the whole thing was meant for somebody else and the bar goes dark. So the
     * panel has to know the names of fields it draws nothing for, and the way
     * that breaks is somebody adding one to the game and not to this switch: the
     * hero's bar simply vanishes the first time a skill is cast.
     */
    @Test
    void aLineCarryingACastIsStillRead() {
        var reading = HeroPanel.Reading.parse(
                LINE + "|cast=FrostNova,412,150.0,150.0,40.0");

        assertNotNull(reading, "the bar went dark the moment a skill was cast");
        assertEquals("Erika", reading.name());
        assertEquals(4, reading.skills().size(), "and the rest of the line survived it");
    }

    /**
     * What the pips and the badge are drawn from.
     *
     * <p>The line says what is in a slot, what fits in it, whether the next point
     * may go there, and the finished word to write under it. The word is the
     * game's — this client serves three other games and writes none of its own —
     * and the COLOUR is the panel's, because that is a fact about a state it can
     * already see.
     */
    @Test
    void aSlotSaysWhatIsInItAndWhetherItMayGrow() {
        var reading = HeroPanel.Reading.parse(LINE
                + "|srank=Q,2,4,up,2 → 3|srank=R,0,3,no,4-daraja|pts=2,NUQTA");

        assertNotNull(reading);
        assertEquals(2, reading.ranks().size());
        var q = reading.ranks().get(0);
        assertEquals('Q', q.key());
        assertEquals(2, q.rank());
        assertEquals(4, q.max());
        assertTrue(q.canRaise());
        assertEquals("2 → 3", q.word());
        assertFalse(reading.ranks().get(1).canRaise(), "the ultimate is waiting for a level");
        assertEquals(2, reading.points());
        assertEquals("NUQTA", reading.pointsWord());
    }

    /**
     * An ordinary slot nobody has bought says "lock" and stops there.
     *
     * <p>It waits for a POINT rather than for a level, so there is no level to
     * name — and the parser used to refuse the whole line for the missing field,
     * which would have taken the hero's bar down with it the first time anybody
     * started a run.
     */
    @Test
    void aLockedSlotNeedNotNameALevel() {
        var reading = HeroPanel.Reading.parse(
                "name=Erika|hp=1/2|xp=0/1|skill=Q,,lock|skill=R,,lock,4-daraja");

        assertNotNull(reading, "the bar went dark on an unbought skill");
        assertEquals(2, reading.skills().size());
        assertEquals("", reading.skills().get(0).label());
        assertEquals("4-daraja", reading.skills().get(1).label());
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

    // ---- the parts a run adds ----

    /** The fuller line: the same hero, plus the figures under his bars and a note. */
    private static final String FULL = LINE
            + "|stat=Zarba,34|stat=Zirh,12|stat=Tezlik,52"
            + "|note=O'tkir tig'";

    @Test
    void theFiguresUnderTheBarsAreRead() {
        var stats = HeroPanel.Reading.parse(FULL).stats();

        assertEquals(3, stats.size());
        assertEquals("Zarba", stats.get(0).word());
        assertEquals("34", stats.get(0).value());
        assertEquals("Tezlik", stats.get(2).word());
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

    // ---- white drawings and painted pictures ----

    /**
     * A white drawing is coloured by its state; a painted one is only dimmed.
     *
     * <p>The panel has always multiplied the picture in a slot by a colour, which
     * is what lets one white file serve a skill that is ready, one reloading and
     * one locked. A painted picture cannot take that — multiply a blue frost
     * burst by the torch colour and it is a gold frost burst — so the state has
     * to be told in brightness instead, and this is the fork where that is
     * decided.
     *
     * <p>Asked as arithmetic rather than by drawing anything: what makes it wrong
     * is a hue applied to a picture that already has one, and a hue is three
     * numbers.
     */
    @Test
    void aPaintedIconIsDimmedRatherThanColoured() {
        var painted = new IconLook(true);

        for (var state : new boolean[][] {{false, false}, {false, true}, {true, false}}) {
            var colour = HeroPanel.skillColour(painted, state[0], state[1]);
            assertEquals(colour.r, colour.g, 0.001f,
                    "a painted picture may be darkened but never tinted");
            assertEquals(colour.g, colour.b, 0.001f,
                    "a painted picture may be darkened but never tinted");
        }
    }

    /** And the three states are still told apart, which is what the colour is for. */
    @Test
    void andItsThreeStatesAreStillToldApart() {
        var painted = new IconLook(true);
        float ready = HeroPanel.skillColour(painted, false, false).r;
        float cooling = HeroPanel.skillColour(painted, false, true).r;
        float locked = HeroPanel.skillColour(painted, true, false).r;

        assertTrue(ready > cooling, "a skill he can cast is brighter than one reloading");
        assertTrue(cooling > locked, "and one reloading is brighter than one he has not earned");
        assertEquals(1f, ready, 0.001f, "ready is the picture exactly as it was painted");
    }

    /** A white drawing is left as it was: the states are hues, and they differ. */
    @Test
    void aWhiteDrawingIsStillColouredByItsState() {
        var white = IconLook.DEFAULT;
        var ready = HeroPanel.skillColour(white, false, false);
        var cooling = HeroPanel.skillColour(white, false, true);
        var locked = HeroPanel.skillColour(white, true, false);

        assertNotEquals(ready.r, ready.b, "the torch colour is warm, not grey");
        assertFalse(ready.equals(cooling) || cooling.equals(locked),
                "three states, three colours");
    }

    /**
     * A figure under the bars carries its own picture.
     *
     * <p>It used to be chosen inside the panel, by which figure it was, out of a
     * list of four names the client held — so a game's third figure was a
     * lightning bolt whatever the game meant by it, and a fifth figure got
     * whatever the fourth had.
     */
    @Test
    void aFigureUnderTheBarsCarriesItsOwnPicture() {
        var stats = HeroPanel.Reading.parse(LINE
                + "|stat=Zarba,31,+6,icons/stats/stat_attack.png"
                + "|stat=Zirh,12,,icons/stats/stat_armor.png").stats();

        assertEquals(2, stats.size());
        assertEquals("icons/stats/stat_attack.png", stats.get(0).icon());
        assertEquals("+6", stats.get(0).bonus());
        assertEquals("icons/stats/stat_armor.png", stats.get(1).icon());
        assertEquals("", stats.get(1).bonus(), "a figure that lends nothing still leaves the gap");
    }

    /** And a game that names none is read as before rather than refused. */
    @Test
    void andAGameThatNamesNoPictureIsStillRead() {
        var stats = HeroPanel.Reading.parse(LINE + "|stat=Wave,4").stats();

        assertEquals(1, stats.size());
        assertEquals("", stats.get(0).icon());
    }

    /** His primary is read off the line, and only his primary. */
    @Test
    void hisPrimaryIsReadOffTheLine() {
        var stats = HeroPanel.Reading.parse(LINE
                + "|stat=Kuch,22,,icons/stats/stat_health.png,primary"
                + "|stat=Aql,8,,icons/stats/stat_crit.png").stats();

        assertTrue(stats.get(0).primary(), "the mark was lost");
        assertFalse(stats.get(1).primary(), "a figure with no mark is not his primary");
        assertEquals("icons/stats/stat_health.png", stats.get(0).icon(),
                "and the picture is still where it was");
    }

    /** An attribute's card is read by its place on the line. */
    @Test
    void anAttributesCardIsReadByItsPlaceOnTheLine() {
        var reading = HeroPanel.Reading.parse(LINE
                + "|stat=Kuch,22,,,primary|stat=Epchillik,10,,"
                + "|stTipName=0,Kuch|stTipAt=0,Asosiy atribut|stTipText=0,Har bir birlik beradi:"
                + "|stTipRow=0,Jon,+12,|stTipRow=0,Zarba,+1,"
                + "|stTipName=1,Epchillik|stTipRow=1,Tezlik,+0.15,");

        assertNotNull(reading, "a line with cards on its figures was refused as somebody else's");
        var strength = reading.statTips().get(0);
        assertEquals("Kuch", strength.name());
        assertEquals("Asosiy atribut", strength.at());
        assertEquals(2, strength.rows().size());
        assertEquals("+12", strength.rows().get(0).now());
        assertEquals("+0.15", reading.statTips().get(1).rows().get(0).now(),
                "a decimal survives the line");
        assertTrue(reading.tips().isEmpty(), "and none of it was taken for a skill's card");
    }

    // ---- what a skill costs ----

    /** The pool and the prices are read off the line. */
    @Test
    void theManaBarAndItsPricesAreRead() {
        var reading = HeroPanel.Reading.parse(LINE
                + "|mana=48/120|cost=Q,22,yes|cost=R,70,no");

        assertNotNull(reading);
        assertEquals(48f, reading.mana(), 0.001f);
        assertEquals(120f, reading.maxMana(), 0.001f);
        assertEquals(2, reading.costs().size());
        assertEquals('Q', reading.costs().get(0).key());
        assertEquals(22, reading.costs().get(0).cost());
        assertTrue(reading.costs().get(0).affordable(), "he has forty-eight and it costs twenty-two");
        assertFalse(reading.costs().get(1).affordable(), "and the ultimate is out of reach");
    }

    /**
     * A game that charges nothing sends none of it and is read as before.
     *
     * <p>The client serves three other games and none of them has a mana bar. A
     * missing pool has to mean "draw no bar" rather than "draw an empty one",
     * which is a claim that the hero is out.
     */
    @Test
    void aGameWithNoManaIsReadAsItAlwaysWas() {
        var reading = HeroPanel.Reading.parse(LINE);

        assertNotNull(reading);
        assertEquals(0f, reading.maxMana(), 0.001f, "no pool at all");
        assertTrue(reading.costs().isEmpty());
    }

    /** The refusal arrives stamped, so it can be sounded once rather than every frame. */
    @Test
    void aRefusalIsStampedWithItsFrame() {
        assertEquals(0, HeroPanel.Reading.parse(LINE).refusedForManaAt(),
                "nothing was refused");
        assertEquals(412, HeroPanel.Reading.parse(LINE + "|noMana=412").refusedForManaAt());
    }

    /** And a price that will not parse refuses the line, like every other field. */
    @Test
    void aBrokenPriceRefusesTheLine() {
        assertNull(HeroPanel.Reading.parse(LINE + "|cost=Q,lots,yes"));
        assertNull(HeroPanel.Reading.parse(LINE + "|cost=Q"));
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

    // ---- what an empty pool does to the key ----

    /** A panel with a hero on it, the way the layout tests raise one. */
    private static HeroPanel shown(String line) {
        var assets = new com.jme3.asset.DesktopAssetManager(true);
        var font = assets.loadFont("Interface/Fonts/Default.fnt");
        var panel = new HeroPanel(assets, font, new com.jme3.scene.Node("gui"), 1600f,
                PanelSkin.NONE, RangeLook.DEFAULT);
        assertTrue(panel.show(line, 0f), "the panel should have taken the line");
        return panel;
    }

    /**
     * A skill he cannot pay for does not arm.
     *
     * <p>It was refused where the cast is made, which reads as the same thing and
     * is not: arming is a step earlier, and arming is what changes the cursor and
     * throws the skill's reach onto the floor. So an ultimate he was forty short
     * of still <em>aimed</em> — and then ate the click and did nothing, which
     * looks like the button is broken rather than like he is out.
     */
    @Test
    void aSkillHeCannotPayForDoesNotArm() {
        var panel = shown(LINE + "|mana=10/120|cost=Q,22,no|cost=E,16,yes");

        assertFalse(panel.readyToCast('Q'), "twenty-two out of ten: it must not aim");
        assertTrue(panel.readyToCast('E'), "and one he can afford still does");
    }

    /**
     * Only the empty pool is answered where he pressed.
     *
     * <p>The other two refusals are already written across the socket he is
     * looking at — a cooldown sweeps and counts down, a locked skill says which
     * level buys it. Being broke is written on a bar at the far end of the panel,
     * so it is the one that has to be said at the key.
     */
    @Test
    void onlyTheEmptyPoolIsWorthAnsweringAtTheKey() {
        var panel = shown(LINE + "|mana=10/120|cost=Q,22,no|cost=W,24,no|cost=E,16,yes"
                + "|cost=R,70,no");

        assertTrue(panel.refusedForMana('Q'), "ready, and he is short");
        assertFalse(panel.refusedForMana('E'), "ready and paid for");
        assertFalse(panel.refusedForMana('W'), "cooling: its own face says so");
        assertFalse(panel.refusedForMana('R'), "locked: its own face says so");
    }

    /**
     * The refusal raised here is taken once, like the simulation's own.
     *
     * <p>Nothing is sent any more when arming is refused, so nothing comes back;
     * the noise has to be raised from this side. Taken once because it is a sound
     * and a flash, and a held key asks many times a second.
     */
    @Test
    void aRefusalRaisedHereIsTakenOnce() {
        var panel = shown(LINE + "|mana=10/120|cost=Q,22,no");

        assertFalse(panel.takeRefusal(), "nothing has been refused yet");
        panel.denyForMana(1f);
        assertTrue(panel.takeRefusal());
        assertFalse(panel.takeRefusal(), "and it is not sounded again every frame");
    }

    // ---- the one line that is a label rather than a reading ----

    /**
     * The title is set with air between its letters, and more between its words.
     *
     * <p>jME has no tracking — a bitmap font advances by whatever was baked into
     * it — so the air is put in by hand. The word gap is the half worth testing:
     * the space the words already had is still there with an inserted one either
     * side, which is what stops a spaced-out title reading as one long string.
     */
    @Test
    void theTitleIsLetteredWithAirInIt() {
        assertEquals("O ' q   u s t a s i", HeroPanel.spacedOut("O'q ustasi"));
        assertEquals("", HeroPanel.spacedOut(""), "a game that gives him no title");
        assertEquals("", HeroPanel.spacedOut(null));
        assertEquals("M", HeroPanel.spacedOut("M"), "one letter has nothing to space from");
    }
}
