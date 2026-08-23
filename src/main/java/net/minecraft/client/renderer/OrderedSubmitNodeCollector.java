package net.minecraft.client.renderer;

import net.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HitboxesRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

@Environment(EnvType.CLIENT)
public interface OrderedSubmitNodeCollector {
	void submitHitbox(PoseStack poseStack, EntityRenderState entityRenderState, HitboxesRenderState hitboxesRenderState);

	void submitShadow(PoseStack poseStack, float f, List<EntityRenderState.ShadowPiece> list);

	void submitNameTag(PoseStack poseStack, @Nullable Vec3 vec3, int i, Component component, boolean bl, int j, double d, CameraRenderState cameraRenderState);

	void submitText(
		PoseStack poseStack, float f, float g, FormattedCharSequence formattedCharSequence, boolean bl, Font.DisplayMode displayMode, int i, int j, int k, int l
	);

	void submitFlame(PoseStack poseStack, EntityRenderState entityRenderState, Quaternionf quaternionf);

	void submitLeash(PoseStack poseStack, EntityRenderState.LeashState leashState);

	/** Explicit semantic End Portal cube; Rust owns the animated layer material. */
	default boolean submitEndPortal(PoseStack poseStack, boolean[] faces, float gameTime, int lightCoords) {
		return false;
	}

	<S> void submitModel(
		Model<? super S> model,
		S object,
		PoseStack poseStack,
		RenderType renderType,
		int i,
		int j,
		int k,
		@Nullable TextureAtlasSprite textureAtlasSprite,
		int l,
		@Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay
	);

	/** Explicit direct-texture semantic model submit used by emissive feature layers. */
	default <S> void submitModelSemanticTexture(
		Model<? super S> model,
		S object,
		PoseStack poseStack,
		RenderType renderType,
		int lightCoords,
		int overlayCoords,
		int tintedColor,
		ResourceLocation textureIdentity,
		int outlineColor,
		@Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay
	) {
		this.submitModel(model, object, poseStack, renderType, lightCoords, overlayCoords, tintedColor, null, outlineColor, crumblingOverlay);
	}

	default <S> void submitAnimatedModelSemanticTexture(
		Model<? super S> model, S object, PoseStack poseStack, RenderType renderType,
		int lightCoords, int overlayCoords, int tintedColor, ResourceLocation textureIdentity,
		int outlineColor, @Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay,
		float uvOffsetU, float uvOffsetV, int textureWidth, int textureHeight
	) {
		this.submitModelSemanticTexture(model, object, poseStack, renderType, lightCoords, overlayCoords,
			tintedColor, textureIdentity, outlineColor, crumblingOverlay);
	}

	default <S> void submitModel(
		Model<? super S> model,
		S object,
		PoseStack poseStack,
		RenderType renderType,
		int i,
		int j,
		int k,
		@Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay
	) {
		this.submitModel(model, object, poseStack, renderType, i, j, -1, null, k, crumblingOverlay);
	}

	default void submitModelPart(ModelPart modelPart, PoseStack poseStack, RenderType renderType, int i, int j, @Nullable TextureAtlasSprite textureAtlasSprite) {
		this.submitModelPart(modelPart, poseStack, renderType, i, j, textureAtlasSprite, false, false, -1, null, 0);
	}

	default void submitModelPart(
		ModelPart modelPart,
		PoseStack poseStack,
		RenderType renderType,
		int i,
		int j,
		@Nullable TextureAtlasSprite textureAtlasSprite,
		int k,
		@Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay
	) {
		this.submitModelPart(modelPart, poseStack, renderType, i, j, textureAtlasSprite, false, false, k, crumblingOverlay, 0);
	}

	default void submitModelPart(
		ModelPart modelPart, PoseStack poseStack, RenderType renderType, int i, int j, @Nullable TextureAtlasSprite textureAtlasSprite, boolean bl, boolean bl2
	) {
		this.submitModelPart(modelPart, poseStack, renderType, i, j, textureAtlasSprite, bl, bl2, -1, null, 0);
	}

	void submitModelPart(
		ModelPart modelPart,
		PoseStack poseStack,
		RenderType renderType,
		int i,
		int j,
		@Nullable TextureAtlasSprite textureAtlasSprite,
		boolean bl,
		boolean bl2,
		int k,
		@Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay,
		int l
	);

