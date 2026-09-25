package uz.dukeengine.core.thing;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.BodyModule;
import uz.dukeengine.core.module.Locomotor;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.UpdateModule;

/**
 * A live instance of a {@link ThingTemplate} in the simulation, ported from
 * SAGE's {@code Object}.
 *
 * <p>Named {@code GameObject} to avoid clashing with {@link java.lang.Object}.
 * It carries identity ({@link ObjectId}), a world transform (position and
 * facing), ownership (player index), and the set of {@link Module modules} that
 * give it behaviour. The object itself is intentionally thin — almost all
 * behaviour lives in modules.
 *
 * <p>Modules are attached once at creation (by {@link ThingFactory}); the update
 * modules are cached so per-frame ticking does not re-scan the module list.
 */
public final class GameObject {

    public static final int NEUTRAL_PLAYER = 0;

    private final ObjectId id;
    private final ThingTemplate template;

    private final List<Module> modules = new ArrayList<>();
    private final List<UpdateModule> updateModules = new ArrayList<>();
    private BodyModule body;
    private boolean mobile;
    /** Bumped whenever the module list changes shape, so a tick can notice. */
    private int moduleRevision;

    private Coord3D position = Coord3D.ZERO;
    private float orientation; // facing angle in radians, 0 = +x
    /** Nose up, in radians: for how it is drawn, and read by nothing in the simulation. */
    private float pitch;
    /** Banked to its right, in radians: the same. */
    private float roll;
    /** Whether it is drawn at its own height even under the ground; see {@link #setKeepsOwnHeight}. */
    private boolean keepsOwnHeight;
    private int playerIndex = NEUTRAL_PLAYER;
    private boolean destroyed;
    /** Whether its modules have been told it is made — see {@link #announceCreated}. */
    private boolean announced;
    /** How far it sees where set for it alone, see {@link #setVisionRange}; negative for its template's. */
    private float visionRange = -1f;
    /** The first frame an enemy may target it — see {@link #setTargetableFrom}. */
    private int targetableFrom;
    private boolean contained;
    private World world;
    private final EnumSet<ObjectStatus> statuses = EnumSet.noneOf(ObjectStatus.class);
    private final java.util.TreeSet<String> conditions = new java.util.TreeSet<>();

    public GameObject(ObjectId id, ThingTemplate template) {
        this.id = id;
        this.template = template;
    }

    /** The simulation this object lives in. Set when the object is created. */
    public World getWorld() {
        return world;
    }

    public void setWorld(World world) {
        this.world = world;
    }

    /** Attach a module. Called during construction; order is preserved. */
    public void addModule(Module module) {
        moduleRevision++;
        modules.add(module);
        if (module instanceof UpdateModule u) {
            updateModules.add(u);
        }
        if (module instanceof Locomotor) {
            mobile = true;
        }
        if (module instanceof BodyModule b) {
            if (body != null) {
                throw new IllegalStateException(
                        "object '" + template.name() + "' has more than one body module");
            }
            body = b;
        }
    }

    /**
     * Detach a module, undoing everything {@link #addModule} derived from it.
     *
     * <p>Composition is only half a mechanism if things can be added and never
     * taken away: a unit that changes what it is — losing a weapon, being
     * upgraded into something else — has to be able to stop carrying the module
     * that made it the old thing.
     *
     * <p>Order is preserved for everything that stays, so the frame's update
     * sequence is unchanged apart from the gap. Structurally changing the module
     * list from <em>inside</em> a module's own {@code update()} is not supported
     * and fails loudly rather than silently skipping a neighbour, which would be
     * a determinism bug that only showed up as a desync much later.
     *
     * @return whether the module was attached in the first place
     */
    public boolean removeModule(Module module) {
        if (!modules.remove(module)) {
            return false;
        }
        moduleRevision++;
        if (module instanceof UpdateModule u) {
            updateModules.remove(u);
        }
        if (module == body) {
            body = null;
        }
        if (module instanceof Locomotor) {
            // Another locomotor may still be attached, so this is recomputed
            // rather than simply cleared.
            mobile = modules.stream().anyMatch(Locomotor.class::isInstance);
        }
        module.onRemoved();
        return true;
    }

