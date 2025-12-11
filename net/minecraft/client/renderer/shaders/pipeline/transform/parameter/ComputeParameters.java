package net.minecraft.client.renderer.shaders.pipeline.transform.parameter;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.minecraft.client.renderer.shaders.texture.TextureType;
import net.minecraft.client.renderer.shaders.helpers.Tri;
import net.minecraft.client.renderer.shaders.pipeline.transform.Patch;
import net.minecraft.client.renderer.shaders.texture.TextureStage;

/**
 * Parameters for compute shader transformation.
 * 
 * VERBATIM copy from IRIS.
 * Reference: frnsrc/Iris-1.21.9/common/src/main/java/net/irisshaders/iris/pipeline/transform/parameter/ComputeParameters.java
 */
public class ComputeParameters extends TextureStageParameters {
	// WARNING: adding new fields requires updating hashCode and equals methods!

	public ComputeParameters(Patch patch, TextureStage stage,
							 Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap) {
		super(patch, stage, textureMap);
	}

	// since this class has no fields, hashCode() and equals() are inherited from
	// TextureStageParameters
}
