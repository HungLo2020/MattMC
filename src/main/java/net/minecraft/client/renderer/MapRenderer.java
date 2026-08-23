package net.minecraft.client.renderer;

import net.blaze3d.vertex.PoseStack;
import net.math.Axis;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.MapTextureManager;
import net.minecraft.client.resources.model.AtlasManager;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

@Environment(EnvType.CLIENT)
public class MapRenderer {
	private static final float MAP_Z_OFFSET = -0.01F;
	private static final float DECORATION_Z_OFFSET = -0.001F;
	public static final int WIDTH = 128;
	public static final int HEIGHT = 128;
	private final TextureAtlas decorationSprites;
	private final MapTextureManager mapTextureManager;

	public MapRenderer(AtlasManager atlasManager, MapTextureManager mapTextureManager) {
		this.decorationSprites = atlasManager.getAtlasOrThrow(AtlasIds.MAP_DECORATIONS);
		this.mapTextureManager = mapTextureManager;
	}

	public void render(MapRenderState mapRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, boolean bl, int i) {
		float[] mapVertices = {0.0F, 128.0F, -0.01F, 128.0F, 128.0F, -0.01F, 128.0F, 0.0F, -0.01F, 0.0F, 0.0F, -0.01F};
		float[] mapUvs = {0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 0.0F, 0.0F, 0.0F};
		boolean mapAccepted = submitNodeCollector.submitTexturedQuad(
			poseStack, RenderType.text(mapRenderState.texture), mapRenderState.texture, mapVertices, mapUvs, -1, i
		);
		if (!mapAccepted && net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
			&& net.vulkanic.world.WorldRenderRoutePolicy.currentTexturedBillboardRoute().usesRustWholeFrameVulkan()) {
			throw new IllegalStateException("Rust whole-frame map route rejected the copied map quad");
		}
		if (!mapAccepted) {
			if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
				throw new IllegalStateException("Rust whole-frame map route is unavailable; Java map geometry is not a fallback");
			}
			submitNodeCollector.submitCustomGeometry(poseStack, RenderType.text(mapRenderState.texture), (pose, vertexConsumer) -> {
				vertexConsumer.addVertex(pose, 0.0F, 128.0F, -0.01F).setColor(-1).setUv(0.0F, 1.0F).setLight(i);
				vertexConsumer.addVertex(pose, 128.0F, 128.0F, -0.01F).setColor(-1).setUv(1.0F, 1.0F).setLight(i);
				vertexConsumer.addVertex(pose, 128.0F, 0.0F, -0.01F).setColor(-1).setUv(1.0F, 0.0F).setLight(i);
				vertexConsumer.addVertex(pose, 0.0F, 0.0F, -0.01F).setColor(-1).setUv(0.0F, 0.0F).setLight(i);
			});
		}
		int j = 0;

