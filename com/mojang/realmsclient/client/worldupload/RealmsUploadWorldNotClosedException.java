package com.mojang.realmsclient.client.worldupload;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public class RealmsUploadWorldNotClosedException extends RealmsUploadException {
	@Override
	public Component getStatusMessage() {
		return Component.translatable("mco.upload.close.failure");
	}
}
