package uz.dukeengine.core.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** A map's scenery line: six words as ever, and a picture, a box and a tint where it says them. */
class SceneryLineTest {

    @Test
    void aLineOfSixWordsReadsAsEverWhiteAndRoundAndIsWrittenBackTheSame() {
        var line = SceneryLine.of("models/trees/oak.glb 4.5 7.5 90.0 1.2 0.75");

        assertEquals("models/trees/oak.glb", line.model());
        assertEquals("", line.picture());
        assertEquals(0xFFFFFF, line.tint());
        assertEquals(0.75f, line.footprint());
        assertEquals(0f, line.boxLength());
        assertEquals("models/trees/oak.glb 4.5 7.5 90.0 1.2 0.75", line.written());
    }

    @Test
    void aPictureABoxAndATintAreReadAndWrittenBack() {
        var written = "models/castles/fort/fort_wall.gltf@textures/fort_9_dark.jpg 5.5 6.0 90.0 1.0 3.0x1.0 0x806050";
        var line = SceneryLine.of(written);

        assertEquals("models/castles/fort/fort_wall.gltf", line.model());
        assertEquals("textures/fort_9_dark.jpg", line.picture());
        assertEquals(3f, line.boxLength());
        assertEquals(1f, line.boxDepth());
        assertEquals(0f, line.footprint());
        assertEquals(0x806050, line.tint());
        assertEquals(written, line.written());
    }

    @Test
    void aLineOfAnotherLengthOrAWrongTintIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> SceneryLine.of("rock 1 2 0 1"));
        assertThrows(IllegalArgumentException.class, () -> SceneryLine.of("rock 1 2 0 1 0 red"));
    }
}
