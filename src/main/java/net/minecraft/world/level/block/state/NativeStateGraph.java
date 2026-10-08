package net.minecraft.world.level.block.state;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import net.minecraft.util.NativeLibraryLoader;

/** Immutable native state graph. Java projects its rows onto temporary state
 * objects; enumeration and transition policy live in {@code content/state}.
 * Dynamic graphs use an automatic arena; built-in block graphs borrow their
 * immutable process-lifetime owner and are never released by Java. */
final class NativeStateGraph {
    private static final MethodHandle CREATE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_state_graph_create",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private static final MethodHandle BUFFER = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_state_graph_buffer",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle RELEASE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_state_graph_release",
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

    private static final MethodHandle BLOCK_GRAPH_BUFFER = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_block_definition_graph_buffer",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    private final MemorySegment owner;
    private final MemorySegment values;
    private final MemorySegment targets;
    private final MemorySegment offsets;
    final int stateCount;
    private final int properties;
    private final int width;

    NativeStateGraph(int[] counts) {
        Arena lifetime = Arena.ofAuto();
        try (Arena inputs = Arena.ofConfined()) {
            MemorySegment header = inputs.allocate(5L * Integer.BYTES, Integer.BYTES);
            MemorySegment raw = (MemorySegment) CREATE.invokeExact(inputs.allocateFrom(ValueLayout.JAVA_INT, counts), counts.length, header);
            if (raw.address() == 0) throw new IllegalArgumentException("Invalid or oversized native state graph");
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

    /** Borrows a bounded graph owned by the native built-in block registry. */
    private NativeStateGraph(int graphId) {
        try (Arena inputs = Arena.ofConfined()) {
            MemorySegment header = blockBuffer(graphId, 0, 5, inputs);
            this.owner = MemorySegment.NULL;
            this.stateCount = header.getAtIndex(ValueLayout.JAVA_INT, 0);
            this.properties = header.getAtIndex(ValueLayout.JAVA_INT, 1);
            this.width = header.getAtIndex(ValueLayout.JAVA_INT, 2);
            int values = header.getAtIndex(ValueLayout.JAVA_INT, 3), targets = header.getAtIndex(ValueLayout.JAVA_INT, 4);
            if (this.stateCount <= 0 || this.stateCount > 65535 || this.properties < 0 || this.properties > 65535 || this.width < 0
                || values < 0 || values > 16777216 || targets < 0 || targets > 16777216
                || (long) this.stateCount * this.properties != values || (long) this.stateCount * this.width != targets) {
                throw new IllegalStateException("Invalid shared native graph: " + graphId);
            }
            this.values = blockBuffer(graphId, 1, values, inputs);
            this.targets = blockBuffer(graphId, 2, targets, inputs);
            this.offsets = blockBuffer(graphId, 3, this.properties, inputs);
        } catch (RuntimeException | Error error) {
            throw error;
        } catch (Throwable error) {
            throw new IllegalStateException("Cannot borrow native block graph", error);
        }
    }

    static NativeStateGraph borrowBlockGraph(int graphId) { return new NativeStateGraph(graphId); }

    private static MemorySegment blockBuffer(int id, int kind, int expected, Arena inputs) throws Throwable {
        MemorySegment length = inputs.allocate(ValueLayout.JAVA_INT);
        MemorySegment pointer = (MemorySegment) BLOCK_GRAPH_BUFFER.invokeExact(id, kind, length);
        if (pointer.address() == 0 || length.get(ValueLayout.JAVA_INT, 0) != expected) throw new IllegalStateException("Native block graph buffer mismatch");
        return pointer.reinterpret((long) expected * Integer.BYTES, Arena.global(), null).asReadOnly();
    }

    private MemorySegment buffer(int kind, int count, Arena lifetime) throws Throwable {
        if (count == 0) return MemorySegment.NULL;
        return ((MemorySegment) BUFFER.invokeExact(this.owner, kind)).reinterpret((long) count * Integer.BYTES, lifetime, null);
    }

    void verifyProperties(java.util.List<net.minecraft.world.level.block.state.properties.Property<?>> views) {
        if (views.size() != this.properties) throw new IllegalStateException("Native property/graph width mismatch");
        for (int i = 0; i < views.size(); i++) {
            if (views.get(i).getPossibleValues().size() != this.value(this.stateCount - 1, i) + 1) {
                throw new IllegalStateException("Native property/graph domain mismatch");
            }
        }
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
