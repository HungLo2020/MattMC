package net.minecraft.world.level.material;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class NativeMapColorsTest {
    @Test void everyPackedColorMatchesUntouchedFrozenImageBytes() throws Exception {
        // Real Frozen NativeImage.setPixel bytes, shared with the Rust owner tests.
        byte[] rgba = Files.readAllBytes(Path.of("src/main/rust/content/map_color/frozen-native-rgba.bin"));
        assertEquals(1024, rgba.length);
        for (int code=0; code<256; code++) {
            int at=code*4;
            int expected=(rgba[at+3]&255)<<24 | (rgba[at]&255)<<16 | (rgba[at+1]&255)<<8 | (rgba[at+2]&255);
            assertEquals(expected,MapColor.getColorFromPackedId(code),"packed " + code);
            assertEquals(expected,MapColor.byId(code>>2).calculateARGBColor(MapColor.Brightness.byId(code&3)));
            assertEquals(expected,MapColor.getColorFromPackedId(code-256));
            assertEquals(expected,MapColor.getColorFromPackedId(code+256));
        }
    }

    @Test void aliasesFallbacksBoundsAndNullBehaviorPreserveCompatibility() {
        assertSame(MapColor.GRASS,MapColor.byId(1));
        assertSame(MapColor.GLOW_LICHEN,MapColor.byId(61));
        assertSame(MapColor.NONE,MapColor.byId(62));
        assertSame(MapColor.NONE,MapColor.byId(63));
        assertEquals(0,MapColor.NONE.calculateARGBColor(null));
        assertThrows(NullPointerException.class,()->MapColor.GRASS.calculateARGBColor(null));
        assertThrows(NullPointerException.class,()->MapColor.NONE.getPackedId(null));
        assertThrows(IndexOutOfBoundsException.class,()->MapColor.byId(-1));
        assertThrows(IndexOutOfBoundsException.class,()->MapColor.byId(65));
        assertThrows(ArrayIndexOutOfBoundsException.class,()->MapColor.byId(64));
        assertThrows(ArrayIndexOutOfBoundsException.class,()->MapColor.Brightness.byId(4));
        for (var brightness : MapColor.Brightness.values())
            assertSame(brightness,MapColor.Brightness.byId(brightness.id));
    }
}
