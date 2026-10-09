package net.minecraft.world.level.chunk;

import java.io.DataInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.foreign.Arena;
import java.lang.foreign.ValueLayout;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeLightLayerTest {
    @Test void productionAdapterMatchesEveryFrozenRepresentationAndCell() throws Exception {
        try (var input = new DataInputStream(Files.newInputStream(Path.of(
                "src/main/rust/world/level/lighting/layers/frozen-data-layer.rle")))) {
            assertEquals(0x4c415952, input.readInt());
            int cases = input.readInt();
            DataLayer layer = null;
            for (int row = 0; row < cases; row++) {
                int initial = input.readInt(), operation = input.readInt();
                switch (operation) {
                    case 0 -> layer = new DataLayer(initial);
                    case 1, 3, 10 -> layer = layer.copy();
                    case 2 -> layer.set(0, 0, 0, 17);
                    case 4 -> layer.set(15, 15, 15, -2);
                    case 5 -> { var previous = layer; layer = layer.copy(); previous.fill(7); }
                    case 6 -> layer.fill(31);
                    case 7, 11 -> layer.getData();
                    case 8 -> layer.set(7, 9, 13, -81);
                    case 9 -> layer.fill(0);
                    default -> fail("Unexpected Frozen operation");
                }
                assertEquals(input.readInt(), layer.defaultValueForNativeLight(), "default row " + row);
                assertEquals(input.readBoolean(), !layer.isDefinitelyHomogenous(), "allocation row " + row);
                int runs = input.readUnsignedShort(), index = 0;
                for (int run = 0; run < runs; run++) {
                    int length = input.readUnsignedShort(), expected = input.readInt();
                    for (int i = 0; i < length; i++, index++)
                        assertEquals(expected, layer.get(index & 15, index >>> 8, (index >>> 4) & 15), "row " + row + " cell " + index);
                }
                assertEquals(4096, index);
            }
            assertEquals(-1, input.read());
        }
    }

    @Test void independentPacketImportOwnsBytesWithoutAJavaClone() {
        byte[] payload = new byte[2048];
        payload[0] = 0x23;
        DataLayer imported = DataLayer.copyOf(payload);
        assertNotNull(imported.nativeLightLayer());
        assertNull(imported.data);
        payload[0] = 0x45;
        assertEquals(3, imported.get(0, 0, 0));
        imported.set(0, 0, 0, 6);
        assertEquals(0x45, payload[0]);
        assertEquals(0, imported.defaultValueForNativeLight());
        assertThrows(NullPointerException.class, () -> DataLayer.copyOf(null));
    }

    @Test void mutableArrayEscapeAliasesAndCopyRemainIndependent() {
        DataLayer layer = new DataLayer(15);
        layer.set(2, 3, 4, 7);
        assertNotNull(layer.nativeLightLayer());
        DataLayer copy = layer.copy();
        byte[] escaped = layer.getData();
        assertNull(layer.nativeLightLayer());
        assertSame(escaped, layer.getData());
        escaped[0] = 0x12;
        assertEquals(2, layer.get(0, 0, 0));
        assertEquals(15, copy.get(0, 0, 0));
        layer.fill(3);
        assertNotNull(layer.nativeLightLayer());
        assertEquals(0x12, escaped[0]);
        assertEquals(3, layer.get(0, 0, 0));
        byte[] imported = new byte[2048];
        DataLayer aliased = new DataLayer(imported), independent = aliased.copy();
        imported[0] = 0x45;
        assertEquals(5, aliased.get(0, 0, 0));
        assertEquals(0, independent.get(0, 0, 0));
    }

    @Test void retainedGenerationSurvivesFillAndExistingWritesAreVisible() {
        DataLayer layer = new DataLayer(15);
        layer.set(0, 0, 0, 3);
        var view = layer.nativeLightLayer().view();
        var bytes = view.bytes();
        layer.set(0, 0, 0, 4);
        assertEquals(4, view.get(0));
        assertSame(bytes, layer.nativeLightLayer().view().bytes());
        layer.fill(6);
        assertEquals(4, view.get(0));
        assertEquals(15, view.rawDefault());
        assertEquals(6, layer.get(0, 0, 0));
        assertTrue(bytes.isReadOnly());
    }

    @Test void invalidWriteMaterializesBeforeOriginalArrayException() {
        DataLayer layer = new DataLayer(16);
        assertEquals(16, layer.get(-1, 0, 0));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> layer.set(-1, 0, 0, 4));
        assertFalse(layer.isDefinitelyHomogenous());
        assertEquals(16, layer.defaultValueForNativeLight());
        assertEquals(0, layer.get(0, 0, 0));
        assertEquals(1, layer.get(1, 0, 0));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> layer.get(-1, 0, 0));
    }

    @Test void skyRepetitionPreservesNativeSourceAndIndependentOutput() {
        DataLayer layer = new DataLayer(15);
        layer.set(4, 0, 7, 3);
        layer.set(4, 3, 7, 8);
        DataLayer repeated = layer.repeatNativeFirstLightLayer();
        assertNotNull(repeated.nativeLightLayer());
        assertNotNull(layer.nativeLightLayer());
        assertEquals(0, repeated.defaultValueForNativeLight());
        for (int y = 0; y < 16; y++) assertEquals(3, repeated.get(4, y, 7));
        repeated.set(4, 0, 7, 9);
        assertEquals(3, layer.get(4, 0, 7));
        DataLayer lazy = new DataLayer(-1).repeatNativeFirstLightLayer();
        assertTrue(lazy.isDefinitelyHomogenous());
        assertEquals(-1, lazy.get(1, 2, 3));
    }

    @Test void canonicalCallbackBorrowsOwnerAndCustomLayerKeepsJavaSemantics() {
        DataLayer layer = new DataLayer(5);
        try (Arena arena = Arena.ofConfined()) {
            var buffer = arena.allocate(NativeLightBlocks.BUFFER, 8);
            assertEquals(2, NativeLightBlocks.layer(layer, true, buffer));
            assertEquals(layer.nativeLightLayer().ownerForNativeCall().address(), buffer.get(ValueLayout.JAVA_LONG, 8));
            assertNull(layer.data);
            class Custom extends DataLayer { Custom() { super(5); } }
            DataLayer custom = new Custom();
            assertNull(custom.nativeLightLayer());
            assertEquals(-1, NativeLightBlocks.layer(custom, true, buffer));
            custom.set(0, 0, 0, 3);
            assertNotNull(custom.data);
            assertEquals(3, custom.get(0, 0, 0));
        }
    }
}
