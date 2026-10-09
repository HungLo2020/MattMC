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
import java.util.function.Function;
import java.util.function.ToIntFunction;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.sounds.NativeSoundDefinitions;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.material.MapColor;
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
        private final Physics physics;
        private final ScalarRule mapColor, light;
        private final MemorySegment intrinsicStates, soundStates, policyStates;
        private final NativeBlockMaterials.Material material;
        private Definition(int id, String name, int firstState, Template template, Physics physics, ScalarRule mapColor, ScalarRule light, MemorySegment intrinsicStates, MemorySegment soundStates, MemorySegment policyStates, NativeBlockMaterials.Material material) {
            this.id = id; this.name = name; this.firstState = firstState; this.template = template; this.physics = physics;
            this.mapColor = mapColor; this.light = light; this.intrinsicStates = intrinsicStates;
            this.soundStates = soundStates; this.policyStates = policyStates; this.material = material;
        }
        void applyProperties(BlockBehaviour.Properties properties) {
            this.physics.apply(properties);
            this.material.apply(properties);
            properties.mapColor = this.mapColor.colorView;
            properties.lightEmission = this.light.lightView;
        }
        public int stateCount() { return this.template.graph.stateCount; }
        public int defaultLocalState() { return this.template.defaultLocal; }
        public StateDefinition<Block, BlockState> createStates(Block owner) {
            var builder = new StateDefinition.Builder<Block, BlockState>(owner);
            builder.add(this.template.properties().toArray(Property<?>[]::new));
            return builder.createWithGraph(Block::defaultBlockState,
                (block, values, codec, local) -> new BlockState(block, values, codec, word(this.intrinsicStates, this.firstState + local),
                    SoundType.nativeView(soundId(this.soundStates, this.firstState + local)), this.material.offset(), this.policyStates.get(ValueLayout.JAVA_BYTE, this.firstState + local) & 255),
                this.template.graph);
        }
    }

    /** Immutable CPU function projection for Properties copies and legacy
     * constructor APIs. Declarations and evaluation tables originate in Rust. */
    private static final class ScalarRule {
        private final int[] propertyIds, values;
        private volatile Property<?>[] propertyViews;
        private final Function<BlockState, MapColor> colorView = state -> MapColor.byId(value(state));
        private final ToIntFunction<BlockState> lightView = this::value;
        private ScalarRule(int[] propertyIds, int[] values) { this.propertyIds = propertyIds; this.values = values; }
        private Property<?>[] properties() {
            var result = this.propertyViews;
            if (result == null) synchronized (this) {
                result = this.propertyViews;
                if (result == null) {
                    result = new Property<?>[this.propertyIds.length];
                    int size = 1;
                    for (int i = 0; i < result.length; i++) {
                        result[i] = NativePropertyDefinitions.view(this.propertyIds[i]);
                        size = Math.multiplyExact(size, result[i].getPossibleValues().size());
                    }
                    if (size != this.values.length) throw new IllegalStateException("Native rule domain changed");
                    this.propertyViews = result;
                }
            }
            return result;
        }
        private int value(BlockState state) {
            if (this.propertyIds.length == 0) return this.values[0];
            int at = 0;
            for (Property<?> property : properties()) at = at * property.getPossibleValues().size() + index(state, property);
            return this.values[at];
        }
        private static <T extends Comparable<T>> int index(BlockState state, Property<T> property) {
            return property.getInternalIndex(state.getValue(property));
        }
    }

    /** One cached Java view per native physical profile; no gameplay FFM calls. */
    private record Physics(float hardness, float resistance, float friction, float speedFactor,
                           float jumpFactor, int flags, PushReaction pushReaction) {
        private boolean flag(int bit) { return (this.flags & (1 << bit)) != 0; }
        private void apply(BlockBehaviour.Properties p) {
            p.destroyTime = this.hardness;
            p.explosionResistance = this.resistance;
            p.friction = this.friction;
            p.speedFactor = this.speedFactor;
            p.jumpFactor = this.jumpFactor;
            p.hasCollision = flag(0);
            p.requiresCorrectToolForDrops = flag(1);
            p.isRandomlyTicking = flag(2);
            p.canOcclude = flag(3);
            p.isAir = flag(4);
            p.ignitedByLava = flag(5);
            p.liquid = flag(6);
            p.forceSolidOff = flag(7);
            p.forceSolidOn = flag(8);
            p.spawnTerrainParticles = flag(9);
            p.replaceable = flag(10);
            p.dynamicShape = flag(11);
            p.pushReaction = this.pushReaction;
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
    private static int soundId(MemorySegment table, int index) { return table.getAtIndex(ValueLayout.JAVA_SHORT,index) & 65535; }
    private static int word(MemorySegment table, int index) { return table.getAtIndex(ValueLayout.JAVA_INT, index); }

    private static Map<String, Definition> load() {
        try (Arena inputs = Arena.ofConfined()) {
            MemorySegment header = buffer(0, 15, Integer.BYTES, inputs);
            int count = word(header, 1), states = word(header, 2), templates = word(header, 3);
            int properties = word(header, 4), nameBytes = word(header, 5), graphs = word(header, 6);
            int physicalProfiles = word(header, 7);
            int rules = word(header, 8), ruleProperties = word(header, 9), ruleValues = word(header, 10), fluidStates = word(header, 11);
            int materials = word(header, 12), offsets = word(header, 13), offsetValues = word(header, 14);
            if (word(header, 0) != 5 || count <= 0 || count > 65535 || states <= 0 || states > 65535
                || templates <= 0 || templates > count || properties < 0 || properties > 1048576
                || nameBytes <= 0 || nameBytes > 16777216 || graphs <= 0 || graphs > templates || physicalProfiles <= 0 || physicalProfiles > count
                || rules <= 0 || rules > count * 2 || ruleProperties < 0 || ruleProperties > 1048576
                || ruleValues <= 0 || ruleValues > states * 2 || fluidStates <= 0 || fluidStates > 65535
                || materials <= 0 || materials > count || offsets <= 0 || offsets > materials || offsetValues <= 0 || offsetValues > offsets * 4096 * 3) {
                throw new IllegalStateException("Unsupported native block schema");
            }
            MemorySegment rows = buffer(1, count * 6, Integer.BYTES, inputs);
            MemorySegment templateRows = buffer(2, templates * 4, Integer.BYTES, inputs);
            MemorySegment propertyIds = buffer(3, properties, Integer.BYTES, inputs);
            MemorySegment names = buffer(4, nameBytes, 1, inputs);
            MemorySegment physicalRows = buffer(5, physicalProfiles * 7, Integer.BYTES, inputs);
            Physics[] physicalViews = new Physics[physicalProfiles];
            PushReaction[] reactions = {PushReaction.NORMAL, PushReaction.DESTROY, PushReaction.BLOCK, PushReaction.IGNORE, PushReaction.PUSH_ONLY};
            for (int id = 0; id < physicalProfiles; id++) {
                int base = id * 7, flags = word(physicalRows, base + 5), reaction = word(physicalRows, base + 6);
                float hardness = Float.intBitsToFloat(word(physicalRows, base));
                float resistance = Float.intBitsToFloat(word(physicalRows, base + 1));
                float friction = Float.intBitsToFloat(word(physicalRows, base + 2));
                float speed = Float.intBitsToFloat(word(physicalRows, base + 3));
                float jump = Float.intBitsToFloat(word(physicalRows, base + 4));
                if ((flags & ~4095) != 0 || reaction < 0 || reaction >= reactions.length
                    || !Float.isFinite(hardness) || !Float.isFinite(resistance) || resistance < 0
                    || !Float.isFinite(friction) || !Float.isFinite(speed) || !Float.isFinite(jump)) {
                    throw new IllegalStateException("Invalid native physical profile: " + id);
                }
                physicalViews[id] = new Physics(hardness, resistance, friction, speed, jump, flags, reactions[reaction]);
            }
            MemorySegment policyStates = buffer(15, states, 1, inputs);
            for (int state = 0; state < states; state++) {
                if ((policyStates.get(ValueLayout.JAVA_BYTE, state) & 255) > 15)
                    throw new IllegalStateException("Invalid native state policy");
            }
            MemorySegment intrinsicStates = buffer(6, states, Integer.BYTES, inputs);
            for (int state = 0; state < states; state++) {
                int packed = word(intrinsicStates, state);
                if (packed < 0 || (packed & 255) > 63 || (packed >>> 12) >= fluidStates) throw new IllegalStateException("Invalid native intrinsic state");
            }
            MemorySegment ruleRefs = buffer(7, count * 2, Integer.BYTES, inputs);
            MemorySegment ruleRows = buffer(8, rules * 4, Integer.BYTES, inputs);
            MemorySegment rulePropertyIds = buffer(9, ruleProperties, Integer.BYTES, inputs);
            MemorySegment ruleOutputs = buffer(10, ruleValues, Integer.BYTES, inputs);
            ScalarRule[] ruleViews = new ScalarRule[rules];
            int nextRuleProperty = 0, nextRuleValue = 0;
            for (int id = 0; id < rules; id++) {
                int base = id * 4, first = word(ruleRows, base), size = word(ruleRows, base + 1);
                int firstValue = word(ruleRows, base + 2), values = word(ruleRows, base + 3);
                if (first != nextRuleProperty || size < 0 || (long) first + size > ruleProperties
                    || firstValue != nextRuleValue || values <= 0 || (long) firstValue + values > ruleValues) {
                    throw new IllegalStateException("Invalid native scalar rule");
                }
                int[] ids = new int[size], outputs = new int[values];
                for (int i = 0; i < size; i++) {
                    ids[i] = word(rulePropertyIds, first + i);
                    if (ids[i] < 0 || ids[i] > 65535) throw new IllegalStateException("Invalid native rule property");
                }
                for (int i = 0; i < values; i++) {
                    outputs[i] = word(ruleOutputs, firstValue + i);
                    if (outputs[i] < 0 || outputs[i] > 63) throw new IllegalStateException("Invalid native rule output");
                }
                ruleViews[id] = new ScalarRule(ids, outputs);
                nextRuleProperty += size; nextRuleValue += values;
            }
            if (nextRuleProperty != ruleProperties || nextRuleValue != ruleValues) throw new IllegalStateException("Incomplete native scalar rules");
            MemorySegment materialRows = buffer(11, materials * 3, Integer.BYTES, inputs);
            MemorySegment soundStates = buffer(12, states, Short.BYTES, inputs);
            MemorySegment offsetRows = buffer(13, offsets * 5, Integer.BYTES, inputs);
            MemorySegment offsetData = buffer(14, offsetValues, Double.BYTES, inputs);
            var materialViews = NativeBlockMaterials.load(materials, offsets, offsetValues, materialRows, offsetRows, offsetData);
            for (int state = 0; state < states; state++) {
                int sound = soundId(soundStates,state);
                if (sound < 0 || sound >= NativeSoundDefinitions.typeCount()) throw new IllegalStateException("Invalid native state sound");
            }
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
                int base = id * 6, start = word(rows, base), length = word(rows, base + 1);
                int firstState = word(rows, base + 2), template = word(rows, base + 3), physical = word(rows, base + 4), material = word(rows, base + 5);
                if (start < 0 || length <= 0 || (long) start + length > nameBytes || firstState != nextState
                    || template < 0 || template >= templates || physical < 0 || physical >= physicalProfiles || material < 0 || material >= materials) throw new IllegalStateException("Invalid native block row: " + id);
                Template t = views[template];
                if ((long) nextState + t.graph.stateCount > states) throw new IllegalStateException("Invalid native block range");
                int colorRule = word(ruleRefs, id * 2), lightRule = word(ruleRefs, id * 2 + 1);
                if (colorRule < 0 || colorRule >= rules || lightRule < 0 || lightRule >= rules) throw new IllegalStateException("Invalid native block rule binding");
                for (int value : ruleViews[lightRule].values) if (value > 15) throw new IllegalStateException("Native emission outside range");
                String name = new String(names.asSlice(start, length).toArray(ValueLayout.JAVA_BYTE), StandardCharsets.UTF_8);
                if (result.put(name, new Definition(id, name, firstState, t, physicalViews[physical], ruleViews[colorRule], ruleViews[lightRule], intrinsicStates, soundStates, policyStates, materialViews[material])) != null) throw new IllegalStateException("Duplicate native block name: " + name);
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