    /**
     * Swap one module for another, with the replacement taking the old one's
     * place in the update order rather than going to the back.
     *
     * <p>Position matters: modules run in attachment order, and a unit whose
     * weapon was replaced mid-game should still fire at the same point in the
     * frame as before. Appending instead would quietly reorder the simulation.
     */
    public void replaceModule(Module oldModule, Module newModule) {
        int at = modules.indexOf(oldModule);
        if (at < 0) {
            throw new IllegalArgumentException("module is not attached to this object");
        }
        removeModule(oldModule);
        modules.add(at, newModule);
        if (newModule instanceof UpdateModule u) {
            // Sit in the same relative place among the update modules, too.
            int updateAt = 0;
            for (var module : modules) {
                if (module == newModule) {
                    break;
                }
                if (module instanceof UpdateModule) {
                    updateAt++;
                }
            }
            updateModules.add(updateAt, u);
        }
        if (newModule instanceof Locomotor) {
            mobile = true;
        }
        if (newModule instanceof BodyModule b) {
            if (body != null) {
                throw new IllegalStateException(
                        "object '" + template.name() + "' has more than one body module");
            }
            body = b;
        }
    }

    /**
     * Tick every update module once, in attachment order.
     *
     * <p>Refuses a module that restructures the list while the list is being
     * walked. This is checked explicitly rather than left to the iterator, which
     * does not reliably catch it: removing the last element leaves the cursor at
     * the new size, so the loop simply ends and whatever was in the gap never
     * ticks. A silently skipped module is a divergence between two machines that
     * would surface as a desync, far from its cause.
     */
    public void updateModules() {
        announceCreated();
        int revision = moduleRevision;
        // One guard here rather than one in every module: a thing still being built does none of what it is
        // for, and every module a building might have would otherwise have to remember to ask.
        boolean building = statuses.contains(ObjectStatus.UNDER_CONSTRUCTION);
        for (int i = 0; i < updateModules.size(); i++) {
            var module = updateModules.get(i);
            if (building && !module.runsWhileUnderConstruction()) {
                continue;
            }
            module.update();
            if (moduleRevision != revision) {
                throw new java.util.ConcurrentModificationException(
                        "object '" + template.name() + "' changed its modules while updating");
            }
        }
    }

    public ObjectId getId() {
        return id;
    }

    public ThingTemplate getTemplate() {
        return template;
    }

    public boolean isKindOf(Kind kind) {
        return Classified.of(template).contains(kind);
    }

    /** Its shape: its template's if that is solid, a point that collides with nothing if not. */
    public Geometry getGeometry() {
        return Solid.of(template);
    }

    /** How far it sees: the range set for it, or its template's if that has eyes, nothing if not. */
    public float getVisionRange() {
        return visionRange >= 0f ? visionRange : Sighted.of(template);
    }

    /**
     * See {@code range} from now on, whatever its template says — a hijacked vehicle seeing as its driver, a power's
     * view growing and shrinking, a plan's longer sight — or its template's again for a negative range. Read
     * wherever sight is; in the checksum and a save while set.
     */
    public void setVisionRange(float range) {
        this.visionRange = range;
    }

    /** The range set for it alone, or negative where it sees as its template does. */
    public float getOwnVisionRange() {
        return visionRange;
    }

    /**
     * Keep it out of every enemy's targeting until {@code frame} — not acquired, not ordered at, not caught by a
     * blast; from that frame a target as any — the reference's ejected pilot, nobody's enemy for two seconds. In the
     * checksum and a save.
     */
    public void setTargetableFrom(int frame) {
        this.targetableFrom = frame;
    }

    /** The first frame an enemy may target it; 0 for from the start. */
    public int getTargetableFrom() {
        return targetableFrom;
    }

    /**
     * Whether it is hidden from {@code player}: {@link ObjectStatus#HIDDEN} from everyone, or kept from that player
     * by a {@link uz.dukeengine.core.module.Concealment} of its — never from its own side.
     */
    public boolean isHiddenFrom(int player) {
        if (statuses.contains(ObjectStatus.HIDDEN)) {
            return true;
        }
        if (player == playerIndex) {
            return false;
        }
        for (var module : modules) {
            if (module instanceof uz.dukeengine.core.module.Concealment concealed && concealed.hiddenFrom(player)) {
                return true;
            }
        }
        return false;
    }

    /** True while this object is inside a transport/structure (hidden, idle). */
    public boolean isContained() {
        return contained;
    }

    public void setContained(boolean contained) {
        this.contained = contained;
    }

    public boolean hasStatus(ObjectStatus status) {
        return statuses.contains(status);
    }

    public void setStatus(ObjectStatus status) {
        statuses.add(status);
    }

