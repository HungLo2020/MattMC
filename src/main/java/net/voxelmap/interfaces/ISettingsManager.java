package net.voxelmap.interfaces;

import net.voxelmap.gui.overridden.EnumOptionsMinimap;

public interface ISettingsManager {
    String getKeyText(EnumOptionsMinimap options);

    void setOptionFloatValue(EnumOptionsMinimap options, float value);

    float getOptionFloatValue(EnumOptionsMinimap options);
}
