package net.minecraft.world.level.lighting;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.util.NativeLibraryLoader;
import org.slf4j.Logger;

/** Actual VM capture roots; Rust owns request policy, cursors and level custody. */
final class NativeLightRequests implements LevelLightEngine.LightRequest {
    private static MethodHandle handle(String name, FunctionDescriptor descriptor) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_light_request_" + name, descriptor);
    }

    private static final MethodHandle CREATE = handle("create", FunctionDescriptor.of(
        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
    private static final MethodHandle RELEASE = handle("release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle BEGIN = handle("begin", FunctionDescriptor.of(
        ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle READ = handle("read", FunctionDescriptor.of(
        ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
    private static final MethodHandle STEP = handle("step", FunctionDescriptor.of(
        ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle RESOURCE = handle("resource", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
    private static final MethodHandle IMPORT = handle("import", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
    private static final MethodHandle END = handle("end", FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));

    private final MemorySegment owner;
    private final int kind;
    final NativeLevelLighting level;
    final LevelLightEngine host;
    final ChunkAccess chunk;
    final ChunkPos position;
    final Logger logger;

    NativeLightRequests(NativeLevelLighting level, LevelLightEngine host, int kind, ChunkAccess chunk,
                        ChunkPos position, boolean value, Logger logger) {
        this.level = level;
        this.host = host;
        this.kind = kind;
        // LIGHT PRE originally captures the position/engine, never the chunk.
        // A rejected POST submission must let the chunk go while PRE is queued.
        this.chunk = kind == 1 ? null : chunk;
        this.position = position;
        this.logger = logger;
        try {
            MemorySegment pointer = (MemorySegment)CREATE.invokeExact(
                level.ownerForTasks(), kind, ChunkPos.asLong(position.x, position.z), value ? 1 : 0);
            owner = NativeLightingMap.adopt(pointer, 1, RELEASE);
        } catch (Throwable failure) {
            throw failed(failure);
        } finally {
            Reference.reachabilityFence(level);
            Reference.reachabilityFence(host);
        }
    }

    private static RuntimeException failed(Throwable failure) {
        if (failure instanceof Error error) throw error;
        if (failure instanceof RuntimeException error) return error;
        return new IllegalStateException("Native lighting request failed", failure);
    }

    public void pre() { run(false); }

    public Runnable postTask(ChunkAccess chunk) {
        if (kind == 1) {
            // Keep only the actual native owner and original POST chunk capture.
            // In particular this lambda must not capture this Java PRE context.
            MemorySegment capturedOwner = owner;
            return () -> completeLight(capturedOwner, chunk);
        }
        return () -> run(true);
    }

    private static void completeLight(MemorySegment owner, ChunkAccess chunk) {
        long frame = 0;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment output = arena.allocate(7L * Long.BYTES, Long.BYTES);
            frame = (long)BEGIN.invokeExact(owner, 1);
            if (frame == 0) throw new IllegalStateException("Invalid native light request phase");
            int action = read(frame, output);
            for (;;) {
                NativeLightCoordinator.checkEffectStatus(action, output);
                if (action == 0) return;
                if (action != 39) throw new IllegalStateException("Unknown native light completion effect " + action);
                chunk.setLightCorrect(output.getAtIndex(ValueLayout.JAVA_LONG, 0) != 0);
                action = step(frame, 0, MemorySegment.NULL, output);
            }
        } catch (Throwable failure) {
            throw failed(failure);
        } finally {
            try {
                if (frame != 0) END.invokeExact(frame);
            } catch (Throwable failure) {
                throw failed(failure);
            } finally {
                Reference.reachabilityFence(owner);
                Reference.reachabilityFence(chunk);
            }
        }
    }

    private void run(boolean post) {
        long frame = 0;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment output = arena.allocate(7L * Long.BYTES, Long.BYTES);
            frame = (long)BEGIN.invokeExact(owner, post ? 1 : 0);
            if (frame == 0) throw new IllegalStateException("Invalid native light request phase");
            level.resumeRequest(frame, this, output);
        } catch (Throwable failure) {
            throw failed(failure);
        } finally {
            try {
                if (frame != 0) END.invokeExact(frame);
            } catch (Throwable failure) {
                throw failed(failure);
            } finally {
                level.drainRequestResources();
                Reference.reachabilityFence(this);
            }
        }
    }

    static int read(long frame, MemorySegment output) throws Throwable {
        return (int)READ.invokeExact(frame, output);
    }

    static int step(long frame, long scalar, MemorySegment entry, MemorySegment output) throws Throwable {
        return (int)STEP.invokeExact(frame, scalar, entry, output);
    }

    static MemorySegment resource(long frame) throws Throwable {
        return (MemorySegment)RESOURCE.invokeExact(frame);
    }

    static int applyImport(long frame) throws Throwable {
        return (int)IMPORT.invokeExact(frame);
    }
}
