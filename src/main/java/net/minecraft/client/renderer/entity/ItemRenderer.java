package net.minecraft.client.renderer.entity;

import net.blaze3d.vertex.PoseStack;
import net.blaze3d.vertex.SheetedDecalTextureGenerator;
import net.blaze3d.vertex.VertexConsumer;
import net.blaze3d.vertex.VertexMultiConsumer;
import net.math.MatrixUtil;
import java.util.List;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemDisplayContext;
import net.sodium.client.model.quad.BakedQuadView;
import net.sodium.client.render.immediate.model.BakedModelEncoder;
import net.sodium.client.render.vertex.VertexConsumerUtils;

@Environment(EnvType.CLIENT)
public class ItemRenderer {
	public static final ResourceLocation ENCHANTED_GLINT_ARMOR = ResourceLocation.withDefaultNamespace("textures/misc/enchanted_glint_armor.png");
	public static final ResourceLocation ENCHANTED_GLINT_ITEM = ResourceLocation.withDefaultNamespace("textures/misc/enchanted_glint_item.png");
	public static final float SPECIAL_FOIL_UI_SCALE = 0.5F;
	public static final float SPECIAL_FOIL_FIRST_PERSON_SCALE = 0.75F;
	public static final float SPECIAL_FOIL_TEXTURE_SCALE = 0.0078125F;
	public static final int NO_TINT = -1;
	// Sodium: Thread-local random for fast item rendering (merged from ItemRendererMixin)
	private static final ThreadLocal<net.minecraft.util.RandomSource> sodium$random = ThreadLocal.withInitial(() -> new net.minecraft.world.level.levelgen.SingleThreadedRandomSource(42L));

	public static void renderItem(
		ItemDisplayContext itemDisplayContext,
		PoseStack poseStack,
		MultiBufferSource multiBufferSource,
		int i,
		int j,
		int[] is,
		List<BakedQuad> list,
		RenderType renderType,
		ItemStackRenderState.FoilType foilType
	) {
		VertexConsumer vertexConsumer;
		if (foilType == ItemStackRenderState.FoilType.SPECIAL) {
			PoseStack.Pose pose = poseStack.last().copy();
			if (itemDisplayContext == ItemDisplayContext.GUI) {
				MatrixUtil.mulComponentWise(pose.pose(), 0.5F);
			} else if (itemDisplayContext.firstPerson()) {
				MatrixUtil.mulComponentWise(pose.pose(), 0.75F);
			}

			vertexConsumer = getSpecialFoilBuffer(multiBufferSource, renderType, pose);
		} else {
			vertexConsumer = getFoilBuffer(multiBufferSource, renderType, true, foilType != ItemStackRenderState.FoilType.NONE);
		}

		renderQuadList(poseStack, vertexConsumer, list, is, i, j, itemDisplayContext != ItemDisplayContext.GUI);
	}

	public static VertexConsumer getSpecialFoilBuffer(MultiBufferSource multiBufferSource, RenderType renderType, PoseStack.Pose pose) { // Made public for Sodium FRAPI integration
		return VertexMultiConsumer.create(
			new SheetedDecalTextureGenerator(
				multiBufferSource.getBuffer(useTransparentGlint(renderType) ? RenderType.glintTranslucent() : RenderType.glint()), pose, 0.0078125F
			),
			multiBufferSource.getBuffer(renderType)
		);
	}

	public static VertexConsumer getFoilBuffer(MultiBufferSource multiBufferSource, RenderType renderType, boolean bl, boolean bl2) {
		if (bl2) {
			return useTransparentGlint(renderType)
				? VertexMultiConsumer.create(multiBufferSource.getBuffer(RenderType.glintTranslucent()), multiBufferSource.getBuffer(renderType))
				: VertexMultiConsumer.create(multiBufferSource.getBuffer(bl ? RenderType.glint() : RenderType.entityGlint()), multiBufferSource.getBuffer(renderType));
		} else {
			return multiBufferSource.getBuffer(renderType);
		}
	}

	public static List<RenderType> getFoilRenderTypes(RenderType renderType, boolean bl, boolean bl2) {
		if (bl2) {
			return useTransparentGlint(renderType)
				? List.of(renderType, RenderType.glintTranslucent())
				: List.of(renderType, bl ? RenderType.glint() : RenderType.entityGlint());
		} else {
			return List.of(renderType);
		}
	}

	private static boolean useTransparentGlint(RenderType renderType) {
		return Minecraft.useShaderTransparency() && renderType == Sheets.translucentItemSheet();
	}

	private static int getLayerColorSafe(int[] is, int i) {
		return i >= 0 && i < is.length ? is[i] : -1;
	}

	private static void renderQuadList(PoseStack poseStack, VertexConsumer vertexConsumer, List<BakedQuad> list, int[] is, int i, int j, boolean allowSodiumFastPath) {
		// Sodium: Use fast rendering path if available (merged from ItemRendererMixin)
		var writer = VertexConsumerUtils.convertOrLog(vertexConsumer);

		if (allowSodiumFastPath && writer != null && !list.isEmpty()) {
			sodium$renderBakedItemQuads(poseStack.last(), writer, list, is, i, j);
			return;
		}

		// Fallback to vanilla rendering
		PoseStack.Pose pose = poseStack.last();

		for (BakedQuad bakedQuad : list) {
			float f;
			float g;
			float h;
			float l;
			if (bakedQuad.isTinted()) {
				int k = getLayerColorSafe(is, bakedQuad.tintIndex());
				f = ARGB.alpha(k) / 255.0F;
				g = ARGB.red(k) / 255.0F;
				h = ARGB.green(k) / 255.0F;
				l = ARGB.blue(k) / 255.0F;
			} else {
				f = 1.0F;
				g = 1.0F;
				h = 1.0F;
				l = 1.0F;
			}

			vertexConsumer.putBulkData(pose, bakedQuad, g, h, l, f, i, j);
		}
	}

	// Sodium: Fast item quad rendering (merged from ItemRendererMixin)
	@SuppressWarnings("ForLoopReplaceableByForEach")
	private static void sodium$renderBakedItemQuads(PoseStack.Pose matrices, net.sodium.api.vertex.buffer.VertexBufferWriter writer, List<BakedQuad> quads, int[] colors, int light, int overlay) {
		for (int i = 0; i < quads.size(); i++) {
			BakedQuad bakedQuad = quads.get(i);

			if (bakedQuad.vertices().length < 32) {
				continue; // ignore bad quads
			}

			BakedQuadView quad = (BakedQuadView) (Object) bakedQuad;

			int color = 0xFFFFFFFF;

			if (bakedQuad.isTinted()) {
				color = net.sodium.api.util.ColorARGB.toABGR(getLayerColorSafe(colors, bakedQuad.tintIndex()));
			}

			BakedModelEncoder.writeQuadVertices(writer, matrices, quad, color, light, overlay, BakedModelEncoder.shouldMultiplyAlpha());

			if (quad.getSprite() != null) {
				net.sodium.api.texture.SpriteUtil.INSTANCE.markSpriteActive(quad.getSprite());
			}
		}
	}
}
