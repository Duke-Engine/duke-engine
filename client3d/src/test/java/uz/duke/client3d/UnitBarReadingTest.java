package uz.duke.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The floor's half of the status line, and the panel's, sharing one string.
 *
 * <p>They have to share it — the channel is one string — and the interesting
 * failures are all at the seam. A field one end adds is a field the other end
 * must at least recognise, because {@link HeroPanel} refuses a line it does not
 * fully understand: one unknown name and the whole card is somebody else's game,
 * and the panel goes blank. That is a change in the <em>dungeon</em> putting out
 * a light in the <em>client</em>, with nothing to connect the two.
 *
 * <p>So the line below is not invented. It was printed out of a running game and
 * pasted, and both readers are pointed at it.
 */
class UnitBarReadingTest {

    /**
     * A line as the dungeon really sends it, taken from a game at frame 90 with
     * nothing selected.
     *
     * <p>Nothing selected on purpose: it is the emptiest card the game can send
     * and still the one that carries every one of the floor's own fields, since
     * those do not depend on the player having clicked anything.
     */
    private static final String REAL =
            "name=|depth=I / IV|depthWord=CHUQURLIK|itWord=NARSALAR|skWord=MAHORAT"
                    + "|cmds=theirs|cmd=F,icons/commands/cmd_move.png,Yur,off"
                    + "|cmd=A,icons/commands/cmd_attack.png,Hujum,off"
                    + "|cmd=S,icons/commands/cmd_stop.png,To'xta,off"
                    + "|cmd=D,icons/commands/cmd_guard.png,Himoya,off"
                    + "|look=Forest,Wooded"
                    + "|deep=1|boss=43|hero=1,1,80,80,0,30"
                    + "|who=Rogue,Erika|who=Knight,Garen|who=Mage,Lira";

    @Test
    void theFloorsOwnFieldsAreRead() {
        var reading = UnitBarReading.read(REAL);

        assertEquals(1, reading.depth());
        assertEquals(43, reading.bossId());
        assertEquals(1, reading.heroId());
        assertEquals(1, reading.heroLevel());
        assertEquals(80, reading.mana());
        assertEquals(80, reading.maxMana());
        assertEquals(30, reading.experienceNeeded());
        assertEquals("Erika", reading.nameOf("Rogue"));
    }

    /**
     * And the panel still takes the same line.
     *
     * <p>The half of this that actually breaks. The floor's fields were added to
     * the game first and the panel knew none of them, which is not a bar drawn
     * wrong — it is every card refused and the whole bottom of the screen empty.
     */
    @Test
    void andThePanelStillTakesTheSameLine() {
        assertNotNull(HeroPanel.Reading.parse(REAL),
                "the panel refused a line its own game sends");
    }

    /** A template nobody renamed is printed as it is named. */
    @Test
    void aCreatureWithNoPrintedNameKeepsItsOwn() {
        var reading = UnitBarReading.read(REAL);

        assertEquals("Skeleton", reading.nameOf("Skeleton"),
                "the game sends no entry when the printed name is the template name");
    }

    // ---- what the medallion says ----

    /**
     * A monster's medallion says the depth, and the hero's says his level.
     *
     * <p>Not a stand-in. A monster is scaled by the depth it was spawned at and a
     * stage's difficulty is <em>defined</em> as a depth, so the number in the disc
     * is the number the simulation used to decide how hard the thing is.
     */
    @Test
    void theDiscSaysTheDepthOnEverybodyButTheHero() {
        var reading = UnitBarReading.read(REAL + "|deep=4");

        assertEquals(4, reading.levelOn(43), "the boss is on the fourth floor like the rest");
        assertEquals(4, reading.levelOn(77));
        assertEquals(1, reading.levelOn(1), "except the hero, who has a level of his own");
    }

    /**
     * The ring turns for the hero and for nobody else.
     *
     * <p>He is the only thing in the game that earns experience. A ring on a
     * skeleton would be a promise that it could fill.
     */
    @Test
    void onlyTheHerosRingTurns() {
        var reading = UnitBarReading.read(REAL.replace("hero=1,1,80,80,0,30",
                "hero=1,7,48,120,45,90"));

        assertEquals(0.5f, reading.experienceOn(1), 0.001f);
        assertEquals(0f, reading.experienceOn(43), "the boss earns nothing");
        assertEquals(0f, reading.experienceOn(77));
        assertTrue(reading.isHero(1));
        assertTrue(reading.isBoss(43));
        assertFalse(reading.isBoss(1));
    }

    /** A full pool, a full ring, and neither runs past its own end. */
    @Test
    void theRingNeverGoesPastTheWholeWayRound() {
        var over = UnitBarReading.read("name=|deep=1|hero=1,7,0,0,200,90");

        assertEquals(1f, over.experienceOn(1), 0.001f);
    }

    // ---- what happens when the line is wrong ----

    /**
     * A half-written hero is dropped rather than half-read.
     *
     * <p>The one field here that is read strictly, and for the same reason the
     * panel reads the whole line strictly: the six numbers are one fact about one
     * creature, and five of them is a medallion with somebody else's mana in it.
     */
    @Test
    void aHalfWrittenHeroIsNotHalfRead() {
        var short6 = UnitBarReading.read("name=|deep=2|hero=1,7,48");
        var broken = UnitBarReading.read("name=|deep=2|hero=1,seven,48,120,0,30");

        assertEquals(0, short6.heroId());
        assertEquals(0, broken.heroId());
        assertEquals(2, short6.depth(), "and the rest of the line is still read");
        assertEquals(2, broken.depth());
    }

    /**
     * Everything else that will not parse is dropped in silence.
     *
     * <p>Deliberately the opposite of the panel, which refuses the whole line. A
     * card that is half read lies about the hero; a mark over the world that did
     * not arrive is a mark not drawn, and the bar under it is still a bar.
     */
    @Test
    void aFieldThatWillNotParseCostsOnlyItself() {
        var reading = UnitBarReading.read("name=|deep=lots|boss=41|who=Brute|who=,nobody");

        assertEquals(0, reading.depth(), "kept whatever it had, which was nothing");
        assertEquals(41, reading.bossId(), "the field beside it still arrived");
        assertEquals("Brute", reading.nameOf("Brute"), "half an entry is no entry");
    }

    /** A game that says none of this is a game with no bars to draw. */
    @Test
    void aGameThatSaysNothing() {
        for (var nothing : new String[] {null, "", "name=Erika|hp=10/10"}) {
            var reading = UnitBarReading.read(nothing);

            assertEquals(0, reading.depth());
            assertEquals(0, reading.heroId());
            assertEquals(0, reading.bossId());
            assertFalse(reading.isHero(0), "nobody is the hero when nobody was named");
            assertFalse(reading.isBoss(0));
        }
    }
}
