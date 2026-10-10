package net.minecraft.world.level.chunk;

import com.seibel.distanthorizons.common.wrappers.block.BiomeWrapper;
import com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper;
import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;
import com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper;
import com.seibel.distanthorizons.common.wrappers.world.ServerLevelWrapper;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jetbrains.annotations.Nullable;

/** CPU compatibility admission; Rust owns all sampling and hash arithmetic. */
public final class NativeDhChunkHash {
    private static MethodHandle handle(String name, FunctionDescriptor signature) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_hash_" + name, signature);
    }
    private static final MethodHandle CREATE = handle("domain_create", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle REGISTER = handle("domain_register", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
        ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle RELEASE = handle("domain_release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle SAMPLE = handle("sample", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle FINISH = handle("finish", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final Class<?> HEIGHT_ACCESSOR = LevelHeightAccessor.create(0, 16).getClass();
    private static final class Scratch {
        final Arena arena = Arena.ofAuto();
        MemorySegment positions = arena.allocate(64 * 12, 4);
        MemorySegment text = arena.allocate(512, 2);
        MemorySegment text(String value) {
            if (text.byteSize() < value.length() * 2L) text = arena.allocate(2L * Integer.highestOneBit(value.length() - 1) * 2, 2);
            for (int i = 0; i < value.length(); i++) text.setAtIndex(ValueLayout.JAVA_CHAR, i, value.charAt(i));
            return text;
        }
        MemorySegment positions(int count) {
            if (positions.byteSize() < count * 12L) positions = arena.allocate(2L * Integer.highestOneBit(count - 1) * 12, 4);
            return positions;
        }
    }
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);
    /** One registry-bound identity table; no chunk, level or world is retained. */
    static final class Domain {
        final MemorySegment owner;
        final AtomicReferenceArray<BlockStateWrapper> states;
        final AtomicReferenceArray<BiomeWrapper> biomes;
        Domain(int biomeCount) {
            states = new AtomicReferenceArray<>(Block.BLOCK_STATE_REGISTRY.size());
            biomes = new AtomicReferenceArray<>(biomeCount);
            MemorySegment pointer;
            try { pointer = (MemorySegment)CREATE.invokeExact(states.length(), biomes.length()); }
            catch (Throwable e) { throw failure(e); }
            if (pointer.address() == 0) throw new IllegalStateException("Cannot create DH hash identity domain");
            try { owner = pointer.reinterpret(1, Arena.ofAuto(), NativeDhChunkHash::release); }
            catch (Throwable e) { release(pointer); throw e; }
        }
        void state(int id, BlockStateWrapper wrapper) {
            if (states.get(id) == wrapper) return;
            synchronized (this) { if (states.get(id) != wrapper) { register(false, id, wrapper.getSerialString()); states.set(id, wrapper); } }
        }
        void biome(int id, BiomeWrapper wrapper) {
            if (biomes.get(id) == wrapper) return;
            synchronized (this) { if (biomes.get(id) != wrapper) { register(true, id, wrapper.getSerialString()); biomes.set(id, wrapper); } }
        }
        private void register(boolean biome, int id, String text) {
            var scratch = SCRATCH.get();
            try {
                if ((int)REGISTER.invokeExact(owner, biome ? 1 : 0, id, scratch.text(text), text.length()) != 1)
                    throw new IllegalStateException("Cannot register DH hash identity");
            } catch (Throwable e) { throw failure(e); }
            finally { Reference.reachabilityFence(this); }
        }
    }
    private static RuntimeException failure(Throwable e) {
        if (e instanceof RuntimeException runtime) return runtime;
        if (e instanceof Error error) throw error;
        return new IllegalStateException("Cannot compute native DH chunk hash", e);
    }
    private static void release(MemorySegment pointer) {
        try { RELEASE.invokeExact(pointer); } catch (Throwable e) { throw failure(e); }
    }
    private static Domain domain(NativeLiveBiomeSection.Binding<?> binding) {
        var current = binding.dhHashDomain;
        if (current != null) return current;
        synchronized (binding) {
            if (binding.dhHashDomain == null) binding.dhHashDomain = new Domain(binding.values.size());
            return binding.dhHashDomain;
        }
    }
    /** High word1 admits the low32 hash; zero preserves original callbacks. */
    public static long hash(ILevelWrapper level, ChunkWrapper wrapper, NativeDhHeightmaps heights) {
        if (wrapper.getClass() != ChunkWrapper.class || heights == null || level == null) return 0;
        if (level.getClass() != ClientLevelWrapper.class && level.getClass() != ServerLevelWrapper.class
            && level.getClass() != com.seibel.distanthorizons.common.wrappers.level.ServerKeyedClientLevelWrapper.class) return 0;
        if (!(level.getWrappedMcObject() instanceof Level world)
            || world.getClass() != net.minecraft.client.multiplayer.ClientLevel.class && world.getClass() != net.minecraft.server.level.ServerLevel.class
            || world.registryAccess().lookupOrThrow(Registries.BLOCK) != BuiltInRegistries.BLOCK) return 0;
        var chunk = wrapper.getChunk();
        if (chunk.getClass() != ProtoChunk.class && chunk.getClass() != LevelChunk.class) return 0;
        var accessor = chunk.levelHeightAccessor;
        if (accessor.getClass() != HEIGHT_ACCESSOR && accessor.getClass() != net.minecraft.client.multiplayer.ClientLevel.class
            && accessor.getClass() != net.minecraft.server.level.ServerLevel.class) return 0;
        if (chunk instanceof ProtoChunk && !chunk.getPersistedStatus().isOrAfter(ChunkStatus.BIOMES)) return 0;
        var inputs = chunk.nativeDhColumnSections(); if (inputs == null) return 0;
        var binding = inputs.biome(0).binding;
        var registry = world.registryAccess().lookupOrThrow(Registries.BIOME);
        if (binding.values.size() != registry.size()) return 0;
        for (int id = 0; id < binding.values.size(); id++) if (registry.getId(binding.values.get(id).value()) != id) return 0;
        for (int s = 0; s < inputs.size(); s++) if (inputs.biome(s).binding != binding) return 0;
        var domain = domain(binding);
        try {
            // Only immutable serialized identities are transferred, once per
            // wrapper binding. Public wrapper replacements are checked each time.
            domain.state(0, BlockStateWrapper.AIR);
            for (int id = 0; id < binding.values.size(); id++) {
                var holder = binding.values.get(id);
                var biome = BiomeWrapper.getBiomeWrapper(holder, level);
                if (biome.getClass() != BiomeWrapper.class || biome.biome != holder) return 0;
                domain.biome(id, biome);
            }
            for (int s = 0; s < inputs.size(); s++) {
                if (inputs.empty(s)) continue;
                var blocks = inputs.block(s);
                for (int p = 0, size = blocks.paletteSize(); p < size; p++) {
                    var state = blocks.paletteValue(p); int id = Block.getId(state);
                    if (id < 0 || Block.stateById(id) != state) return 0;
                    var block = BlockStateWrapper.fromBlockState(state, level);
                    if (block.getClass() != BlockStateWrapper.class || !state.isAir() && block.blockState != state) return 0;
                    domain.state(id, block);
                }
            }
            long sampled = (long)SAMPLE.invokeExact(domain.owner, inputs.stageOwner(), heights.stageOwner());
            if (sampled >>> 32 != 1) return 0;
            // Frozen enumerates/caches emitters only after sampled and surface
            // reads. No world callback or source enumeration runs under Rust locks.
            var sources = wrapper.nativeDhHashSources();
            try {
                if (sources != null) return (long)FINISH.invokeExact(domain.owner, inputs.stageOwner(), sources.stageOwner(), MemorySegment.NULL, 0, (int)sampled);
                var positions = wrapper.getWorldBlockLightPosList();
                if (positions.size() > 1_048_576) return 0;
                var scratch = SCRATCH.get(); var span = scratch.positions(positions.size());
                for (int i = 0; i < positions.size(); i++) {
                    DhBlockPos position = positions.get(i);
                    if (position == null || position.getClass() != DhBlockPos.class) return 0;
                    span.setAtIndex(ValueLayout.JAVA_INT, i * 3L, position.getX() & 15);
                    span.setAtIndex(ValueLayout.JAVA_INT, i * 3L + 1, position.getY());
                    span.setAtIndex(ValueLayout.JAVA_INT, i * 3L + 2, position.getZ() & 15);
                }
                return (long)FINISH.invokeExact(domain.owner, inputs.stageOwner(), MemorySegment.NULL, span, positions.size(), (int)sampled);
            } finally { Reference.reachabilityFence(sources); }
        } catch (Throwable e) { throw failure(e); }
        finally { Reference.reachabilityFence(inputs); Reference.reachabilityFence(domain); Reference.reachabilityFence(heights); Reference.reachabilityFence(wrapper); }
    }
}
