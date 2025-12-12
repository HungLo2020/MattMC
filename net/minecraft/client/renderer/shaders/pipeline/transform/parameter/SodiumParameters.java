package net.minecraft.client.renderer.shaders.pipeline.transform.parameter;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.minecraft.client.renderer.shaders.gl.blending.AlphaTest;
import net.minecraft.client.renderer.shaders.texture.TextureType;
import net.minecraft.client.renderer.shaders.helpers.Tri;
import net.minecraft.client.renderer.shaders.pipeline.transform.Patch;
import net.minecraft.client.renderer.shaders.texture.TextureStage;

/**
 * Parameters for Sodium shader transformation.
 * 
 * VERBATIM copy from IRIS.
 * Reference: frnsrc/Iris-1.21.9/common/src/main/java/net/irisshaders/iris/pipeline/transform/parameter/SodiumParameters.java
 */
public class SodiumParameters extends Parameters {
	// WARNING: adding new fields requires updating hashCode and equals methods!

	// DO NOT include this field in hashCode or equals, it's mutable!
	// (See use of setAlphaFor in TransformPatcher)
	public final AlphaTest alpha;

	public SodiumParameters(Patch patch,
							Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap,
							AlphaTest alpha) {
		super(patch, textureMap);

		this.alpha = alpha;
	}

	@Override
	public AlphaTest getAlphaTest() {
		return alpha;
	}

	@Override
	public TextureStage getTextureStage() {
		return TextureStage.GBUFFERS_AND_SHADOW;
	}


	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((alpha == null) ? 0 : alpha.hashCode());
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
		SodiumParameters other = (SodiumParameters) obj;
		if (alpha == null) {
			return other.alpha == null;
		} else return alpha.equals(other.alpha);
	}
}
