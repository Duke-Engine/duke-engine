package uz.duke.dungeon.content;

import java.util.List;
import uz.duke.core.effect.Effect;
import uz.duke.core.data.Link;
import uz.duke.core.module.ModuleData;
import uz.duke.core.thing.Sighted;
import uz.duke.core.thing.Titled;

/**
 * A thing in flight, as one {@code Projectile} block writes it: what the engine builds it from,
 * and what it is drawn as. It takes up no room — nothing collides with an arrow, it collides
 * with things — so it has no shape; and it may see, as a flare arrow would.
 *
 * @param part         the name <em>inside</em> the model file, for a projectile that is one mesh
 *                     of a larger file; none for a file that is the projectile
 * @param height       how far off the ground it flies
 * @param effect       the {@code Effect} it burns with, by name
 * @param effectOffset how far back along it the effect sits
 */
public record Projectile(String name, String displayName, float visionRange, List<ModuleData> modules,
        String model, String part, float scale, float facing, float height, int tint, @Link(Effect.class) String effect,
        float effectOffset) implements Sighted, Titled {

    /** What a block leaves out. */
    static final Projectile DEFAULTS = new Projectile("", "", 0f, List.of(), null, null, 1f, 90f, 0f, 0xFFFFFF,
            null, 0f);

    public Projectile {
        displayName = displayName == null ? "" : displayName;
        modules = modules == null ? List.of() : List.copyOf(modules);
    }

    /** What it is drawn as. */
    public DungeonSettings.ArrowLook look() {
        return new DungeonSettings.ArrowLook(name, model, part, scale, facing, height, tint, effect, effectOffset);
    }
}
