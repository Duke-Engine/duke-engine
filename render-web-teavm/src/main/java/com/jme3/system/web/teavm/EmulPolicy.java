package com.jme3.system.web.teavm;

import java.util.Set;
import org.teavm.extension.spi.substitution.SimpleSubstitutionPolicy;
import org.teavm.extension.spi.substitution.SubstitutionSink;

/**
 * Stands the classes in `emul.<package>.T<Name>` (render-web) in for JDK classes TeaVM lacks or gets wrong, and for
 * jME classes that reach for a class by its name.
 */
public class EmulPolicy extends SimpleSubstitutionPolicy {
    static final Set<String> CLASSES = Set.of(
            "java.util.Scanner",
            "java.util.logging.Logger",
            "java.lang.ref.PhantomReference",
            "java.util.concurrent.ConcurrentLinkedQueue",
            "java.util.concurrent.Future",
            "java.util.concurrent.TimeoutException",
            "java.util.concurrent.locks.Lock",
            "java.util.concurrent.locks.ReentrantLock",
            "java.util.concurrent.locks.Condition",
            "com.jme3.util.BufferAllocatorFactory",
            "uz.dukeengine.core.data.ReflectedShapes");

    @Override
    public void contribute(SubstitutionSink sink) {
        sink.selectClasses(CLASSES::contains).packagePrefix("emul.").simpleNamePrefix("T");
    }
}
