package net.minecraft.world.level.block.state;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import net.minecraft.util.NativeLibraryLoader;

/** Immutable native state graph. Java projects its rows onto temporary state
 * objects; enumeration and transition policy live in {@code content/state}.
 * The automatic arena releases the native owner after all its views die. */
final class NativeStateGraph {
    private static final MethodHandle CREATE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_state_graph_create",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private static final MethodHandle BUFFER = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_state_graph_buffer",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle RELEASE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_state_graph_release",
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

    private final MemorySegment owner;
    private final MemorySegment values;
    private final MemorySegment targets;
    private final MemorySegment offsets;
    final int stateCount;
    private final int properties;
    private final int width;

    NativeStateGraph(int[] counts) {
        try (Arena inputs = Arena.ofConfined()) {
            MemorySegment header = inputs.allocate(5L * Integer.BYTES, Integer.BYTES);
            MemorySegment raw = (MemorySegment) CREATE.invokeExact(inputs.allocateFrom(ValueLayout.JAVA_INT, counts), counts.length, header);
            if (raw.address() == 0) throw new IllegalArgumentException("Invalid or oversized native state graph");
            Arena lifetime = Arena.ofAuto();
            this.owner = raw.reinterpret(1, lifetime, NativeStateGraph::release);
            this.stateCount = header.getAtIndex(ValueLayout.JAVA_INT, 0);
            this.properties = header.getAtIndex(ValueLayout.JAVA_INT, 1);
            this.width = header.getAtIndex(ValueLayout.JAVA_INT, 2);
            this.values = buffer(0, header.getAtIndex(ValueLayout.JAVA_INT, 3), lifetime);
            this.targets = buffer(1, header.getAtIndex(ValueLayout.JAVA_INT, 4), lifetime);
            this.offsets = buffer(2, this.properties, lifetime);
        } catch (RuntimeException | Error error) {
            throw error;
        } catch (Throwable error) {
            throw new IllegalStateException("Native state graph construction", error);
        }
    }

    private MemorySegment buffer(int kind, int count, Arena lifetime) throws Throwable {
        if (count == 0) return MemorySegment.NULL;
        return ((MemorySegment) BUFFER.invokeExact(this.owner, kind)).reinterpret((long) count * Integer.BYTES, lifetime, null);
    }

    int value(int state, int property) {
        return this.values.getAtIndex(ValueLayout.JAVA_INT, (long) state * this.properties + property);
    }

    int target(int state, int property, int value) {
        int offset = this.offsets.getAtIndex(ValueLayout.JAVA_INT, property);
        return this.targets.getAtIndex(ValueLayout.JAVA_INT, (long) state * this.width + offset + value);
    }

    private static void release(MemorySegment owner) {
        try {
            RELEASE.invokeExact(owner);
        } catch (Throwable error) {
            throw new IllegalStateException("Native state graph release", error);
        }
    }
}
