package uz.duke.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class DukeTextTest {

    @Test
    void aLoneWordOpensABlockAndEndClosesTheInnermost() {
        var blocks = DukeText.parse("""
                ; a comment
                Monster
                  Name = Brute              ; the monster's own name
                  DisplayName = O'q ustasi
                  MoveUpdate
                    Speed = 10
                  End
                End
                Hero
                End
                """, "units.duke");

        assertEquals(List.of("Monster", "Hero"), blocks.stream().map(Block::word).toList());
        var monster = blocks.getFirst();
        assertEquals(2, monster.line());
        assertEquals(new Value.Text("Brute"), monster.fields().get(0).value());
        assertEquals(new Value.Text("O'q ustasi"), monster.fields().get(1).value());
        var move = monster.blocks().getFirst();
        assertEquals("MoveUpdate", move.word());
        assertEquals(new Field("Speed", new Value.Text("10"), 6), move.fields().getFirst());
    }

    @Test
    void aListIsOneValueAndMayRunOverLines() {
        var fields = DukeText.parse("""
                Content
                  KindOf = [INFANTRY, SELECTABLE]
                  Files = [
                    units/rogue.duke,   ; the archer
                    units/knight.duke,
                  ]
                  Words = ["You died", "a, b; c", ""]
                  None = []
                  Quoted = "x; y"
                End
                """, "content.duke").getFirst().fields();

        assertEquals(new Value.Items(List.of("INFANTRY", "SELECTABLE")), fields.get(0).value());
        assertEquals(new Value.Items(List.of("units/rogue.duke", "units/knight.duke")), fields.get(1).value());
        assertEquals(new Value.Items(List.of("You died", "a, b; c", "")), fields.get(2).value());
        assertEquals(new Value.Items(List.of()), fields.get(3).value());
        assertEquals(new Value.Text("x; y"), fields.get(4).value());
    }

    @Test
    void whatCannotBeReadSaysWhereAndWhy() {
        assertError("units.duke:1: a block opens with one word; its name goes inside it: 'Monster', then 'Name = Brute'",
                "Monster Brute\nEnd\n");
        assertError("units.duke:1: 'Monster' has no End", "Monster\n  Name = Brute\n");
        assertError("units.duke:1: End with no block open", "End\n");
        assertError("units.duke:1: 'Speed' stands outside any block", "Speed = 10\n");
        assertError("units.duke:3: 'File' is written twice in 'Content', first on line 2; a list is one value, [a, b]",
                "Content\n  File = a\n  File = b\nEnd\n");
        assertError("units.duke:2: '[' is never closed by ']'", "Content\n  Files = [a,\nEnd\n");
        assertError("units.duke:2: a comma is missing between the items of a list", "Content\n  Files = [a\n b]\nEnd\n");
        assertError("units.duke:2: a quote is never closed", "Content\n  Name = \"open\nEnd\n");
    }

    private static void assertError(String message, String text) {
        var error = assertThrows(DataException.class, () -> DukeText.parse(text, "units.duke"));
        assertTrue(error.getMessage().equals(message), error.getMessage());
    }
}
