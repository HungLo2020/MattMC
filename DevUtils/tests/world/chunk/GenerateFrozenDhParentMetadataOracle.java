import com.google.gson.JsonParser;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Actual pinned Frozen signed-byte metadata helpers; supplemental CPU evidence. */
public final class GenerateFrozenDhParentMetadataOracle {
    private static String hash(Class<?> type) throws Exception {
        try (var in = type.getResourceAsStream(type.getSimpleName() + ".class")) {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));
        }
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("output class-pins-json");
        String sourceHash = hash(FullDataSourceV2.class);
        var pins = JsonParser.parseString(Files.readString(Path.of(args[1]))).getAsJsonObject();
        if (!sourceHash.equals(pins.get(FullDataSourceV2.class.getName()).getAsString())) throw new IllegalStateException("Not pinned Frozen FullDataSourceV2");
        var generation = FullDataSourceV2.class.getDeclaredMethod("determineMinWorldGenStepForTwoByTwoColumn", ByteArrayList.class, int.class, int.class);
        var compression = FullDataSourceV2.class.getDeclaredMethod("determineHighestWorldCompressionForTwoByTwoColumn", ByteArrayList.class, int.class, int.class);
        generation.setAccessible(true); compression.setAccessible(true);
        var random = new Random(0x44485031L);
        try (var out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(Path.of(args[0]))))) {
            out.writeInt(0x44485031); out.writeUTF(sourceHash); out.writeUTF(hash(ByteArrayList.class));
            out.writeUTF("Actual Frozen parent metadata helpers over canonical, extrema and signed-byte CPU patterns; not gameplay or performance evidence.");
            out.writeInt(32);
            for (int test = 0; test < 32; test++) {
                byte[] steps = new byte[4096], modes = new byte[4096];
                for (int i = 0; i < 4096; i++) {
                    if (test == 1) { steps[i] = (byte)(i % 9); modes[i] = (byte)(i % 2); }
                    else if (test == 2) {
                        byte[] extremes = {-128, -1, 0, 127}; steps[i] = extremes[(i / 64 + i % 64) % 4]; modes[i] = extremes[3 - (i / 64 + i % 64) % 4];
                    } else if (test == 3) { steps[i] = (byte)i; modes[i] = (byte)(255 - i); }
                    else if (test >= 4) { steps[i] = (byte)random.nextInt(256); modes[i] = (byte)random.nextInt(256); }
                }
                out.write(steps); out.write(modes);
                var stepList = new ByteArrayList(steps); var modeList = new ByteArrayList(modes);
                for (int x = 0; x < 64; x += 2) for (int z = 0; z < 64; z += 2) {
                    out.writeByte((byte)generation.invoke(null, stepList, x, z));
                    out.writeByte((byte)compression.invoke(null, modeList, x, z));
                }
            }
        }
        System.out.println("Recorded 32768 actual Frozen parent metadata pairs; source class " + sourceHash);
    }
}
