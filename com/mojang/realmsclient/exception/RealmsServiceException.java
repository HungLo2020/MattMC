package com.mojang.realmsclient.exception;

import com.mojang.realmsclient.client.RealmsError;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public class RealmsServiceException extends Exception {
	public final RealmsError realmsError;

	public RealmsServiceException(RealmsError realmsError) {
		this.realmsError = realmsError;
	}

	public String getMessage() {
		return this.realmsError.logMessage();
	}
}
