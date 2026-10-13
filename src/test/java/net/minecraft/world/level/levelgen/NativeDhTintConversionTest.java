package net.minecraft.world.level.levelgen;

import java.io.File;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Render conversion colors tinted blocks in Rust from the cached biome tints.
 * Over recorded terrain, its output must equal Java's per-point colors at
 * blend radii 0, 1 and 3. Runs in a fresh JVM: the build classes capture the
 * render wrapper statically.
 */
class NativeDhTintConversionTest {
    @Test @Tag("parity") void nativeTintRouteMatchesJavaPerPointColors(@TempDir Path directory) throws Exception {
        var paths = new LinkedHashSet<String>(Arrays.asList(System.getProperty("java.class.path").split(File.pathSeparator)));
        for (var loader = getClass().getClassLoader(); loader != null; loader = loader.getParent())
            if (loader instanceof URLClassLoader urls) for (var url : urls.getURLs())
                if (url.getProtocol().equals("file")) paths.add(Path.of(url.toURI()).toString());
        var command = List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "--enable-native-access=ALL-UNNAMED", "-Xmx2g",
            "-Dmattmc.rust.natives.dir=" + System.getProperty("mattmc.rust.natives.dir"),
            "-cp", String.join(File.pathSeparator, paths), DhSourceVerification.class.getName(), "1", "1", "tintcheck");
        var log = directory.resolve("tint.log");
        var process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if (!process.waitFor(600, TimeUnit.SECONDS)) { process.destroyForcibly(); process.waitFor(); fail(Files.readString(log)); }
        assertEquals(0, process.exitValue(), Files.readString(log));
        assertTrue(Files.readString(log).contains("TINT ROUTES MATCH"), Files.readString(log));
    }
}
