package net.minecraft.client.model.geom.builders;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@FunctionalInterface
@Environment(EnvType.CLIENT)
public interface MeshTransformer {
	MeshTransformer IDENTITY = meshDefinition -> meshDefinition;

	static MeshTransformer scaling(float f) {
		float g = 24.016F * (1.0F - f);
		return meshDefinition -> meshDefinition.transformed(partPose -> partPose.scaled(f).translated(0.0F, g, 0.0F));
	}

	MeshDefinition apply(MeshDefinition meshDefinition);
}
