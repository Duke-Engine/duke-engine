package uz.dukeengine.game;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import uz.dukeengine.combat.Armoury;
import uz.dukeengine.combat.ArmedWorld;
import uz.dukeengine.combat.module.Weapon;
import uz.dukeengine.combat.module.WeaponBonus;
import uz.dukeengine.core.Flavour;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.module.Brings;
import uz.dukeengine.core.module.ModuleFactory;
import uz.dukeengine.core.thing.ThingTemplate;
import uz.dukeengine.core.thing.ThingTemplateLoader;
import uz.dukeengine.core.thing.WorldTemplate;

/** What the game's world is made from: its templates, weapons and bonus table, the way they are read, and its world. */
final class Content {

    private record UnitText(String text, String source) {
    }

    private final List<UnitText> unitTexts = new ArrayList<>();
    private final List<ThingTemplate> units = new ArrayList<>();
    private final List<Weapon> weapons = new ArrayList<>();
    private final List<WeaponBonus> weaponBonuses = new ArrayList<>();
    private final List<Consumer<ModuleFactory>> moduleCustomizers = new ArrayList<>();
    private final List<Consumer<ThingTemplateLoader>> templateCustomizers = new ArrayList<>();
    private WorldTemplate world;
    /** What else each template may bring into the world, by name. */
    private Function<String, ? extends Collection<String>> brings;

    void addText(String text, String source) {
        unitTexts.add(new UnitText(text, source));
    }

    void addUnits(Collection<? extends ThingTemplate> templates) {
        units.addAll(templates);
    }

    void addWeapons(Collection<Weapon> more) {
        weapons.addAll(more);
    }

    void addWeaponBonuses(Collection<WeaponBonus> more) {
        weaponBonuses.addAll(more);
    }

    void customModules(Consumer<ModuleFactory> customizer) {
        moduleCustomizers.add(customizer);
    }

    void templates(Consumer<ThingTemplateLoader> customizer) {
        templateCustomizers.add(customizer);
    }

    void world(WorldTemplate world) {
        this.world = world;
    }

    void brings(Function<String, ? extends Collection<String>> rule) {
        this.brings = rule;
    }

    /** Read everything into the world: custom modules first, since a unit's block may name them. */
    void load(GameLogic logic, Flavour flavour) {
        for (var customizer : moduleCustomizers) {
            customizer.accept(logic.getThingFactory().getModuleFactory());
        }
        var loader = new ThingTemplateLoader(logic.getThingFactory());
        flavour.templates(loader);
        for (var customizer : templateCustomizers) {
            customizer.accept(loader);
        }
        for (var template : units) {
            logic.getThingFactory().addTemplate(template);
        }
        if (!weapons.isEmpty()) {
            armoury(logic, flavour).addWeapons(weapons);
        }
        if (!weaponBonuses.isEmpty()) {
            armoury(logic, flavour).setWeaponBonuses(weaponBonuses);
        }
        for (var text : unitTexts) {
            loader.load(text.text(), text.source());
        }
    }

    void setWorld(GameLogic logic) {
        logic.setWorld(world);
    }

    /** The world's armoury, where its weapons and bonus table go: an error for a kind whose things do not fight. */
    private static Armoury armoury(GameLogic logic, Flavour flavour) {
        if (logic instanceof ArmedWorld armed) {
            return armed.armoury();
        }
        throw new IllegalStateException(flavour.getClass().getName() + " makes a world with no weapons to give");
    }

    /** Every template that stands in the world, can be produced from it, or is brought in by what is. */
    Set<String> reachableFrom(GameLogic logic) {
        var seen = new LinkedHashSet<String>();
        var next = new ArrayDeque<String>();
        for (var object : logic.getObjects()) {
            next.add(object.getTemplate().name());
        }
        while (!next.isEmpty()) {
            var name = next.poll();
            var template = logic.getThingFactory().findTemplate(name);
            if (template == null || !seen.add(name)) {
                continue; // a name nothing answers to draws nothing
            }
            for (var module : template.modules()) {
                if (module instanceof Brings line) {
                    next.addAll(line.brings());
                }
            }
            if (brings != null) {
                next.addAll(brings.apply(name));
            }
        }
        return Collections.unmodifiableSet(seen);
    }
}
