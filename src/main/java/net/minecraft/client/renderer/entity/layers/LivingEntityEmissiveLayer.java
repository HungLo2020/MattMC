package net.minecraft.client.renderer.entity.layers;

import net.blaze3d.vertex.PoseStack;
import java.util.function.Function;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;

@Environment(EnvType.CLIENT)
public class LivingEntityEmissiveLayer<S extends LivingEntityRenderState, M extends EntityModel<S>> extends RenderLayer<S, M> {
	private final Function<S, ResourceLocation> textureProvider;
	private final ResourceLocation semanticIdentity;
	private final LivingEntityEmissiveLayer.AlphaFunction<S> alphaFunction;
	private final M model;
	private final Function<ResourceLocation, RenderType> bufferProvider;
	private final boolean alwaysVisible;

	public LivingEntityEmissiveLayer(
		RenderLayerParent<S, M> renderLayerParent,
		Function<S, ResourceLocation> function,
		ResourceLocation semanticIdentity,
		LivingEntityEmissiveLayer.AlphaFunction<S> alphaFunction,
		M entityModel,
		Function<ResourceLocation, RenderType> function2,
		boolean bl
	) {
		super(renderLayerParent);
		this.textureProvider = function;
		this.semanticIdentity = semanticIdentity;
		this.alphaFunction = alphaFunction;
		this.model = entityModel;
		this.bufferProvider = function2;
		this.alwaysVisible = bl;
	}

	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, S livingEntityRenderState, float f, float g) {
		if (!livingEntityRenderState.isInvisible || this.alwaysVisible) {
			float h = this.alphaFunction.apply(livingEntityRenderState, livingEntityRenderState.ageInTicks);
			if (!(h <= 1.0E-5F)) {
				int j = ARGB.white(h);
				ResourceLocation texture = (ResourceLocation)this.textureProvider.apply(livingEntityRenderState);
				RenderType renderType = (RenderType)this.bufferProvider.apply(texture);
				boolean rustWholeFrame = net.vulkanic.world.WorldRenderRoutePolicy.currentModelMeshRoute(true).usesRustWholeFrameVulkan();
				if (rustWholeFrame && net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueStandaloneTranslucentModelMesh(
					this.model, livingEntityRenderState, poseStack.last(), renderType, texture, this.semanticIdentity,
					i, LivingEntityRenderer.getOverlayCoords(livingEntityRenderState, 0.0F), j, livingEntityRenderState.outlineColor
				)) {
					net.vulkanic.world.RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
						"rust-vulkan-whole-frame", texture, this.model.getClass().getName(), livingEntityRenderState.entityId,
						true, true, false
					);
					return;
				}
				if (rustWholeFrame) {
					net.vulkanic.world.RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
						"rust-vulkan-unavailable", texture, this.model.getClass().getName(), livingEntityRenderState.entityId,
						false, false, false
					);
					throw new IllegalStateException("Rust whole-frame emissive layer has no semantic mesh: " + this.semanticIdentity);
				}
				submitNodeCollector.order(1)
					.submitModelSemanticTexture(
						this.model,
						livingEntityRenderState,
						poseStack,
						renderType,
						i,
						LivingEntityRenderer.getOverlayCoords(livingEntityRenderState, 0.0F),
						j,
						texture,
						livingEntityRenderState.outlineColor,
						null
					);
			}
		}
	}

	@Environment(EnvType.CLIENT)
	public interface AlphaFunction<S extends LivingEntityRenderState> {
		float apply(S livingEntityRenderState, float f);
	}
}
