package uz.dukeengine.combat.module;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import uz.dukeengine.core.GameConstants;
import uz.dukeengine.core.module.Death;
import uz.dukeengine.core.module.DieModule;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.ModuleGroups;
import uz.dukeengine.core.module.UpdateModule;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Kind;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.ObjectStatus;

/**
 * What stands near its thing holds its words, and is given back health, for as long as it stays: SAGE's
 * {@code PropagandaTowerBehavior} — the tower's Enthusiastic and its heal — as a mechanism, which a leader's bonus or a
 * fountain is as well. The words are what a game's data reads: a {@code WeaponBonus} line, an armour set, a weapon set.
 *
 * <p>Who is within its reach is looked at every {@code PulseFrames} (the reference's {@code DelayBetweenUpdates}), on
 * its thing's first update and then on its own clock, in the order the world keeps its things; its {@code PulseEffect}
 * is played riding its thing then, and its thing's {@link AuraListener}s are told. Every frame, each thing found holds
 * the words and is healed its share of its most health a second, taken from one aura at a time
 * ({@link uz.dukeengine.core.module.BodyModule#healFromOne}) as the reference's towers do not heal twice. A thing no
 * longer found gives the words back, but for a word another aura still holds it in — so where two leaders' reaches
 * meet, leaving one does not take away what the other gives.
 *
 * <p>It holds nothing while its thing is dead, disabled, sold or not there to be seen, nor while it is inside another
 * that it does not fire out of — the reference's tower works on an Overlord's back and not inside a Helix — nor while
 * it is being built; what it gave is taken back then, when its thing dies, and when it is taken off its thing, as a
 * skill that gave it is given up.
 */
@ModuleGroup(ModuleGroups.EFFECT)
public final class AuraUpdate extends UpdateModule implements DieModule {

    /**
     * @param radius             how far from its thing's middle it reaches, measured along the ground — the reference's
     *                           {@code Radius}, from centre in two dimensions
     * @param pulseFrames        how often it looks who is within that reach, in logic frames
     * @param words              what each thing within it holds while it is there
     * @param healShareEachSecond the share of each one's most health given back every second, a little each frame —
     *                           the reference's {@code HealPercentEachSecond}: 0.01 for its 1%
     * @param affects            whom it reaches ({@link Weapon.Affects}), as a blast's words say it; none named is its
     *                           thing's side and its allies, and its thing itself only where {@code SELF} is named — the
     *                           reference's {@code AffectsSelf}
     * @param kinds              the kinds it reaches, any of them; none named is every kind
     * @param exceptKinds        kinds it never reaches — the reference's tower passes buildings by
     * @param pulseEffect        what the world plays riding its thing each time it looks, anything it plays by name —
     *                           the reference's {@code PulseFX}; null for nothing
     */
    public record Data(float radius, int pulseFrames, List<String> words, float healShareEachSecond,
            List<Weapon.Affects> affects, List<Kind> kinds, List<Kind> exceptKinds, String pulseEffect)
            implements ModuleData {

        /** What a block leaves out. */
        static final Data DEFAULTS = new Data(0f, 1, List.of(), 0f, List.of(), List.of(), List.of(), null);

        public Data {
            words = words == null ? List.of() : List.copyOf(words);
            affects = affects == null ? List.of() : List.copyOf(affects);
            kinds = kinds == null ? List.of() : List.copyOf(kinds);
            exceptKinds = exceptKinds == null ? List.of() : List.copyOf(exceptKinds);
        }

        /** Words for its allies within {@code radius}, looked at every {@code pulseFrames}. */
        public Data(float radius, int pulseFrames, List<String> words) {
            this(radius, pulseFrames, words, 0f, List.of(), List.of(), List.of(), null);
        }
    }

    private final Data data;
    private final int pulseFrames;
    /** Who it found at its last look, in the order the world keeps its things. */
    private final Set<ObjectId> inside = new LinkedHashSet<>();
    private int nextLook;

    public AuraUpdate(GameObject owner, Data data) {
        super(owner);
        this.data = data;
        this.pulseFrames = Math.max(1, data.pulseFrames());
    }

    @Override
    public void update() {
        var owner = getOwner();
        var world = owner.getWorld();
        if (world == null || !working(owner)) {
            letGo();
            return;
        }
        if (world.getFrame() >= nextLook) {
            nextLook = world.getFrame() + pulseFrames;
            look(owner);
        }
        give(owner);
    }

    /** Who it found within its reach at its last look, in the order the world keeps its things. */
    public List<ObjectId> getInside() {
        return List.copyOf(inside);
    }

    /** Whether it holds {@code thing} in {@code word}: found at its last look, and a word it gives. */
    public boolean holds(ObjectId thing, String word) {
        return inside.contains(thing) && data.words().contains(word);
    }

    public Data getData() {
        return data;
    }

    @Override
    public void onDie(Death death) {
        letGo();
    }

    @Override
    public void onRemoved() {
        letGo();
    }

