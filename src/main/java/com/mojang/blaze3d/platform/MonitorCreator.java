package com.mojang.blaze3d.platform;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public interface MonitorCreator {
	Monitor createMonitor(long l);
}
