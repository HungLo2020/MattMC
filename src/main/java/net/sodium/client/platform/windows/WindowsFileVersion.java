package net.sodium.client.platform.windows;

import net.sodium.client.platform.windows.api.version.VersionFixedFileInfoStruct;
import org.jetbrains.annotations.NotNull;

public record WindowsFileVersion(int x, int y, int z, int w) {
    public static @NotNull WindowsFileVersion fromFileVersion(VersionFixedFileInfoStruct fileVersion) {
        int x = (fileVersion.getFileVersionMostSignificantBits() >>> 16) & 0xffff;
        int y = (fileVersion.getFileVersionMostSignificantBits()) & 0xffff;
        int z = (fileVersion.getFileVersionLeastSignificantBits() >>> 16) & 0xffff;
        int w = (fileVersion.getFileVersionLeastSignificantBits()) & 0xffff;

        return new WindowsFileVersion(x, y, z, w);
    }

    @Override
    public @NotNull String toString() {
        return "%s.%s.%s.%s".formatted(this.x, this.y, this.z, this.w);
    }
}