    private static boolean working(GameObject owner) {
        if (owner.isEffectivelyDead() || owner.hasStatus(ObjectStatus.DISABLED) || owner.hasStatus(ObjectStatus.SOLD)
                || owner.hasStatus(ObjectStatus.HIDDEN)) {
            return false;
        }
        return !owner.isContained() || WeaponUpdate.firesFromInside(owner);
    }

    private void look(GameObject owner) {
        var world = owner.getWorld();
        var found = new LinkedHashSet<ObjectId>();
        var things = new ArrayList<GameObject>();
        for (var candidate : world.thingsNear(owner.getPosition(), Math.abs(data.radius()))) {
            if (reaches(owner, candidate)) {
                found.add(candidate.getId());
                things.add(candidate);
            }
        }
        var left = new ArrayList<ObjectId>();
        for (var id : inside) {
            if (!found.contains(id)) {
                left.add(id);
            }
        }
        inside.clear();
        inside.addAll(found);
        takeBack(left);
        if (data.pulseEffect() != null && !data.pulseEffect().isBlank()) {
            world.effect(data.pulseEffect(), owner);
        }
        for (var module : owner.getModules()) {
            if (module instanceof AuraListener listener) {
                listener.onPulse(this, List.copyOf(things));
            }
        }
    }

    private boolean reaches(GameObject owner, GameObject candidate) {
        var affects = data.affects();
        if (candidate == owner) {
            return affects.contains(Weapon.Affects.SELF) && ofItsKinds(candidate);
        }
        if (candidate.isEffectivelyDead() || candidate.isContained() || candidate.hasStatus(ObjectStatus.HIDDEN)
                || !ofItsKinds(candidate) || !within(owner, candidate)) {
            return false;
        }
        var world = owner.getWorld();
        var relationship = candidate.getPlayerIndex() == owner.getPlayerIndex() ? Relationship.ALLIES
                : world.getRelationship(owner.getPlayerIndex(), candidate.getPlayerIndex());
        if (affects.contains(Weapon.Affects.NOT_SIMILAR) && relationship == Relationship.ALLIES
                && candidate.getTemplate().name().equals(owner.getTemplate().name())) {
            return false;
        }
        if (affects.contains(Weapon.Affects.NOT_AIRBORNE) && candidate.hasStatus(ObjectStatus.AIRBORNE)) {
            return false;
        }
        var sides = affects.stream().filter(a -> a == Weapon.Affects.ALLIES || a == Weapon.Affects.ENEMIES
                || a == Weapon.Affects.NEUTRALS).toList();
        if (sides.isEmpty()) {
            return relationship == Relationship.ALLIES;
        }
        return sides.contains(switch (relationship) {
            case ALLIES -> Weapon.Affects.ALLIES;
            case ENEMIES -> Weapon.Affects.ENEMIES;
            case NEUTRAL -> Weapon.Affects.NEUTRALS;
        });
    }

    private boolean ofItsKinds(GameObject candidate) {
        for (var kind : data.exceptKinds()) {
            if (candidate.isKindOf(kind)) {
                return false;
            }
        }
        if (data.kinds().isEmpty()) {
            return true;
        }
        for (var kind : data.kinds()) {
            if (candidate.isKindOf(kind)) {
                return true;
            }
        }
        return false;
    }

    private boolean within(GameObject owner, GameObject candidate) {
        var here = owner.getPosition();
        var there = candidate.getPosition();
        float dx = there.x() - here.x();
        float dy = there.y() - here.y();
        return dx * dx + dy * dy <= data.radius() * data.radius();
    }

    /** The words held and the heal given, every frame, to each it found that is still in the world. */
    private void give(GameObject owner) {
        var world = owner.getWorld();
        for (var it = inside.iterator(); it.hasNext();) {
            var thing = world.findObject(it.next());
            if (thing == null) {
                it.remove(); // gone from the world, and its words with it
                continue;
            }
            data.words().forEach(thing::setCondition);
            var body = thing.getBody();
            if (data.healShareEachSecond() > 0f && body != null && !body.isDead()) {
                body.healFromOne(body.getMaxHealth() * data.healShareEachSecond()
                        * GameConstants.SECONDS_PER_LOGICFRAME, owner.getId(), pulseFrames);
            }
        }
    }

    /** Everything it gave, taken back from everyone it holds. */
    private void letGo() {
        if (inside.isEmpty()) {
            return;
        }
        var left = new ArrayList<>(inside);
        inside.clear();
        takeBack(left);
    }

    /** Its words taken back from {@code left}, each but where another aura in the world still holds it in the word. */
    private void takeBack(List<ObjectId> left) {
        var world = getOwner().getWorld();
        if (left.isEmpty() || data.words().isEmpty() || world == null) {
            return;
        }
        var others = new ArrayList<AuraUpdate>();
        for (var thing : world.getObjects()) {
            for (var module : thing.getModules()) {
                if (module instanceof AuraUpdate aura && aura != this) {
                    others.add(aura);
                }
            }
        }
        for (var id : left) {
            var thing = world.findObject(id);
            if (thing == null) {
                continue;
            }
            for (var word : data.words()) {
                if (others.stream().noneMatch(aura -> aura.holds(id, word))) {
                    thing.clearCondition(word);
                }
            }
        }
    }
}
