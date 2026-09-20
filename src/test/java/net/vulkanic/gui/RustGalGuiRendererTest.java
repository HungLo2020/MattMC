package net.vulkanic.gui;

import net.minecraft.client.gui.font.TextGlyphQuad;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RustGalGuiRendererTest {
	@Test
	void entityPreviewPreservesPlayerAndArmorNoCullMaterials() {
		var texture = net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/player/wide/hunglo.png");
		assertEquals(net.vulkanic.bridge.VulkanicGalBridge.GUI_MESH_MATERIAL_ENTITY_TRANSLUCENT_NO_CULL,
			RustGalGuiRenderer.entityPipMaterialMode(net.minecraft.client.renderer.RenderType.entityTranslucent(texture)));
		assertEquals(net.vulkanic.bridge.VulkanicGalBridge.GUI_MESH_MATERIAL_ENTITY_CUTOUT_NO_CULL,
			RustGalGuiRenderer.entityPipMaterialMode(net.minecraft.client.renderer.RenderType.armorCutoutNoCull(texture)));
		assertEquals(net.vulkanic.bridge.VulkanicGalBridge.GUI_MESH_MATERIAL_ENTITY_DECAL_CUTOUT_NO_CULL,
			RustGalGuiRenderer.entityPipMaterialMode(net.minecraft.client.renderer.RenderType.createArmorDecalCutoutNoCull(texture)));
		assertEquals(4, RustGalGuiRenderer.entityPipMaterialMode(net.minecraft.client.renderer.RenderType.armorEntityGlint()));
		assertEquals(3, RustGalGuiRenderer.entityPipMaterialMode(
			net.minecraft.client.renderer.RenderType.itemEntityTranslucentCull(texture)));
	}

	@Test
	void entityLayerCopyConsumesStateImmediatelyAndKeepsIndependentOrderHandles() throws Exception {
		var part = new net.minecraft.client.model.geom.ModelPart(List.of(), java.util.Map.of());
		var model = new net.minecraft.client.model.Model<float[]>(part, net.minecraft.client.renderer.RenderType::entitySolid) {
			@Override public void setupAnim(float[] state) { root().x = state[0]; }
		};
		var observed = new java.util.ArrayList<List<Number>>();
		java.util.function.Function<Object,GuiModelPipSemanticCollector.Result> copy = input -> {
			try {
				var setup = input.getClass().getDeclaredMethod("setupModel"); setup.setAccessible(true);
				((Runnable)setup.invoke(input)).run();
				var tint = input.getClass().getDeclaredMethod("tint"); tint.setAccessible(true);
				var order = input.getClass().getDeclaredMethod("layerOrder"); order.setAccessible(true);
				observed.add(List.of((Integer)order.invoke(input),(Integer)tint.invoke(input),part.x));
				var vertex = new net.vulkanic.bridge.VulkanicGalBridge.GuiMeshVertexRecord(
					new float[] {part.x,0,0},new float[] {0,0},new float[] {0,0},(Integer)tint.invoke(input),0);
				var batch = new net.vulkanic.bridge.VulkanicGalBridge.GuiMeshBatchRecord(
					1,0,2,2,7L,0L,0F,new float[] {1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1},new float[] {1,0,0,1,0,0},
					0,0,16,16,32,32,18,18,1,List.of(vertex,vertex,vertex),List.of(0,1,2));
				return new GuiModelPipSemanticCollector.Result(batch,
					new net.minecraft.client.gui.navigation.ScreenRectangle(0,0,16,16),List.of());
			} catch (ReflectiveOperationException e) {throw new AssertionError(e);}
		};
		Class<?> type = Class.forName("net.vulkanic.gui.RustGalGuiRenderer$EntityPipLayerCapture");
		var constructor = type.getDeclaredConstructor(java.util.function.Function.class); constructor.setAccessible(true);
		var root = (net.minecraft.client.renderer.SubmitNodeCollector)constructor.newInstance(copy);
		var first = root.order(7);
		var second = root.order(-2);
		var texture = net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/equipment/humanoid/leather.png");
		var material = net.minecraft.client.renderer.RenderType.armorCutoutNoCull(texture);
		var pose = new net.blaze3d.vertex.PoseStack();
		float[] state = {3F};
		first.submitModelSemanticTexture(model,state,pose,material,15728880,655360,0xff3366cc,texture,0,null);
		state[0]=9F;
		second.submitModelSemanticTexture(model,state,pose,material,15728880,655360,-1,texture,0,null);
		state[0]=12F;
		first.submitModelSemanticTexture(model,state,pose,material,15728880,655360,0xff112233,texture,0,null);
		state[0]=99F; part.x=99F;
		assertEquals(List.of(List.of(7,0xff3366cc,3F),List.of(-2,-1,9F),List.of(7,0xff112233,12F)),observed);
		for(int i=3;i<33;i++) first.submitModelSemanticTexture(model,state,pose,material,15728880,655360,-1,texture,0,null);
		assertEquals(32,observed.size(),"the model copy bound is shared across order handles");
		var unsupported = type.getDeclaredField("unsupported");unsupported.setAccessible(true);
		assertTrue(unsupported.getBoolean(root));
	}

	@Test
	void entityPreviewLayersShareOneSequenceAndRetainTheirGeometry() throws ReflectiveOperationException {
		var vertex = new net.vulkanic.bridge.VulkanicGalBridge.GuiMeshVertexRecord(
			new float[] {0,0,0}, new float[] {0,0}, new float[] {0,0}, 0xff3366cc, 0);
		var bounds = new net.minecraft.client.gui.navigation.ScreenRectangle(0,0,16,16);
		var inputs = new java.util.ArrayList<GuiModelPipSemanticCollector.Result>();
		for (int i=0; i<3; i++) {
			var batch = new net.vulkanic.bridge.VulkanicGalBridge.GuiMeshBatchRecord(
				1,0, i==0 ? 1 : 2,2,7L+i,0L,0F,
				new float[] {1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1}, new float[] {1,0,0,1,0,0},
				0,0,16,16,32,32,18,18,1,List.of(vertex,vertex,vertex),List.of(0,1,2));
			inputs.add(new GuiModelPipSemanticCollector.Result(batch,bounds,List.of()));
		}
		var batches = RustGalGuiRenderer.entityPipBatches(inputs, 17);
		var output = new java.util.ArrayList<net.vulkanic.bridge.VulkanicGalBridge.GuiMeshBatchRecord>();
		Class<?> queued = Class.forName("net.vulkanic.gui.RustGalFrameCoordinator$QueuedGuiRequest");
		var factory = queued.getDeclaredMethod("meshBatches",List.class);
		factory.setAccessible(true);
		var append = queued.getDeclaredMethod("appendTo",List.class,List.class,List.class,List.class,long.class);
		append.setAccessible(true);
		append.invoke(factory.invoke(null,batches),new java.util.ArrayList<>(),new java.util.ArrayList<>(),
			output,new java.util.ArrayList<>(),42L);
		assertEquals(3,output.size());
		for (int i=0;i<3;i++) {
			assertEquals(42L,output.get(i).sequence());
			assertEquals(i,output.get(i).layerIndex());
			assertEquals(17,output.get(i).stratum());
			assertEquals(net.vulkanic.bridge.VulkanicGalBridge.GUI_MESH_LIGHTING_ENTITY_PREVIEW,output.get(i).lightingMode());
			assertEquals(7L+i,output.get(i).assetId());
			assertEquals(inputs.get(i).batch().vertices(),output.get(i).vertices());
			assertEquals(inputs.get(i).batch().materialMode(),output.get(i).materialMode());
		}
	}

	@Test
	void shippedDefaultPanoramaHasSixRealDimensionMatchedFaces() throws Exception {
		Path root = Path.of("src/main/resources/assets/minecraft/textures/gui/title/background/caves");
		int width = -1;
		int height = -1;
		for (int face = 0; face < 6; face++) {
			var image = ImageIO.read(root.resolve("panorama_" + face + ".png").toFile());
			assertTrue(image != null && image.getWidth() > 1 && image.getHeight() > 1,
				"default panorama face must be a real image, not a placeholder");
			if (face == 0) {
				width = image.getWidth();
				height = image.getHeight();
			} else {
				assertEquals(width, image.getWidth(), "panorama faces must share one width");
				assertEquals(height, image.getHeight(), "panorama faces must share one height");
			}
		}
	}


	@Test
	void panoramaCubeUvMatchesFrozenTransposedModelViewRotation() {
		float[] identity = RustGalPanoramaRenderer.cubeUv(new Matrix4f(), 0.0F, 0.0F, 1.0F, 1.0F);
		assertEquals(5.5F / 6.0F, identity[1], 0.000001F);

		float[] quarterTurn = RustGalPanoramaRenderer.cubeUv(
			new Matrix4f().rotationY((float)(Math.PI * 0.5)), 0.0F, 0.0F, 1.0F, 1.0F
		);
		assertEquals(0.5F / 6.0F, quarterTurn[1], 0.000001F,
			"Frozen panorama.vsh applies transpose(mat3(ModelViewMat)) before the shared face selector");
	}



	private static int occurrences(String source, String needle) {
		int count = 0;
		int from = 0;
		while ((from = source.indexOf(needle, from)) >= 0) {
			count++;
			from += needle.length();
		}
		return count;
	}

	@Test
	void regularBlitRepeatIntervalsSplitIntoBoundedUnitUvs() {
		List<float[]> segments = RustGalGuiRenderer.wrappedUnitIntervalSegments(0.0F, 26.6875F);
		assertEquals(27, segments.size(), "a 32-pixel separator stretched to 854 pixels needs bounded repeated-image segments");
		assertEquals(0.0F, segments.getFirst()[2], 0.000001F);
		assertEquals(1.0F, segments.getFirst()[3], 0.000001F);
		assertEquals(0.6875F, segments.getLast()[3] - segments.getLast()[2], 0.000001F);
	}








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
	void voxelMapFrameResourceRetainsTransparentCenter() throws Exception {
		try (var stream = RustGalGuiRendererTest.class.getResourceAsStream("/assets/voxelmap/images/squaremap.png")) {
			assertTrue(stream != null);
			var image = javax.imageio.ImageIO.read(stream);
			assertEquals(256, image.getWidth());
			assertEquals(256, image.getHeight());
			assertEquals(0, (image.getRGB(128, 128) >>> 24) & 0xff);
			assertEquals(255, (image.getRGB(10, 128) >>> 24) & 0xff);
		}
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










































}
