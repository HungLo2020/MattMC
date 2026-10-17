import com.google.gson.JsonParser;
import com.seibel.distanthorizons.common.wrappers.WrapperFactory;
import com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper;
import com.seibel.distanthorizons.core.dataObjects.fullData.FullDataPointIdMap;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.IWrapperFactory;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftSharedWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import java.io.*;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;

/** Records the actual Frozen 2x2 reducer over previously recorded production columns. */
public final class GenerateFrozenDhReductionOracle {
    private static final Class<?>[] PINNED = {FullDataSourceV2.class, FullDataPointIdMap.class,
        FullDataPointUtil.class, BlockStateWrapper.class, WrapperFactory.class};
    private static String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static String hash(Class<?> type) throws Exception {
        try (var input = type.getResourceAsStream(type.getSimpleName() + ".class")) { return digest(input.readAllBytes()); }
    }
    private static LongArrayList[] readColumns(DataInputStream in) throws Exception {
        var columns = new LongArrayList[4096];
        for (int i = 0; i < columns.length; i++) {
            int size = in.readInt(); columns[i] = new LongArrayList(size);
            for (int p = 0; p < size; p++) columns[i].add(in.readLong());
        }
        return columns;
    }
    private static void writeColumn(DataOutputStream out, LongList column) throws Exception {
        out.writeInt(column.size()); for (int p = 0; p < column.size(); p++) out.writeLong(column.getLong(p));
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("DHO1-raw-input output class-pins-json");
        var pins = JsonParser.parseString(Files.readString(Path.of(args[2]))).getAsJsonObject();
        for (var type : PINNED) if (!hash(type).equals(pins.get(type.getName()).getAsString())) throw new IllegalStateException("Not pinned Frozen: " + type);
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        SingletonInjector.INSTANCE.bind(IWrapperFactory.class, WrapperFactory.INSTANCE);
        File install = Path.of(System.getProperty("mattmc.fixture.installation_dir")).toFile();
        var shared = (IMinecraftSharedWrapper) Proxy.newProxyInstance(IMinecraftSharedWrapper.class.getClassLoader(), new Class<?>[]{IMinecraftSharedWrapper.class},
            (proxy, method, input) -> switch (method.getName()) {
                case "getInstallationDirectory" -> install;
                case "isDedicatedServer" -> false;
                case "getDelayedSetupComplete" -> true;
                default -> throw new UnsupportedOperationException("Unexpected environment callback: " + method);
            });
        SingletonInjector.INSTANCE.bind(IMinecraftSharedWrapper.class, shared);
        var makeLevel = GenerateFrozenDhColumnsOracle.class.getDeclaredMethod("registryOnlyLevel", RegistryAccess.class);
        makeLevel.setAccessible(true);
        var level = (ILevelWrapper) makeLevel.invoke(null, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        var makeBiome = GenerateFrozenDhOcclusionOracle.class.getDeclaredMethod("uniqueBiome", int.class); makeBiome.setAccessible(true);
        var reduce = FullDataSourceV2.class.getDeclaredMethod("mergeInputTwoByTwoDataColumn", FullDataSourceV2.class, int.class, int.class); reduce.setAccessible(true);
        byte[] raw = Files.readAllBytes(Path.of(args[0]));
        try (var in = new DataInputStream(new ByteArrayInputStream(raw));
             var out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(Path.of(args[1]))))) {
            if (in.readInt() != 0x44484f31) throw new IllegalArgumentException("Not DHO1 production columns");
            for (int n = in.readInt(); n > 0; n--) { in.readUTF(); in.readUTF(); }
            in.readUTF(); in.readUTF(); int cases = in.readInt();
            out.writeInt(0x44485231); out.writeInt(PINNED.length);
            for (var type : PINNED) { out.writeUTF(type.getName()); out.writeUTF(hash(type)); }
            out.writeUTF(digest(raw)); out.writeUTF("Actual Frozen 2x2 reduction over recorded saved-chunk production columns; unique biome fixture identities preserve IDs only. Supplemental CPU evidence, not gameplay.");
            out.writeInt(cases);
            for (int test = 0; test < cases; test++) {
                String name = in.readUTF(); out.writeUTF(name); int materials = in.readInt(); out.writeInt(materials);
                var mapping = new FullDataPointIdMap(6);
                for (int id = 0; id < materials; id++) {
                    int state = in.readInt(), opacity = in.readUnsignedByte();
                    var wrapper = state == 0 ? BlockStateWrapper.AIR : BlockStateWrapper.fromBlockState(Block.stateById(state), level);
                    if (opacity != wrapper.getOpacity() || mapping.addIfNotPresentAndGetId((IBiomeWrapper) makeBiome.invoke(null, id), wrapper) != id) throw new IllegalStateException("Material identity changed");
                    out.writeInt(state); out.writeByte(opacity); out.writeBoolean(wrapper.isAir()); out.writeBoolean(wrapper.isLiquid());
                }
                var columns = readColumns(in); readColumns(in); readColumns(in);
                try (var source = FullDataSourceV2.createWithData(6, mapping, columns, new byte[4096], new byte[4096])) {
                    for (var column : columns) writeColumn(out, column);
                    for (int x = 0; x < 64; x += 2) for (int z = 0; z < 64; z += 2)
                        writeColumn(out, (LongList) reduce.invoke(null, source, x, z));
                }
                System.out.println("Recorded " + name + " 1024 actual Frozen 2x2 reductions");
            }
            if (in.read() != -1) throw new IllegalArgumentException("Trailing DHO1 data");
        }
    }
}
