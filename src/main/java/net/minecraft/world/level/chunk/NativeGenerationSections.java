package net.minecraft.world.level.chunk;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.block.Block;

/** CPU-only native stage/live-storage handoff. No palette/word Java mirror. */
public final class NativeGenerationSections {
    private NativeGenerationSections() {}
    private static final MethodHandle CREATE_PROTO = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_proto_chunk_create_live",
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static MethodHandle result(String name) {
        // These ordinary calls may allocate/lock; never use critical heap access.
        return NativeLibraryLoader.downcallHandle("mattmc_rust", name,
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    }
    private static final MethodHandle NOISE = result("mattmc_noise_fill_section_live");
    private static final MethodHandle PROTO = result("mattmc_proto_chunk_section_live");
    private static final ThreadLocal<MemorySegment> RESULT = ThreadLocal.withInitial(() -> Arena.ofAuto().allocate(24, 8));

    /** -1 means compatibility storage; 0 rejected input; otherwise a new stage
     * handle. Rust copies packed inputs under each owner's lock and retains none
     * of these pointers. The caller releases the stage with its existing API. */
    public static long createProto(ChunkAccess chunk, long[] surface, long[] floor) {
        if (surface.length != floor.length || surface.length == 0) return 0;
        var sections = chunk.getSections();
        if (sections.length == 0 || sections.length > 256) return -1;
        var owners = new NativeLiveBlockSection[sections.length];
        try (var arena = Arena.ofConfined()) {
            var pointers = arena.allocate(sections.length * 8L, 8);
            var counters = arena.allocate(sections.length * 12L, 4);
            for (int i = 0; i < sections.length; i++) {
                var section = sections[i];
                var owner = section == null ? null : section.nativeGenerationInput(counters, i);
                if (owner == null) return -1;
                owners[i] = owner;
                pointers.setAtIndex(ValueLayout.ADDRESS, i, owner.stageOwner());
            }
            var a = arena.allocateFrom(ValueLayout.JAVA_LONG, surface);
            var b = arena.allocateFrom(ValueLayout.JAVA_LONG, floor);
            return (long)CREATE_PROTO.invokeExact(pointers, counters, sections.length, chunk.getMinY(), chunk.getHeight(),
                    sections[0].generatedGlobalPaletteBits(), a, b, surface.length);
        } catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new IllegalStateException("Cannot capture native generation sections", failure); }
        finally { Reference.reachabilityFence(owners); }
    }

    public static int installNoise(LevelChunkSection section, long handle, int index) {
        return install(NOISE, section, handle, index);
    }
    public static int installProto(LevelChunkSection section, long handle, int index) {
        return install(PROTO, section, handle, index);
    }

    /** 0 untouched, 1 installed, 2 requires the original compatibility export. */
    private static int install(MethodHandle operation, LevelChunkSection section, long handle, int index) {
        if (section.getClass() != LevelChunkSection.class) return 2;
        var states = section.getStates();
        if (states.getClass() != PalettedContainer.class || states.nativeLiveBlocks() == null) return 2;
        var result = RESULT.get();
        try {
            int status = (int)operation.invokeExact(handle, index, Block.BLOCK_STATE_REGISTRY.size(),
                    section.generatedGlobalPaletteBits(), result);
            if (status == 0 || status == 2) return status;
            if (status != 1) throw new IllegalStateException("Invalid native generation result: " + status);
            // Adopt the unique returned allocation before publishing any Java state.
            var owner = NativeLiveBlockSection.adoptOwned(result.getAtIndex(ValueLayout.ADDRESS, 0));
            section.installNativeGenerated(owner, result.getAtIndex(ValueLayout.JAVA_INT, 2),
                    result.getAtIndex(ValueLayout.JAVA_INT, 3), result.getAtIndex(ValueLayout.JAVA_INT, 4));
            return 1;
        } catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new IllegalStateException("Cannot install native generation section", failure); }
    }
}
