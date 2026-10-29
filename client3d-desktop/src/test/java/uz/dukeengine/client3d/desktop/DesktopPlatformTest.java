package uz.dukeengine.client3d.desktop;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.platform.ClientPlatform;

class DesktopPlatformTest {

    @Test
    void theDesktopIsFoundOnceAndKept() {
        assertInstanceOf(DesktopPlatform.class, ClientPlatform.get());
        assertSame(ClientPlatform.get(), ClientPlatform.get());
    }
}
