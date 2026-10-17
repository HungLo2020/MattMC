import com.google.gson.JsonParser;
import com.seibel.distanthorizons.common.wrappers.WrapperFactory;
import com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper;
import com.seibel.distanthorizons.core.dataObjects.fullData.FullDataPointIdMap;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dataObjects.transformers.FullDataOcclusionCuller;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.IWrapperFactory;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftSharedWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper;
import it.unimi.dsi.fastutil.longs.LongArrayList;
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

/** Actual untouched Frozen culler over recorded production columns, not an expected-policy mirror. */
public final class GenerateFrozenDhOcclusionOracle {
    private static final Class<?>[] PINNED = {FullDataOcclusionCuller.class, FullDataSourceV2.class,
        FullDataPointIdMap.class, FullDataPointUtil.class, BlockStateWrapper.class, WrapperFactory.class};
    private static String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static String hash(Class<?> type) throws Exception {
        try (var input = type.getResourceAsStream(type.getSimpleName() + ".class")) { return digest(input.readAllBytes()); }
    }
    private static IBiomeWrapper uniqueBiome(int id) {
        return (IBiomeWrapper) Proxy.newProxyInstance(IBiomeWrapper.class.getClassLoader(), new Class<?>[]{IBiomeWrapper.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getSerialString", "getName", "toString" -> "occlusion-fixture-" + id;
                case "hashCode" -> id;
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException("Unexpected biome callback: " + method);
            });
    }
    private static void writeColumns(DataOutputStream out, FullDataSourceV2 source) throws Exception {
        for (int x = 0; x < 64; x++) for (int z = 0; z < 64; z++) {
            var column = source.getColumnAtRelPos(x, z); out.writeInt(column.size());
            for (int p = 0; p < column.size(); p++) out.writeLong(column.getLong(p));
        }
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("DHC1-raw-input output class-pins-json");
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
        var level = (com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper)
            makeLevel.invoke(null, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        byte[] raw = Files.readAllBytes(Path.of(args[0]));
        try (var in = new DataInputStream(new ByteArrayInputStream(raw));
             var out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(Path.of(args[1]))))) {
            if (in.readInt() != 0x44484331) throw new IllegalArgumentException("Not DHC1 production columns");
            for (int n = in.readInt(); n > 0; n--) { in.readUTF(); in.readUTF(); }
            in.readUTF(); in.readUTF();
            for (int n = in.readInt(); n > 0; n--) { in.readUTF(); in.readInt(); }
            for (int n = in.readInt(); n > 0; n--) { in.readUTF(); in.readInt(); }
            int cases = in.readInt();
            out.writeInt(0x44484f31); out.writeInt(PINNED.length);
            for (var type : PINNED) { out.writeUTF(type.getName()); out.writeUTF(hash(type)); }
            out.writeUTF(digest(raw)); out.writeUTF("Actual Frozen culler over recorded saved-chunk production columns; unique fixture biome identities only preserve material ID positions. Supplemental CPU evidence, not gameplay.");
            out.writeInt(cases);
            for (int test = 0; test < cases; test++) {
                String name = in.readUTF(); out.writeUTF(name);
                in.readInt(); in.readInt(); in.readInt(); int height = in.readInt(); in.readInt(); in.readInt();
                in.skipNBytes((long) height * 256 * 4 + (long) height / 16 * 64 * 4 + (long) height * 256 + 256L * 8);
                long position = in.readLong(); in.readBoolean(); in.readBoolean(); in.readInt(); in.readInt();
                int materials = in.readInt(); out.writeInt(materials); var mapping = new FullDataPointIdMap(position);
                for (int id = 0; id < materials; id++) {
                    in.readInt(); int state = in.readInt(); in.readUTF(); in.readUTF();
                    var wrapper = state == 0 ? BlockStateWrapper.AIR : BlockStateWrapper.fromBlockState(Block.stateById(state), level);
                    if (mapping.addIfNotPresentAndGetId(uniqueBiome(id), wrapper) != id) throw new IllegalStateException("Material ID changed");
                    out.writeInt(state); out.writeByte(wrapper.getOpacity());
                }
                var columns = new LongArrayList[4096]; byte[] generation = new byte[4096], compression = new byte[4096];
                for (int i = 0; i < columns.length; i++) {
                    generation[i] = in.readByte(); compression[i] = in.readByte(); int size = in.readInt();
                    if (size < 0) throw new IllegalArgumentException("Null recorded column");
                    columns[i] = new LongArrayList(size); for (int p = 0; p < size; p++) columns[i].add(in.readLong());
                }
                try (var source = FullDataSourceV2.createWithData(position, mapping, columns, generation, compression)) {
                    writeColumns(out, source);
                    for (int pass = 0; pass < 2; pass++) {
                        for (int x = 0; x < 64; x++) for (int z = 0; z < 64; z++) if (source.getColumnAtRelPos(x, z).size() > 1)
                            FullDataOcclusionCuller.cullHiddenDatapointsInColumn(source, x, z);
                        writeColumns(out, source);
                    }
                }
                System.out.println("Recorded " + name + " two complete ordered occlusion passes");
            }
            if (in.read() != -1) throw new IllegalArgumentException("Trailing DHC1 data");
        }
    }
}
