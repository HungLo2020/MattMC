package net.sodium.client.render;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

class SectionInputDiagnosticsTest {
    @Test void capturesExactOrderedSectionAndOcclusionHaloWithoutMutatingInputs() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        AtomicInteger reads = new AtomicInteger();
        BlockGetter input = (BlockGetter)Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[]{BlockGetter.class}, (proxy, method, args) -> {
                if (!method.getName().equals("getBlockState")) throw new AssertionError(method.getName());
                reads.incrementAndGet();
                BlockPos pos = (BlockPos)args[0];
                return pos.equals(new BlockPos(15, 31, 47)) ? Blocks.BELL.defaultBlockState() : Blocks.STONE.defaultBlockState();
            });
        var first = SectionInputDiagnostics.snapshot(input, 16, 32, 48);
        assertEquals(5832, reads.get());
        assertEquals(5832, first.getAsJsonArray("states").size());
        assertEquals(2, first.getAsJsonArray("palette").size());
        assertTrue(first.getAsJsonArray("palette").get(0).getAsString().contains("minecraft:bell"));
        assertEquals(0, first.getAsJsonArray("states").get(0).getAsInt());
        assertEquals(1, first.getAsJsonArray("states").get(1).getAsInt());
        assertEquals(first, SectionInputDiagnostics.snapshot(input, 16, 32, 48));
    }
}
