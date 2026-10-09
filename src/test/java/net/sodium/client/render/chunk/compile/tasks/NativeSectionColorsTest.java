package net.sodium.client.render.chunk.compile.tasks;

import java.lang.reflect.Proxy;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;
import static org.junit.jupiter.api.Assertions.*;

class NativeSectionColorsTest {
    @Test
    void adjacentGrassCoordinatesAreSampledOnceAndFoliageRemainsSeparate() {
        long active = MemoryUtil.nmemAlloc(6);
        long kinds = MemoryUtil.nmemCalloc(4096, 1);
        try {
            MemoryUtil.memPutShort(active, (short)0);
            MemoryUtil.memPutShort(active + 2, (short)1);
            MemoryUtil.memPutShort(active + 4, (short)16);
            MemoryUtil.memPutByte(kinds, (byte)1);
            MemoryUtil.memPutByte(kinds + 1, (byte)1);
            MemoryUtil.memPutByte(kinds + 16, (byte)2);
            var observed = new java.util.HashSet<String>();
            var calls = new int[3];
            var slice = (BlockAndTintGetter) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] {BlockAndTintGetter.class}, (proxy, method, args) -> {
                        assertEquals("getBlockTint", method.getName());
                        BlockPos pos = (BlockPos) args[0];
                        int kind = args[1] == BiomeColors.GRASS_COLOR_RESOLVER ? 1
                                : args[1] == BiomeColors.FOLIAGE_COLOR_RESOLVER ? 2 : 0;
                        assertTrue(kind != 0);
                        calls[kind]++;
                        assertTrue(observed.add(kind + ":" + pos.getX() + ":" + pos.getY() + ":" + pos.getZ()));
                        return 0x205080;
                    });
            try (var colors = NativeSectionColors.capture(-17, 80, -33, active, 3, kinds, 0, slice)) {
                assertTrue(colors.identity() > 0);
                assertEquals(80, calls[1]);
                assertEquals(64, calls[2]);
            }
        } finally { MemoryUtil.nmemFree(active); MemoryUtil.nmemFree(kinds); }
    }

    @Test
    void providerFailureReleasesEveryConstructionLease() {
        long active = MemoryUtil.nmemCalloc(1, 2);
        long kinds = MemoryUtil.nmemCalloc(4096, 1);
        MemoryUtil.memPutByte(kinds, (byte)1);
        try {
            var slice = (BlockAndTintGetter) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] {BlockAndTintGetter.class}, (proxy, method, args) -> {
                        throw new IllegalArgumentException("authored provider failure");
                    });
            for (int i = 0; i < 140; i++) {
                var failure = assertThrows(IllegalStateException.class,
                        () -> NativeSectionColors.capture(0, 0, 0, active, 1, kinds, 0, slice));
                assertInstanceOf(IllegalArgumentException.class, failure.getCause());
            }
        } finally { MemoryUtil.nmemFree(active); MemoryUtil.nmemFree(kinds); }
    }

    @Test
    void ordinaryUntintedStatesNeedNoLiteralRowsAndDryFoliageRetainsItsResolver() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        var colors = net.minecraft.client.color.block.BlockColors.createDefault();
        assertEquals(0, NativeSectionSnapshot.colorSourceKind(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), colors));
        assertEquals(1, NativeSectionSnapshot.colorSourceKind(net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(), colors));
        assertEquals(2, NativeSectionSnapshot.colorSourceKind(net.minecraft.world.level.block.Blocks.OAK_LEAVES.defaultBlockState(), colors));
        assertEquals(3, NativeSectionSnapshot.colorSourceKind(net.minecraft.world.level.block.Blocks.LEAF_LITTER.defaultBlockState(), colors));
        assertEquals(4, NativeSectionSnapshot.colorSourceKind(net.minecraft.world.level.block.Blocks.REDSTONE_WIRE.defaultBlockState(), colors));
    }
}
