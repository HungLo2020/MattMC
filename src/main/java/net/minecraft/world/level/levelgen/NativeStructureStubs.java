package net.minecraft.world.level.levelgen;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jetbrains.annotations.Nullable;

/** Rust-owned start positions ({@code structure/stubs.rs}) for structures whose
 * {@code findGenerationPoint} draws no randomness: the monument's surrounding
 * biome scan, single-piece temples' corner heights, swamp huts and buried
 * treasure, mansions and End cities (one rotation draw), ruined portals
 * (every {@code findGenerationPoint} draw and {@code findSuitableY}'s base
 * columns), nether fossils (a base column's floor scan) and mineshafts (every
 * piece, planned in {@code structure/mineshaft_plan.rs}), each with the stub's biome check on the climate tree. Java builds
 * the pieces with the generation context's random, untouched by the search. */
public final class NativeStructureStubs {
    private static final MethodHandle STUB = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_structure_stub",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final boolean ENABLED = !Boolean.getBoolean("mattmc.worldgen.javaStructureStubs");
    public static final AtomicLong PLANNED = new AtomicLong();
    // Per structure and climate parameter list: the structure's biomes and the monument's surrounding tag, per node.
    private static final Map<Structure, Map<Climate.ParameterList<?>, byte[][]>> ACCEPT = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private static final int NETHER_FOSSIL = 6;
    private static final int MINESHAFT = 7;
    // Planned piece words (mineshafts, fortresses 8, strongholds 9); a larger plan falls back to Java.
    private static final int PIECE_WORDS = 1 << 16;

    private NativeStructureStubs() {
    }

    /** The End's five biomes in native leaf order: in the structure's set, and never the monument's tag. */
    private static byte[][] endAccept(net.minecraft.world.level.biome.TheEndBiomeSource source, java.util.function.Predicate<Holder<Biome>> biomes) {
        var list = source.nativeBiomes();
        byte[] inSet = new byte[list.size()];
        for (int i = 0; i < inSet.length; i++) inSet[i] = (byte)(biomes.test(list.get(i)) ? 1 : 0);
        return new byte[][]{inSet, new byte[list.size()]};
    }

