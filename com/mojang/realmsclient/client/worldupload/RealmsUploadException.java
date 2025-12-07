package com.mojang.realmsclient.client.worldupload;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public abstract class RealmsUploadException extends RuntimeException {
	@Nullable
	public Component getStatusMessage() {
		return null;
	}

	@Nullable
	public Component[] getErrorMessages() {
		return null;
	}
}
