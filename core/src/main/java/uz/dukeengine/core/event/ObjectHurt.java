package uz.dukeengine.core.event;

import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.thing.ObjectId;

/**
 * A blow landed and took health: what was struck, by what kind of damage, how hard, by whom, and where on it.
 *
 * <p>A snapshot shows only that health dropped — the client's flinch — and says nothing a game could choose a
 * look by. The RTS this was measured in names what its things show when struck on 1388 armour sets, eleven
 * tables of them, each choosing by the blow's damage type and whether it was a heavy one: sparks off a tank for
 * small arms, a heavier strike for a shell, dust off a building, nothing off a man. This says everything that
 * choice needs, and the client makes it ({@code hurt.<template>.<type>.<major|minor>}).
 *
 * <p>Posted for a blow of any source that takes health — a hit, a carried shot landing, a blast, a special power,
 * being run over; a blow that takes none, healing among them, posts nothing.
 *
 * @param amount   what the blow was worth after the victim's armour — the reference's {@code actualDamageDealt},
 *                 not held down to the health it had left
 * @param attacker what dealt it, or {@code null} for no one
 * @param where    where on the victim it landed: its middle for a direct hit, the point of it nearest a blast
 * @param from     where the blow came from — the attacker, or the blast — or {@code null}, so what is drawn can
 *                 face the way it came
 */
public record ObjectHurt(int frame, ObjectId object, String templateName, int playerIndex, DamageType damageType,
        float amount, ObjectId attacker, Coord3D where, Coord3D from) implements WorldEvent {

    @Override
    public Coord3D where() {
        return where;
    }
}
