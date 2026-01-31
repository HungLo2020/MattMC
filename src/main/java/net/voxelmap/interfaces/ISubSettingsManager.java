package net.voxelmap.interfaces;

import java.io.File;
import java.io.PrintWriter;

public interface ISubSettingsManager extends ISettingsManager {
    void loadSettings(File settingsFile);

    void saveAll(PrintWriter out);
}
