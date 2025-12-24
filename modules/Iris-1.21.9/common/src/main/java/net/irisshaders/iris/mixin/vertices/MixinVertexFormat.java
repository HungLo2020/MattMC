package net.irisshaders.iris.mixin.vertices;

import com.google.common.collect.ImmutableSet;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.iris.pipeline.programs.VertexFormatExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;

/**
 * Ensures that the correct state for the extended vertex format is set up when needed.
 */
@Mixin(VertexFormat.class)
public abstract class MixinVertexFormat implements VertexFormatExtension {
	@Shadow
	public abstract List<String> getElementAttributeNames();

	@Unique
	private static final ImmutableSet<String> ATTRIBUTE_LIST = ImmutableSet.of("Position", "Color", "Normal", "UV0", "UV1", "UV2");

	@Override
	public void bindAttributesIris(boolean isFallback, int i) {
		int j = 0;

		for (String string : this.getElementAttributeNames()) {
			//Iris.logger.warn("Binding " + string + " to " + j);
			GlStateManager._glBindAttribLocation(i, j, ATTRIBUTE_LIST.contains(string) && !isFallback ? "iris_" + string : string);
			j++;
		}
	}
}
