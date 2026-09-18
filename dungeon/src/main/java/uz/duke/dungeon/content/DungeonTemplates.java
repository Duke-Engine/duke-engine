package uz.duke.dungeon.content;

import java.util.Objects;
import uz.duke.core.ini.FieldParseTable;
import uz.duke.core.thing.ThingTemplateLoader;

/**
 * The dungeon's block types, for a world's template loader: {@code Monster}, {@code Hero},
 * {@code Projectile} and {@code Prop}.
 *
 * <p>Each block is read twice, by two readers that each know the other's fields: the
 * settings read the dungeon's part before any world exists, passing over the engine's,
 * and a world reads the engine's part and its modules here, passing over the dungeon's,
 * which it takes from the settings by name. A field neither reads is refused by both.
 */
public final class DungeonTemplates {

    private DungeonTemplates() {
    }

    public static ThingTemplateLoader register(ThingTemplateLoader loader, DungeonSettings settings) {
        return loader
                .type("Monster", Monster.class, FieldParseTable.passingOver(DungeonSettings.MONSTER_FIELDS), () -> null,
                        (parts, none) -> new Monster(parts.name(), parts.displayName(), parts.kinds(), parts.visionRange(),
                                parts.geometry(), parts.modules(), named(settings.monster(parts.name()), "Monster", parts.name())))
                .type("Hero", Hero.class, FieldParseTable.passingOver(DungeonSettings.HERO_FIELDS), () -> null,
                        (parts, none) -> new Hero(parts.name(), parts.displayName(), parts.kinds(), parts.visionRange(),
                                parts.geometry(), parts.modules(), settings.heroNamed(parts.name())))
                .type("Projectile", Projectile.class, FieldParseTable.passingOver(DungeonSettings.PROJECTILE_FIELDS), () -> null,
                        (parts, none) -> new Projectile(parts.name(), parts.displayName(), parts.visionRange(), parts.modules(),
                                settings.projectile(parts.name())))
                .type("Prop", Prop.class, FieldParseTable.passingOver(DungeonSettings.PROP_FIELDS), () -> null,
                        (parts, none) -> new Prop(parts.name(), parts.displayName(), parts.kinds(), parts.visionRange(), parts.geometry(),
                                parts.modules(), settings.prop(parts.name())));
    }

    /** The settings' part of a block, which they read from the same files; missing is a mismatch, and said. */
    private static <T> T named(T found, String type, String name) {
        return Objects.requireNonNull(found, () -> type + " " + name + " is in the units but not in the settings;"
                + " both are read from the same blocks, so the two were given different files");
    }
}
