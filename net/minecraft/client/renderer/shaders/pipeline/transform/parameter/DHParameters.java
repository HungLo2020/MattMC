package net.minecraft.client.renderer.shaders.pipeline.transform.parameter;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.minecraft.client.renderer.shaders.texture.TextureType;
import net.minecraft.client.renderer.shaders.helpers.Tri;
import net.minecraft.client.renderer.shaders.pipeline.transform.Patch;
import net.minecraft.client.renderer.shaders.texture.TextureStage;

/**
 * Parameters for Distant Horizons shader transformation.
 * 
 * VERBATIM copy from IRIS.
 * Reference: frnsrc/Iris-1.21.9/common/src/main/java/net/irisshaders/iris/pipeline/transform/parameter/DHParameters.java
 */
public class DHParameters extends Parameters {
	public DHParameters(Patch patch, Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap) {
		super(patch, textureMap);
	}

	@Override
	public TextureStage getTextureStage() {
		return TextureStage.GBUFFERS_AND_SHADOW;
	}
}
