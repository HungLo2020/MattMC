package net.minecraft.client.renderer.feature;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;

@Environment(EnvType.CLIENT)
public class TextFeatureRenderer {
	public void render(SubmitNodeCollection submitNodeCollection, MultiBufferSource.BufferSource bufferSource) {
		Font font = Minecraft.getInstance().font;
		// Iris: Track block entity state (from MixinTextFeatureRenderer)
		boolean hasBE = false;

		for (SubmitNodeStorage.TextSubmit textSubmit : submitNodeCollection.getTextSubmits()) {
			// Iris: Set model storage state before rendering (from MixinTextFeatureRenderer)
			((net.irisshaders.iris.mixinterface.ModelStorage) (Object) textSubmit).iris$set();
			if (((net.irisshaders.iris.mixinterface.ModelStorage) (Object) textSubmit).iris$wasBE()) {
				hasBE = true;
				net.irisshaders.iris.vertices.ImmediateState.isRenderingBEs = true;
			} else if (hasBE) {
				hasBE = false;
				net.irisshaders.iris.vertices.ImmediateState.isRenderingBEs = false;
			}
			
			if (textSubmit.outlineColor() == 0) {
				font.drawInBatch(
					textSubmit.string(),
					textSubmit.x(),
					textSubmit.y(),
					textSubmit.color(),
					textSubmit.dropShadow(),
					textSubmit.pose(),
					bufferSource,
					textSubmit.displayMode(),
					textSubmit.backgroundColor(),
					textSubmit.lightCoords()
				);
			} else {
				font.drawInBatch8xOutline(
					textSubmit.string(),
					textSubmit.x(),
					textSubmit.y(),
					textSubmit.color(),
					textSubmit.outlineColor(),
					textSubmit.pose(),
					bufferSource,
					textSubmit.lightCoords()
				);
			}
		}
		
		// Iris: Clear rendering state (from MixinTextFeatureRenderer)
		net.irisshaders.iris.uniforms.CapturedRenderingState.INSTANCE.setCurrentRenderedItem(0);
		net.irisshaders.iris.uniforms.CapturedRenderingState.INSTANCE.setCurrentEntity(0);
		net.irisshaders.iris.uniforms.CapturedRenderingState.INSTANCE.setCurrentBlockEntity(0);
		net.irisshaders.iris.vertices.ImmediateState.isRenderingBEs = false;
	}
}
