package net.minecraft.client.renderer.entity.layers;

import net.blaze3d.vertex.PoseStack;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.TropicalFishModelA;
import net.minecraft.client.model.TropicalFishModelB;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.TropicalFishRenderState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.TropicalFish.Pattern;

@Environment(EnvType.CLIENT)
public class TropicalFishPatternLayer extends RenderLayer<TropicalFishRenderState, EntityModel<TropicalFishRenderState>> {
	private static final ResourceLocation KOB_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/fish/tropical_a_pattern_1.png");
	private static final ResourceLocation SUNSTREAK_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/fish/tropical_a_pattern_2.png");
	private static final ResourceLocation SNOOPER_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/fish/tropical_a_pattern_3.png");
	private static final ResourceLocation DASHER_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/fish/tropical_a_pattern_4.png");
	private static final ResourceLocation BRINELY_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/fish/tropical_a_pattern_5.png");
	private static final ResourceLocation SPOTTY_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/fish/tropical_a_pattern_6.png");
	private static final ResourceLocation FLOPPER_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/fish/tropical_b_pattern_1.png");
	private static final ResourceLocation STRIPEY_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/fish/tropical_b_pattern_2.png");
	private static final ResourceLocation GLITTER_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/fish/tropical_b_pattern_3.png");
	private static final ResourceLocation BLOCKFISH_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/fish/tropical_b_pattern_4.png");
	private static final ResourceLocation BETTY_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/fish/tropical_b_pattern_5.png");
	private static final ResourceLocation CLAYFISH_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/fish/tropical_b_pattern_6.png");
	private final TropicalFishModelA modelA;
	private final TropicalFishModelB modelB;

	public TropicalFishPatternLayer(
		RenderLayerParent<TropicalFishRenderState, EntityModel<TropicalFishRenderState>> renderLayerParent, EntityModelSet entityModelSet
	) {
		super(renderLayerParent);
		this.modelA = new TropicalFishModelA(entityModelSet.bakeLayer(ModelLayers.TROPICAL_FISH_SMALL_PATTERN));
		this.modelB = new TropicalFishModelB(entityModelSet.bakeLayer(ModelLayers.TROPICAL_FISH_LARGE_PATTERN));
	}

	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, TropicalFishRenderState tropicalFishRenderState, float f, float g) {
		if (tropicalFishRenderState == null || tropicalFishRenderState.pattern == null) {
			if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
				throw new IllegalStateException("Rust whole-frame tropical-fish pattern route requires copied pattern semantics");
			}
			return;
		}
		Pattern pattern = tropicalFishRenderState.pattern;

		EntityModel<TropicalFishRenderState> entityModel = (EntityModel<TropicalFishRenderState>)(switch (pattern.base()) {
			case SMALL -> this.modelA;
			case LARGE -> this.modelB;
			default -> throw new MatchException(null, null);
		});

		ResourceLocation resourceLocation = switch (pattern) {
			case KOB -> KOB_TEXTURE;
			case SUNSTREAK -> SUNSTREAK_TEXTURE;
			case SNOOPER -> SNOOPER_TEXTURE;
			case DASHER -> DASHER_TEXTURE;
			case BRINELY -> BRINELY_TEXTURE;
			case SPOTTY -> SPOTTY_TEXTURE;
			case FLOPPER -> FLOPPER_TEXTURE;
			case STRIPEY -> STRIPEY_TEXTURE;
			case GLITTER -> GLITTER_TEXTURE;
			case BLOCKFISH -> BLOCKFISH_TEXTURE;
			case BETTY -> BETTY_TEXTURE;
			case CLAYFISH -> CLAYFISH_TEXTURE;
			default -> throw new MatchException(null, null);
		};
		if (net.vulkanic.world.WorldRenderRoutePolicy.currentMaterialRoute().usesRustWholeFrameVulkan()) {
			boolean eligible = net.vulkanic.world.RustGalWorldPrimitiveRenderer.isVanillaTropicalFishPatternModelMeshEligible(
				entityModel, tropicalFishRenderState, RenderType.entityCutoutNoCull(resourceLocation), resourceLocation,
				LivingEntityRenderer.getOverlayCoords(tropicalFishRenderState, 0.0F), tropicalFishRenderState.outlineColor);
			if (eligible && net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueStandaloneModelMesh(
				entityModel, tropicalFishRenderState, poseStack.last(), RenderType.entityCutoutNoCull(resourceLocation),
				resourceLocation, ResourceLocation.withDefaultNamespace("tropical_fish_pattern"), i,
				LivingEntityRenderer.getOverlayCoords(tropicalFishRenderState, 0.0F), tropicalFishRenderState.patternColor,
				tropicalFishRenderState.outlineColor)) {
				return;
			}
			// Whole-frame Vulkan owns this layer; an unavailable copied pattern is
			// recorded as absent rather than reaching Java's renderer.
			 net.vulkanic.world.RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
				"rust-vulkan-unavailable", resourceLocation, entityModel.getClass().getName(), tropicalFishRenderState.entityId,
				false, false, false);
			throw new IllegalStateException(
				"Rust whole-frame tropical-fish pattern route has no semantic mesh for " + resourceLocation
			);
		}
		coloredCutoutModelCopyLayerRender(
			entityModel, resourceLocation, poseStack, submitNodeCollector, i, tropicalFishRenderState, tropicalFishRenderState.patternColor, 1
		);
	}
}
