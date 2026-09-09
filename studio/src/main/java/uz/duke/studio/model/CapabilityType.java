package uz.duke.studio.model;

import java.util.List;

/**
 * The capabilities a unit can be given in the Studio — each maps to an engine
 * module, with a friendly name and a parameter schema the inspector renders as
 * a form. This is the "add behaviour by clicking" catalogue: pick a unit, add
 * "Movement", fill in speed — the Studio generates the engine INI.
 */
public enum CapabilityType {

    MOVE("Movement (walk/drive)", List.of(
            new Param("Speed", "Speed (units/sec)", "14"),
            new Param("TurnRate", "Turn rate (deg/sec)", "0"))),

    ATTACK("Attack (weapon)", List.of(
            new Param("Damage", "Damage per shot", "10"),
            new Param("AttackRange", "Range", "25"),
            new Param("ReloadFrames", "Reload (frames, 30=1s)", "15"),
            new Param("SplashRadius", "Splash radius (0=none)", "0"),
            new Param("DamageType", "Damage type (NORMAL/EXPLOSION/ARMOR_PIERCING/FLAME)", "NORMAL"))),

    PRODUCE("Produce units (factory)", List.of(
            new Param("Builds", "Units it can build (space-separated names)", ""))),

    CAPACITY_GATE("Stall production without spare capacity", List.of()),

    POWER("Power grid", List.of(
            new Param("Produces", "Power produced", "0"),
            new Param("Consumes", "Power consumed", "0"))),

    EXPERIENCE("Veterancy (gains ranks)", List.of(
            new Param("ExperienceValue", "Worth to killer (XP)", "30"),
            new Param("ExperienceRequired", "XP per rank (any number)", "60 180 360"),
            new Param("LevelDamageBonus", "Damage multiplier per rank", "1.1 1.2 1.3"),
            new Param("HealOnPromotion", "Full heal on promotion", "Yes"))),

    AUTO_HEAL("Self-healing", List.of(
            new Param("HealPerSecond", "Health regained per second", "2"))),

    SUPPLY("Resource pile (harvestable)", List.of(
            new Param("Amount", "Resources it holds", "2000"))),

    HARVEST("Harvest resources (worker)", List.of(
            new Param("LoadPerTrip", "Resources per trip", "50"),
            new Param("FramesPerTrip", "Frames per trip (30 = 1s)", "90")));

    /** One editable parameter of a capability. */
    public record Param(String key, String label, String defaultValue) {
    }

    private final String displayName;
    private final List<Param> params;

    CapabilityType(String displayName, List<Param> params) {
        this.displayName = displayName;
        this.params = params;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<Param> getParams() {
        return params;
    }
}
