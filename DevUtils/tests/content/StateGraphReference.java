import java.io.DataOutputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

/** Same public-API observer on Current and Frozen; no Frozen source edits.
 * Hashes every value, default and single-property transition in every graph. */
public final class StateGraphReference {
    private static long states, transitions;

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
        System.out.println("STATE_GRAPH_REFERENCE blocks=" + BuiltInRegistries.BLOCK.size()
            + " fluids=" + BuiltInRegistries.FLUID.size() + " states=" + states + " transitions=" + transitions
            + " sha256=" + HexFormat.of().formatHex(digest.digest())
            + " fluid_states=" + fluidStates + " fluid_sha256=" + HexFormat.of().formatHex(fluidDigest.digest())
            + " bootstrap_ns=" + bootstrapNs + " bootstrap_thread_bytes=" + bootstrapBytes);
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
