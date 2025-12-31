package net.minecraft.client.main;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public class SilentInitException extends RuntimeException {
	public SilentInitException(String string) {
		super(string);
	}

	public SilentInitException(String string, Throwable throwable) {
		super(string, throwable);
	}
}
