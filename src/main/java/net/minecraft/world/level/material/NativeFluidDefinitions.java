package net.minecraft.world.level.material;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;

/** CPU compatibility views of Rust's process-lifetime fluid registry.
 * Definitions never come from Java; hot getters use the projected immutable
 * rows without FFM calls. World-dependent gameplay remains in Java adapters. */
final class NativeFluidDefinitions {
    private static final MethodHandle BUFFER = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_fluid_definitions_buffer",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private static final List<Definition> DEFINITIONS = load();

    record Traits(int amount, boolean source, float ownHeight, int legacyLevel) {}
    record Definition(int id, String name, int family, boolean source, List<Property<?>> properties,
                      int firstState, int defaultLocalState, float explosionResistance, List<Traits> states) {}

    static List<Definition> definitions() { return DEFINITIONS; }

    private static MemorySegment buffer(int kind, int expected, int elementBytes, Arena input) throws Throwable {
        MemorySegment length = input.allocate(ValueLayout.JAVA_INT);
        MemorySegment pointer = (MemorySegment) BUFFER.invokeExact(kind, length);
        if (pointer.address() == 0 || length.get(ValueLayout.JAVA_INT, 0) != expected) {
            throw new IllegalStateException("Native fluid buffer mismatch: " + kind);
        }
        return pointer.reinterpret((long) expected * elementBytes, Arena.global(), null).asReadOnly();
    }

    private static int word(MemorySegment table, int index) { return table.getAtIndex(ValueLayout.JAVA_INT, index); }

    private static List<Definition> load() {
        try (Arena inputs = Arena.ofConfined()) {
            MemorySegment header = buffer(0, 7, Integer.BYTES, inputs);
            int count = word(header, 1), stateCount = word(header, 2), propertyCount = word(header, 5), nameBytes = word(header, 6);
            if (word(header, 0) != 1 || word(header, 3) != 10 || word(header, 4) != 4
                || count <= 0 || count > 65535 || stateCount <= 0 || stateCount > 65535
                || propertyCount < 0 || propertyCount > 65535 || nameBytes <= 0 || nameBytes > 1048576) {
                throw new IllegalStateException("Unsupported native fluid registry schema");
            }
            MemorySegment rows = buffer(1, count * 10, Integer.BYTES, inputs);
            MemorySegment properties = buffer(2, propertyCount, Integer.BYTES, inputs);
            MemorySegment traits = buffer(3, stateCount * 4, Integer.BYTES, inputs);
            MemorySegment names = buffer(4, nameBytes, 1, inputs);
            List<Definition> result = new ArrayList<>(count);
            int nextState = 0;
            for (int id = 0; id < count; id++) {
                int base = id * 10;
                int family = word(rows, base + 2), source = word(rows, base + 3);
                int firstProperty = word(rows, base + 4), numProperties = word(rows, base + 5);
                int firstState = word(rows, base + 6), numStates = word(rows, base + 7), initial = word(rows, base + 8);
                if (family < 0 || family > 2 || source < 0 || source > 1 || firstProperty < 0 || numProperties < 0
                    || (long) firstProperty + numProperties > propertyCount || firstState != nextState || numStates <= 0
                    || (long) firstState + numStates > stateCount || initial < 0 || initial >= numStates) {
                    throw new IllegalStateException("Invalid native fluid definition: " + id);
                }
                List<Property<?>> projected = new ArrayList<>(numProperties);
                int projectedStates = 1;
                for (int p = 0; p < numProperties; p++) {
                    Property<?> property = switch (word(properties, firstProperty + p)) {
                        case 1 -> BlockStateProperties.FALLING;
                        case 2 -> BlockStateProperties.LEVEL_FLOWING;
                        default -> throw new IllegalStateException("Unsupported native fluid property");
                    };
                    projected.add(property);
                    projectedStates = Math.multiplyExact(projectedStates, property.getPossibleValues().size());
                }
                if (projectedStates != numStates) throw new IllegalStateException("Fluid property projection changed");
                List<Traits> stateViews = new ArrayList<>(numStates);
                for (int state = firstState; state < firstState + numStates; state++) {
                    int t = state * 4;
                    int amount = word(traits, t), isSource = word(traits, t + 1), legacy = word(traits, t + 3);
                    float height = Float.intBitsToFloat(word(traits, t + 2));
                    if (amount < 0 || amount > 8 || isSource != source || !Float.isFinite(height)
                        || height < 0 || height > 1 || legacy < 0 || legacy > 15) {
                        throw new IllegalStateException("Invalid native fluid state: " + state);
                    }
                    stateViews.add(new Traits(amount, isSource != 0, height, legacy));
                }
                String name = new String(names.asSlice(word(rows, base), word(rows, base + 1)).toArray(ValueLayout.JAVA_BYTE), StandardCharsets.UTF_8);
                result.add(new Definition(id, name, family, source != 0, List.copyOf(projected), firstState, initial,
                    Float.intBitsToFloat(word(rows, base + 9)), List.copyOf(stateViews)));
                nextState += numStates;
            }
            if (nextState != stateCount) throw new IllegalStateException("Incomplete native fluid states");
            return List.copyOf(result);
        } catch (RuntimeException | Error error) {
            throw error;
        } catch (Throwable error) {
            throw new IllegalStateException("Cannot load Rust fluid definitions", error);
        }
    }
}
