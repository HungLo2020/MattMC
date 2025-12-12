package net.minecraft.client.renderer.shaders.pipeline.transform.parameter;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.minecraft.client.renderer.shaders.texture.TextureType;
import net.minecraft.client.renderer.shaders.helpers.Tri;
import net.minecraft.client.renderer.shaders.pipeline.transform.Patch;
import net.minecraft.client.renderer.shaders.texture.TextureStage;

/**
 * Parameters for texture stage-based shader transformation.
 * 
 * VERBATIM copy from IRIS.
 * Reference: frnsrc/Iris-1.21.9/common/src/main/java/net/irisshaders/iris/pipeline/transform/parameter/TextureStageParameters.java
 */
public class TextureStageParameters extends Parameters {
	private final TextureStage stage;
	// WARNING: adding new fields requires updating hashCode and equals methods!

	public TextureStageParameters(Patch patch, TextureStage stage,
								  Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap) {
		super(patch, textureMap);
		this.stage = stage;
	}

	@Override
	public TextureStage getTextureStage() {
		return stage;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((stage == null) ? 0 : stage.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		TextureStageParameters other = (TextureStageParameters) obj;
		return stage == other.stage;
	}
}
