package uz.dukeengine.client3d.rpg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.combat.CombatModules;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;
import uz.dukeengine.rpg.Seal;
import uz.dukeengine.rpg.skill.Skill;
import uz.dukeengine.rpg.skill.SkillBook;
import uz.dukeengine.rpg.skill.SkillEffect;
import uz.dukeengine.rpg.skill.SkillRanks;

/** What the card writes, the panel reads: every field it has, read back as it was written. */
class HeroCardTest {

    private static final HeroCard.Words WORDS = new HeroCard.Words("-daraja", "USTA", "YOPIQ",
            new HeroCard.TipWords("Zarba", "Kutish", "Radius", "Masofa", "Kuch", "oshirish", "Ctrl+",
                    "To'la", "Ochko yo'q", "-daraja", " s", "Mana"));

    private record Plain(String name, List<ModuleData> modules) implements ThingTemplate {
    }

    private static Skill strike(char key) {
        return new Skill("Hero", key, SkillEffect.STRIKE, 40f, 10f, 0f, 120f, 0f, 0f, 0, 0, 0, 0, 0, 90, 0, 4, 0, 0,
                12, 0, "", "icons/skills/strike.png", "", "", 0f, "Zarba", "Bir urish", 0f, 0f, 0, Map.of(), 0, 0, 0,
                0);
    }

    @Test
    void everyFieldItWritesThePanelReadsBack() {
        var card = HeroCard.named("Erika").title("O'q ustasi").rank("7-daraja").health(128, 200)
                .experience(38, 100).mana(30, 60).refusedForMana(77).depth("III / IV", "CHUQURLIK")
                .headings("MAHORAT", "NARSALAR").note("Qilich")
                .stat("Zarba", 34, 30, "icons/stats/attack.png")
                .attribute("Kuch", 12, 10, "icons/stats/str.png", true)
                .attributeTip(0, "Kuch", "Asosiy", "Har ochko:", List.of(new HeroCard.Row("Jon", "+0.50", "")), "")
                .item("icons/items/sword.png", 3).ordersAreHis(true).order('A', "icons/cmd/attack.png", "Hujum", true)
                .points(2, "OCHKO").field("deep", "3").toString();

        var read = PanelReading.parse(card);

        assertNotNull(read, card);
        assertEquals("Erika", read.name());
        assertEquals("O'q ustasi", read.title());
        assertEquals("7-daraja", read.rank());
        assertEquals(128f, read.health());
        assertEquals(200f, read.maxHealth());
        assertEquals(38f, read.experience());
        assertEquals(30f, read.mana());
        assertEquals(77, read.refusedForManaAt());
        assertEquals("III / IV", read.depth());
        assertEquals("CHUQURLIK", read.depthWord());
        assertEquals("MAHORAT", read.skillsWord());
        assertEquals("NARSALAR", read.itemsWord());
        assertEquals("Qilich", read.note());
        assertEquals(List.of(new PanelReading.Stat("Zarba", "34", "+4", "icons/stats/attack.png")), read.stats());
        assertEquals(List.of(new PanelReading.Stat("Kuch", "12", "+2", "icons/stats/str.png", true)),
                read.attributes());
        assertEquals("Kuch", read.attributeTips().get(0).name());
        assertEquals(List.of(new PanelReading.ItemReading("icons/items/sword.png", 3)), read.items());
        assertTrue(read.ordersAreHis());
        assertEquals(List.of(new PanelReading.OrderReading('A', "icons/cmd/attack.png", "Hujum", true)),
                read.orders());
        assertEquals(2, read.points());
        assertEquals("OCHKO", read.pointsWord());
    }

    @Test
    void aBooksSkillsAreReadAsTheirSlotsPipsAndCards() {
        var skills = List.of(strike('Q'), strike('W'));
        var world = new GameLogic(new ThingFactory(CombatModules.withDefaults().register(SkillBook.Data.class,
                (owner, data) -> new SkillBook(owner, skills, new SkillBook.Rules(5f, 200f, 45f, 4, 10, 15,
                        List.of(), 30), Seal.NONE)))) {
            @Override
            protected void simulate() {
            }
        };
        world.init();
        world.getThingFactory().addTemplate(new Plain("Hero", List.of(new ActiveBody.Data(100f),
                new SkillBook.Data())));
        var hero = world.spawn(world.findTemplate("Hero"), new Coord3D(0f, 0f, 0f), 0);
        var book = hero.findModule(SkillBook.class);
        var ranks = new SkillRanks(1);
        ranks.startWith(skills);
        ranks.raise('Q', 1);

        var read = PanelReading.parse(HeroCard.named("Erika").skills(book, ranks, 1, WORDS).toString());

        assertNotNull(read);
        assertEquals(2, read.skills().size());
        assertEquals(PanelReading.State.READY, read.skills().get(0).state(), "a point in it: ready");
        assertEquals(PanelReading.State.LOCKED, read.skills().get(1).state(), "none: locked");
        assertEquals("Zarba", read.tips().get('Q').name());
        assertEquals(new PanelReading.CostReading('Q', 12, true), read.costs().get(0));
    }
}
