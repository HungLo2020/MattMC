package net.vulkanic.gui;

import net.minecraft.client.gui.font.TextGlyphQuad;
import org.joml.Matrix3x2f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RustGalGuiRendererTest {
	@Test
	void textQuadUsesMinecraftCornerOrderToBuildTheAffineBasis() {
		TextGlyphQuad quad = new TextGlyphQuad(
			"minecraft:font/default/0", false,
			10.0F, 20.0F,
			10.0F, 30.0F,
			18.0F, 30.0F,
			18.0F, 20.0F,
			0.0F, 0.1F, 0.2F, 0.4F, 0.7F, 0xFFFFFFFF
		);

		var request = RustGalGuiRenderer.transformTextQuad(quad, new Matrix3x2f(), 41L, 320, 180, null);

		assertEquals(10.0F, request.x0());
		assertEquals(20.0F, request.y0());
		assertEquals(18.0F, request.x1(), "U axis must end at the top-right glyph corner");
		assertEquals(20.0F, request.y1());
		assertEquals(10.0F, request.x3(), "V axis must end at the bottom-left glyph corner");
		assertEquals(30.0F, request.y3());
	}

	@Test
	void flatItemFoilUsesTheCopiedGlintAssetAndProjectedUvTransform() throws Exception {
		String source = java.nio.file.Files.readString(java.nio.file.Path.of(
			"src/main/java/net/vulkanic/gui/RustGalGuiItemRenderer.java"
		));
		assertTrue(source.contains("layer.foilType() == ItemStackRenderState.FoilType.STANDARD"));
		assertTrue(source.contains("RustGalGuiRawImageAssets.stage(glint)"));
		assertTrue(source.contains("source.atlasU0()"));
		assertTrue(source.contains("ENCHANTED_GLINT_ITEM"));
		assertTrue(source.contains("specialFoilQuad"));
		assertTrue(source.contains("SPECIAL_FOIL_TEXTURE_SCALE"));
	}

	@Test
	void specialGuiItemsUseOnlyBoundedVanillaModelCopiers() throws Exception {
		String itemSource = java.nio.file.Files.readString(java.nio.file.Path.of(
			"src/main/java/net/vulkanic/gui/RustGalGuiItemRenderer.java"
		));
		String stateSource = java.nio.file.Files.readString(java.nio.file.Path.of(
			"src/main/java/net/minecraft/client/renderer/item/ItemStackRenderState.java"
		));
		assertTrue(itemSource.contains("tryEnqueueSpecialItem"));
		assertTrue(itemSource.contains("TridentSpecialRenderer"));
		assertTrue(itemSource.contains("ChestSpecialRenderer"));
		assertTrue(itemSource.contains("chest.material().texture()"));
		assertTrue(itemSource.contains("HangingSignSpecialRenderer"));
		assertTrue(itemSource.contains("StandingSignSpecialRenderer"));
		assertTrue(itemSource.contains("CopperGolemStatueSpecialRenderer"));
		assertTrue(itemSource.contains("BedSpecialRenderer"));
		assertTrue(itemSource.contains("bed.headModel()"));
		assertTrue(itemSource.contains("ShieldSpecialRenderer"));
		assertTrue(itemSource.contains("Sheets.getShieldMaterial(layer.pattern())"));
		assertTrue(itemSource.contains("BannerSpecialRenderer"));
		assertTrue(itemSource.contains("renderer.standingModel()"));
		assertTrue(itemSource.contains("renderer.standingFlagModel()"));
		assertTrue(itemSource.contains("Sheets.getBannerMaterial(layer.pattern())"));
		assertTrue(itemSource.contains("SkullSpecialRenderer"));
		assertTrue(itemSource.contains("skull.texture()"));
		assertTrue(itemSource.contains("PlayerHeadSpecialRenderer"));
		assertTrue(itemSource.contains("info.playerSkin().body().texturePath()"));
		assertTrue(itemSource.contains("ShulkerBoxSpecialRenderer"));
		assertTrue(itemSource.contains("shulker.material().texture()"));
		assertTrue(itemSource.contains("shulker.orientation().getRotation()"));
		assertTrue(itemSource.contains("tryEnqueueModelPartPip"));
		assertTrue(itemSource.contains("DecoratedPotSpecialRenderer"));
		assertTrue(itemSource.contains("renderer.sideMaterial(decorations.front())"));
		assertTrue(itemSource.contains("special-renderer-unavailable"));
		assertTrue(itemSource.contains("ItemRenderer.ENCHANTED_GLINT_ITEM"));
		assertTrue(itemSource.contains("0xffffffff, 4"));
		assertTrue(itemSource.contains("shield.model(), ItemRenderer.ENCHANTED_GLINT_ITEM"));
		assertTrue(itemSource.contains("flagModel, ItemRenderer.ENCHANTED_GLINT_ITEM"));
		assertTrue(itemSource.contains("skull.model(), ItemRenderer.ENCHANTED_GLINT_ITEM"));
		assertTrue(itemSource.contains("playerHead.model(), ItemRenderer.ENCHANTED_GLINT_ITEM"));
		assertTrue(itemSource.contains("model, ItemRenderer.ENCHANTED_GLINT_ITEM"));
		assertTrue(itemSource.contains("hangingSign.model(), ItemRenderer.ENCHANTED_GLINT_ITEM"));
		assertTrue(itemSource.contains("standingSign.model(), ItemRenderer.ENCHANTED_GLINT_ITEM"));
		assertTrue(itemSource.contains("copperGolem.model(), ItemRenderer.ENCHANTED_GLINT_ITEM"));
		assertTrue(itemSource.contains("new Model.Simple(conduit.model(), RenderType::entitySolid)"));
		assertTrue(itemSource.contains("new Model.Simple(part, RenderType::entitySolid), ItemRenderer.ENCHANTED_GLINT_ITEM"));
		assertTrue(itemSource.contains("foot ? bed.footModel() : bed.headModel(), ItemRenderer.ENCHANTED_GLINT_ITEM"));
		assertTrue(itemSource.contains("model, ItemRenderer.ENCHANTED_GLINT_ITEM"));
		assertTrue(stateSource.contains("forEachSpecialRenderer"));
	}

	@Test
	void crosshairBlitsUseTheExplicitInvertAffineRoute() throws Exception {
		String source = java.nio.file.Files.readString(java.nio.file.Path.of(
			"src/main/java/net/vulkanic/gui/RustGalGuiRenderer.java"
		));
		assertTrue(source.contains("blit.pipeline() != RenderPipelines.CROSSHAIR"));
		assertTrue(source.contains("boolean invertBlend = blit.pipeline() == RenderPipelines.CROSSHAIR"));
		assertTrue(source.contains("invertBlend ? GuiRenderStratum.GUI_CROSSHAIR.order()"));
	}

	@Test
	void premultipliedGuiBlitsUseAnExplicitBlendStratum() throws Exception {
		String source = java.nio.file.Files.readString(java.nio.file.Path.of(
			"src/main/java/net/vulkanic/gui/RustGalGuiRenderer.java"
		));
		assertTrue(source.contains("blit.pipeline() != RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA"));
		assertTrue(source.contains("boolean premultipliedBlend = blit.pipeline() == RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA"));
		assertTrue(source.contains("premultipliedBlend ? 790"));
	}

	@Test
	void nauseaOverlayBlitsUseTheExplicitAdditiveRoute() throws Exception {
		String source = java.nio.file.Files.readString(java.nio.file.Path.of(
			"src/main/java/net/vulkanic/gui/RustGalGuiRenderer.java"
		));
		assertTrue(source.contains("blit.pipeline() != RenderPipelines.GUI_NAUSEA_OVERLAY"));
		assertTrue(source.contains("boolean additiveBlend = blit.pipeline() == RenderPipelines.GUI_NAUSEA_OVERLAY"));
		assertTrue(source.contains("additiveBlend ? 795"));
	}

	@Test
	void tiledGuiUvIntervalsPreserveRepeatedTextureTurns() throws Exception {
		var method = RustGalGuiRenderer.class.getDeclaredMethod("wrappedUnitIntervalSegments", float.class, float.class);
		method.setAccessible(true);
		@SuppressWarnings("unchecked")
		var segments = (java.util.List<float[]>) method.invoke(null, 0.75F, 2.25F);
		assertEquals(3, segments.size());
		assertEquals(0.75F, segments.get(0)[2], 0.0001F);
		assertEquals(0.0F, segments.get(1)[2], 0.0001F);
		assertEquals(0.0F, segments.get(2)[2], 0.0001F);
		assertEquals(1.0F, segments.get(2)[3], 0.0001F);
	}

	@Test
	void mojangLogoUsesTheExistingExplicitAlphaAffineRoute() throws Exception {
		String source = java.nio.file.Files.readString(java.nio.file.Path.of(
			"src/main/java/net/vulkanic/gui/RustGalGuiRenderer.java"
		));
		assertTrue(source.contains("blit.pipeline() != RenderPipelines.MOJANG_LOGO"));
	}

	@Test
	void firstPersonScreenEffectsUseTheCopiedAffineTextureRoute() throws Exception {
		String source = java.nio.file.Files.readString(java.nio.file.Path.of(
			"src/main/java/net/vulkanic/gui/RustGalGuiRenderer.java"
		));
		assertTrue(source.contains("blit.pipeline() != RenderPipelines.BLOCK_SCREEN_EFFECT"));
		assertTrue(source.contains("blit.pipeline() != RenderPipelines.FIRE_SCREEN_EFFECT"));
		assertTrue(source.contains("first-person block/fire screen effects are ordinary single-sampler"));
	}

	@Test
	void textHighlightRectanglesUseTheExplicitAdditiveStratum() throws Exception {
		String source = java.nio.file.Files.readString(java.nio.file.Path.of(
			"src/main/java/net/vulkanic/gui/RustGalGuiRenderer.java"
		));
		assertTrue(source.contains("rectangle.pipeline() == RenderPipelines.GUI_TEXT_HIGHLIGHT"));
		assertTrue(source.contains("requestLayerOrder = 795"));
	}

	@Test
	void taczGuiSpecialItemsUseBoundedBedrockQuadAdapter() throws Exception {
		String source = java.nio.file.Files.readString(java.nio.file.Path.of(
			"src/main/java/net/vulkanic/gui/RustGalGuiItemRenderer.java"
		));
		assertTrue(source.contains("instanceof TaczGlock17SpecialRenderer"));
		assertTrue(source.contains("TaczGuiSubmitCollector"));
		assertTrue(source.contains("submitTexturedQuads"));
		assertTrue(source.contains("private static final int MAX_QUADS = 4096"));
		assertTrue(source.contains("TACZ GUI semantic capture encountered arbitrary custom geometry"));
		assertTrue(source.contains("minecraft.gui.tacz-bedrock"));
		assertTrue(source.contains("item.pose().m00()"));
		assertTrue(source.contains("glintAsset != null"));
		assertTrue(source.contains("ItemRenderer.SPECIAL_FOIL_TEXTURE_SCALE"));
		assertTrue(source.contains("new VulkanicGalBridge.GuiMeshBatchRecord(layerOrder, records.size(), 4"));
	}
}
