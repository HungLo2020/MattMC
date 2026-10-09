package net.minecraft.world.level.block;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NativeBlockPolicyTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void registeredViewsAndRegistryFlagsMatchEveryFrozenState() throws Exception {
        byte[] expected = Files.readAllBytes(Path.of("src/main/rust/content/block/definitions/policy/frozen-state-policy.bin"));
        assertEquals(Block.BLOCK_STATE_REGISTRY.size(), expected.length);
        assertTrue(NativeBlockRegistry.ready());
        int[] flags = NativeBlockRegistry.column(1);
        for (int id = 0; id < expected.length; id++) {
            var state = Block.BLOCK_STATE_REGISTRY.byId(id);
            int policy = expected[id] & 255;
            assertEquals((policy & 1) != 0, state.isRandomlyTicking(), "tick state " + id);
            assertEquals((policy & 2) != 0, state.useShapeForLightOcclusion(), "light state " + id);
            assertEquals((policy & 4) != 0, (flags[id] & NativeBlockRegistry.LEAVES) != 0, "leaf state " + id);
            assertEquals((policy & 8) != 0, state.hasBlockEntity(), "entity state " + id);
        }
    }

    @Test
    void genericStateConstructorsRetainStateDependentJavaCallbacks() {
        for (var block : List.of(Blocks.WHEAT, Blocks.PITCHER_CROP, Blocks.TORCHFLOWER_CROP,
                Blocks.BAMBOO, Blocks.KELP, Blocks.OAK_LEAVES, Blocks.OAK_SLAB, Blocks.PISTON, Blocks.CHEST)) {
            for (var canonical : block.getStateDefinition().getPossibleStates()) {
                var values = new Reference2ObjectArrayMap<>(canonical.getValues());
                var legacy = new BlockState(block, values, MapCodec.unit(() -> canonical));
                legacy.initCache();
                assertEquals(canonical.isRandomlyTicking(), legacy.isRandomlyTicking(), canonical.toString());
                assertEquals(canonical.useShapeForLightOcclusion(), legacy.useShapeForLightOcclusion(), canonical.toString());
                assertEquals(canonical.hasBlockEntity(), legacy.hasBlockEntity(), canonical.toString());
            }
        }
    }
}
