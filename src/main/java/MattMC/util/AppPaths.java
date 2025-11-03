package MattMC.util;

import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

public final class AppPaths {
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
        } catch (Exception e) {
            // Fallback to current working dir
            return Paths.get(".").toAbsolutePath().normalize();
        }
    }

    /** Creates <app-root>/<dirName> and returns it. Never null. 
     * App root is the parent of the lib directory (where bin/, lib/ are located). */
    public static Path ensureDataDirInJarParent(String dirName) throws IOException {
        Path jarDir = jarBaseDir();
        
        // Determine the app root directory
        Path appRoot;
        if (jarDir.endsWith("lib")) {
            // Running from packaged distribution - go up one level from lib/
            appRoot = jarDir.getParent();
        } else {
            // Running from IDE or other setup - use jarDir itself
            appRoot = jarDir;
        }
        
        if (appRoot == null) appRoot = jarDir; // last-resort fallback

        Path dataDir = appRoot.resolve(dirName);
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
