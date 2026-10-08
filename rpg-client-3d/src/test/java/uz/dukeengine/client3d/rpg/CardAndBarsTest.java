package uz.dukeengine.client3d.rpg;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

/** The hero card and the bars over the world come down one status line, so the panel takes the bars' fields too. */
class CardAndBarsTest {

    /** The line the dungeon sends with nothing selected, as the client's own bars test has it. */
    private static final String REAL =
            "name=|depth=I / IV|depthWord=CHUQURLIK|itWord=NARSALAR|skWord=MAHORAT"
                    + "|cmds=theirs|cmd=F,icons/commands/cmd_move.png,Yur,off"
                    + "|cmd=A,icons/commands/cmd_attack.png,Hujum,off"
                    + "|cmd=S,icons/commands/cmd_stop.png,To'xta,off"
                    + "|cmd=D,icons/commands/cmd_guard.png,Himoya,off"
                    + "|look=Forest,Wooded"
                    + "|deep=1|boss=43|hero=1,1,80,80,0,30"
                    + "|who=Rogue,Erika|who=Knight,Garen|who=Mage,Lira";

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
}
