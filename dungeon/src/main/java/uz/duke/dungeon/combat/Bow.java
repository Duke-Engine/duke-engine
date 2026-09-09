package uz.duke.dungeon.combat;

import uz.duke.core.module.DamageType;
import uz.duke.core.module.Module;
import uz.duke.core.thing.GameObject;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.rts.module.ProjectileLauncher;

/**
 * Turns the hero's shots into arrows that have to get there.
 *
 * <p>His weapon still decides everything else — what he is shooting at, how often,
 * how hard — and hands over only the landing. What arrives is an object in the
 * world with a distance to cross, so the damage happens where and when the arrow
 * does.
 *
 * <p>That is the whole difference between an archer and a man who points: at
 * ninety-five units the shot is in the air for about a third of a second, which
 * is long enough to see, long enough to walk out from under, and long enough that
 * a monster can die from an arrow loosed before it started moving.
 */
public final class Bow extends Module implements ProjectileLauncher {

    private final DungeonSettings settings;

    public Bow(GameObject owner, DungeonSettings settings) {
        super(owner);
        this.settings = settings;
    }

    /**
     * The empty block that puts this on a creature. Everything it needs is in
     * {@code dungeon.ini}, so the creature file says only that he shoots arrows.
     */
    public static uz.duke.core.module.ModuleData parseData(uz.duke.core.ini.Ini ini) {
        ini.initFromIni(new Object(), NO_FIELDS);
        return null;
    }

    private static final uz.duke.core.ini.FieldParseTable<Object> NO_FIELDS =
            new uz.duke.core.ini.FieldParseTable<>();

    /**
     * Where the arrow appears: out at the bow rather than in the middle of the
     * archer.
     *
     * <p>Starting it at his position puts it inside him, and what the player sees
     * is a shaft squeezing out of his chest and setting off. The bow is held out
     * in front, and in front is where he is facing — he turns to shoot, so his
     * heading is the line of the shot.
     *
     * <p>Never further out than the target is, so a shot at something almost
     * touching him does not begin behind it and have to turn round.
     */
    private uz.duke.core.math.Coord3D atTheBow(GameObject shooter, GameObject victim) {
        var from = shooter.getPosition();
        float reach = Math.min(settings.arrowMuzzleOffset(),
                from.distance(victim.getPosition()) * 0.5f);
        float facing = shooter.getOrientation();
        return new uz.duke.core.math.Coord3D(
                from.x() + (float) StrictMath.cos(facing) * reach,
                from.y() + (float) StrictMath.sin(facing) * reach,
                from.z());
    }

    @Override
    public boolean launch(GameObject shooter, GameObject victim, float damage, DamageType type) {
        var world = shooter.getWorld();
        if (world == null) {
            return false;
        }
        var template = world.findTemplate(settings.arrowTemplate());
        if (template == null) {
            // Declining rather than swallowing the shot: a hero whose arrow is
            // missing from the data files should still be able to fight, and the
            // weapon lands it the old way.
            return false;
        }
        var arrow = world.spawn(template, atTheBow(shooter, victim), shooter.getPlayerIndex());
        var flight = arrow.findModule(ArrowUpdate.class);
        if (flight == null) {
            arrow.markDestroyed();
            return false; // the template exists but is not an arrow
        }
        flight.loose(shooter, victim, damage, type, settings.arrowSpeed());
        return true;
    }
}
