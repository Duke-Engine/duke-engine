package uz.dukeengine.rpg;

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
import uz.dukeengine.rpg.item.GroundItem;
import uz.dukeengine.rpg.mission.GateUpdate;
import uz.dukeengine.rpg.skill.Skill;
import uz.dukeengine.rpg.skill.SkillBook;

/**
 * A world an RPG plays in and nothing more: core's logic, combat's modules, and the rpg's as a game registers them —
 * no RTS anywhere on the classpath. Two sides, enemies of each other.
 */
public final class RpgWorld extends GameLogic implements ArmedWorld {

    /** The numbers a game hands its skill books. */
    public static final SkillBook.Rules RULES = new SkillBook.Rules(5f, 200f, 45f, 4, 10, 15, List.of(), 30);

    /** A template of the modules it is given and nothing else. */
    public record Plain(String name, List<ModuleData> modules) implements ThingTemplate {
    }

    private final Armoury armoury = new Armoury();
    public final int heroes;
    public final int monsters;

    /** @param skills each template's skills, as a game reads them out of its blocks */
    public RpgWorld(Map<String, List<Skill>> skills, Seal seal) {
        super(new ThingFactory(CombatModules.withDefaults()
                .register(SkillBook.Data.class, (owner, data) -> new SkillBook(owner,
                        skills.getOrDefault(owner.getTemplate().name(), List.of()), RULES, seal))
                .register(GroundItem.Data.class, (owner, data) -> new GroundItem(owner))
                .register(GateUpdate.Data.class, GateUpdate::new)));
        init();
        var players = getPlayerList();
        var hero = players.addPlayer("Hero");
        var dungeon = players.addPlayer("Monsters");
        hero.setRelationshipTo(dungeon, Relationship.ENEMIES);
        dungeon.setRelationshipTo(hero, Relationship.ENEMIES);
        heroes = hero.getIndex();
        monsters = dungeon.getIndex();
    }

    public RpgWorld template(String name, ModuleData... modules) {
        getThingFactory().addTemplate(new Plain(name, List.of(modules)));
        return this;
    }

    public GameObject put(String template, int side, float x, float y) {
        return spawn(findTemplate(template), new Coord3D(x, y, 0f), side);
    }

    public void run(int frames) {
        for (int frame = 0; frame < frames; frame++) {
            update();
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
