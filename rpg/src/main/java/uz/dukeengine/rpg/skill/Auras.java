package uz.dukeengine.rpg.skill;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.rpg.Seal;
import uz.dukeengine.rpg.hero.LevelBonus;

/** A bearer's auras: lent to its side on its pulse, and worn -- its own looks, and the circle for what is lent it. */
final class Auras {

    private final GameObject owner;
    private final Passives passives;
    private final SkillBook.Rules rules;
    private final Seal seal;

    Auras(GameObject owner, Passives passives, SkillBook.Rules rules, Seal seal) {
        this.owner = owner;
        this.passives = passives;
        this.rules = rules;
        this.seal = seal;
    }

    /**
     * Its auras are lent: once every Combat {@code AuraPulseFrames}, on the frames its object id falls on -- as its
     * looks are worn, so a roomful of bearers are not all lending on one frame -- each aura its level has opened is
     * given, at the skill's worth ({@code BoostPercent}, or a {@code MANA_AURA}'s {@code ManaRegen}), to everyone of
     * its side within the skill's {@code Radius} of it, middle to middle, through walls but never across the keep's
     * shut gate, living and carrying a book -- itself among them -- to hold for Combat's {@code BuffLifeFrames}, this
     * frame the first. No hero is given one: no bearer stands on a hero's side, a passive being refused on a hero.
     *
     * <p>Stunned, it lends still; fallen, it lends no more, and what it lent runs out by itself. The life outlasts the
     * pulse, so what it lends to one still in reach holds on until the next pulse gives it again, whichever of the two
     * books is updated first on that frame.
     *
     * <p>A lender holds one buff of a kind, so of two auras of one kind on one bearer they are lent weakest first, the
     * order in its file breaking a tie: the stronger replaces the weaker for everyone within its reach, and whoever
     * stands beyond it but within the weaker's keeps the weaker.
     */
    void lend() {
        var world = owner.getWorld();
        int pulse = rules.auraPulseFrames();
        if (world == null || owner.isEffectivelyDead()
                || world.getFrame() % pulse != Math.floorMod(owner.getId().value(), pulse)) {
            return;
        }
        var here = owner.getPosition();
        int until = world.getFrame() + rules.buffLifeFrames() - 1;
        for (var skill : weakestFirst()) {
            int worth = worthOf(skill);
            for (var one : world.objectsInRange(here, skill.radius(),
                    candidate -> candidate.getPlayerIndex() == owner.getPlayerIndex()
                            && !candidate.isEffectivelyDead() && !seal.parts(world, here, candidate.getPosition()))) {
                var book = one.findModule(SkillBook.class);
                if (book != null) {
                    book.give(skill.effect(), owner.getId().value(), worth, until);
                }
            }
        }
    }

    /** Its opened auras by kind in the order the kinds first appear, the weakest of a kind first. */
    private List<Skill> weakestFirst() {
        var opened = passives.all().stream()
                .filter(skill -> skill.effect().isAura() && LevelBonus.rankOf(owner, skill) > 0).toList();
        var ordered = new ArrayList<Skill>();
        for (var skill : opened) {
            if (ordered.stream().noneMatch(done -> done.effect() == skill.effect())) {
                opened.stream().filter(same -> same.effect() == skill.effect())
                        .sorted(Comparator.comparingInt(Auras::worthOf)).forEach(ordered::add);
            }
        }
        return ordered;
    }

    private static int worthOf(Skill skill) {
        return skill.effect() == SkillEffect.MANA_AURA ? skill.manaRegen() : skill.boostPercent();
    }

    /**
     * Its auras are worn: each its level has opened and that says what it looks like is played on it every
     * {@code TickFrames} -- on the frames its object id falls on, as a brain's re-plans are, so a roomful are not all
     * played on one frame -- for as long as it lives, stunned or not. The game makes each last two
     * ticks, so it rides its bearer while it stands and is gone within two of its fall. An event: out of the checksum.
     */
    void wear() {
        var world = owner.getWorld();
        if (world == null || owner.isEffectivelyDead()) {
            return;
        }
        for (var skill : passives.all()) {
            int beat = skill.tickFrames();
            if (skill.effect().isAura() && skill.hasLook() && beat > 0
                    && world.getFrame() % beat == Math.floorMod(owner.getId().value(), beat)
                    && LevelBonus.rankOf(owner, skill) > 0) {
                world.effect(skill.look(), owner);
            }
        }
    }

    /**
     * The circle under it is worn: every Combat {@code AuraMarkTickFrames} -- on the frames its object id falls on, as
     * {@link #wear} does -- it asks {@link SkillBook#aurasOn} of itself and, if it holds any aura lent to it, plays on
     * itself the look for how many kinds it holds: the first of the Combat block's {@code AuraMarkLooks} for one, the
     * second for two, and past the list's end its last. The game makes each last two beats, so it
     * rides the creature while an aura is lent to it and is gone within two of the last lapsing. A hero's book asks the
     * same and is answered none, as is a creature that has fallen. An event: out of the checksum.
     */
    void wearTheMark() {
        var world = owner.getWorld();
        var looks = rules.auraMarkLooks();
        int beat = rules.auraMarkTickFrames();
        if (world == null || looks.isEmpty() || beat <= 0
                || world.getFrame() % beat != Math.floorMod(owner.getId().value(), beat)) {
            return;
        }
        int kinds = SkillBook.aurasOn(owner).size();
        if (kinds > 0) {
            world.effect(looks.get(Math.min(kinds, looks.size()) - 1), owner);
        }
    }
}
