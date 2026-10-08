package uz.dukeengine.client3d.rpg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.MenuStyle;
import uz.dukeengine.combat.CombatModules;
import uz.dukeengine.core.GameConstants;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;
import uz.dukeengine.rpg.Seal;
import uz.dukeengine.rpg.skill.Skill;
import uz.dukeengine.rpg.skill.SkillBook;
import uz.dukeengine.rpg.skill.SkillEffect;

/** What is on a creature: a haste while it burns and each aura that reaches it, said in the game's words. */
class BuffScreenTest {

    private static final BuffScreen.Words WORDS = new BuffScreen.Words("Zarba", "Mana/s", "Qon", "Hujum tezligi",
            "s");

    private record Plain(String name, List<ModuleData> modules) implements ThingTemplate {
    }

    private static Skill skill(SkillEffect kind, int boost, String name) {
        return new Skill("Mage", 'Q', kind, 0f, 0f, 50f, 100f, 0f, 0f, boost, 0, 90, 0, 0, 90, 0, 4, 0, 0, 0, 0, "",
                "", "", "", 0f, name, "", 0f, 0f, 0, Map.of(), 0, 0, 0, 0);
    }

    private static final Skill HASTE = skill(SkillEffect.HASTE, 75, "Shoshilish");
    private static final Skill MIGHT = skill(SkillEffect.DAMAGE_AURA, 50, "Kuch");

    private final BuffScreen screen = new BuffScreen(List.of(HASTE, MIGHT), List.of(), MenuStyle.DEFAULTS,
            PanelLook.DEFAULTS, 24f, WORDS);

    private static GameObject creature() {
        var world = new GameLogic(new ThingFactory(CombatModules.withDefaults().register(SkillBook.Data.class,
                (owner, data) -> new SkillBook(owner, List.of(), new SkillBook.Rules(5f, 200f, 45f, 4, 10, 15,
                        List.of(), 30), Seal.NONE)))) {
            @Override
            protected void simulate() {
            }
        };
        world.init();
        world.getThingFactory().addTemplate(new Plain("Skeleton", List.of(new ActiveBody.Data(100f),
                new SkillBook.Data())));
        return world.spawn(world.findTemplate("Skeleton"), new Coord3D(0f, 0f, 0f), 0);
    }

    @Test
    void aHasteIsOnWhileItBurnsWithItsSecondsLeftAndSaidInTheGamesWords() {
        var skeleton = creature();
        skeleton.findModule(SkillBook.class).give(SkillEffect.HASTE, 9, 75, 2 * GameConstants.LOGICFRAMES_PER_SECOND);

        var on = screen.on(skeleton, 0);

        assertEquals(List.of(new BuffScreen.Buff(HASTE, 75, 2 * GameConstants.LOGICFRAMES_PER_SECOND)), on);
        assertEquals("+75% Hujum tezligi · 2s", screen.addsOf(on.getFirst()));
        assertTrue(screen.on(skeleton, 3 * GameConstants.LOGICFRAMES_PER_SECOND).isEmpty(), "burnt out");
    }

    @Test
    void anAurasFigureIsSaidByItsKind() {
        assertEquals("+50% Zarba", screen.addsOf(new BuffScreen.Buff(MIGHT, 50, 0)));
        assertEquals("+5 Mana/s", screen.addsOf(new BuffScreen.Buff(skill(SkillEffect.MANA_AURA, 0, "M"), 50, 0)));
    }
}
