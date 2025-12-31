package net.minecraft.client.model.geom.builders;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public class MaterialDefinition {
	final int xTexSize;
	final int yTexSize;

	public MaterialDefinition(int i, int j) {
		this.xTexSize = i;
		this.yTexSize = j;
	}
}