		for (MapRenderState.MapDecorationRenderState mapDecorationRenderState : mapRenderState.decorations) {
			if (!bl || mapDecorationRenderState.renderOnFrame) {
				poseStack.pushPose();
				poseStack.translate(mapDecorationRenderState.x / 2.0F + 64.0F, mapDecorationRenderState.y / 2.0F + 64.0F, -0.02F);
				poseStack.mulPose(Axis.ZP.rotationDegrees(mapDecorationRenderState.rot * 360 / 16.0F));
				poseStack.scale(4.0F, 4.0F, 3.0F);
				poseStack.translate(-0.125F, 0.125F, 0.0F);
				TextureAtlasSprite textureAtlasSprite = mapDecorationRenderState.atlasSprite;
				if (textureAtlasSprite != null) {
					float f = j * -0.001F;
					float[] vertices = {-1.0F, 1.0F, f, 1.0F, 1.0F, f, 1.0F, -1.0F, f, -1.0F, -1.0F, f};
					float[] uvs = {textureAtlasSprite.getU0(), textureAtlasSprite.getV0(), textureAtlasSprite.getU1(), textureAtlasSprite.getV0(), textureAtlasSprite.getU1(), textureAtlasSprite.getV1(), textureAtlasSprite.getU0(), textureAtlasSprite.getV1()};
					boolean decorationAccepted = submitNodeCollector.submitTexturedQuad(
						poseStack, RenderType.text(textureAtlasSprite.atlasLocation()), textureAtlasSprite.atlasLocation(), vertices, uvs, -1, i
					);
					if (!decorationAccepted && net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
						&& net.vulkanic.world.WorldRenderRoutePolicy.currentTexturedBillboardRoute().usesRustWholeFrameVulkan()) {
						throw new IllegalStateException("Rust whole-frame map route rejected a copied decoration quad");
					}
					if (!decorationAccepted) {
						if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
							throw new IllegalStateException("Rust whole-frame map-decoration route is unavailable; Java map geometry is not a fallback");
						}
						submitNodeCollector.submitCustomGeometry(poseStack, RenderType.text(textureAtlasSprite.atlasLocation()), (pose, vertexConsumer) -> {
						vertexConsumer.addVertex(pose, -1.0F, 1.0F, f).setColor(-1).setUv(textureAtlasSprite.getU0(), textureAtlasSprite.getV0()).setLight(i);
						vertexConsumer.addVertex(pose, 1.0F, 1.0F, f).setColor(-1).setUv(textureAtlasSprite.getU1(), textureAtlasSprite.getV0()).setLight(i);
						vertexConsumer.addVertex(pose, 1.0F, -1.0F, f).setColor(-1).setUv(textureAtlasSprite.getU1(), textureAtlasSprite.getV1()).setLight(i);
						vertexConsumer.addVertex(pose, -1.0F, -1.0F, f).setColor(-1).setUv(textureAtlasSprite.getU0(), textureAtlasSprite.getV1()).setLight(i);
						});
					}
					poseStack.popPose();
				}

				if (mapDecorationRenderState.name != null) {
					Font font = Minecraft.getInstance().font;
					float g = font.width(mapDecorationRenderState.name);
					float h = Mth.clamp(25.0F / g, 0.0F, 6.0F / 9.0F);
					poseStack.pushPose();
					poseStack.translate(mapDecorationRenderState.x / 2.0F + 64.0F - g * h / 2.0F, mapDecorationRenderState.y / 2.0F + 64.0F + 4.0F, -0.025F);
					poseStack.scale(h, h, -1.0F);
					poseStack.translate(0.0F, 0.0F, 0.1F);
					submitNodeCollector.order(1)
						.submitText(poseStack, 0.0F, 0.0F, mapDecorationRenderState.name.getVisualOrderText(), false, Font.DisplayMode.NORMAL, i, -1, Integer.MIN_VALUE, 0);
					poseStack.popPose();
				}

				j++;
			}
		}
	}

	public void extractRenderState(MapId mapId, MapItemSavedData mapItemSavedData, MapRenderState mapRenderState) {
		mapRenderState.texture = this.mapTextureManager.prepareMapTexture(mapId, mapItemSavedData);
		mapRenderState.decorations.clear();

		for (MapDecoration mapDecoration : mapItemSavedData.getDecorations()) {
			mapRenderState.decorations.add(this.extractDecorationRenderState(mapDecoration));
		}
	}

	private MapRenderState.MapDecorationRenderState extractDecorationRenderState(MapDecoration mapDecoration) {
		MapRenderState.MapDecorationRenderState mapDecorationRenderState = new MapRenderState.MapDecorationRenderState();
		mapDecorationRenderState.atlasSprite = this.decorationSprites.getSprite(mapDecoration.getSpriteLocation());
		mapDecorationRenderState.x = mapDecoration.x();
		mapDecorationRenderState.y = mapDecoration.y();
		mapDecorationRenderState.rot = mapDecoration.rot();
		mapDecorationRenderState.name = (Component)mapDecoration.name().orElse(null);
		mapDecorationRenderState.renderOnFrame = mapDecoration.renderOnFrame();
		return mapDecorationRenderState;
	}
}
