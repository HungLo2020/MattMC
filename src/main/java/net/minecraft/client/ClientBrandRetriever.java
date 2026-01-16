package net.minecraft.client;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public class ClientBrandRetriever {
	public static final String VANILLA_NAME = "vanilla";

	public static String getClientModName() {
		return "vanilla";
	}
}
