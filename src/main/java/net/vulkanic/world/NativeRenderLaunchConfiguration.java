package net.vulkanic.world;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.ValueLayout;
import net.minecraft.util.NativeLibraryLoader;

/** Immutable process configuration is decoded once in Rust. Runtime game options
 * and diagnostic system properties remain live inputs at their original callsites. */
public final class NativeRenderLaunchConfiguration {
    private static final int FLAGS = load();
    private NativeRenderLaunchConfiguration() {}
    private static int load() {
        var query = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_render_launch_configuration", FunctionDescriptor.of(ValueLayout.JAVA_INT));
        try { return (int) query.invokeExact(); }
        catch (Throwable failure) { throw new ExceptionInInitializerError(failure); }
    }
    public static boolean selectedSourceExecutionRequested() { return (FLAGS & 1) != 0; }
    public static boolean graphicsAuditEnabled() { return (FLAGS & 2) != 0; }
    public static boolean hasSelectedSourceOverride() { return (FLAGS & 4) != 0; }
}
