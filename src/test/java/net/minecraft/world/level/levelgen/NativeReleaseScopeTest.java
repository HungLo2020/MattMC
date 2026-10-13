package net.minecraft.world.level.levelgen;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.NativeReleaseScope;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Chunks created inside a release scope work normally, are freed when it
 * closes, and fail safely (no freed-memory read) if touched afterwards.
 * Chunks created outside are unaffected.
 */
class NativeReleaseScopeTest {
    @BeforeAll static void load() { NativeNoiseFillTest.load(); }
    @AfterAll static void close() { NativeNoiseFillTest.close(); }

    private static ProtoChunk chunk(PalettedContainerFactory factory, int x) {
        return new ProtoChunk(new ChunkPos(x, 0), UpgradeData.EMPTY, LevelHeightAccessor.create(-64, 384), factory, null);
    }

    @Test
    void scopedChunksAreFreedOnCloseAndUnscopedOnesAreUntouched() {
        var factory = PalettedContainerFactory.create(NativeNoiseFillTest.registries);
        var stone = Blocks.STONE.defaultBlockState();
        var outside = chunk(factory, 100);
        long closedBefore = NativeReleaseScope.closed();
        ProtoChunk scoped;
        net.minecraft.world.level.chunk.LevelChunkSection copied;
        try (var scope = NativeReleaseScope.open()) {
            assertNotNull(scope, "scopes are enabled by default");
            scoped = chunk(factory, 0);
            var section = scoped.getSections()[4];
            section.setBlockState(1, 2, 3, stone);
            // Grow the palette so retired generations and new views are exercised too.
            for (int i = 0; i < 300; i++) section.setBlockState(i & 15, 8 + ((i >> 4) & 7), i % 7,
                (i & 1) == 0 ? Blocks.DIRT.defaultBlockState() : Blocks.GRANITE.defaultBlockState());
            assertSame(stone, section.getBlockState(1, 2, 3));
            copied = section.copy();
            assertSame(stone, copied.getBlockState(1, 2, 3), "copies inside the scope work");
            outside.getSections()[4].setBlockState(1, 2, 3, stone);
        }
        assertEquals(closedBefore + 1, NativeReleaseScope.closed(), "closing frees the scope's arena");
        assertThrows(IllegalStateException.class, () -> scoped.getSections()[4].getBlockState(1, 2, 3),
            "a read after close fails instead of reading freed memory");
        assertThrows(IllegalStateException.class, () -> copied.getBlockState(1, 2, 3));
        assertSame(stone, outside.getSections()[4].getBlockState(1, 2, 3), "chunks created outside the scope are unaffected");
        var after = chunk(factory, 1);
        after.getSections()[0].setBlockState(0, 0, 0, stone);
        assertSame(stone, after.getSections()[0].getBlockState(0, 0, 0), "after close, new chunks use ordinary owners");
    }

    @Test
    void nestedScopesCloseIndependently() {
        var factory = PalettedContainerFactory.create(NativeNoiseFillTest.registries);
        var stone = Blocks.STONE.defaultBlockState();
        ProtoChunk outer, inner;
        try (var a = NativeReleaseScope.open()) {
            outer = chunk(factory, 0);
            try (var b = NativeReleaseScope.open()) { inner = chunk(factory, 1); }
            assertThrows(IllegalStateException.class, () -> inner.getSections()[0].getBlockState(0, 0, 0));
            outer.getSections()[0].setBlockState(0, 0, 0, stone);
            assertSame(stone, outer.getSections()[0].getBlockState(0, 0, 0), "the outer scope is still open");
        }
        assertThrows(IllegalStateException.class, () -> outer.getSections()[0].getBlockState(0, 0, 0));
    }
}
