package uz.dukeengine.rpg.skill;

import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.rpg.Seal;

/**
 * A module that does what a skill of {@link SkillEffect#MODULE} does: an effect brought by a library or a game rather
 * than written into {@link SkillBook}. The book asks the first of its caster's modules that is one, after everything a
 * cast is refused for before it goes off — a cooldown, mana, a stun, a zone — and spends the cast only when it says yes.
 */
public interface SkillCaster {

    /**
     * Cast {@code skill} toward {@code towards} — null where the caster's facing is meant — refusing, as a cast aimed
     * at nothing it could reach is, over a {@code seal}. Called on the simulation thread.
     *
     * @return whether it went off; false, and nothing is spent
     */
    boolean cast(Skill skill, Coord3D towards, Seal seal);
}
