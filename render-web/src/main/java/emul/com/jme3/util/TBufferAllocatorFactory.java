package emul.com.jme3.util;

import com.jme3.util.BufferAllocator;
import com.jme3.util.PrimitiveAllocator;

/** jME's allocator factory in a browser: the one allocator there is, without naming it by reflection. */
public class TBufferAllocatorFactory {
    public static BufferAllocator create() {
        return new PrimitiveAllocator();
    }
}
