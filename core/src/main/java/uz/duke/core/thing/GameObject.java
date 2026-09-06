package uz.duke.core.thing;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import uz.duke.core.math.Coord3D;
import uz.duke.core.module.BodyModule;
import uz.duke.core.module.Module;
import uz.duke.core.module.UpdateModule;

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
        modules.add(module);
        if (module instanceof UpdateModule u) {
            updateModules.add(u);
        }
        if (module instanceof BodyModule b) {
            if (body != null) {
                throw new IllegalStateException(
                        "object '" + template.getName() + "' has more than one body module");
            }
            body = b;
        }
    }

    /** Tick every update module once, in attachment order. */
    public void updateModules() {
        for (var update : updateModules) {
            update.update();
        }
    }

    public ObjectId getId() {
        return id;
    }

    public ThingTemplate getTemplate() {
        return template;
    }

    public boolean isKindOf(KindOf kind) {
        return template.isKindOf(kind);
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
