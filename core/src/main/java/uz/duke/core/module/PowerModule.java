package uz.duke.core.module;

import uz.duke.core.ini.FieldParseTable;
import uz.duke.core.ini.Ini;
import uz.duke.core.thing.GameObject;

/**
 * Contributes to or draws from a player's power grid, ported in spirit from
 * SAGE's {@code PowerPlantUpdate} / power consumption on structures.
 *
 * <p>Power plants {@link #getProduced() produce}; most structures
 * {@link #getConsumed() consume}. The player's net surplus determines whether
 * power-dependent structures function — when a base is under-powered, production
 * stalls and defenses go offline, exactly as in Generals.
 */
public final class PowerModule extends Module {

    /** INI config: {@code Produces} / {@code Consumes} (power units). */
    public record Data(int produced, int consumed) implements ModuleData {
    }

    private static final class DataBuilder {
        int produced;
        int consumed;

        Data build() {
            return new Data(produced, consumed);
        }
    }

    private static final FieldParseTable<DataBuilder> DATA_TABLE = new FieldParseTable<DataBuilder>()
            .add("Produces", Ini.integer((b, v) -> b.produced = v))
            .add("Consumes", Ini.integer((b, v) -> b.consumed = v));

    public static ModuleData parseData(Ini ini) {
        var builder = new DataBuilder();
        ini.initFromIni(builder, DATA_TABLE);
        return builder.build();
    }

    private final int produced;
    private final int consumed;

    public PowerModule(GameObject owner, Data data) {
        super(owner);
        this.produced = data.produced();
        this.consumed = data.consumed();
    }

    public int getProduced() {
        return produced;
    }

    public int getConsumed() {
        return consumed;
    }
}
