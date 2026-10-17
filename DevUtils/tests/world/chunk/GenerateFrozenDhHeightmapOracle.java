import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper;
import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import com.seibel.distanthorizons.core.util.LodUtil;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.LongStream;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.ticks.ProtoChunkTicks;

/** Run only against the untouched Frozen classpath; never candidate expectations. */
public final class GenerateFrozenDhHeightmapOracle {
    private static String hash(Class<?> type) throws Exception {
        try (var stream = type.getResourceAsStream(type.getSimpleName() + ".class")) {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
        }
    }

    private static ILevelWrapper registryOnlyLevel() throws Exception {
        // Production serialization reads Level.registryAccess only. No server,
        // world simulation or overridden DH calculation is involved in this fixture.
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        Field field = unsafeType.getDeclaredField("theUnsafe"); field.setAccessible(true);
        Object unsafe = field.get(null);
        Level level = (Level)unsafeType.getMethod("allocateInstance", Class.class).invoke(unsafe, ServerLevel.class);
        Field registry = Level.class.getDeclaredField("registryAccess"); registry.setAccessible(true);
        registry.set(level, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        return (ILevelWrapper)Proxy.newProxyInstance(ILevelWrapper.class.getClassLoader(), new Class<?>[]{ILevelWrapper.class},
            (proxy, method, args) -> {
                if (method.getName().equals("getWrappedMcObject")) return level;
                if (method.getName().equals("toString")) return "Frozen fixture registry-only level";
                if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                if (method.getName().equals("equals")) return proxy == args[0];
                throw new UnsupportedOperationException("Unexpected fixture level callback: " + method);
            });
    }

    private static ProtoChunk recorded(JsonObject record) {
        int minY = record.get("min_y").getAsInt(), height = record.get("height").getAsInt();
        var sections = new LevelChunkSection[height / 16];
        var strategy = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
        for (var item : record.getAsJsonArray("sections")) {
            var section = item.getAsJsonObject();
            var palette = new ArrayList<BlockState>();
            for (var state : section.getAsJsonArray("palette"))
                palette.add(BlockState.CODEC.parse(JsonOps.INSTANCE, state).result().orElseThrow());
            var data = section.getAsJsonArray("data"); long[] words = new long[data.size()];
            for (int i = 0; i < words.length; i++) words[i] = data.get(i).getAsLong();
            var packed = new PalettedContainerRO.PackedData<>(palette,
                words.length == 0 ? Optional.empty() : Optional.of(LongStream.of(words)));
            var states = PalettedContainer.unpack(strategy, packed).result().orElseThrow();
            sections[section.get("y").getAsInt() - minY / 16] = new LevelChunkSection(states, null);
        }
        for (var section : sections) if (section == null) throw new IllegalArgumentException("Incomplete saved chunk");
        return new ProtoChunk(new ChunkPos(-37, 41), UpgradeData.EMPTY, sections, new ProtoChunkTicks<>(),
            new ProtoChunkTicks<>(), LevelHeightAccessor.create(minY, height), null, null);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 5) throw new IllegalArgumentException("output saved-chunks-json Frozen-wrapper-sha Frozen-chunk-wrapper-sha Frozen-proto-chunk-sha");
        Class<?>[] types = {BlockStateWrapper.class, ChunkWrapper.class, ProtoChunk.class};
        for (int i = 0; i < types.length; i++)
            if (!hash(types[i]).equals(args[i + 2])) throw new IllegalStateException("Not pinned Frozen: " + types[i]);
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        ILevelWrapper level = registryOnlyLevel();
        var geometryIds = new LinkedHashMap<List<AABB>, Integer>();
        var stateIds = new ArrayList<Integer>();
        int cached = 0, opacityDifferences = 0;
        for (BlockState state : Block.BLOCK_STATE_REGISTRY) {
            int shape = -1;
            if (!state.getBlock().hasDynamicShape() && state.getClass() == BlockState.class) {
                List<AABB> boxes = List.copyOf(state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).toAabbs());
                shape = geometryIds.computeIfAbsent(boxes, ignored -> geometryIds.size()); cached++;
            }
            stateIds.add(shape);
        }
        byte[] savedBytes = Files.readAllBytes(Path.of(args[1]));
        var chunks = JsonParser.parseString(new String(savedBytes, java.nio.charset.StandardCharsets.UTF_8))
            .getAsJsonObject().getAsJsonArray("chunks");
        try (var out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(Path.of(args[0]))))) {
            out.writeInt(0x44484831);
            for (var type : types) out.writeUTF(hash(type));
            out.writeUTF(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(savedBytes)));
            out.writeUTF("Actual Frozen DH wrappers; registry-only fixture level; cached and dynamic states recorded separately. Supplemental CPU corpus only.");
            out.writeInt(geometryIds.size());
            for (var boxes : geometryIds.keySet()) {
                out.writeInt(boxes.size());
                for (var box : boxes) for (double value : new double[]{box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ}) out.writeDouble(value);
            }
            out.writeInt(stateIds.size());
            for (BlockState state : Block.BLOCK_STATE_REGISTRY) {
                var wrapper = BlockStateWrapper.fromBlockState(state, level);
                int nativeOpacity = state.isAir() ? 0 : !state.getFluidState().isEmpty() && !state.canOcclude() ? 1
                    : state.getLightBlock() == 0 && !state.canOcclude() ? 0 : LodUtil.BLOCK_FULLY_OPAQUE;
                if (nativeOpacity != wrapper.getOpacity()) opacityDifferences++;
                out.writeInt(Block.getId(state)); out.writeInt(stateIds.get(Block.getId(state)));
                out.writeBoolean(state.isAir()); out.writeBoolean(state.canOcclude());
                out.writeBoolean(!state.getFluidState().isEmpty()); out.writeByte(state.getLightBlock());
                out.writeBoolean(wrapper.isSolid()); out.writeByte(wrapper.getOpacity());
            }
            out.writeInt(chunks.size());
            for (var item : chunks) {
                var record = item.getAsJsonObject(); var chunk = recorded(record);
                var wrapper = new ChunkWrapper(chunk, level);
                out.writeUTF(record.get("dimension").getAsString()); out.writeLong(record.get("seed").getAsLong());
                out.writeInt(chunk.getMinY()); out.writeInt(chunk.getHeight());
                out.writeInt(wrapper.getMinNonEmptyHeight()); out.writeInt(wrapper.getMaxNonEmptyHeight());
                // Immutable IDs are verification input, never a proposed runtime handoff.
                for (var section : chunk.getSections()) for (int y=0;y<16;y++) for(int z=0;z<16;z++) for(int x=0;x<16;x++)
                    out.writeInt(Block.getId(section.getBlockState(x,y,z)));
                for (int x=0;x<16;x++) for(int z=0;z<16;z++) {
                    out.writeInt(wrapper.getSolidHeightMapValue(x,z)); out.writeInt(wrapper.getLightBlockingHeightMapValue(x,z));
                }
            }
        }
        System.out.println("Frozen DH heightmap states="+stateIds.size()+" cached="+cached+" geometry="+geometryIds.size()
            +" saved-chunks="+chunks.size()+" derived-opacity-differences="+opacityDifferences);
    }
}
