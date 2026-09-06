package uz.duke.core.ini;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import org.junit.jupiter.api.Test;

class IniTest {

    enum Side { America, China, GLA }

    /** Mutable POJO populated from INI, in the spirit of a SAGE template. */
    static final class Weapon {
        String name;
        Side side;
        int clipSize;
        float damage;
        boolean autoReload;
        float x;
        float y;
        float z;
    }

    private static HashMap<String, Ini.BlockParser> weaponRegistry(java.util.List<Weapon> out) {
        var table = new FieldParseTable<Weapon>()
                .add("Side", Ini.enumeration(Side.class, (w, v) -> w.side = v))
                .add("ClipSize", Ini.integer((w, v) -> w.clipSize = v))
                .add("Damage", Ini.real((w, v) -> w.damage = v))
                .add("AutoReload", Ini.bool((w, v) -> w.autoReload = v))
                .add("MuzzleOffset", (ini, w) -> {
                    w.x = Ini.scanReal(ini.getNextSubToken("X"));
                    w.y = Ini.scanReal(ini.getNextSubToken("Y"));
                    w.z = Ini.scanReal(ini.getNextSubToken("Z"));
                });

        var registry = new HashMap<String, Ini.BlockParser>();
        registry.put("Weapon", ini -> {
            var w = new Weapon();
            w.name = ini.getNextToken();
            ini.initFromIni(w, table);
            out.add(w);
        });
        return registry;
    }

    @Test
    void parsesBlockWithCommentsAndMixedFields() {
        var out = new java.util.ArrayList<Weapon>();
        var text = """
                Weapon TankCannon       ; the crusader's gun
                  Side = America
                  ClipSize = 6
                  Damage = 75.5
                  AutoReload = Yes
                  MuzzleOffset = X:1.0 Y:0.0 Z:2.5
                End
                """;

        Ini.of(text, weaponRegistry(out)).load();

        assertEquals(1, out.size());
        var w = out.get(0);
        assertEquals("TankCannon", w.name);
        assertEquals(Side.America, w.side);
        assertEquals(6, w.clipSize);
        assertEquals(75.5f, w.damage, 1e-6f);
        assertTrue(w.autoReload);
        assertEquals(1.0f, w.x, 1e-6f);
        assertEquals(2.5f, w.z, 1e-6f);
    }

    @Test
    void parsesMultipleBlocks() {
        var out = new java.util.ArrayList<Weapon>();
        var text = """
                Weapon A
                  ClipSize = 1
                End
                Weapon B
                  ClipSize = 2
                End
                """;
        Ini.of(text, weaponRegistry(out)).load();
        assertEquals(2, out.size());
        assertEquals("A", out.get(0).name);
        assertEquals(2, out.get(1).clipSize);
    }

    @Test
    void unknownFieldThrows() {
        var out = new java.util.ArrayList<Weapon>();
        var text = """
                Weapon Bad
                  NoSuchField = 3
                End
                """;
        var ex = assertThrows(IniException.class, () -> Ini.of(text, weaponRegistry(out)).load());
        assertTrue(ex.getMessage().contains("NoSuchField"));
    }

    @Test
    void missingEndTokenThrows() {
        var out = new java.util.ArrayList<Weapon>();
        var text = """
                Weapon Truncated
                  ClipSize = 9
                """;
        var ex = assertThrows(IniException.class, () -> Ini.of(text, weaponRegistry(out)).load());
        assertTrue(ex.getMessage().contains("End"));
    }

    @Test
    void scanBoolRejectsGarbage() {
        assertTrue(Ini.scanBool("Yes"));
        assertFalse(Ini.scanBool("no"));
        assertThrows(IniException.class, () -> Ini.scanBool("maybe"));
    }
}
