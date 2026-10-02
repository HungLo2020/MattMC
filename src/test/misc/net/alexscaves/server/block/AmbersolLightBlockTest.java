package net.alexscaves.server.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.ticks.BlackholeTickAccess;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AmbersolLightBlockTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void lightColumnSurvivesUntilAmbersolIsRemovedThenClearsOnShapeUpdates() {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        BlockPos source = new BlockPos(0, 4, 0);
        BlockPos floor = new BlockPos(0, 1, 0);
        blocks.put(source, Blocks.AMBERSOL.defaultBlockState());
        blocks.put(floor, Blocks.STONE.defaultBlockState());
        LevelAccessor level = columnLevel(blocks);

        assertEquals(floor, AmbersolBlock.fillWithLights(source, level));
        AmbersolLightBlock light = (AmbersolLightBlock) Blocks.AMBERSOL_LIGHT;
        for (int y = 2; y < 4; y++) {
            BlockPos pos = new BlockPos(0, y, 0);
            assertTrue(blocks.get(pos).is(Blocks.AMBERSOL_LIGHT));
            assertTrue(light.canSurvive(blocks.get(pos), level, pos));
        }

        blocks.remove(source);
        // Deliver the shape updates explicitly. An actual world's neighbor
        // propagation and rendered lighting are still integration checks.
        for (int y = 3; y >= 2; y--) {
            BlockPos pos = new BlockPos(0, y, 0);
            BlockState state = blocks.get(pos);
            assertFalse(light.canSurvive(state, level, pos));
            BlockState updated = light.updateShape(state, level, level, pos, Direction.UP,
                pos.above(), level.getBlockState(pos.above()), RandomSource.create(0));
            assertTrue(updated.isAir());
            blocks.put(pos, updated);
        }
        assertTrue(blocks.get(floor).is(Blocks.STONE));
    }

    private static LevelAccessor columnLevel(Map<BlockPos, BlockState> blocks) {
        return (LevelAccessor) Proxy.newProxyInstance(LevelAccessor.class.getClassLoader(),
            new Class<?>[] {LevelAccessor.class}, (proxy, method, args) -> switch (method.getName()) {
                case "getBlockState" -> blocks.getOrDefault((BlockPos) args[0], Blocks.AIR.defaultBlockState());
                case "setBlock" -> {
                    blocks.put(((BlockPos) args[0]).immutable(), (BlockState) args[1]);
                    yield true;
                }
                case "getMinY" -> 0;
                case "getMaxY" -> 8;
                case "getBlockTicks", "getFluidTicks" -> BlackholeTickAccess.emptyLevelList();
                case "createTick" -> null; // The blackhole tick scheduler ignores the value.
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }
}
