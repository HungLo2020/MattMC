package net.irisshaders.iris.pipeline.transform.parameter;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.iris.gl.texture.TextureType;
import net.iris.helpers.Tri;
import net.iris.pipeline.transform.Patch;
import net.iris.shaderpack.texture.TextureStage;

public class ComputeParameters extends TextureStageParameters {
	// WARNING: adding new fields requires updating hashCode and equals methods!

	public ComputeParameters(Patch patch, TextureStage stage,
							 Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap) {
		super(patch, stage, textureMap);
	}

	// since this class has no fields, hashCode() and equals() are inherited from
	// TextureStageParameters
}
