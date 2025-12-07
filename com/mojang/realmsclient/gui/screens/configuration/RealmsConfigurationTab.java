package com.mojang.realmsclient.gui.screens.configuration;

import com.mojang.realmsclient.dto.RealmsServer;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public interface RealmsConfigurationTab {
	void updateData(RealmsServer realmsServer);

	default void onSelected(RealmsServer realmsServer) {
	}

	default void onDeselected(RealmsServer realmsServer) {
	}
}
