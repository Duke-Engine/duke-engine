package uz.dukeengine.core.event;

import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.DeathType;
import uz.dukeengine.core.thing.ObjectId;

/**
 * An object's health reached zero and it left the world.
 *
 * <p>Posted as the object is reaped, which is the last moment anything can be
 * known about it. That is why the event carries a copy of what a client needs —
 * what it was, whose it was, where it stood, how it died and who killed it —
 * rather than just an id: by the time this is read, {@code findObject(id)}
 * returns null.
 *
 * <p>Objects removed for other reasons (a script clearing the map, say) do not
 * produce this. It means "destroyed", so it is safe to hang an explosion on.
 *
 * <p>The simulation's own code hears the same record through {@code GameLogic.onDied}, beside this event rather than
 * from it: an event is for the client, and a count kept from events would lose what an undrained queue drops.
 *
 * @param deathType how it died, which a client draws and sounds by ({@code died.<template>.<type>})
 * @param killer    what dealt the killing blow, or {@code null} for a death by no one
 * @param orientation which way it faced, so what is drawn for its death turns with it
 * @param killerPlayerIndex whose side dealt the killing blow — known even when the killer is gone too — or -1
 */
public record ObjectDied(
        int frame,
        ObjectId object,
        String templateName,
        int playerIndex,
        Coord3D position,
        DeathType deathType,
        ObjectId killer,
        float orientation,
        int killerPlayerIndex) implements WorldEvent {

    public ObjectDied {
        deathType = deathType == null ? DeathType.NORMAL : deathType;
    }

    /** A death whose killer's side is not said: nobody's. */
    public ObjectDied(int frame, ObjectId object, String templateName, int playerIndex, Coord3D position,
            DeathType deathType, ObjectId killer, float orientation) {
        this(frame, object, templateName, playerIndex, position, deathType, killer, orientation, -1);
    }

    @Override
    public Coord3D where() {
        return position;
    }
}
