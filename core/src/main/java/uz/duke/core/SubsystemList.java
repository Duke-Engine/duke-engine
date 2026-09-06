package uz.duke.core;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Owns and drives every {@link SubsystemInterface}, ported from SAGE's
 * {@code SubsystemInterfaceList}.
 *
 * <p>Registration order is the lifecycle order: {@link #postProcessLoadAll()},
 * {@link #resetAll()} and {@link #shutdownAll()} all walk the list so a single
 * call services the whole engine. Subsystems are inited individually via
 * {@link #initSubsystem} as they are added, mirroring the original engine where
 * each subsystem is constructed, inited and registered together.
 */
public final class SubsystemList {

    private static final Logger LOG = Logger.getLogger(SubsystemList.class.getName());

    private final List<SubsystemInterface> subsystems = new ArrayList<>();

    /**
     * Init a subsystem and register it. The original engine also loads INI
     * data here from the given paths; that hook is reserved for when the INI
     * loader lands.
     */
    public void initSubsystem(SubsystemInterface sys, String name) {
        sys.setName(name);
        sys.init();
        addSubsystem(sys);
    }

    public void addSubsystem(SubsystemInterface sys) {
        subsystems.add(sys);
    }

    public void removeSubsystem(SubsystemInterface sys) {
        subsystems.remove(sys);
    }

    public void postProcessLoadAll() {
        for (var sys : subsystems) {
            sys.postProcessLoad();
        }
    }

    public void resetAll() {
        for (var sys : subsystems) {
            sys.reset();
        }
    }

    /** Shut down in reverse registration order, so dependents go before deps. */
    public void shutdownAll() {
        for (int i = subsystems.size() - 1; i >= 0; i--) {
            var sys = subsystems.get(i);
            try {
                sys.shutdown();
            } catch (RuntimeException e) {
                LOG.warning(() -> "Subsystem '" + sys.getName() + "' failed to shut down: " + e);
            }
        }
        subsystems.clear();
    }
}
