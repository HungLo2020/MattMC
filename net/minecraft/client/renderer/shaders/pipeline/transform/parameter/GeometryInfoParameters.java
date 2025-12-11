package net.minecraft.client.renderer.shaders.pipeline.transform.parameter;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.minecraft.client.renderer.shaders.texture.TextureType;
import net.minecraft.client.renderer.shaders.helpers.Tri;
import net.minecraft.client.renderer.shaders.pipeline.transform.Patch;
import net.minecraft.client.renderer.shaders.texture.TextureStage;

/**
 * Parameters for shaders with geometry stage.
 * 
 * VERBATIM copy from IRIS.
 * Reference: frnsrc/Iris-1.21.9/common/src/main/java/net/irisshaders/iris/pipeline/transform/parameter/GeometryInfoParameters.java
 */
public abstract class GeometryInfoParameters extends Parameters {
	public final boolean hasGeometry;
	public final boolean hasTesselation;
	// WARNING: adding new fields requires updating hashCode and equals methods!

	public GeometryInfoParameters(Patch patch,
								  Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap, boolean hasGeometry, boolean hasTesselation) {
		super(patch, textureMap);
		this.hasGeometry = hasGeometry;
		this.hasTesselation = hasTesselation;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + (hasGeometry ? 1231 : 1237);
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
		GeometryInfoParameters other = (GeometryInfoParameters) obj;
		return hasGeometry == other.hasGeometry;
	}
}
