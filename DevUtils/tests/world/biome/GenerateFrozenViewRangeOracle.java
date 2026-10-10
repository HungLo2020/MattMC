import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Records the actual Frozen client storage method, including Java int wrapping. */
public final class GenerateFrozenViewRangeOracle {
    public static void main(String[] args) throws Exception {
        String name = "net.minecraft.client.multiplayer.ClientChunkCache$Storage";
        Class<?> storage = Class.forName(name);
        try (InputStream stream = storage.getResourceAsStream("ClientChunkCache$Storage.class")) {
            String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
            if (!sha.equals("b7885ea3f3e9bbe00b7088afc2fbc615eca9f397731b9d5b9b0171239c867658"))
                throw new IllegalStateException("Unexpected Frozen storage class: " + sha);
        }
        Constructor<?> constructor = storage.getDeclaredConstructors()[0]; constructor.setAccessible(true);
        Field centerX = storage.getDeclaredField("viewCenterX"), centerZ = storage.getDeclaredField("viewCenterZ");
        centerX.setAccessible(true); centerZ.setAccessible(true);
        Method inRange = storage.getDeclaredMethod("inRange", int.class, int.class); inRange.setAccessible(true);
        Field unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe"); unsafeField.setAccessible(true);
        Object unsafe = unsafeField.get(null);
        Object enclosing = unsafe.getClass().getMethod("allocateInstance",Class.class)
            .invoke(unsafe,Class.forName("net.minecraft.client.multiplayer.ClientChunkCache"));
        int[] coordinates = {Integer.MIN_VALUE, Integer.MIN_VALUE + 1, -1875000, -1, 0, 1, 1875000, Integer.MAX_VALUE};
        int count = coordinates.length * coordinates.length * coordinates.length * coordinates.length * 3;
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(Path.of(args[0]))))) {
            out.writeInt(0x56525731); out.writeInt(count);
            for (int radius : new int[]{0,3,127}) {
                // Only the storage constructor runs; inRange never reads the unused enclosing cache.
                Object value = constructor.newInstance(enclosing, radius);
                for (int x : coordinates) for (int z : coordinates) {
                    centerX.setInt(value,x); centerZ.setInt(value,z);
                    for (int queryX : coordinates) for (int queryZ : coordinates) {
                        out.writeInt(radius); out.writeInt(x); out.writeInt(z); out.writeInt(queryX); out.writeInt(queryZ);
                        out.writeBoolean((boolean)inRange.invoke(value,queryX,queryZ));
                    }
                }
            }
        }
        System.out.println("Recorded " + count + " actual Frozen storage range cases; baseline behavior unchanged");
    }
}