    /** Every status it carries, a bit each in the order they are declared: what the world's checksum mixes in. */
    public int statusBits() {
        int bits = 0;
        for (var status : statuses) {
            bits |= 1 << status.ordinal();
        }
        return bits;
    }

    public void clearStatus(ObjectStatus status) {
        statuses.remove(status);
    }

    /**
     * Say that a word holds for this thing — an upgrade it has finished, a crate it picked up, a rank. A
     * condition is only a word: the engine defines none of them and knows what none of them mean, and what
     * reads them is a game's data, such as a weapon set that names them. It is simulation state, so it is set
     * from the simulation, and alike on every machine.
     */
    public void setCondition(String word) {
        if (word != null && !word.isBlank()) {
            conditions.add(word);
        }
    }

    public void clearCondition(String word) {
        if (word != null) {
            conditions.remove(word);
        }
    }

    public boolean hasCondition(String word) {
        return conditions.contains(word);
    }

    /** Every word that holds for it, sorted, so it reads the same way on every machine. */
    public java.util.SortedSet<String> getConditions() {
        return java.util.Collections.unmodifiableSortedSet(conditions);
    }

    public List<Module> getModules() {
        return List.copyOf(modules);
    }

    /**
     * What moves it — its {@link uz.dukeengine.core.module.Locomotor}, walking or flying — or {@code null} for a thing
     * that cannot move itself. What anything giving orders asks, so the two are ordered alike.
     */
    public uz.dukeengine.core.module.Locomotor getLocomotor() {
        for (var module : modules) {
            if (module instanceof uz.dukeengine.core.module.Locomotor locomotor) {
                return locomotor;
            }
        }
        return null;
    }

    /** The first attached module of the given type, or {@code null} if none. */
    public <M extends Module> M findModule(Class<M> type) {
        for (var module : modules) {
            if (type.isInstance(module)) {
                return type.cast(module);
            }
        }
        return null;
    }

    /**
     * True when some module can move this object — i.e. it carries a
     * {@link Locomotor}.
     *
     * <p>What cannot move is effectively terrain, and the simulation treats it
     * that way: an immobile object with a shape is baked into the navigation grid
     * so paths route around it instead of into it.
     */
    public boolean isMobile() {
        return mobile;
    }

    /** The body module, or {@code null} if this object has none. */
    public BodyModule getBody() {
        return body;
    }

    /** True once health has reached zero. Objects with no body never die this way. */
    public boolean isEffectivelyDead() {
        return body != null && body.isDead();
    }

    public Coord3D getPosition() {
        return position;
    }

    public void setPosition(Coord3D position) {
        this.position = position;
    }

    public float getOrientation() {
        return orientation;
    }

    public void setOrientation(float orientation) {
        this.orientation = orientation;
    }

    public float getPitch() {
        return pitch;
    }

    /**
     * Tip its nose up by {@code pitch} radians — a climbing jet, a shell arcing — beside its facing. How it is drawn
     * only: the simulation reads none of it, and it is no part of the checksum.
     */
    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public float getRoll() {
        return roll;
    }

    /** Bank it to its right by {@code roll} radians — into a turn. How it is drawn only, as {@link #setPitch}. */
    public void setRoll(float roll) {
        this.roll = roll;
    }

    public boolean keepsOwnHeight() {
        return keepsOwnHeight;
    }

    /**
     * Draw it at its own height even where that is under the ground — a thing that dives or digs. Left alone it is
     * drawn at its height or on the ground, whichever is higher, so a ground unit whose height is stale is never
     * drawn under the map.
     */
    public void setKeepsOwnHeight(boolean keepsOwnHeight) {
        this.keepsOwnHeight = keepsOwnHeight;
    }

    public int getPlayerIndex() {
        return playerIndex;
    }

    public void setPlayerIndex(int playerIndex) {
        this.playerIndex = playerIndex;
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    /**
     * Tell its modules it is made ({@link uz.dukeengine.core.module.Module#onCreated}), once: before its first
     * update, or at the end of the frame it was made in, whichever comes first — after whatever made it has set it.
     */
    public void announceCreated() {
        if (announced) {
            return;
        }
        announced = true;
        for (var module : List.copyOf(modules)) {
            module.onCreated();
        }
    }

    /** A thing brought back from a save: it was made long ago, and its modules are not told again. */
    public void restored() {
        announced = true;
    }

    /** Flag this object for removal. Prefer {@code GameLogic.destroyObject}. */
    public void markDestroyed() {
        this.destroyed = true;
    }
}
