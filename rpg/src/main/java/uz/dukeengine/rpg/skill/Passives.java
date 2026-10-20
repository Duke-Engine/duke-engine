package uz.dukeengine.rpg.skill;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import uz.dukeengine.core.GameConstants;
import uz.dukeengine.core.module.BodyModule;
import uz.dukeengine.core.module.Death;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.rpg.item.LootBag;

/**
 * What a book bears rather than casts: its own passives at the ranks the game gave them, those lent by what its bearer
 * wears, and what they do -- a {@code DRAGON_BLOOD}'s scale and health, an {@code UNYIELDING}'s floor, a
 * {@code STAGGER}'s flinch, a {@code SOUL_DRINK}'s souls, a {@code LIFESTEAL}'s share.
 */
final class Passives {

    private final GameObject owner;
    private final List<Skill> skills;

    /**
     * Passives lent by what its bearer wears, by the id of the thing lending them, in the order they were put on —
     * see {@link #lendFrom}. Read wherever its own passives are: never cast, never in a slot.
     */
    private final Map<String, List<Skill>> lent = new LinkedHashMap<>();
    /** What it wears, and the bag's count of changes when it last looked: see {@link #lendFrom}. */
    private LootBag wornFrom;
    private int wornVersion = -1;
    /** Each skill's rank, by key, as the game says it — see {@link SkillBook#setRank}. */
    private final Map<Character, Integer> ranks = new HashMap<>();
    /** The souls a {@code SOUL_DRINK} it has drunk: every enemy its bearer killed while it had one. */
    private int souls;
    /** Whether a {@code DRAGON_BLOOD} of its holds its bearer's damage scale, and the health it owes him. */
    private boolean holdsTheScale;
    private float bloodOwed;
    /** Whether an {@code UNYIELDING} of its has held its bearer's floor, and whether he has been beaten to it. */
    private boolean holdsTheFloor;
    private boolean beaten;
    /** The first frame a {@code STAGGER} may stagger him again, and the frame its stun ends. */
    private int staggerFrom;
    private int staggerEnds;
    /** The flinch the stagger under way holds him at. */
    private String staggerClip;

    Passives(GameObject owner, List<Skill> skills) {
        this.owner = owner;
        this.skills = skills;
    }

    /** Its passives: its own, and those lent by what its bearer wears. */
    List<Skill> all() {
        lookAtWhatIsWorn();
        // Its own at a rank the game said is 0 are not held; a worn thing's are held while it is worn.
        var all = new ArrayList<Skill>(skills.size());
        for (var skill : skills) {
            if (rankOf(skill) > 0) {
                all.add(skill);
            }
        }
        lent.values().forEach(all::addAll);
        return all;
    }

    /** The first of its passives, its own or lent, of {@code effect}; null for none. */
    Skill first(SkillEffect effect) {
        for (var skill : all()) {
            if (skill.effect() == effect) {
                return skill;
            }
        }
        return null;
    }

    /** See {@link SkillBook#lendFrom}. */
    void lendFrom(LootBag bag) {
        this.wornFrom = bag;
        this.wornVersion = -1;
    }

    private void lookAtWhatIsWorn() {
        if (wornFrom == null || wornFrom.version() == wornVersion) {
            return;
        }
        wornVersion = wornFrom.version();
        lent.clear();
        for (var item : wornFrom.acting()) {
            // ponytail: only passives are lent; a worn thing that casts would want a slot and a key of its own.
            var passive = item.skills().stream().filter(skill -> skill.effect().isPassive()).toList();
            if (!passive.isEmpty()) {
                lent.merge(item.id(), passive, (was, more) -> Stream.concat(was.stream(), more.stream()).toList());
            }
        }
    }

    /** {@code rank} of the skill on {@code key}, raised by what he wears while he wears it -- a learnt one only. */
    int raised(char key, int rank) {
        return rank > 0 && wornFrom != null ? rank + wornFrom.skillRanks(key) : rank;
    }

    void setRank(char key, int rank) {
        ranks.put(Character.toUpperCase(key), rank);
    }

    /** Its rank: what the game said, 0 for not held; the first where it never said. */
    int rankOf(Skill skill) {
        return raised(skill.key(), Math.max(0, ranks.getOrDefault(Character.toUpperCase(skill.key()), 1)));
    }

    int souls() {
        return souls;
    }

