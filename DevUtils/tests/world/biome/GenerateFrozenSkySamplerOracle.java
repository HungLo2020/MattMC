import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Random;
import java.util.function.Function;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.sodium.client.util.color.FastCubicSampler;

/** Actual unchanged Frozen CPU sky sampler, not a reimplementation oracle. */
public final class GenerateFrozenSkySamplerOracle {
    static void verify(String name, String expected) throws Exception {
        try (InputStream stream = GenerateFrozenSkySamplerOracle.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (stream == null || !HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes())).equals(expected))
                throw new IllegalStateException("Unexpected Frozen class: " + name);
        }
    }
    public static void main(String[] args) throws Exception {
        verify("net/sodium/client/util/color/FastCubicSampler", "c5953b0d886b668b6f217be8b5648baac56b9eac67b07826bd9c9768bca7a63c");
        verify("net/minecraft/world/phys/Vec3", "f7f2c2090e276e0ef5568f6bebb51bea5068a6dbd98bea6156ac024bfb20539b");
        verify("net/minecraft/util/Mth", "1df366798282efe8986eef5cf215253b2bed737c2caec2f53e74416ce764b470");
        Random random = new Random(0x534b594649454c44L);
        int cases = 256;
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(Path.of(args[0]))))) {
            out.writeInt(0x534b5931); out.writeInt(cases);
            for (int test = 0; test < cases; test++) {
                double[] position = new double[3];
                for (int axis = 0; axis < 3; axis++) {
                    position[axis] = switch (test % 8) {
                        case 0 -> -0.0;
                        case 1 -> -1234.0;
                        case 2 -> -0.0000000000000001;
                        case 3 -> Math.nextDown(16.0);
                        case 4 -> Math.nextUp(-16.0);
                        case 5 -> 7499999.75 - axis * 31.125;
                        case 6 -> -7499999.75 + axis * 31.125;
                        default -> random.nextInt(65536) - 32768 + random.nextDouble();
                    };
                    out.writeDouble(position[axis]);
                }
                int[] colors = new int[216];
                int constant = random.nextInt();
                for (int index = 0; index < colors.length; index++) {
                    colors[index] = switch (test % 7) {
                        case 0 -> constant;
                        case 1 -> 0;
                        case 2 -> -1;
                        case 3 -> (index % 2 == 0 ? 0xff000000 : 0x00000000) | 0x778899;
                        case 4 -> index % 2 == 0 ? 0xffff0000 : 0xff0000ff;
                        default -> random.nextInt();
                    };
                    out.writeInt(colors[index]);
                }
                int originX = Mth.floor(position[0]) - 2;
                int originY = Mth.floor(position[1]) - 2;
                int originZ = Mth.floor(position[2]) - 2;
                int[] calls = {0};
                Vec3 result = FastCubicSampler.sampleColor(new Vec3(position[0], position[1], position[2]),
                    (x,y,z) -> { calls[0]++; return colors[(z-originZ)*36+(y-originY)*6+(x-originX)]; }, Function.identity());
                if (calls[0] != 216) throw new IllegalStateException("Unexpected callback count");
                out.writeDouble(result.x); out.writeDouble(result.y); out.writeDouble(result.z);
            }
        }
        System.out.println("Recorded 256 actual Frozen identity-transform sky samples, including full-int homogeneous admission and negative/quart boundary coordinates");
    }
}
