import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.util.HexFormat;
import net.minecraft.world.level.chunk.DataLayer;

/** Record the unchanged Frozen class, never the migrated implementation. */
public final class GenerateFrozenLightLayerOracle {
    private static final String FROZEN_CLASS_SHA = "fa57ffc1904d3844c390aeccff1cdfe8f88f7f3b1fa62b15a1d1e59b31b3f795";
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected output file");
        try (var source = DataLayer.class.getResourceAsStream("DataLayer.class")) {
            String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.readAllBytes()));
            if (!sha.equals(FROZEN_CLASS_SHA)) throw new IllegalStateException("Not the recorded Frozen DataLayer class: " + sha);
        }
        int[] defaults = {0, 1, 15, 16, 255, -1, 31, Integer.MIN_VALUE, Integer.MAX_VALUE};
        Field rawDefault = DataLayer.class.getDeclaredField("defaultValue");
        rawDefault.setAccessible(true);
        try (var out = new DataOutputStream(Files.newOutputStream(Path.of(args[0])))) {
            out.writeInt(0x4c415952); out.writeInt(defaults.length * 12);
            for (int initial : defaults) {
                DataLayer layer = new DataLayer(initial);
                for (int operation = 0; operation < 12; operation++) {
                    switch (operation) {
                        case 1, 3, 10 -> layer = layer.copy();
                        case 2 -> layer.set(0, 0, 0, 17);
                        case 4 -> layer.set(15, 15, 15, -2);
                        case 5 -> { var previous = layer; layer = layer.copy(); previous.fill(7); }
                        case 6 -> layer.fill(31);
                        case 7, 11 -> layer.getData();
                        case 8 -> layer.set(7, 9, 13, -81);
                        case 9 -> layer.fill(0);
                    }
                    int[] values = new int[4096];
                    int runs = 0;
                    for (int i = 0; i < values.length; i++) {
                        values[i] = layer.get(i & 15, i >>> 8, (i >>> 4) & 15);
                        if (i == 0 || values[i] != values[i - 1]) runs++;
                    }
                    out.writeInt(initial); out.writeInt(operation); out.writeInt(rawDefault.getInt(layer));
                    out.writeBoolean(!layer.isDefinitelyHomogenous()); out.writeShort(runs);
                    for (int i = 0; i < values.length;) {
                        int end = i + 1;
                        while (end < values.length && values[end] == values[i]) end++;
                        out.writeShort(end - i); out.writeInt(values[i]); i = end;
                    }
                }
            }
        }
    }
}
