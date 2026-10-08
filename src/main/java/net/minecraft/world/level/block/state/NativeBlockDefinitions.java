package net.minecraft.world.level.block.state;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.NativePropertyDefinitions;
import net.minecraft.world.level.block.state.properties.Property;

/** Compatibility views of Rust's ordered built-in block definitions. Metadata
 * and graph buffers are borrowed immutable CPU data with process lifetime. */
public final class NativeBlockDefinitions {
    private static final MethodHandle BUFFER = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_block_definitions_buffer",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private static final Map<String, Definition> DEFINITIONS = load();

    public static final class Definition {
        public final int id;
        public final String name;
        public final int firstState;
        private final Template template;
        private Definition(int id, String name, int firstState, Template template) {
            this.id = id; this.name = name; this.firstState = firstState; this.template = template;
        }
        public int stateCount() { return this.template.graph.stateCount; }
        public int defaultLocalState() { return this.template.defaultLocal; }
        public StateDefinition<Block, BlockState> createStates(Block owner) {
            var builder = new StateDefinition.Builder<Block, BlockState>(owner);
            builder.add(this.template.properties().toArray(Property<?>[]::new));
            return builder.createWithGraph(Block::defaultBlockState, BlockState::new, this.template.graph);
        }
    }

    private static final class Template {
        private final int[] propertyIds;
        private final int defaultLocal;
        private final NativeStateGraph graph;
        private List<Property<?>> propertyViews;
        private Template(int[] propertyIds, int defaultLocal, NativeStateGraph graph) {
            this.propertyIds = propertyIds; this.defaultLocal = defaultLocal; this.graph = graph;
        }
        private synchronized List<Property<?>> properties() {
            if (this.propertyViews == null) {
                List<Property<?>> views = new ArrayList<>(this.propertyIds.length);
                for (int id : this.propertyIds) views.add(NativePropertyDefinitions.view(id));
                this.graph.verifyProperties(views);
                this.propertyViews = List.copyOf(views);
            }
            return this.propertyViews;
        }
    }

    public static Definition require(String name) {
        Definition definition = DEFINITIONS.get(name);
        if (definition == null) throw new IllegalStateException("Missing native block definition: " + name);
        return definition;
    }
    public static int size() { return DEFINITIONS.size(); }

    private static MemorySegment buffer(int kind, int expected, int bytes, Arena inputs) throws Throwable {
        MemorySegment length = inputs.allocate(ValueLayout.JAVA_INT);
        MemorySegment pointer = (MemorySegment) BUFFER.invokeExact(kind, length);
        if (pointer.address() == 0 || length.get(ValueLayout.JAVA_INT, 0) != expected) throw new IllegalStateException("Native block buffer mismatch: " + kind);
        return pointer.reinterpret((long) expected * bytes, Arena.global(), null).asReadOnly();
    }
    private static int word(MemorySegment table, int index) { return table.getAtIndex(ValueLayout.JAVA_INT, index); }

    private static Map<String, Definition> load() {
        try (Arena inputs = Arena.ofConfined()) {
            MemorySegment header = buffer(0, 7, Integer.BYTES, inputs);
            int count = word(header, 1), states = word(header, 2), templates = word(header, 3);
            int properties = word(header, 4), nameBytes = word(header, 5), graphs = word(header, 6);
            if (word(header, 0) != 1 || count <= 0 || count > 65535 || states <= 0 || states > 65535
                || templates <= 0 || templates > count || properties < 0 || properties > 1048576
                || nameBytes <= 0 || nameBytes > 16777216 || graphs <= 0 || graphs > templates) {
                throw new IllegalStateException("Unsupported native block schema");
            }
            MemorySegment rows = buffer(1, count * 4, Integer.BYTES, inputs);
            MemorySegment templateRows = buffer(2, templates * 4, Integer.BYTES, inputs);
            MemorySegment propertyIds = buffer(3, properties, Integer.BYTES, inputs);
            MemorySegment names = buffer(4, nameBytes, 1, inputs);
            NativeStateGraph[] graphViews = new NativeStateGraph[graphs];
            for (int id = 0; id < graphs; id++) graphViews[id] = NativeStateGraph.borrowBlockGraph(id);
            Template[] views = new Template[templates];
            int nextProperty = 0;
            for (int id = 0; id < templates; id++) {
                int base = id * 4, first = word(templateRows, base), size = word(templateRows, base + 1);
                int initial = word(templateRows, base + 2), graph = word(templateRows, base + 3);
                if (first != nextProperty || size < 0 || (long) first + size > properties || graph < 0 || graph >= graphs
                    || initial < 0 || initial >= graphViews[graph].stateCount) throw new IllegalStateException("Invalid native state template: " + id);
                int[] ids = new int[size];
                for (int i = 0; i < size; i++) {
                    ids[i] = word(propertyIds, first + i);
                    if (ids[i] < 0 || ids[i] > 65535) throw new IllegalStateException("Invalid native property identity");
                }
                views[id] = new Template(ids, initial, graphViews[graph]);
                nextProperty += size;
            }
            if (nextProperty != properties) throw new IllegalStateException("Incomplete native block properties");
            Map<String, Definition> result = new HashMap<>();
            int nextState = 0;
            for (int id = 0; id < count; id++) {
                int base = id * 4, start = word(rows, base), length = word(rows, base + 1);
                int firstState = word(rows, base + 2), template = word(rows, base + 3);
                if (start < 0 || length <= 0 || (long) start + length > nameBytes || firstState != nextState
                    || template < 0 || template >= templates) throw new IllegalStateException("Invalid native block row: " + id);
                Template t = views[template];
                if ((long) nextState + t.graph.stateCount > states) throw new IllegalStateException("Invalid native block range");
                String name = new String(names.asSlice(start, length).toArray(ValueLayout.JAVA_BYTE), StandardCharsets.UTF_8);
                if (result.put(name, new Definition(id, name, firstState, t)) != null) throw new IllegalStateException("Duplicate native block name: " + name);
                nextState += t.graph.stateCount;
            }
            if (nextState != states) throw new IllegalStateException("Incomplete native block states");
            return Map.copyOf(result);
        } catch (RuntimeException | Error error) {
            throw error;
        } catch (Throwable error) {
            throw new IllegalStateException("Cannot load native block definitions", error);
        }
    }
}
