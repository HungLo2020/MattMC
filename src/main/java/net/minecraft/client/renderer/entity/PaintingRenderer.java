package net.minecraft.client.renderer.entity;

import net.blaze3d.vertex.PoseStack;
import net.blaze3d.vertex.VertexConsumer;
import net.math.Axis;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.PaintingRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.minecraft.world.level.Level;

@Environment(EnvType.CLIENT)
public class PaintingRenderer extends EntityRenderer<Painting, PaintingRenderState> {
	private static final ResourceLocation BACK_SPRITE_LOCATION = ResourceLocation.withDefaultNamespace("back");
	/** Six explicit faces are emitted for every painting tile on the Rust route. */
	private static final int MAX_RUST_PAINTING_QUADS = 65_536;
	private final TextureAtlas paintingsAtlas;

	public PaintingRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.paintingsAtlas = context.getAtlas(AtlasIds.PAINTINGS);
	}

	public void submit(PaintingRenderState paintingRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
		PaintingVariant paintingVariant = paintingRenderState.variant;
		if (paintingVariant != null) {
			poseStack.pushPose();
			poseStack.mulPose(Axis.YP.rotationDegrees(180 - paintingRenderState.direction.get2DDataValue() * 90));
			TextureAtlasSprite textureAtlasSprite = this.paintingsAtlas.getSprite(paintingVariant.assetId());
			TextureAtlasSprite textureAtlasSprite2 = this.paintingsAtlas.getSprite(BACK_SPRITE_LOCATION);
			this.renderPainting(
				poseStack,
				submitNodeCollector,
				RenderType.entitySolidZOffsetForward(textureAtlasSprite2.atlasLocation()),
				paintingRenderState.lightCoordsPerBlock,
				paintingVariant.width(),
				paintingVariant.height(),
				textureAtlasSprite,
				textureAtlasSprite2
			);
			poseStack.popPose();
			super.submit(paintingRenderState, poseStack, submitNodeCollector, cameraRenderState);
		}
	}

	public PaintingRenderState createRenderState() {
		return new PaintingRenderState();
	}

	public void extractRenderState(Painting painting, PaintingRenderState paintingRenderState, float f) {
		super.extractRenderState(painting, paintingRenderState, f);
		Direction direction = painting.getDirection();
		PaintingVariant paintingVariant = (PaintingVariant)painting.getVariant().value();
		paintingRenderState.direction = direction;
		paintingRenderState.variant = paintingVariant;
		int i = paintingVariant.width();
		int j = paintingVariant.height();
		long tileCount = (long)i * j;
		if (i <= 0 || j <= 0 || tileCount > MAX_RUST_PAINTING_QUADS / 6L) {
			throw new IllegalStateException("Rust painting tile bound exceeded " + MAX_RUST_PAINTING_QUADS / 6L);
		}
		if (paintingRenderState.lightCoordsPerBlock.length != tileCount) {
			paintingRenderState.lightCoordsPerBlock = new int[(int)tileCount];
		}

		float g = -i / 2.0F;
		float h = -j / 2.0F;
		Level level = painting.level();

		for (int k = 0; k < j; k++) {
			for (int l = 0; l < i; l++) {
				float m = l + g + 0.5F;
				float n = k + h + 0.5F;
				int o = painting.getBlockX();
				int p = Mth.floor(painting.getY() + n);
				int q = painting.getBlockZ();
				switch (direction) {
					case NORTH:
						o = Mth.floor(painting.getX() + m);
						break;
					case WEST:
						q = Mth.floor(painting.getZ() - m);
						break;
					case SOUTH:
						o = Mth.floor(painting.getX() - m);
						break;
					case EAST:
						q = Mth.floor(painting.getZ() + m);
				}

				paintingRenderState.lightCoordsPerBlock[l + k * i] = LevelRenderer.getLightColor(level, new BlockPos(o, p, q));
			}
		}
	}

	private void renderPainting(
		PoseStack poseStack,
		SubmitNodeCollector submitNodeCollector,
		RenderType renderType,
		int[] is,
		int i,
		int j,
		TextureAtlasSprite textureAtlasSprite,
		TextureAtlasSprite textureAtlasSprite2
	) {
		if (renderPaintingSemantic(poseStack, submitNodeCollector, renderType, is, i, j, textureAtlasSprite, textureAtlasSprite2)) {
			return;
		}
		throw new IllegalStateException("Rust whole-frame painting route rejected semantic quads");
	}

	/** Copies the six painting faces as explicit atlas quads for Rust Vulkan. */
	private boolean renderPaintingSemantic(
		PoseStack poseStack, SubmitNodeCollector submitNodeCollector, RenderType renderType,
		int[] light, int width, int height, TextureAtlasSprite art, TextureAtlasSprite back
	) {
		// Preflight the complete semantic payload before enqueueing any face. A
		// resource-pack or modded painting variant must fail closed as one unit;
		// partial Rust material work would otherwise survive the route rejection.
		long requiredQuads = 6L * width * height;
		if (width <= 0 || height <= 0 || requiredQuads > MAX_RUST_PAINTING_QUADS
			|| light == null || (long)light.length != (long)width * height) {
			return false;
		}
		float originX = -width / 2.0F;
		float originY = -height / 2.0F;
		float depth = 0.03125F;
		float backU0 = back.getU0(), backU1 = back.getU1(), backV0 = back.getV0(), backV1 = back.getV1();
		float sideU0 = back.getU0(), sideU1 = back.getU(0.0625F), sideV0 = back.getV0(), sideV1 = back.getV(0.0625F);
		int checkpoint = net.vulkanic.world.RustGalWorldPrimitiveRenderer.markMaterialQuadBatch();
		try {
		for (int tileX = 0; tileX < width; tileX++) {
			for (int tileY = 0; tileY < height; tileY++) {
				float x1 = originX + tileX + 1.0F, x0 = originX + tileX;
				float y1 = originY + tileY + 1.0F, y0 = originY + tileY;
				int tileLight = light[tileX + tileY * width];
				float uFront0 = art.getU((float)(width - tileX) / width), uFront1 = art.getU((float)(width - tileX - 1) / width);
				float vFront0 = art.getV((float)(height - tileY) / height), vFront1 = art.getV((float)(height - tileY - 1) / height);
				ResourceLocation artAtlas = art.atlasLocation();
				ResourceLocation backAtlas = back.atlasLocation();
				if (!submitPaintingQuad(submitNodeCollector, poseStack, renderType, artAtlas,
					new float[] {x1, y0, -depth, x0, y0, -depth, x0, y1, -depth, x1, y1, -depth},
					new float[] {uFront1, vFront0, uFront0, vFront0, uFront0, vFront1, uFront1, vFront1}, tileLight)
					|| !submitPaintingQuad(submitNodeCollector, poseStack, renderType, backAtlas,
					new float[] {x1, y1, depth, x0, y1, depth, x0, y0, depth, x1, y0, depth},
					new float[] {backU1, backV0, backU0, backV0, backU0, backV1, backU1, backV1}, tileLight)
					|| !submitPaintingQuad(submitNodeCollector, poseStack, renderType, backAtlas,
					new float[] {x1, y1, -depth, x0, y1, -depth, x0, y1, depth, x1, y1, depth},
					new float[] {backU0, backV0, backU1, backV0, backU1, sideV1, backU0, sideV1}, tileLight)
					|| !submitPaintingQuad(submitNodeCollector, poseStack, renderType, backAtlas,
					new float[] {x1, y0, depth, x0, y0, depth, x0, y0, -depth, x1, y0, -depth},
					new float[] {backU0, backV0, backU1, backV0, backU1, sideV1, backU0, sideV1}, tileLight)
					|| !submitPaintingQuad(submitNodeCollector, poseStack, renderType, backAtlas,
					new float[] {x1, y1, depth, x1, y0, depth, x1, y0, -depth, x1, y1, -depth},
					new float[] {sideU1, sideV0, sideU1, sideV1, sideU0, sideV1, sideU0, sideV0}, tileLight)
					|| !submitPaintingQuad(submitNodeCollector, poseStack, renderType, backAtlas,
					new float[] {x0, y1, -depth, x0, y0, -depth, x0, y0, depth, x0, y1, depth},
					new float[] {sideU1, sideV0, sideU1, sideV1, sideU0, sideV1, sideU0, sideV0}, tileLight)) {
					net.vulkanic.world.RustGalWorldPrimitiveRenderer.rollbackMaterialQuadBatch(checkpoint);
					return false;
				}
			}
		}
		} catch (RuntimeException failure) {
			net.vulkanic.world.RustGalWorldPrimitiveRenderer.rollbackMaterialQuadBatch(checkpoint);
			throw failure;
		}
		return true;
	}

	private static boolean submitPaintingQuad(
		SubmitNodeCollector collector, PoseStack poseStack, RenderType renderType,
		ResourceLocation texture, float[] vertices, float[] uvs, int light
	) {
		return collector.submitTexturedQuadSemantic(poseStack, renderType, texture, vertices, uvs, -1, light);
	}

	private void vertex(PoseStack.Pose pose, VertexConsumer vertexConsumer, float f, float g, float h, float i, float j, int k, int l, int m, int n) {
		vertexConsumer.addVertex(pose, f, g, j).setColor(-1).setUv(h, i).setOverlay(OverlayTexture.NO_OVERLAY).setLight(n).setNormal(pose, k, l, m);
	}
}
