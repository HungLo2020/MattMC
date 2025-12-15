package net.caffeinemc.mods.sodium.client.compatibility.workarounds.nvidia;

import net.caffeinemc.mods.sodium.client.compatibility.environment.GlContextInfo;
import net.caffeinemc.mods.sodium.client.compatibility.environment.OsUtils;
import net.caffeinemc.mods.sodium.client.compatibility.environment.OsUtils.OperatingSystem;
import net.caffeinemc.mods.sodium.client.compatibility.environment.probe.GraphicsAdapterProbe;
import net.caffeinemc.mods.sodium.client.compatibility.environment.probe.GraphicsAdapterVendor;
import net.caffeinemc.mods.sodium.client.compatibility.workarounds.Workarounds;
import net.caffeinemc.mods.sodium.client.platform.unix.Libc;
import net.caffeinemc.mods.sodium.client.platform.windows.WindowsCommandLine;
import net.caffeinemc.mods.sodium.client.platform.windows.WindowsFileVersion;
import net.caffeinemc.mods.sodium.client.platform.windows.api.d3dkmt.D3DKMT;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.KHRDebug;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NvidiaWorkarounds {
    private static final Logger LOGGER = LoggerFactory.getLogger("Sodium-NvidiaWorkarounds");

    public static boolean isNvidiaGraphicsCardPresent() {
        return GraphicsAdapterProbe.getAdapters()
                .stream()
                .anyMatch(adapter -> adapter.vendor() == GraphicsAdapterVendor.NVIDIA);
    }

    // https://github.com/CaffeineMC/sodium/issues/1486
    // The way which NVIDIA tries to detect the Minecraft process could not be circumvented until fairly recently
    // So we require that an up-to-date graphics driver is installed so that our workarounds can disable the Threaded
    // Optimizations driver hack.
    public static @Nullable WindowsFileVersion findNvidiaDriverMatchingBug1486() {
        if (OsUtils.getOs() != OperatingSystem.WIN) {
            return null;
        }

        for (var adapter : GraphicsAdapterProbe.getAdapters()) {
            if (adapter.vendor() != GraphicsAdapterVendor.NVIDIA) {
                continue;
            }

            if (adapter instanceof D3DKMT.WDDMAdapterInfo wddmAdapterInfo) {
                var driverVersion = wddmAdapterInfo.openglIcdVersion();

                if (driverVersion.z() == 15) { // Only match 5XX.XX drivers
                    // Broken in x.y.15.2647 (526.47)
                    // Fixed in x.y.15.3623 (536.23)
                    if (driverVersion.w() >= 2647 && driverVersion.w() < 3623) {
                        return driverVersion;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Detects if NVIDIA threaded optimizations workaround is needed on Linux.
     * The issue affects drivers before 536.23. We use version 536 as the threshold
     * since Linux and Windows drivers are generally aligned in their major versions.
     * 
     * @return true if the workaround should be applied, false if driver is new enough
     */
    public static boolean isLinuxNvidiaThreadedOptimizationsWorkaroundNeeded() {
        if (OsUtils.getOs() != OperatingSystem.LINUX) {
            return false;
        }

        var driverVersion = getLinuxNvidiaDriverVersion();
        if (driverVersion == null) {
            // If we can't determine the driver version, apply the workaround to be safe
            LOGGER.warn("Unable to determine NVIDIA driver version on Linux. Applying threaded optimizations workaround as a precaution.");
            return true;
        }

        LOGGER.info("Detected NVIDIA Linux driver version: {}", driverVersion);

        // Parse major version from the driver string (e.g., "560.35.03" -> 560)
        try {
            var parts = driverVersion.split("\\.");
            if (parts.length > 0) {
                int majorVersion = Integer.parseInt(parts[0]);
                
                // The fix was introduced in version 536.23
                // We use 536 as the threshold since the issue affects all drivers before this
                if (majorVersion >= 536) {
                    LOGGER.info("NVIDIA driver version {} is new enough to not require threaded optimizations workaround", driverVersion);
                    return false;
                } else {
                    LOGGER.warn("NVIDIA driver version {} is older than 536 and may have threaded optimizations issues", driverVersion);
                    return true;
                }
            }
        } catch (NumberFormatException e) {
            LOGGER.warn("Failed to parse NVIDIA driver version: {}", driverVersion, e);
        }

        // If parsing failed, apply the workaround to be safe
        return true;
    }

    /**
     * Attempts to retrieve the NVIDIA driver version on Linux from various sources.
     * 
     * @return the driver version string (e.g., "560.35.03"), or null if not found
     */
    private static @Nullable String getLinuxNvidiaDriverVersion() {
        // Method 1: Try /proc/driver/nvidia/version
        var procVersion = getDriverVersionFromProc();
        if (procVersion != null) {
            return procVersion;
        }

        // Method 2: Try nvidia-smi command
        var smiVersion = getDriverVersionFromNvidiaSmi();
        if (smiVersion != null) {
            return smiVersion;
        }

        // Method 3: Try modinfo command
        var modinfoVersion = getDriverVersionFromModinfo();
        if (modinfoVersion != null) {
            return modinfoVersion;
        }

        return null;
    }

    private static @Nullable String getDriverVersionFromProc() {
        try {
            var versionFile = java.nio.file.Path.of("/proc/driver/nvidia/version");
            if (java.nio.file.Files.exists(versionFile)) {
                var content = java.nio.file.Files.readString(versionFile);
                // Format: "NVRM version: NVIDIA UNIX x86_64 Kernel Module  560.35.03  Tue Oct  1 16:14:14 UTC 2024"
                // Extract version using regex
                var matcher = java.util.regex.Pattern.compile("(\\d+\\.\\d+(?:\\.\\d+)?)").matcher(content);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to read NVIDIA driver version from /proc/driver/nvidia/version", e);
        }
        return null;
    }

    private static @Nullable String getDriverVersionFromNvidiaSmi() {
        try {
            var process = Runtime.getRuntime().exec(new String[]{"nvidia-smi", "--query-gpu=driver_version", "--format=csv,noheader"});
            var result = process.waitFor();
            
            if (result == 0) {
                try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                    var version = reader.readLine();
                    if (version != null && !version.trim().isEmpty()) {
                        return version.trim();
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to get NVIDIA driver version from nvidia-smi", e);
        }
        return null;
    }

    private static @Nullable String getDriverVersionFromModinfo() {
        try {
            var process = Runtime.getRuntime().exec(new String[]{"modinfo", "nvidia"});
            var result = process.waitFor();
            
            if (result == 0) {
                try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("version:")) {
                            var version = line.substring("version:".length()).trim();
                            // modinfo returns version in format "560.35.03" or similar
                            return version;
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to get NVIDIA driver version from modinfo", e);
        }
        return null;
    }

    public static void applyEnvironmentChanges() {
        // We can't know if the OpenGL context will actually be initialized using the NVIDIA ICD, but we need to
        // modify the process environment *now* otherwise the driver will initialize with bad settings. For non-NVIDIA
        // drivers, these workarounds are not likely to cause issues.
        if (!isNvidiaGraphicsCardPresent()) {
            return;
        }

        LOGGER.info("Modifying process environment to apply workarounds for the NVIDIA graphics driver...");

        try {
            if (OsUtils.getOs() == OperatingSystem.WIN) {
                applyEnvironmentChanges$Windows();
            } else if (OsUtils.getOs() == OperatingSystem.LINUX) {
                applyEnvironmentChanges$Linux();
            }
        } catch (Throwable t) {
            LOGGER.error("Failed to modify the process environment", t);
            logWarning();
        }
    }


    private static void applyEnvironmentChanges$Windows() {
        // The NVIDIA drivers rely on parsing the command line arguments to detect Minecraft. We need to
        // make sure that it detects the game so that *some* important optimizations are applied. Later,
        // we will try to enable GL_DEBUG_OUTPUT_SYNCHRONOUS so that "Threaded Optimizations" cannot
        // be enabled.
        WindowsCommandLine.setCommandLine("net.caffeinemc.sodium / net.minecraft.client.main.Main /");
    }

    private static void applyEnvironmentChanges$Linux() {
        // Unlike Windows, we can just request that it not use threaded optimizations instead.
        Libc.setEnvironmentVariable("__GL_THREADED_OPTIMIZATIONS", "0");
    }

    public static void undoEnvironmentChanges() {
        if (OsUtils.getOs() == OperatingSystem.WIN) {
            undoEnvironmentChanges$Windows();
        }
    }

    private static void undoEnvironmentChanges$Windows() {
        WindowsCommandLine.resetCommandLine();
    }

    public static void applyContextChanges(GlContextInfo context) {
        // The context may not have been initialized with the NVIDIA ICD, even if we think there is an NVIDIA
        // graphics adapter in use. Because enabling these workarounds have the potential to severely hurt performance
        // on other drivers, make sure we exit now.
        if (GraphicsAdapterVendor.fromContext(context) != GraphicsAdapterVendor.NVIDIA) {
            return;
        }

        LOGGER.info("Modifying OpenGL context to apply workarounds for the NVIDIA graphics driver...");

        if (Workarounds.isWorkaroundEnabled(Workarounds.Reference.NVIDIA_THREADED_OPTIMIZATIONS_BROKEN)) {
            if (OsUtils.getOs() == OperatingSystem.WIN) {
                applyContextChanges$Windows();
            }
        }
    }

    private static void applyContextChanges$Windows() {
        // On Windows, the NVIDIA drivers do not have any environment variable to control whether
        // "Threaded Optimizations" are enabled. But we can enable the "GL_DEBUG_OUTPUT_SYNCHRONOUS" option to
        // achieve the same effect.
        var capabilities = GL.getCapabilities();

        if (capabilities.GL_KHR_debug) {
            LOGGER.info("Enabling GL_DEBUG_OUTPUT_SYNCHRONOUS to force the NVIDIA driver to disable threaded " +
                    "command submission");
            GL32C.glEnable(KHRDebug.GL_DEBUG_OUTPUT_SYNCHRONOUS);
        } else {
            LOGGER.error("GL_KHR_debug does not appear to be supported, unable to disable threaded " +
                    "command submission!");
            logWarning();
        }
    }

    private static void logWarning() {
        LOGGER.error("READ ME!");
        LOGGER.error("READ ME! The workarounds for the NVIDIA Graphics Driver did not apply correctly!");
        LOGGER.error("READ ME! You are very likely going to run into unexplained crashes and severe performance issues.");
        LOGGER.error("READ ME! More information about what went wrong can be found above this message.");
        LOGGER.error("READ ME!");
        LOGGER.error("READ ME! Please help us understand why this problem occurred by opening a bug report on our issue tracker:");
        LOGGER.error("READ ME!   https://github.com/CaffeineMC/sodium/issues");
        LOGGER.error("READ ME!");

    }
}
