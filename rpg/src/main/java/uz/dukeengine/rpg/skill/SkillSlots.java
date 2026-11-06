package uz.dukeengine.rpg.skill;

import java.util.List;
import uz.dukeengine.core.save.UnsavedModuleException;

/** Which skill of a book a save means: where it stands in the book's list, which its template makes the same again. */
final class SkillSlots {

    private SkillSlots() {
    }

    /** The place of {@code skill} in {@code skills}, or -1 for none; refused for one the book does not list. */
    static int of(List<Skill> skills, Skill skill) {
        if (skill == null) {
            return -1;
        }
        int slot = skills.indexOf(skill);
        if (slot < 0) {
            throw new UnsavedModuleException(SkillBook.class.getName(), "a skill book holds a skill ("
                    + skill.key() + ") that it does not list");
        }
        return slot;
    }

    /** The skill at {@code slot}, or null for -1. */
    static Skill at(List<Skill> skills, int slot) {
        return slot < 0 ? null : skills.get(slot);
    }
}
