import com.google.gson.JsonParser;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Actual pinned Frozen recipient update; signed-byte matrix and saved point inputs. */
public final class GenerateFrozenDhSameDetailOracle {
    private static String hash(Class<?> type) throws Exception {
        try (var in = type.getResourceAsStream(type.getSimpleName() + ".class")) {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));
        }
    }
    private static void state(DataOutputStream out, FullDataSourceV2 source) throws Exception {
        for (int i = 0; i < 4096; i++) {
            out.writeByte(source.columnGenerationSteps.getByte(i)); out.writeByte(source.columnWorldCompressionMode.getByte(i));
            var column = source.dataPoints[i]; out.writeInt(column == null ? -1 : column.size());
            if (column != null) for (int p = 0; p < column.size(); p++) out.writeLong(column.getLong(p));
        }
    }
    private static void record(DataOutputStream out, String name, long[][] points, byte[] generations, byte[] modes,
        int[] remap, int matrix) throws Exception {
        try (var target = FullDataSourceV2.createEmpty(6); var source = FullDataSourceV2.createEmpty(6)) {
            for (int i = 0; i < 4096; i++) {
                if (matrix >= 0) {
                    source.columnGenerationSteps.set(i, (byte)(matrix * 4096 + i));
                    target.columnGenerationSteps.set(i, (byte)((matrix * 4096 + i) >>> 8));
                    source.columnWorldCompressionMode.set(i, (byte)7); target.columnWorldCompressionMode.set(i, (byte)13);
                    source.dataPoints[i].add(1L << 32); target.dataPoints[i].add(1L << 32);
                } else {
                    source.columnGenerationSteps.set(i, generations[i]); source.columnWorldCompressionMode.set(i, modes[i]);
                    target.columnGenerationSteps.set(i, (byte)(i % 13 - 2)); target.columnWorldCompressionMode.set(i, (byte)(255 - i));
                    source.dataPoints[i].addElements(0, points[i]);
                    for (long point : points[i]) target.dataPoints[i].add(FullDataPointUtil.remap(remap, point));
                    if (i % 5 == 0 && !target.dataPoints[i].isEmpty()) target.dataPoints[i].set(0, target.dataPoints[i].getLong(0) ^ (1L << 56));
                    if (i % 11 == 0 && !target.dataPoints[i].isEmpty()) target.dataPoints[i].removeLong(target.dataPoints[i].size() - 1);
                    if (i % 17 == 0) target.dataPoints[i] = null;
                    if (i % 23 == 0) source.dataPoints[i] = null;
                }
            }
            out.writeUTF(name); out.writeInt(remap.length); for (int id : remap) out.writeInt(id);
            out.writeBoolean(target.isEmpty); state(out, target); state(out, source);
            var method = FullDataSourceV2.class.getDeclaredMethod("updateFromSameDetailLevel", FullDataSourceV2.class, int[].class);
            method.setAccessible(true);
            for (int pass = 0; pass < 2; pass++) {
                out.writeBoolean((boolean)method.invoke(target, source, remap)); out.writeBoolean(target.isEmpty); state(out, target);
            }
        }
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("output class-pins-json columns-fixture");
        var pins = JsonParser.parseString(Files.readString(Path.of(args[1]))).getAsJsonObject();
        String sourceHash = hash(FullDataSourceV2.class);
        if (!sourceHash.equals(pins.get(FullDataSourceV2.class.getName()).getAsString())) throw new IllegalStateException("Not pinned Frozen source");
        var validation = FullDataSourceV2.class.getDeclaredField("RUN_DATA_ORDER_VALIDATION"); validation.setAccessible(true);
        if (validation.getBoolean(null)) throw new IllegalStateException("Frozen development order validation enabled; do not alter it");
        try (var out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(Path.of(args[0]))));
             var in = new DataInputStream(new GZIPInputStream(Files.newInputStream(Path.of(args[2]))))) {
            out.writeInt(0x44484d31); out.writeUTF(sourceHash); out.writeUTF(hash(FullDataPointUtil.class));
            out.writeUTF("Actual Frozen private same-detail update: exhaustive 65536 signed generation pairs and32 saved-column inputs; supplemental CPU proof, not gameplay/performance.");
            out.writeInt(48);
            for (int matrix = 0; matrix < 16; matrix++) record(out, "signed-matrix-" + matrix, null, null, null, new int[]{0}, matrix);
            if (in.readInt() != 0x44484331) throw new IllegalStateException("Not DHC1");
            for (int n = in.readInt(); n > 0; n--) { in.readUTF(); in.readUTF(); }
            in.readUTF(); in.readUTF();
            for (int n = in.readInt(); n > 0; n--) { in.readUTF(); in.readInt(); }
            for (int n = in.readInt(); n > 0; n--) { in.readUTF(); in.readInt(); }
            if (in.readInt() != 32) throw new IllegalStateException("Saved corpus changed");
            for (int test = 0; test < 32; test++) {
                String name = in.readUTF(); in.readInt(); in.readInt(); in.readInt(); int height = in.readInt(); in.readInt(); in.readInt();
                in.skipNBytes((long)height * 256 * 4 + (long)height / 16 * 64 * 4 + (long)height * 256 + 512L * 4);
                in.readLong(); in.readByte(); in.readByte(); in.readInt(); in.readInt();
                int count = in.readInt(); int[] remap = new int[count];
                for (int id = 0; id < count; id++) { in.readInt(); in.readInt(); in.readUTF(); in.readUTF(); remap[id] = count - id - 1; }
                long[][] points = new long[4096][]; byte[] generations = new byte[4096], modes = new byte[4096];
                for (int i = 0; i < 4096; i++) {
                    generations[i] = in.readByte(); modes[i] = in.readByte(); points[i] = new long[in.readInt()];
                    for (int p = 0; p < points[i].length; p++) points[i][p] = in.readLong();
                }
                record(out, name, points, generations, modes, remap, -1);
            }
            if (in.read() != -1) throw new IllegalStateException("Unexpected fixture suffix");
        }
        System.out.println("Recorded48 actual Frozen sources, each with two ordered updates; exhaustive65536 signed generation pairs. Source class " + sourceHash);
    }
}
