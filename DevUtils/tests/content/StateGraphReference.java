import java.io.DataOutputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Comparator;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.level.EmptyBlockGetter;

/** Same API/immutable-field observer on Current and Frozen; no Frozen source edits.
 * Hashes every value, default and single-property transition in every graph. */
public final class StateGraphReference {
    private static long states, transitions;

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().threadId();
        long allocated = bean.getThreadAllocatedBytes(thread), start = System.nanoTime();
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        long bootstrapNs = System.nanoTime() - start;
        long bootstrapBytes = bean.getThreadAllocatedBytes(thread) - allocated;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var out = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
            for (var block : BuiltInRegistries.BLOCK) {
                graph(out, BuiltInRegistries.BLOCK.getKey(block).toString(), block.getStateDefinition(), block.defaultBlockState());
            }
            for (var fluid : BuiltInRegistries.FLUID) {
                graph(out, BuiltInRegistries.FLUID.getKey(fluid).toString(), fluid.getStateDefinition(), fluid.defaultFluidState());
            }
        }
        MessageDigest fluidDigest = MessageDigest.getInstance("SHA-256");
        int fluidStates = 0;
        try (var out = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), fluidDigest))) {
            for (var fluid : BuiltInRegistries.FLUID) {
                out.writeUTF(BuiltInRegistries.FLUID.getKey(fluid).toString());
                out.writeInt(BuiltInRegistries.FLUID.getId(fluid));
                out.writeInt(Fluid.FLUID_STATE_REGISTRY.getId(fluid.defaultFluidState()));
                for (var state : fluid.getStateDefinition().getPossibleStates()) {
                    out.writeInt(Fluid.FLUID_STATE_REGISTRY.getId(state));
                    out.writeInt(state.getAmount()); out.writeBoolean(state.isSource()); out.writeBoolean(state.isEmpty());
                    out.writeInt(Float.floatToRawIntBits(state.getOwnHeight()));
                    out.writeInt(Float.floatToRawIntBits(state.getExplosionResistance()));
                    out.writeInt(Block.getId(state.createLegacyBlock()));
                    var encoded = FluidState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow();
                    out.writeUTF(encoded.toString());
                    if (FluidState.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow() != state) {
                        throw new AssertionError("Fluid codec changed state identity: " + state);
                    }
                    fluidStates++;
                }
            }
        }
        MessageDigest propertyDigest = MessageDigest.getInstance("SHA-256");
        int propertyDefinitions = 0;
        try (var out = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), propertyDigest))) {
            List<java.lang.reflect.Field> definitions = new java.util.ArrayList<>();
            for (var field : BlockStateProperties.class.getFields()) {
                if (Property.class.isAssignableFrom(field.getType())) definitions.add(field);
            }
            definitions.add(Class.forName("net.alexscaves.server.block.DinosaurEggBlock").getField("NEEDS_PLAYER"));
            definitions.add(Class.forName("net.alexscaves.server.block.PrimalMagmaBlock").getField("ACTIVE"));
            definitions.add(Class.forName("net.alexscaves.server.block.PrimalMagmaBlock").getField("PERMANENT"));
            definitions.add(Class.forName("net.alexscaves.server.block.PewenBranchBlock").getField("PINES"));
            definitions.add(Class.forName("net.alexscaves.server.block.PewenBranchBlock").getField("ROTATION"));
            definitions.add(Class.forName("net.alexscaves.server.block.DinosaurChopBlock").getField("BITES"));
            definitions.add(Class.forName("net.alexscaves.server.block.FissurePrimalMagmaBlock").getField("REGEN_HEIGHT"));
            definitions.add(Class.forName("net.alexsmobs.block.BlockHummingbirdFeeder").getField("CONTENTS"));
            definitions.add(Class.forName("net.minecraft.world.level.block.RedstoneRandomizerBlock").getField("OUTPUT_SIDE"));
            definitions.add(Class.forName("net.minecraft.world.level.block.custom.FlytrapBlock").getField("OPEN"));
            definitions.add(Class.forName("net.minecraft.world.level.block.custom.CycadBlock").getField("TOP"));
            definitions.sort(Comparator.comparing(field -> field.getDeclaringClass().getName() + "." + field.getName()));
            for (var field : definitions) {
                Property<?> property = (Property<?>) field.get(null);
                out.writeUTF(field.getDeclaringClass().getName() + "." + field.getName());
                out.writeUTF(property.getName()); out.writeUTF(property.getValueClass().getName());
                out.writeInt(property.getPossibleValues().size());
                for (Object value : property.getPossibleValues()) propertyValue(out, property, value);
                propertyDefinitions++;
            }
        }
        MessageDigest blockDigest = MessageDigest.getInstance("SHA-256");
        int blockStates = 0;
        List<BlockPos> offsetPositions = List.of(BlockPos.ZERO, new BlockPos(23, 7, -13));
        try (var out = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), blockDigest))) {
            for (var block : BuiltInRegistries.BLOCK) {
                for (var state : block.getStateDefinition().getPossibleStates()) {
                    out.writeInt(Block.getId(state)); out.writeInt(BuiltInRegistries.BLOCK.getId(block));
                    out.writeBoolean(state.isAir()); out.writeBoolean(state.blocksMotion());
                    out.writeBoolean(state.isRandomlyTicking()); out.writeBoolean(state.canOcclude());
                    out.writeBoolean(state.useShapeForLightOcclusion()); out.writeBoolean(state.isSolidRender());
                    out.writeBoolean(state.hasBlockEntity()); out.writeInt(state.getLightBlock());
                    out.writeInt(state.getLightEmission());
                    out.writeInt(Fluid.FLUID_STATE_REGISTRY.getId(state.getFluidState()));
                    out.writeBoolean(state.hasOffsetFunction());
                    for (var position : offsetPositions) {
                        var offset = state.getOffset(position);
                        out.writeLong(Double.doubleToRawLongBits(offset.x));
                        out.writeLong(Double.doubleToRawLongBits(offset.y));
                        out.writeLong(Double.doubleToRawLongBits(offset.z));
                    }
                    for (Direction direction : Direction.values()) {
                        var boxes = LightEngine.getOcclusionShape(state, direction).toAabbs();
                        out.writeInt(boxes.size());
                        for (var box : boxes) {
                            out.writeLong(Double.doubleToRawLongBits(box.minX)); out.writeLong(Double.doubleToRawLongBits(box.minY));
                            out.writeLong(Double.doubleToRawLongBits(box.minZ)); out.writeLong(Double.doubleToRawLongBits(box.maxX));
                            out.writeLong(Double.doubleToRawLongBits(box.maxY)); out.writeLong(Double.doubleToRawLongBits(box.maxZ));
                        }
                    }
                    blockStates++;
                }
            }
        }
        MessageDigest physicalDigest = MessageDigest.getInstance("SHA-256");
        String[] physicalFields = {
            "destroyTime", "explosionResistance", "friction", "speedFactor", "jumpFactor",
            "hasCollision", "requiresCorrectToolForDrops", "isRandomlyTicking", "canOcclude",
            "isAir", "ignitedByLava", "liquid", "forceSolidOff", "forceSolidOn",
            "spawnTerrainParticles", "replaceable", "dynamicShape", "pushReaction"
        };
        try (var out = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), physicalDigest))) {
            for (var block : BuiltInRegistries.BLOCK) {
                out.writeInt(BuiltInRegistries.BLOCK.getId(block));
                out.writeUTF(BuiltInRegistries.BLOCK.getKey(block).toString());
                for (String name : physicalFields) {
                    var field = BlockBehaviour.Properties.class.getDeclaredField(name);
                    field.setAccessible(true);
                    Object value = field.get(block.properties());
                    out.writeUTF(name);
                    if (value instanceof Float f) out.writeInt(Float.floatToRawIntBits(f));
                    else if (value instanceof Boolean b) out.writeBoolean(b);
                    else if (value instanceof Enum<?> e) out.writeUTF(e.name());
                    else throw new AssertionError("Unsupported physical field: " + name);
                    try {
                        var cached = BlockBehaviour.class.getDeclaredField(name);
                        cached.setAccessible(true);
                        if (!cached.get(block).equals(value)) throw new AssertionError("Cached physical value differs: " + block + "/" + name);
                    } catch (NoSuchFieldException expected) { }
                }
                for (var state : block.getStateDefinition().getPossibleStates()) {
                    out.writeInt(Block.getId(state));
                    out.writeInt(Float.floatToRawIntBits(state.getDestroySpeed(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)));
                    out.writeBoolean(state.requiresCorrectToolForDrops());
                    out.writeBoolean(state.ignitedByLava());
                    out.writeBoolean(state.liquid());
                    out.writeBoolean(state.shouldSpawnTerrainParticles());
                    out.writeBoolean(state.canBeReplaced());
                    out.writeUTF(state.getPistonPushReaction().name());
                }
            }
        }
        MessageDigest intrinsicDigest = MessageDigest.getInstance("SHA-256");
        var colorField = BlockBehaviour.Properties.class.getDeclaredField("mapColor");
        var lightField = BlockBehaviour.Properties.class.getDeclaredField("lightEmission");
        colorField.setAccessible(true); lightField.setAccessible(true);
        try (var out = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), intrinsicDigest))) {
            for (var block : BuiltInRegistries.BLOCK) {
                var copied = BlockBehaviour.Properties.ofFullCopy(block);
                var color = (Function<BlockState, MapColor>) colorField.get(copied);
                var light = (ToIntFunction<BlockState>) lightField.get(copied);
                out.writeInt(BuiltInRegistries.BLOCK.getId(block));
                out.writeInt(block.defaultMapColor().id);
                for (var state : block.getStateDefinition().getPossibleStates()) {
                    out.writeInt(Block.getId(state));
                    out.writeInt(state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).id);
                    out.writeInt(color.apply(state).id);
                    out.writeInt(state.getLightEmission()); out.writeInt(light.applyAsInt(state));
                    out.writeInt(Fluid.FLUID_STATE_REGISTRY.getId(state.getFluidState()));
                }
            }
        }
        MessageDigest materialDigest = MessageDigest.getInstance("SHA-256");
        int soundProfiles = 0, offsetSamples = 0;
        var soundNames = new IdentityHashMap<SoundType,String>();
        try (var out = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), materialDigest))) {
            for (var event : BuiltInRegistries.SOUND_EVENT) {
                out.writeInt(BuiltInRegistries.SOUND_EVENT.getId(event));
                out.writeUTF(BuiltInRegistries.SOUND_EVENT.getKey(event).toString());out.writeUTF(event.location().toString());
                out.writeBoolean(event.fixedRange().isPresent());
                if(event.fixedRange().isPresent())out.writeInt(Float.floatToRawIntBits(event.fixedRange().get()));
                for(float volume:new float[]{Float.NEGATIVE_INFINITY,-1.0F,-0.0F,0.0F,1.0F,1.0001F,2.0F,Float.NaN,Float.POSITIVE_INFINITY})
                    out.writeInt(Float.floatToRawIntBits(event.getRange(volume)));
            }
            for(var owner:List.of(SoundType.class,Class.forName("net.alexscaves.server.block.ACSoundTypes"))) {
                var fields=new java.util.ArrayList<java.lang.reflect.Field>();
                for(var field:owner.getFields())if(field.getType()==SoundType.class)fields.add(field);
                fields.sort(Comparator.comparing(java.lang.reflect.Field::getName));
                for(var field:fields) {
                    var sound=(SoundType)field.get(null);String name=owner.getName()+"."+field.getName();
                    if(soundNames.put(sound,name)!=null)throw new AssertionError("Merged sound profile identities");
                    out.writeUTF(name);soundProfile(out,sound);soundProfiles++;
                }
            }
            for(var instrument:NoteBlockInstrument.values()) {
                out.writeInt(instrument.ordinal());out.writeUTF(instrument.getSerializedName());
                out.writeInt(BuiltInRegistries.SOUND_EVENT.getId(instrument.getSoundEvent().value()));
                out.writeBoolean(instrument.isTunable());out.writeBoolean(instrument.hasCustomSound());out.writeBoolean(instrument.worksAboveNoteBlock());
            }
            var soundField=BlockBehaviour.Properties.class.getDeclaredField("soundType");soundField.setAccessible(true);
            var maxH=BlockBehaviour.class.getDeclaredMethod("getMaxHorizontalOffset");maxH.setAccessible(true);
            var maxV=BlockBehaviour.class.getDeclaredMethod("getMaxVerticalOffset");maxV.setAccessible(true);
            var offsetConfigs=new java.util.HashSet<String>();
            for(var block:BuiltInRegistries.BLOCK) {
                out.writeInt(BuiltInRegistries.BLOCK.getId(block));
                out.writeUTF(java.util.Objects.requireNonNull(soundNames.get(soundField.get(block.properties()))));
                float h=(Float)maxH.invoke(block),v=(Float)maxV.invoke(block);
                out.writeInt(Float.floatToRawIntBits(h));out.writeInt(Float.floatToRawIntBits(v));
                for(var state:block.getStateDefinition().getPossibleStates()) {
                    out.writeInt(Block.getId(state));out.writeUTF(java.util.Objects.requireNonNull(soundNames.get(state.getSoundType())));
                    out.writeUTF(state.instrument().getSerializedName());
                }
                var state=block.defaultBlockState();
                int kind=!state.hasOffsetFunction()?0:state.getOffset(new BlockPos(23,7,-13)).y!=0.0||state.getOffset(new BlockPos(121,0,47)).y!=0.0?2:1;
                String key=kind+"/"+Float.floatToRawIntBits(h)+"/"+Float.floatToRawIntBits(v);
                if(!offsetConfigs.add(key))continue;
                out.writeUTF(key);int size=kind==2?4096:kind==1?256:1,filled=0;boolean[] seen=new boolean[size];
                for(int x=0;x<1000000 && filled<size;x++) {
                    int z=-113;long seed=Mth.getSeed(x,0,z);
                    int at=kind==2?(int)seed&4095:kind==1?((int)seed&15)|((int)(seed>>4)&240):0;
                    if(seen[at])continue;seen[at]=true;filled++;
                    offset(out,state,new BlockPos(x,73,z));offsetSamples++;
                }
                if(filled!=size)throw new AssertionError("Incomplete offset key coverage: "+key);
                for(int[] pos:new int[][]{{Integer.MIN_VALUE,Integer.MAX_VALUE},{Integer.MAX_VALUE,Integer.MIN_VALUE},{-30000000,30000000},{-687,687}}) {
                    offset(out,state,new BlockPos(pos[0],-31,pos[1]));offsetSamples++;
                }
            }
        }
        System.out.println("STATE_GRAPH_REFERENCE blocks=" + BuiltInRegistries.BLOCK.size()
            + " fluids=" + BuiltInRegistries.FLUID.size() + " states=" + states + " transitions=" + transitions
            + " sha256=" + HexFormat.of().formatHex(digest.digest())
            + " fluid_states=" + fluidStates + " fluid_sha256=" + HexFormat.of().formatHex(fluidDigest.digest())
            + " property_definitions=" + propertyDefinitions + " property_sha256=" + HexFormat.of().formatHex(propertyDigest.digest())
            + " block_states=" + blockStates + " block_sha256=" + HexFormat.of().formatHex(blockDigest.digest())
            + " physical_sha256=" + HexFormat.of().formatHex(physicalDigest.digest())
            + " intrinsic_sha256=" + HexFormat.of().formatHex(intrinsicDigest.digest())
            + " sound_events=" + BuiltInRegistries.SOUND_EVENT.size() + " sound_profiles=" + soundProfiles
            + " instruments=" + NoteBlockInstrument.values().length + " offset_samples=" + offsetSamples
            + " material_sha256=" + HexFormat.of().formatHex(materialDigest.digest())
            + " bootstrap_ns=" + bootstrapNs + " bootstrap_thread_bytes=" + bootstrapBytes);
    }

    private static void soundProfile(DataOutputStream out, SoundType sound) throws Exception {
        out.writeInt(Float.floatToRawIntBits(sound.getVolume()));out.writeInt(Float.floatToRawIntBits(sound.getPitch()));
        for(var event:List.of(sound.getBreakSound(),sound.getStepSound(),sound.getPlaceSound(),sound.getHitSound(),sound.getFallSound()))
            out.writeInt(BuiltInRegistries.SOUND_EVENT.getId(event));
    }
    private static void offset(DataOutputStream out, BlockState state, BlockPos pos) throws Exception {
        out.writeInt(pos.getX());out.writeInt(pos.getY());out.writeInt(pos.getZ());
        var value=state.getOffset(pos);
        out.writeLong(Double.doubleToRawLongBits(value.x));out.writeLong(Double.doubleToRawLongBits(value.y));out.writeLong(Double.doubleToRawLongBits(value.z));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void propertyValue(DataOutputStream out, Property property, Object value) throws Exception {
        String name = valueName(property, value);
        out.writeUTF(name); out.writeInt(property.getInternalIndex((Comparable) value));
        if (!property.getValue(name).orElseThrow().equals(value)) throw new AssertionError("Property parse failed: " + property);
        var encoded = property.codec().encodeStart(JsonOps.INSTANCE, value).getOrThrow();
        out.writeUTF(encoded.toString());
        if (!property.codec().parse(JsonOps.INSTANCE, encoded).getOrThrow().equals(value)) throw new AssertionError("Property codec failed: " + property);
    }

    private static void graph(DataOutputStream out, String name, StateDefinition<?, ?> definition, StateHolder<?, ?> initial) throws Exception {
        List<? extends StateHolder<?, ?>> rows = definition.getPossibleStates();
        var ids = new IdentityHashMap<StateHolder<?, ?>, Integer>();
        for (int state = 0; state < rows.size(); state++) ids.put(rows.get(state), state);
        List<Property<?>> properties = List.copyOf(definition.getProperties());
        out.writeUTF(name); out.writeInt(rows.size()); out.writeInt(ids.get(initial)); out.writeInt(properties.size());
        for (Property<?> property : properties) {
            out.writeUTF(property.getName()); out.writeInt(property.getPossibleValues().size());
            for (Object possible : property.getPossibleValues()) out.writeUTF(valueName(property, possible));
        }
        for (StateHolder<?, ?> state : rows) {
            for (Property<?> property : properties) {
                out.writeUTF(valueName(property, state.getValue(property)));
                for (Object possible : property.getPossibleValues()) {
                    Integer next = ids.get(with(state, property, possible));
                    if (next == null) throw new AssertionError("Transition leaves its definition: " + name + "/" + property.getName());
                    out.writeInt(next); transitions++;
                }
            }
            states++;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String valueName(Property property, Object value) { return property.getName((Comparable) value); }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static StateHolder<?, ?> with(StateHolder state, Property property, Object value) {
        return (StateHolder<?, ?>) state.setValue(property, (Comparable) value);
    }
}
