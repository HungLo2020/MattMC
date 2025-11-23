package mattmc.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static mattmc.util.Validate.notEmpty;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

public final class AppPaths {
    private static final Logger logger = LoggerFactory.getLogger(AppPaths.class);
    
    private AppPaths() {}

    /** Returns the directory that contains the running JAR, or the classes dir when run from IDE. */
    public static Path jarBaseDir() {
        try {
            URI uri = AppPaths.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path p = Paths.get(uri).toRealPath(); // resolves symlinks
            if (Files.isRegularFile(p) && p.toString().endsWith(".jar")) {
                return p.getParent(); // …/lib
            }
            // Running from classes dir or an exploded install dir
            return Files.isDirectory(p) ? p : p.getParent();
        } catch (URISyntaxException | IOException e) {
            // Fallback to current working dir
            return Paths.get(".").toAbsolutePath().normalize();
        }
    }

    /** Creates <app-root>/<dirName> and returns it. Never null. 
     * App root is the parent of the lib directory (where bin/, lib/ are located). 
     * 
     * @param dirName Name of the directory to create (validated to prevent path traversal)
     * @throws IOException if directory creation fails
     * @throws IllegalArgumentException if dirName contains invalid characters
     */
    public static Path ensureDataDirInJarParent(String dirName) throws IOException {
        // Validate dirName to prevent path traversal
        notEmpty(dirName, "Directory name cannot be null or empty");
        if (dirName.contains("..") || dirName.contains("/") || dirName.contains("\\")) {
            throw new IllegalArgumentException("Directory name contains invalid characters: " + dirName);
        }
        
        Path jarDir = jarBaseDir();
        logger.debug("AppPaths.jarBaseDir() = {}", jarDir);
        logger.debug("jarDir.endsWith(\"lib\") = {}", jarDir.endsWith("lib"));
        
        // Determine the app root directory
        Path appRoot;
        if (jarDir.endsWith("lib")) {
            // Running from packaged distribution - go up one level from lib/
            appRoot = jarDir.getParent();
            logger.debug("Packaged mode: appRoot = {}", appRoot);
        } else if (jarDir.toString().contains("/build/classes/")) {
            // Running from Gradle :run task in dev mode
            // Path is like: /path/to/project/build/classes/java/main
            // Navigate to /path/to/project/build/install/MattMC/
            Path current = jarDir;
            while (current != null && !current.endsWith("build")) {
                current = current.getParent();
            }
            if (current != null) {
                // Use build/install/MattMC as the app root for dev builds
                appRoot = current.resolve("install").resolve("MattMC");
                logger.debug("Gradle dev mode: appRoot = {}", appRoot);
            } else {
                // Fallback if we can't find build directory
                appRoot = jarDir;
                logger.debug("Dev mode fallback: appRoot = {}", appRoot);
            }
        } else {
            // Running from IDE or other setup
            // Navigate up to find project root (where build/ directory would be)
            // and use build/install/MattMC as the app root
            Path current = jarDir;
            appRoot = null;
            while (current != null && current.getParent() != null) {
                Path buildDir = current.resolve("build");
                if (Files.exists(buildDir) && Files.isDirectory(buildDir)) {
                    appRoot = buildDir.resolve("install").resolve("MattMC");
                    logger.debug("IDE mode: found build dir, appRoot = {}", appRoot);
                    break;
                }
                current = current.getParent();
            }
            if (appRoot == null) {
                appRoot = jarDir;
                logger.debug("IDE fallback: appRoot = {}", appRoot);
            }
        }
        
        if (appRoot == null) {
            appRoot = jarDir; // last-resort fallback
            logger.debug("appRoot was null, using jarDir = {}", appRoot);
        }

        Path dataDir = appRoot.resolve(dirName);
        logger.debug("Final dataDir = {}", dataDir);
        
        // Verify the resolved path is within appRoot (extra safety check)
        if (!dataDir.normalize().startsWith(appRoot.normalize())) {
            throw new IllegalArgumentException("Resolved path is outside app root: " + dataDir);
        }
        
        Files.createDirectories(dataDir);

        // Try to set sane POSIX perms on Unix; ignore if unsupported (e.g., Windows).
        try {
            Set<PosixFilePermission> perms = EnumSet.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE,
                    PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE,
                    PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE
            );
            Files.setPosixFilePermissions(dataDir, perms);
        } catch (UnsupportedOperationException ignored) {}

        return dataDir;
    }
}
