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
    private int playerIndex = NEUTRAL_PLAYER;
    private boolean destroyed;
    private boolean contained;
    private World world;
    private final EnumSet<ObjectStatus> statuses = EnumSet.noneOf(ObjectStatus.class);

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
        int revision = moduleRevision;
        for (int i = 0; i < updateModules.size(); i++) {
            updateModules.get(i).update();
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

    /** How far it sees: its template's range if that has eyes, nothing if not. */
    public float getVisionRange() {
        return Sighted.of(template);
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

    public void clearStatus(ObjectStatus status) {
        statuses.remove(status);
    }

    public List<Module> getModules() {
        return List.copyOf(modules);
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

    public int getPlayerIndex() {
        return playerIndex;
    }

    public void setPlayerIndex(int playerIndex) {
        this.playerIndex = playerIndex;
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    /** Flag this object for removal. Prefer {@code GameLogic.destroyObject}. */
    public void markDestroyed() {
        this.destroyed = true;
    }
}
