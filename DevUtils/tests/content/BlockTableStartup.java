import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.NativeLibraryLoader;

/** One fresh-JVM sample of the one-time block-state table setup that the
 * Rust consumers need: from frozen registries to every table ready. Runs
 * against either tree's test classpath; the tree decides what exists.
 * Reference tree: each bridge builds its own table in Java. Candidate tree:
 * NativeBlockRegistry exports once and Rust derives every view. Output:
 * {@code BLOCK_TABLES_STARTUP tree=<reference|candidate> ns=<n> check=<c>}; a table
 * that did not build throws instead. */
public final class BlockTableStartup {
    private static ClassLoader loader;
    // The candidate's view-forcing calls, linked before timing: in production
    // each view is built by the consumer's own first call.
    private static MethodHandle skyTables, heightmapSection, protoChunkCreate;

    public static void main(String[] args) throws Throwable {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        loader = BlockTableStartup.class.getClassLoader();
        // Load the native library before timing; both trees have this symbol.
        NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_light_engine_create", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
        boolean candidate = exists("net.minecraft.world.level.block.NativeBlockRegistry");
        if (candidate) {
            skyTables = link("mattmc_skylight_sources_tables", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            heightmapSection = link("mattmc_heightmap_section", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            protoChunkCreate = link("mattmc_proto_chunk_create", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        }
        long start = System.nanoTime();
        long check = candidate ? candidate() : reference();
        long elapsed = System.nanoTime() - start;
        System.out.println("BLOCK_TABLES_STARTUP tree=" + (candidate ? "candidate" : "reference") + " ns=" + elapsed + " check=" + check);
    }

    private static void require(boolean ready, String table) {
        if (!ready) throw new IllegalStateException("Not built: " + table);
    }

    private static long reference() throws Throwable {
        long check = 1;
        require((long)field("net.minecraft.world.level.lighting.NativeLightPropagation$Tables", "HANDLE") != 0, "light");
        init("net.minecraft.world.level.chunk.NativeSkyLightSources");
        init("net.minecraft.world.level.chunk.NativeHeightmap");
        Method flags = Class.forName("net.minecraft.world.level.levelgen.NativeNoiseFill", true, loader).getDeclaredMethod("flags");
        flags.setAccessible(true);
        require(((MemorySegment)flags.invoke(null)).byteSize() > 0, "noise flags");
        init("net.minecraft.world.level.levelgen.NativeCarvers");
        require((long)field("net.minecraft.world.level.chunk.NativeChunkSections$Vocabulary", "HANDLE") != 0, "vocabulary");
        init("net.minecraft.world.level.chunk.NativePalettePacking$BlockLabels");
        return check;
    }

    private static long candidate() throws Throwable {
        long check = 1;
        Method ready = Class.forName("net.minecraft.world.level.block.NativeBlockRegistry", true, loader).getDeclaredMethod("ready");
        require((boolean)ready.invoke(null), "registry");
        require((long)field("net.minecraft.world.level.lighting.NativeLightPropagation$Tables", "HANDLE") != 0, "light");
        // Rust derives the remaining views on first use; force each one.
        init("net.minecraft.world.level.chunk.NativeSkyLightSources");
        require((int)skyTables.invokeExact(MemorySegment.NULL, 0, MemorySegment.NULL, 0) > 0, "skylight");
        init("net.minecraft.world.level.chunk.NativeHeightmap");
        int unused = (int)heightmapSection.invokeExact(MemorySegment.NULL, 0, MemorySegment.NULL, 0, MemorySegment.NULL, 0);
        init("net.minecraft.world.level.levelgen.NativeNoiseFill");
        long none = (long)protoChunkCreate.invokeExact(MemorySegment.NULL, 0, MemorySegment.NULL, 0, 0);
        init("net.minecraft.world.level.levelgen.NativeCarvers");
        require((long)field("net.minecraft.world.level.chunk.NativeChunkSections$Vocabulary", "HANDLE") != 0, "vocabulary");
        init("net.minecraft.world.level.chunk.NativePalettePacking");
        return check;
    }

    private static boolean exists(String name) {
        try {
            Class.forName(name, false, loader);
            return true;
        } catch (ClassNotFoundException missing) {
            return false;
        }
    }

    private static void init(String name) throws ClassNotFoundException {
        Class.forName(name, true, loader);
    }

    private static Object field(String owner, String name) throws ReflectiveOperationException {
        var field = Class.forName(owner, true, loader).getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    private static MethodHandle link(String symbol, FunctionDescriptor descriptor) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", symbol, descriptor);
    }
}
