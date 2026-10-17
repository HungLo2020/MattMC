package net.minecraft.world.level.lighting;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.NativeLightSources;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeBlockLightSourcesTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    private static void assertSources(ProtoChunk chunk, NativeLightQueues queues) {
        List<Long> expected = new ArrayList<>();
        chunk.findBlockLightSources((position, state) -> {
            expected.add(position.asLong());
            expected.add(LightEngine.QueueEntry.increaseLightFromEmission(state.getLightEmission(), LightEngine.isEmptyShape(state)));
        });
        assertTrue(NativeLightSources.seed(chunk, queues), "canonical sections must use native production");
        assertEquals(expected.size(), queues.size(1));
        for (long word : expected) assertEquals(word, queues.dequeue(1));
        assertTrue(queues.isEmpty(1));
    }

    @Test void staleEmptyCountersDoNotHideEmittersAndBindingsObserveGrowthAndReplacement() {
        var chunk = SkyLightSourcesFixtures.chunk("empty", -64, 32, 1);
        var queues = new NativeLightQueues();
        // Raw palette writes intentionally leave the section counter stale-empty.
        chunk.getSection(0).getStates().set(15, 15, 15, Blocks.GLOWSTONE.defaultBlockState());
        chunk.getSection(0).getStates().set(0, 0, 0, Blocks.TORCH.defaultBlockState());
        chunk.getSection(1).getStates().set(0, 0, 0, Blocks.LAVA.defaultBlockState());
        assertTrue(chunk.getSection(0).hasOnlyAir());
        assertSources(chunk, queues);
        for (int i = 0; i < 256; i++) chunk.getSection(0).getStates().set(i & 15, 2, i >> 4,
            net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY.byId(i));
        assertSources(chunk, queues);
        var replacement = SkyLightSourcesFixtures.chunk("empty", -64, 32, 2).getSection(0);
        replacement.getStates().set(3, 4, 5, Blocks.SEA_LANTERN.defaultBlockState());
        chunk.getSections()[0] = replacement;
        assertSources(chunk, queues);
    }

    @Test void sourcesAppendAfterExistingWorkAndPreserveDecreaseQueue() {
        var chunk = SkyLightSourcesFixtures.chunk("empty", 0, 16, 3);
        chunk.getSection(0).getStates().set(1, 2, 3, Blocks.TORCH.defaultBlockState());
        var queues = new NativeLightQueues();
        queues.enqueue(0, 11, 12); queues.enqueue(1, 21, 22);
        assertTrue(NativeLightSources.seed(chunk, queues));
        assertEquals(11, queues.dequeue(0)); assertEquals(12, queues.dequeue(0));
        assertEquals(21, queues.dequeue(1)); assertEquals(22, queues.dequeue(1));
        assertEquals(2, queues.size(1));
    }
}