	void submitBlock(PoseStack poseStack, BlockState blockState, int i, int j, int k);

	default void submitBlockDisplay(PoseStack poseStack, BlockState blockState, int i, int j, int k) {
		this.submitBlock(poseStack, blockState, i, j, k);
	}

	default void submitBlockDisplay(PoseStack poseStack, BlockState blockState, int i, int j, int k, BlockPos tintPos) {
		this.submitBlockDisplay(poseStack, blockState, i, j, k);
	}

	/**
	 * Marks the bounded primed-TNT block submit without changing its copied
	 * gameplay inputs. Implementations that do not retain submit provenance
	 * still receive the ordinary block call; the collection used by rendering
	 * records the explicit semantic source before route selection.
	 */
	default void submitPrimedTntBlock(PoseStack poseStack, BlockState blockState, int i, int j, int k) {
		this.submitBlock(poseStack, blockState, i, j, k);
	}

	void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState movingBlockRenderState);

	default void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState movingBlockRenderState, SubmitNodeStorage.MovingBlockSubmitSource source) {
		this.submitMovingBlock(poseStack, movingBlockRenderState);
	}

	void submitBlockModel(PoseStack poseStack, RenderType renderType, BlockStateModel blockStateModel, float f, float g, float h, int i, int j, int k);

	void submitItem(
		PoseStack poseStack,
		ItemDisplayContext itemDisplayContext,
		int i,
		int j,
		int k,
		int[] is,
		List<BakedQuad> list,
		RenderType renderType,
		ItemStackRenderState.FoilType foilType
	);

	void submitCustomGeometry(PoseStack poseStack, RenderType renderType, SubmitNodeCollector.CustomGeometryRenderer customGeometryRenderer);

	/** Explicit semantic Guardian beam primitive; implementations may reject unsupported backends. */
	default boolean submitGuardianBeam(
		PoseStack poseStack,
		RenderType renderType,
		ResourceLocation textureIdentity,
		float[] vertices,
		float[] uvs,
		int[] colors,
		int lightCoords
	) {
		return false;
	}

	/** Explicit semantic End Crystal beam primitive with copied quad data. */
	default boolean submitCrystalBeam(
		PoseStack poseStack, RenderType renderType, ResourceLocation textureIdentity,
		float[] vertices, float[] uvs, int[] colors, int lightCoords
	) {
		return false;
	}

	/** Explicit semantic textured billboard quad for small projectile renderers. */
	default boolean submitTexturedQuad(
		PoseStack poseStack, RenderType renderType, ResourceLocation textureIdentity,
		float[] vertices, float[] uvs, int color, int lightCoords
	) {
		return false;
	}

	/** Explicit semantic translucent textured billboard quad. */
	default boolean submitTranslucentTexturedQuad(
		PoseStack poseStack, RenderType renderType, ResourceLocation textureIdentity,
		float[] vertices, float[] uvs, int color, int lightCoords
	) {
		return false;
	}

	/** Explicit semantic batch of textured quads sharing one copied texture asset. */
	default boolean submitTexturedQuads(
		PoseStack poseStack, RenderType renderType, ResourceLocation textureIdentity,
		float[] vertices, float[] uvs, int[] colors, int lightCoords
	) {
		return false;
	}

	/** Explicit first-person optical mesh batch with a Rust-owned stencil role. */
	default boolean submitOpticalTexturedQuads(
		PoseStack poseStack, RenderType renderType, ResourceLocation textureIdentity,
		float[] vertices, float[] uvs, int[] colors, int lightCoords, int materialMode
	) {
		return false;
	}

	/** Explicit semantic line-list primitive for bounded entity ropes and lines. */
	default boolean submitLineSegments(PoseStack poseStack, float[] endpoints, int color, float lineWidth) {
		return false;
	}

	/** Explicit semantic colored textured-quad list for procedural effects. */
	default boolean submitColoredQuads(PoseStack poseStack, RenderType renderType, float[] vertices, float[] uvs, int[] colors, int lightCoords) {
		return false;
	}

	void submitParticleGroup(SubmitNodeCollector.ParticleGroupRenderer particleGroupRenderer);
}