    void setSouls(int souls) {
        this.souls = Math.max(0, souls);
    }

    void drinkASoul() {
        souls++;
    }

    /** See {@link SkillBook#soulPercent}. */
    int soulPercent() {
        var drink = first(SkillEffect.SOUL_DRINK);
        if (drink == null || drink.tickFrames() <= 0) {
            return 0;
        }
        long percent = (long) (souls / drink.tickFrames()) * drink.boostPercent();
        return (int) (drink.damage() > 0f ? Math.min(percent, (long) drink.damage()) : Math.min(percent,
                Integer.MAX_VALUE));
    }

    /** Its own {@code LIFESTEAL}s and those lent by what it wears, each at its rank, added. */
    int lifesteal() {
        int share = 0;
        for (var skill : all()) {
            if (skill.effect() == SkillEffect.LIFESTEAL) {
                share += skill.boostAt(rankOf(skill));
            }
        }
        return share;
    }

    /** A {@code DRAGON_BLOOD}'s health each second at its rank, and {@code meal} besides. */
    float healthPerSecond(float meal) {
        var blood = first(SkillEffect.DRAGON_BLOOD);
        return blood == null ? meal : blood.heal() * rankOf(blood) + meal;
    }

    /** A {@code DRAGON_BLOOD}'s armour held on his body's damage scale and its health given back, while he has one. */
    void runTheBlood() {
        var body = owner.getBody();
        if (body == null) {
            return;
        }
        var blood = first(SkillEffect.DRAGON_BLOOD);
        if (blood == null) {
            if (holdsTheScale) {
                body.setDamageScale(1f);
                holdsTheScale = false;
            }
            return;
        }
        // ponytail: it owns the damage scale while he has it; a game scaling the same body otherwise is overruled.
        holdsTheScale = true;
        int rank = rankOf(blood);
        body.setDamageScale(1f - Math.clamp(blood.boostAt(rank), 0, 100) / 100f);
        if (body.isDead() || blood.heal() <= 0f) {
            return;
        }
        // Whole points as they are owed, so a heal is told once a point rather than every frame.
        bloodOwed += blood.heal() * rank * GameConstants.SECONDS_PER_LOGICFRAME;
        if (bloodOwed >= 1f) {
            float whole = (float) Math.floor(bloodOwed);
            bloodOwed -= whole;
            body.heal(whole);
        }
    }

    /** An {@code UNYIELDING}'s floor kept at its share of his most health, which may grow; let go once it is gone. */
    void holdTheFloor() {
        var body = owner.getBody();
        if (body == null) {
            return;
        }
        var unyielding = first(SkillEffect.UNYIELDING);
        if (unyielding == null) {
            if (holdsTheFloor) {
                body.setLeastHealth(0f);
                holdsTheFloor = false;
            }
            return;
        }
        holdsTheFloor = true;
        body.setLeastHealth(body.getMaxHealth() * unyielding.healBelowPercent() / 100f);
        beaten |= atTheFloor(body);
    }

    private static boolean atTheFloor(BodyModule body) {
        // A blow cut to the floor lands on it within a float's rounding.
        return body.getHealth() <= body.getLeastHealth() + 1e-3f;
    }

    /** See {@link SkillBook#isBeaten}. */
    boolean isBeaten() {
        var body = owner.getBody();
        return beaten || holdsTheFloor && body != null && atTheFloor(body);
    }

    /** A blow on him: a {@code STAGGER} he bears stuns him and holds him at his flinch, if the blow is heavy enough. */
    void onBlow(float amount, Death blow, int frame) {
        if (staggerClip != null && frame < staggerEnds && !owner.isEffectivelyDead()) {
            // A blow ends whatever work he was at; a stagger under way is no work, and holds to its last frame.
            owner.busy(staggerClip, staggerEnds - frame);
        }
        var stagger = first(SkillEffect.STAGGER);
        if (stagger == null || frame < staggerFrom || owner.isEffectivelyDead() || stagger.stunFrames() <= 0
                || amount < stagger.damage() && (blow == null || !blow.critical())) {
            return;
        }
        Blows.daze(owner, stagger.stunFrames());
        owner.busy(stagger.castAnim(), stagger.stunFrames());
        staggerClip = stagger.castAnim();
        staggerEnds = frame + stagger.stunFrames();
        staggerFrom = staggerEnds + stagger.durationFrames();
    }
}
