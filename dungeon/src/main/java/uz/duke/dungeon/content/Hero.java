package uz.duke.dungeon.content;

import java.util.List;
import java.util.Set;
import uz.duke.core.thing.Classified;
import uz.duke.core.thing.Geometry;
import uz.duke.core.thing.Kind;
import uz.duke.core.thing.Sighted;
import uz.duke.core.thing.Solid;
import uz.duke.core.thing.Titled;

/**
 * A hero, as one {@code Hero} block writes it: what the engine builds him from, and how
 * he looks. His portrait's camera is in the same block, under {@code Portrait…} names,
 * and is read into the settings' portraits; his skills are blocks of their own, since he
 * has four.
 */
public record Hero(String name, String displayName, Set<Kind> kinds, float visionRange, Geometry geometry,
        List<ModuleEntry> modules, HeroLook look) implements Solid, Sighted, Classified, Titled {
}
