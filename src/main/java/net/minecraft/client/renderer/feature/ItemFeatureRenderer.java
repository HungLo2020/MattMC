package net.minecraft.client.renderer.feature;

import net.blaze3d.vertex.PoseStack;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.sodium.client.render.frapi.render.ItemRenderContext;
import net.sodium.client.render.frapi.render.MeshItemCommand;
import net.sodium.client.render.frapi.render.SubmitNodeCollectionExtension;

@Environment(EnvType.CLIENT)
public class ItemFeatureRenderer {
	private final PoseStack poseStack = new PoseStack();
	// Sodium FRAPI: Item render context (merged from ItemFeatureRendererMixin)
	private final ItemRenderContext itemRenderContext = new ItemRenderContext();

	public void render(SubmitNodeCollection submitNodeCollection, MultiBufferSource.BufferSource bufferSource, OutlineBufferSource outlineBufferSource) {
		for (SubmitNodeStorage.ItemSubmit itemSubmit : submitNodeCollection.getItemSubmits()) {
			// Iris: Set model storage before rendering
			((net.irisshaders.iris.mixinterface.ModelStorage) (Object) itemSubmit).iris$set();
			
			this.poseStack.pushPose();
			this.poseStack.last().set(itemSubmit.pose());
			ItemRenderer.renderItem(
				itemSubmit.displayContext(),
				this.poseStack,
				bufferSource,
				itemSubmit.lightCoords(),
				itemSubmit.overlayCoords(),
				itemSubmit.tintLayers(),
				itemSubmit.quads(),
				itemSubmit.renderType(),
				itemSubmit.foilType()
			);
			if (itemSubmit.outlineColor() != 0) {
				outlineBufferSource.setColor(itemSubmit.outlineColor());
				ItemRenderer.renderItem(
					itemSubmit.displayContext(),
					this.poseStack,
					outlineBufferSource,
					itemSubmit.lightCoords(),
					itemSubmit.overlayCoords(),
					itemSubmit.tintLayers(),
					itemSubmit.quads(),
					itemSubmit.renderType(),
					ItemStackRenderState.FoilType.NONE
				);
			}

			this.poseStack.popPose();
		}
		
		// Sodium FRAPI: Render mesh item commands (merged from ItemFeatureRendererMixin)
		for (MeshItemCommand itemCommand : ((SubmitNodeCollectionExtension) submitNodeCollection).sodium_getMeshItemCommands()) {
			poseStack.pushPose();
			poseStack.last().set(itemCommand.positionMatrix());

			itemRenderContext.renderItem(itemCommand.displayContext(), poseStack, bufferSource, itemCommand.lightCoords(), itemCommand.overlayCoords(), itemCommand.tintLayers(), itemCommand.quads(), itemCommand.mesh(), itemCommand.renderType(), itemCommand.glintType(), false);

			if (itemCommand.outlineColor() != 0) {
				outlineBufferSource.setColor(itemCommand.outlineColor());
				itemRenderContext.renderItem(itemCommand.displayContext(), poseStack, outlineBufferSource, itemCommand.lightCoords(), itemCommand.overlayCoords(), itemCommand.tintLayers(), itemCommand.quads(), itemCommand.mesh(), itemCommand.renderType(), ItemStackRenderState.FoilType.NONE, true);
			}

			poseStack.popPose();
		}
		
		// Iris: Clear captured rendering state
		net.irisshaders.iris.uniforms.CapturedRenderingState.INSTANCE.setCurrentRenderedItem(0);
		net.irisshaders.iris.uniforms.CapturedRenderingState.INSTANCE.setCurrentEntity(0);
		net.irisshaders.iris.uniforms.CapturedRenderingState.INSTANCE.setCurrentBlockEntity(0);
	}
}
