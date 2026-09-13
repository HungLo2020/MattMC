package net.minecraft.client.renderer.entity.layers;

import com.google.common.collect.ImmutableMap;
import net.blaze3d.vertex.PoseStack;
import java.util.Map;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.model.IronGolemModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.IronGolemRenderState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Crackiness.Level;

@Environment(EnvType.CLIENT)
public class IronGolemCrackinessLayer extends RenderLayer<IronGolemRenderState, IronGolemModel> {
	private static final ResourceLocation IRON_GOLEM_CRACKS_IDENTITY = ResourceLocation.withDefaultNamespace("iron_golem_cracks");
	private static final Map<Level, ResourceLocation> resourceLocations = ImmutableMap.of(
		Level.LOW,
		ResourceLocation.withDefaultNamespace("textures/entity/iron_golem/iron_golem_crackiness_low.png"),
		Level.MEDIUM,
		ResourceLocation.withDefaultNamespace("textures/entity/iron_golem/iron_golem_crackiness_medium.png"),
		Level.HIGH,
		ResourceLocation.withDefaultNamespace("textures/entity/iron_golem/iron_golem_crackiness_high.png")
	);

	public IronGolemCrackinessLayer(RenderLayerParent<IronGolemRenderState, IronGolemModel> renderLayerParent) {
		super(renderLayerParent);
	}

	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, IronGolemRenderState ironGolemRenderState, float f, float g) {
		if (!ironGolemRenderState.isInvisible) {
			Level level = ironGolemRenderState.crackiness;
			if (level != Level.NONE) {
				ResourceLocation resourceLocation = (ResourceLocation)resourceLocations.get(level);
				IronGolemModel model = this.getParentModel(ironGolemRenderState);
				RenderType renderType = RenderType.entityCutoutNoCull(resourceLocation);
				if (net.vulkanic.world.WorldRenderRoutePolicy.currentModelMeshRoute(true).usesRustWholeFrameVulkan()) {
					boolean queued = net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueStandaloneModelMesh(
						model, ironGolemRenderState, poseStack.last(), renderType, resourceLocation,
						IRON_GOLEM_CRACKS_IDENTITY, i,
						LivingEntityRenderer.getOverlayCoords(ironGolemRenderState, 0.0F), -1,
						ironGolemRenderState.outlineColor
					);
					if (queued) {
						net.vulkanic.world.RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
							"rust-vulkan-whole-frame", resourceLocation, model.getClass().getName(),
							ironGolemRenderState.entityId, true, true, false
						);
						return;
					}
					net.vulkanic.world.RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
						"rust-vulkan-unavailable", resourceLocation, model.getClass().getName(),
						ironGolemRenderState.entityId, false, false, false
					);
					throw new IllegalStateException("Rust whole-frame iron-golem-cracks route has no copied semantic mesh");
				}
				renderColoredCutoutModel(model, resourceLocation, poseStack, submitNodeCollector, i, ironGolemRenderState, -1, 1);
			}
		}
	}
}
