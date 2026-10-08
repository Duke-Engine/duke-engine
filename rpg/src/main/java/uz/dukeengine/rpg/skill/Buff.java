package uz.dukeengine.rpg.skill;

/**
 * What one creature has lent another -- an aura's, or a haste it cast on it -- as the borrower's book holds it: a
 * {@code kind}, worth {@code value} -- a {@code BoostPercent}, or a {@code MANA_AURA}'s tenths of a point a second --
 * given by {@code source}, the lender's object id, and holding through frame {@code until}, the last it counts on.
 *
 * <p>Plain numbers and nothing else, so that it is the same on every machine: it is read by the frame it is asked on
 * and never by a clock, and is kept in the order it was given. A book holds one for each kind and lender -- see
 * {@link SkillBook#give}.
 */
public record Buff(SkillEffect kind, int source, int value, int until) {
}
