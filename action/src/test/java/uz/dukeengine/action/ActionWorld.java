package uz.dukeengine.action;

import java.util.List;
import java.util.Map;
import uz.dukeengine.combat.ArmedWorld;
import uz.dukeengine.combat.Armoury;
import uz.dukeengine.combat.CombatModules;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;
import uz.dukeengine.rpg.Seal;
import uz.dukeengine.rpg.skill.Skill;
import uz.dukeengine.rpg.skill.SkillBook;

/**
 * A world a hero is played in from his shoulder and nothing more: core's logic, combat's modules, a skill book and the
 * dodge as a game registers them. Two sides, enemies of each other.
 */
final class ActionWorld extends GameLogic implements ArmedWorld {

    private static final SkillBook.Rules RULES = new SkillBook.Rules(5f, 200f, 45f, 4, 10, 15, List.of(), 30);

    /** A template of the modules it is given and nothing else. */
    private record Plain(String name, List<ModuleData> modules) implements ThingTemplate {
    }

    private final Armoury armoury = new Armoury();
    final int heroes;
    final int monsters;

    /** @param skills each template's skills */
    ActionWorld(Map<String, List<Skill>> skills) {
        super(new ThingFactory(CombatModules.withDefaults()
                .register(SkillBook.Data.class, (owner, _) -> new SkillBook(owner,
                        skills.getOrDefault(owner.getTemplate().name(), List.of()), RULES, Seal.NONE))
                .register(Dodge.Data.class, Dodge::new)));
        init();
        var players = getPlayerList();
        var hero = players.addPlayer("Hero");
        var dungeon = players.addPlayer("Monsters");
        hero.setRelationshipTo(dungeon, Relationship.ENEMIES);
        dungeon.setRelationshipTo(hero, Relationship.ENEMIES);
        heroes = hero.getIndex();
        monsters = dungeon.getIndex();
    }

    ActionWorld template(String name, ModuleData... modules) {
        getThingFactory().addTemplate(new Plain(name, List.of(modules)));
        return this;
    }

    ActionWorld template(ThingTemplate template) {
        getThingFactory().addTemplate(template);
        return this;
    }

    GameObject put(String template, int side, float x, float y) {
        return spawn(findTemplate(template), new Coord3D(x, y, 0f), side);
    }

    void run(int frames) {
        for (int frame = 0; frame < frames; frame++) {
            update();
            drainEvents();
        }
    }

    @Override
    public Armoury armoury() {
        return armoury;
    }

    @Override
    protected void simulate() {
    }
}
