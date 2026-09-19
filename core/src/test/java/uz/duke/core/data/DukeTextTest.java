package uz.duke.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class DukeTextTest {

    @Test
    void everyLineOfABlockIsOneOfItsFields() {
        var blocks = DukeText.parse("""
                ; a comment
                Monster
                  Name = Brute              ; the monster's own name
                  DisplayName = O'q ustasi
                  Geometry = Cylinder
                    Radius = 6
                  End
                  Modules = [               ; its behaviour
                    MoveUpdate
                      Speed = 10
                    End
                    EyesOnly
                    End
                  ]
                  Armor
                    FLAME = 0.5
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

        var geometry = assertInstanceOf(Value.Nested.class, monster.fields().get(2).value()).block();
        assertEquals("Cylinder", geometry.word());
        assertEquals(5, geometry.line());
        assertEquals(new Field("Radius", new Value.Text("6"), 6), geometry.fields().getFirst());

        var modules = assertInstanceOf(Value.NestedList.class, monster.fields().get(3).value()).blocks();
        assertEquals(List.of("MoveUpdate", "EyesOnly"), modules.stream().map(Block::word).toList());
        assertEquals(new Field("Speed", new Value.Text("10"), 10), modules.getFirst().fields().getFirst());

        assertEquals("Armor", monster.blocks().getFirst().word());
    }

    @Test
    void aWordAfterTheEqualsSignOpensABlockOnlyWhenTheLineUnderItIsDeeper() {
        var fields = DukeText.parse("""
                Monster
                  Geometry = Sphere
                  Projectile = HeavyArrow
                  Portrait = PortraitArt
                    ; a comment is not what decides it
                    Yaw = -12
                  End
                  Look = Fire
                End
                """, "units.duke").getFirst().fields();

        assertEquals(new Value.Text("Sphere"), fields.get(0).value());
        assertEquals(new Value.Text("HeavyArrow"), fields.get(1).value());
        assertEquals("PortraitArt", assertInstanceOf(Value.Nested.class, fields.get(2).value()).block().word());
        assertEquals(new Value.Text("Fire"), fields.get(3).value());
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
                  Kinds = [
                    TRAIL,
                    GLOW
                  ]
                End
                """, "content.duke").getFirst().fields();

        assertEquals(new Value.Items(List.of("INFANTRY", "SELECTABLE")), fields.get(0).value());
        assertEquals(new Value.Items(List.of("units/rogue.duke", "units/knight.duke")), fields.get(1).value());
        assertEquals(new Value.Items(List.of("You died", "a, b; c", "")), fields.get(2).value());
        assertEquals(new Value.Items(List.of()), fields.get(3).value());
        assertEquals(new Value.Text("x; y"), fields.get(4).value());
        assertEquals(new Value.Items(List.of("TRAIL", "GLOW")), fields.get(5).value(), "words with commas are values");
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
        assertError("units.duke:2: 'Cylinder' has no End; if Cylinder is a value, the line under 'Geometry = Cylinder'"
                + " is indented too deep", "Monster\n  Geometry = Cylinder\n    Radius = 3\n");
        assertError("units.duke:2: 'Modules = [' is never closed by ']'", "Monster\n  Modules = [\n    Bow\n    End\n");
        assertError("units.duke:5: 'Modules' is a list of blocks, each closed by End, and ends with ']' on a line of its own,"
                + " not 'Speed = 1'", "Monster\n  Modules = [\n    Bow\n    End\n    Speed = 1\n  ]\nEnd\n");
        assertError("units.duke:3: ']' with no list of blocks open", "Monster\n  Name = Brute\n  ]\nEnd\n");
    }

    /**
     * A value whose next line is indented by mistake opens a block, which would take the End of the
     * block around it; the error is said at the line that did it, not where that End runs out.
     */
    @Test
    void aLineIndentedTooDeepIsFoundWhereItIs() {
        var misindented = String.join("\n",
                "Monster",
                "  Name = Brute",
                "  Effect = EmberEyes",
                "    ModelScale = 4.2",
                "  Walk = Running_A",
                "End",
                "");
        assertError("units.duke:3: 'EmberEyes' has no End; if EmberEyes is a value, the line under"
                + " 'Effect = EmberEyes' is indented too deep", misindented);
        assertError("units.duke:2: 'Cylinder' has no End; if Cylinder is a value, the line under"
                + " 'Geometry = Cylinder' is indented too deep",
                "Monster\n  Geometry = Cylinder\n    Radius = 3\nEnd\n");
    }

    private static void assertError(String message, String text) {
        var error = assertThrows(DataException.class, () -> DukeText.parse(text, "units.duke"));
        assertTrue(error.getMessage().equals(message), error.getMessage());
    }
}
