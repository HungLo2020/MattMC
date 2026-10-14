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
    private static final Class<?> FIXED_HEIGHT = net.minecraft.world.level.LevelHeightAccessor.create(0, 16).getClass();
    private static boolean retainedAdmission(ChunkAccess chunk) {
        if (chunk.getClass() != ProtoChunk.class && chunk.getClass() != LevelChunk.class) return false;
        // Binding admission reads height earlier than the legacy snapshot call.
        // Custom height providers keep their original ordered callback sequence.
        var height = chunk.levelHeightAccessor;
        return height != null && (height.getClass() == FIXED_HEIGHT
            || height.getClass() == net.minecraft.server.level.ServerLevel.class
            || height.getClass() == net.minecraft.client.multiplayer.ClientLevel.class);
    }
    private static final MethodHandle CREATE_PROTO = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_proto_chunk_create_live_counters",
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle CREATE_RETAINED = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_proto_chunk_create_retained",
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
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
        var sections = chunk.getSectionsForRead();
        if (sections.length == 0 || sections.length > 256) return -1;
        // One retained CPU owner supplies all block/counter bindings to Rust.
        // Admission rereads replaced palettes; no per-stage pointer/pin arrays.
        if (retainedAdmission(chunk)) {
            var inputs = chunk.nativeDhSections();
            if (inputs != null) {
                try (var arena = Arena.ofConfined()) {
                    var a = arena.allocateFrom(ValueLayout.JAVA_LONG, surface);
                    var b = arena.allocateFrom(ValueLayout.JAVA_LONG, floor);
                    return (long)CREATE_RETAINED.invokeExact(inputs.stageOwner(), sections[0].generatedGlobalPaletteBits(), a, b, surface.length);
                } catch (RuntimeException | Error failure) { throw failure; }
                catch (Throwable failure) { throw new IllegalStateException("Cannot capture retained generation inputs", failure); }
                finally { Reference.reachabilityFence(inputs); Reference.reachabilityFence(chunk); }
            }
        }
        // Pin exactly the borrowed owners/views, even if a caller later replaces an array entry.
        var owners = new Object[sections.length * 2];
        try (var arena = Arena.ofConfined()) {
            var pointers = arena.allocate(sections.length * 8L, 8);
            var counters = arena.allocate(sections.length * 8L, 8);
            for (int i = 0; i < sections.length; i++) {
                var section = sections[i];
                var owner = section == null ? null : section.nativeGenerationInput();
                if (owner == null) return -1;
                var counterView = section.nativeGenerationCounters();
                owners[i * 2] = owner;
                owners[i * 2 + 1] = counterView;
                pointers.setAtIndex(ValueLayout.ADDRESS, i, owner.stageOwner());
                counters.setAtIndex(ValueLayout.ADDRESS, i, counterView);
            }
            var a = arena.allocateFrom(ValueLayout.JAVA_LONG, surface);
            var b = arena.allocateFrom(ValueLayout.JAVA_LONG, floor);
            return (long)CREATE_PROTO.invokeExact(pointers, counters, sections.length, chunk.getMinY(), chunk.getHeight(),
                    sections[0].generatedGlobalPaletteBits(), a, b, surface.length);
        } catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new IllegalStateException("Cannot capture native generation sections", failure); }
        finally { Reference.reachabilityFence(owners); Reference.reachabilityFence(chunk); Reference.reachabilityFence(sections); }
    }

    public static int installNoise(LevelChunkSection section, long handle, int index) {
        return install(NOISE, section, handle, index);
    }
    public static int installProto(LevelChunkSection section, long handle, int index) {
        return install(PROTO, section, handle, index);
    }

    /**
     * A section's stage result prepared for publication without touching the
     * chunk: an adopted native owner with its counters, or an exported palette.
     * {@link #commit} installs it.
     */
    public static final class Prepared {
        final LevelChunkSection section;
        final NativeLiveBlockSection owner;
        final int requestedBits, nonEmpty, ticking, fluid;
        final java.util.List<net.minecraft.world.level.block.state.BlockState> palette;
        final long[] raw;

        private Prepared(LevelChunkSection section, NativeLiveBlockSection owner, int requestedBits, java.util.List<net.minecraft.world.level.block.state.BlockState> palette,
                         long[] raw, int nonEmpty, int ticking, int fluid) {
            this.section = section;
            this.owner = owner;
            this.requestedBits = requestedBits;
            this.palette = palette;
            this.raw = raw;
            this.nonEmpty = nonEmpty;
            this.ticking = ticking;
            this.fluid = fluid;
        }

        /** An exported (compatibility) result for {@code section}. */
        public static Prepared exported(LevelChunkSection section, int requestedBits, java.util.List<net.minecraft.world.level.block.state.BlockState> palette, long[] raw,
                                        int nonEmpty, int ticking, int fluid) {
            return new Prepared(section, null, requestedBits, palette, raw, nonEmpty, ticking, fluid);
        }

        /** Publishes the result; nothing here calls native code or can fail on its inputs. */
        public void commit() {
            if (this.owner != null) this.section.installNativeGenerated(this.owner, this.nonEmpty, this.ticking, this.fluid);
            else this.section.installGenerated(this.requestedBits, this.palette, this.raw, this.nonEmpty, this.ticking, this.fluid);
        }
    }

    /** The prepared native result (null untouched), or {@link #COMPATIBILITY} when the original export is needed. */
    public static final Prepared COMPATIBILITY = new Prepared(null, null, 0, null, null, 0, 0, 0);

    public static Prepared prepareNoise(LevelChunkSection section, long handle, int index) {
        return prepare(NOISE, section, handle, index);
    }

    public static Prepared prepareProto(LevelChunkSection section, long handle, int index) {
        return prepare(PROTO, section, handle, index);
    }

    private static Prepared prepare(MethodHandle operation, LevelChunkSection section, long handle, int index) {
        if (section.getClass() != LevelChunkSection.class) return COMPATIBILITY;
        var states = section.getStates();
        if (states.getClass() != PalettedContainer.class || states.nativeLiveBlocks() == null) return COMPATIBILITY;
        var result = RESULT.get();
        try {
            int status = (int)operation.invokeExact(handle, index, Block.BLOCK_STATE_REGISTRY.size(),
                    section.generatedGlobalPaletteBits(), result);
            if (status == 0) return null;
            if (status == 2) return COMPATIBILITY;
            if (status != 1) throw new IllegalStateException("Invalid native generation result: " + status);
            // Adopted now (its cleaner frees it if publication never happens), installed at commit.
            var owner = NativeLiveBlockSection.adoptOwned(result.getAtIndex(ValueLayout.ADDRESS, 0));
            return new Prepared(section, owner, 0, null, null, result.getAtIndex(ValueLayout.JAVA_INT, 2),
                    result.getAtIndex(ValueLayout.JAVA_INT, 3), result.getAtIndex(ValueLayout.JAVA_INT, 4));
        } catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new IllegalStateException("Cannot prepare native generation section", failure); }
    }

    /** Verification only: {@code -Dmattmc.worldgen.stagePublishFailAfter=N} fails the Nth prepared section. */
    private static final java.util.concurrent.atomic.AtomicLong PUBLISH_FAIL = new java.util.concurrent.atomic.AtomicLong(
            Long.getLong("mattmc.worldgen.stagePublishFailAfter", -1));

    /** Counts one prepared result; throws at the injected publication failure. */
    public static void preparedHook() {
        if (PUBLISH_FAIL.get() >= 0 && PUBLISH_FAIL.getAndDecrement() == 1) throw new IllegalStateException("Injected stage publication failure");
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
