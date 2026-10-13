package net.minecraft.world.level.chunk;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NativeRandomTicksTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    private static LevelChunkSection section(BlockState state) {
        return new LevelChunkSection(new PalettedContainer<>(state, Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY)), null);
    }
    private static ServerLevel world(NativeRandomTicks sequence) throws Exception {
        var level = mock(ServerLevel.class, CALLS_REAL_METHODS);
        Field f = Level.class.getDeclaredField("randomTicks"); f.setAccessible(true); f.set(level, sequence);
        return level;
    }
    private static LevelChunk chunk(ServerLevel level, LevelChunkSection[] sections) throws Exception {
        var chunk = mock(LevelChunk.class, CALLS_REAL_METHODS);
        for (var entry : java.util.Map.of("sections", sections, "levelHeightAccessor", level).entrySet()) {
            var f = ChunkAccess.class.getDeclaredField(entry.getKey()); f.setAccessible(true); f.set(chunk, entry.getValue());
        }
        var f = LevelChunk.class.getDeclaredField("level"); f.setAccessible(true); f.set(chunk, level);
        doReturn(0).when(level).getMinY(); doReturn(sections.length * 16).when(level).getHeight();
        return chunk;
    }
    @Test void scalarSequencePreservesSignedMasksAndCoordinateOverflow() throws Exception {
        for (int seed : new int[]{0, -1, Integer.MIN_VALUE, Integer.MAX_VALUE, 71}) {
            var sequence = new NativeRandomTicks(seed); var level = world(sequence); int state = seed;
            for (int mask : new int[]{0, 15, -1, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
                for (int i = 0; i < 1000; i++) {
                    state = state * 3 + 1013904223; int bits = state >> 2;
                    assertEquals(new BlockPos(Integer.MAX_VALUE + (bits & 15), Integer.MIN_VALUE + (bits >> 16 & mask), -99 + (bits >> 8 & 15)),
                        level.getBlockRandomPos(Integer.MAX_VALUE, Integer.MIN_VALUE, -99, mask));
                }
            }
        }
    }
    @Test void inactiveCandidatesSkipJavaReadsButAdvanceEveryAttempt() throws Exception {
        var sequence = new NativeRandomTicks(71); var level = world(sequence);
        sequence.tickSection(level, section(Blocks.STONE.defaultBlockState()), 0, -16, 0, 9000, InactiveProfiler.INSTANCE);
        int expected = 71; for (int i = 0; i < 9001; i++) expected = expected * 3 + 1013904223;
        assertEquals(expected >> 2, sequence.nextBits());
        verify(level, never()).getBlockRandomPos(anyInt(), anyInt(), anyInt(), anyInt());
    }
    @Test void callbacksConsumeSharedSequenceAndCanReplaceThePrimaryPalette() throws Exception {
        var sequence = new NativeRandomTicks(71); var level = world(sequence);
        var section = section(Blocks.WHEAT.defaultBlockState()); var seen = new ArrayList<BlockPos>();
        doAnswer(call -> {
            seen.add(call.getArgument(0));
            level.getBlockRandomPos(0, 0, 0, 15); // Reentrant use of the same world sequence.
            if (seen.size() == 2) section.getStates().installGenerated(0, List.of(Blocks.STONE.defaultBlockState()), new long[0]);
            return 0;
        }).when(level).getRawBrightness(any(), eq(0));
        sequence.tickSection(level, section, -16, -32, 48, 8, InactiveProfiler.INSTANCE);
        int state = 71; var expected = new ArrayList<BlockPos>();
        for (int i = 0; i < 8; i++) {
            state = state * 3 + 1013904223; int bits = state >> 2;
            if (i < 2) { expected.add(new BlockPos(-16 + (bits & 15), -32 + (bits >> 16 & 15), 48 + (bits >> 8 & 15))); state = state * 3 + 1013904223; }
        }
        assertEquals(expected, seen); state = state * 3 + 1013904223;
        assertEquals(state >> 2, sequence.nextBits());
    }
    @Test void profilerCallbacksPrecedeStateLookupAndStillRunForEmptyAttempts() throws Exception {
        var sequence = new NativeRandomTicks(71); var level = world(sequence);
        var section = section(Blocks.STONE.defaultBlockState()); var profiler = mock(ProfilerFiller.class);
        doAnswer(call -> { section.getStates().installGenerated(0, List.of(Blocks.WHEAT.defaultBlockState()), new long[0]); return null; })
            .when(profiler).push("randomTick");
        doReturn(0).when(level).getRawBrightness(any(), eq(0));
        sequence.tickSection(level, section, 0, 0, 0, 3, profiler);
        verify(profiler, times(3)).push("randomTick"); verify(profiler, times(3)).pop();
        verify(level, times(3)).getRawBrightness(any(), eq(0));
        verify(level, times(3)).getBlockRandomPos(anyInt(), anyInt(), anyInt(), anyInt());
    }
    @Test void throwingCallbackLeavesLaterAttemptsUnconsumed() throws Exception {
        var sequence = new NativeRandomTicks(71); var level = world(sequence);
        doThrow(new IllegalStateException("tick")).when(level).getRawBrightness(any(), eq(0));
        assertThrows(IllegalStateException.class, () -> sequence.tickSection(level, section(Blocks.WHEAT.defaultBlockState()), 0, 0, 0, 20, InactiveProfiler.INSTANCE));
        int expected = (71 * 3 + 1013904223) * 3 + 1013904223;
        assertEquals(expected >> 2, sequence.nextBits());
    }
    @Test void chunkTraversalPreservesCapturedSectionWhenCallbackEscapesAndReplacesSlots() throws Exception {
        var sequence = new NativeRandomTicks(71); var level = world(sequence);
        var sections = new LevelChunkSection[]{section(Blocks.WHEAT.defaultBlockState()), section(Blocks.WHEAT.defaultBlockState()), section(Blocks.STONE.defaultBlockState())};
        var chunk = chunk(level, sections); var seen = new ArrayList<BlockPos>();
        var nested = section(Blocks.WHEAT.defaultBlockState());
        nested.getStates().installGenerated(0, List.of(Blocks.STONE.defaultBlockState()), new long[0]);
        var nestedChunk = chunk(level, new LevelChunkSection[]{nested, section(Blocks.STONE.defaultBlockState()), section(Blocks.STONE.defaultBlockState())});
        doAnswer(call -> {
            seen.add(call.getArgument(0));
            if (seen.size() == 1) {
                chunk.getSections()[0] = section(Blocks.STONE.defaultBlockState());
                chunk.getSections()[1] = section(Blocks.STONE.defaultBlockState());
                // Nested native selection must not overwrite the outer cursor.
                sequence.tickChunk(level, nestedChunk, 0, 0, 5, InactiveProfiler.INSTANCE);
            }
            return 0;
        }).when(level).getRawBrightness(any(), eq(0));
        sequence.tickChunk(level, chunk, 0, 0, 3, InactiveProfiler.INSTANCE);
        assertEquals(3, seen.size()); assertTrue(seen.stream().allMatch(p -> p.getY() < 16));
        int expected = 71; for (int i = 0; i < 9; i++) expected = expected * 3 + 1013904223;
        assertEquals(expected >> 2, sequence.nextBits());
    }
    @Test void lavaKeepsBothSelectedStateCallbacksAfterCellReplacement() throws Exception {
        var sequence = new NativeRandomTicks(71); var level = world(sequence);
        var section = section(Blocks.LAVA.defaultBlockState());
        var rules = mock(net.minecraft.world.level.GameRules.class);
        doAnswer(call -> {
            section.getStates().installGenerated(0, List.of(Blocks.STONE.defaultBlockState()), new long[0]);
            return rules;
        }).when(level).getGameRules();
        sequence.tickSection(level, section, 0, 0, 0, 5, InactiveProfiler.INSTANCE);
        // LiquidBlock delegates its block tick to the fluid, then ServerLevel
        // ticks that same selected state's fluid again, even after replacement.
        verify(level, times(2)).getGameRules();
        int expected = 71; for (int i = 0; i < 6; i++) expected = expected * 3 + 1013904223;
        assertEquals(expected >> 2, sequence.nextBits());
    }
    @Test void retainedChunkRebindsPaletteGrowthAndSeesFutureSectionCounterChanges() throws Exception {
        var sequence = new NativeRandomTicks(71); var level = world(sequence);
        var sections = new LevelChunkSection[]{section(Blocks.WHEAT.defaultBlockState()), section(Blocks.STONE.defaultBlockState())};
        var chunk = chunk(level, sections); var seen = new ArrayList<BlockPos>();
        doAnswer(call -> {
            seen.add(call.getArgument(0));
            if (seen.size() == 1) {
                sections[0].getStates().installGenerated(0, List.of(Blocks.STONE.defaultBlockState()), new long[0]);
                for (int y=0;y<16;y++) for (int z=0;z<16;z++) for (int x=0;x<16;x++)
                    sections[1].setBlockState(x,y,z,Blocks.WHEAT.defaultBlockState());
            }
            return 0;
        }).when(level).getRawBrightness(any(), eq(0));
        sequence.tickChunk(level, chunk, 0, 0, 3, InactiveProfiler.INSTANCE);
        assertFalse(chunk.hasEscapedSectionArray());
        assertEquals(4, seen.size()); assertTrue(seen.subList(1,4).stream().allMatch(p -> p.getY() >= 16));
        int expected = 71; for (int i=0;i<7;i++) expected = expected * 3 + 1013904223;
        assertEquals(expected >> 2, sequence.nextBits());
    }
}
