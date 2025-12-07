package com.mojang.realmsclient.exception;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public class RealmsHttpException extends RuntimeException {
	public RealmsHttpException(String string, Exception exception) {
		super(string, exception);
	}
}