    /** {@code structure.generate(...)} with a native start position, or null for Java's path (nothing drawn or changed). */
    @Nullable
    public static StructureStart generate(Structure structure, RegistryAccess registries, ChunkGenerator generator, BiomeSource biomeSource,
                                          RandomState randomState, StructureTemplateManager templates, long seed, ChunkPos chunkPos, int references,
                                          LevelHeightAccessor height, java.util.function.Predicate<Holder<Biome>> biomes) {
        int[] kind = structure.nativeStub();
        if (!ENABLED || kind == null || !(generator instanceof NoiseBasedChunkGenerator noise)) return null;
        boolean end = biomeSource.getClass() == net.minecraft.world.level.biome.TheEndBiomeSource.class;
        if (!end && (biomeSource.getClass() != MultiNoiseBiomeSource.class
            || ((MultiNoiseBiomeSource)biomeSource).nativeParameters().getClass() != Climate.ParameterList.class)) {
            return null;
        }
        NativeColumns columns = NativeColumns.source(noise, height, randomState);
        if (columns == null) return null;
        int[] surface = columns.matches(Heightmap.Types.WORLD_SURFACE_WG), ocean = columns.matches(Heightmap.Types.OCEAN_FLOOR_WG);
        NativeBiomeSearch.Program program = NativeBiomeSearch.climateProgram(randomState.sampler());
        if (surface == null || ocean == null || program == null) return null;
        var list = end ? null : ((MultiNoiseBiomeSource)biomeSource).nativeParameters();
        byte[][] accept = end ? endAccept((net.minecraft.world.level.biome.TheEndBiomeSource)biomeSource, biomes)
            : ACCEPT.computeIfAbsent(structure, s -> java.util.Collections.synchronizedMap(new IdentityHashMap<>())).computeIfAbsent(list, l -> {
            byte[] inSet = new byte[list.nativeNodeCount()], surround = new byte[list.nativeNodeCount()];
            for (int node = 0; node < inSet.length; node++) {
                Holder<Biome> leaf = list.nativeLeafValue(node);
                if (leaf == null) continue;
                inSet[node] = (byte)(biomes.test(leaf) ? 1 : 0);
                surround[node] = (byte)(leaf.is(BiomeTags.REQUIRED_OCEAN_MONUMENT_SURROUNDING) ? 1 : 0);
            }
            return new byte[][]{inSet, surround};
        });
        int[] params = structure.nativeStubParams(templates, generator, height);
        if (params == null) return null;
        // Scattered and template structures planned by small_plan.rs (single-piece and chunk-centre stubs).
        int[] small = (kind[0] == 1 || kind[0] == 2) ? structure.nativePlanParams(templates) : null;
        if (small != null) params = small;
        if (kind[0] == NETHER_FOSSIL) {
            // The fossil's floor test over the column's states: air above, soul sand or a sturdy top below.
            int[] air = columns.passing(BlockBehaviour.BlockStateBase::isAir);
            int[] floor = columns.passing(state -> !state.hasOffsetFunction()
                && (state.is(Blocks.SOUL_SAND) || state.isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, Direction.UP)));
            if (air == null || floor == null) return null;
            params = java.util.stream.IntStream.concat(java.util.stream.IntStream.of(params),
                java.util.stream.IntStream.concat(java.util.stream.IntStream.concat(java.util.stream.IntStream.of(air.length), java.util.stream.IntStream.of(air)),
                    java.util.stream.IntStream.concat(java.util.stream.IntStream.of(floor.length), java.util.stream.IntStream.of(floor)))).toArray();
        }
        int[] ints = new int[9 + surface.length + ocean.length + params.length];
        System.arraycopy(new int[]{kind[0], chunkPos.x, chunkPos.z, generator.getSeaLevel(), height.getMinY(), kind[1], kind[2], surface.length, ocean.length},
            0, ints, 0, 9);
        System.arraycopy(surface, 0, ints, 9, surface.length);
        System.arraycopy(ocean, 0, ints, 9 + surface.length, ocean.length);
        System.arraycopy(params, 0, ints, 9 + surface.length + ocean.length, params.length);
        int status, previous;
        long[] stub;
        int[] pieces = new int[0];
        try (Arena arena = Arena.ofConfined()) {
            var previousSegment = arena.allocateFrom(ValueLayout.JAVA_INT, list == null ? -1 : list.previousNativeLeaf());
            var out = arena.allocate(ValueLayout.JAVA_LONG, 8);
            boolean planned = small != null || kind[0] == MINESHAFT || kind[0] == 8 || kind[0] == 9 || (kind[0] == 4 || kind[0] == 0) && kind[1] != 0;
            var words = planned ? arena.allocate(ValueLayout.JAVA_INT, PIECE_WORDS) : java.lang.foreign.MemorySegment.NULL;
            status = (int)STUB.invokeExact(columns.handle(), columns.program(), arena.allocateFrom(ValueLayout.JAVA_INT, ints), ints.length,
                arena.allocateFrom(ValueLayout.JAVA_LONG, program.handle, list == null ? 0 : list.nativeNodes().address(), list == null ? 0L : (long)list.nativeNodeCount(), seed),
                arena.allocateFrom(ValueLayout.JAVA_BYTE, accept[0]), arena.allocateFrom(ValueLayout.JAVA_BYTE, accept[1]), previousSegment, out, words, planned ? PIECE_WORDS : 0);
            previous = previousSegment.get(ValueLayout.JAVA_INT, 0);
            stub = out.toArray(ValueLayout.JAVA_LONG);
            if (status == 1 && planned && stub[3] != 0) pieces = words.asSlice(0, stub[5] * 4L).toArray(ValueLayout.JAVA_INT);
        } catch (Throwable error) {
            throw new IllegalStateException("Native structure stub failed", error);
        } finally {
            Reference.reachabilityFence(columns);
            Reference.reachabilityFence(program);
        }
        if (status == -2) return null;
        if (status < 0) throw new IllegalStateException("Native structure stub returned " + status);
        if (list != null) list.setPreviousNativeLeaf(previous);
        PLANNED.incrementAndGet();
        if (status == 0 || stub[3] == 0) return StructureStart.INVALID_START;
        var context = new Structure.GenerationContext(registries, generator, biomeSource, randomState, templates, seed, chunkPos, height, biomes);
        var builder = new StructurePiecesBuilder();
        if (small != null) structure.nativePlanPieces(builder, templates, pieces);
        else structure.nativeStubPieces(builder, context, new net.minecraft.core.BlockPos((int)stub[0], (int)stub[1], (int)stub[2]),
            net.minecraft.world.level.block.Rotation.values()[(int)stub[4]], java.util.Arrays.copyOfRange(stub, 5, 8), pieces);
        var start = new StructureStart(structure, chunkPos, references, builder.build());
        return start.isValid() ? start : StructureStart.INVALID_START;
    }
}
