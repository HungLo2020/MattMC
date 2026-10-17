package net.minecraft.world.level.block;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class NativeBlockRegistryReadinessTest {
    @Tag("slow") @Test void earlyQueryDoesNotPermanentlyDisableRegistry(@TempDir Path directory) throws Exception {
        // Other suites may already have bootstrapped this worker. Exercise the
        // lifecycle in a fresh JVM using the worker's exact classes/dependencies.
        var paths=new LinkedHashSet<String>();
        paths.addAll(java.util.Arrays.asList(System.getProperty("java.class.path").split(java.io.File.pathSeparator)));
        for(var loader=getClass().getClassLoader();loader!=null;loader=loader.getParent()) {
            if(loader instanceof URLClassLoader urls)for(var url:urls.getURLs()) {
                if(url.getProtocol().equals("file"))paths.add(Path.of(url.toURI()).toString());
            }
        }
        assertFalse(paths.isEmpty());
        var command=new ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"),"bin","java").toString());
        command.add("--enable-native-access=ALL-UNNAMED");
        command.add("-Dmattmc.rust.natives.dir="+System.getProperty("mattmc.rust.natives.dir"));
        command.add("-cp");command.add(String.join(java.io.File.pathSeparator,paths));
        command.add(Probe.class.getName());
        var log=directory.resolve("readiness.log");
        var process=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if(!process.waitFor(60,TimeUnit.SECONDS)) {
            process.destroyForcibly();process.waitFor();fail("Registry readiness probe timed out: "+Files.readString(log));
        }
        assertEquals(0,process.exitValue(),Files.readString(log));
    }
    public static final class Probe {
        public static void main(String[] args) {
            if(NativeBlockRegistry.ready())throw new AssertionError("Registry ready before bootstrap");
            net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
            if(!NativeBlockRegistry.ready())throw new AssertionError("Early query poisoned native registry");
        }
    }
}
