package net.vulkanic.world;

import java.lang.reflect.Method;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TranslucentMeshRasterSemanticsTest {
    @Test
    void entityDecalCopiesDeclaredEqualDepthAndWrites() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        Method extract = RustGalWorldPrimitiveRenderer.class.getDeclaredMethod("modelMeshRenderSemantics", RenderType.class);
        extract.setAccessible(true);
        RenderType type = RenderType.entityDecal(
            ResourceLocation.withDefaultNamespace("textures/entity/enderdragon/dragon.png"));
        assertEquals(net.blaze3d.platform.DepthTestFunction.EQUAL_DEPTH_TEST, type.pipeline().getDepthTestFunction());
        assertTrue(type.pipeline().isWriteDepth());
        Object semantics = extract.invoke(null, type);
        assertEquals(RustGalWorldPrimitiveRenderer.DEPTH_POLICY_TEST_EQUAL_WRITE, field(semantics, "depthPolicy"));
        assertEquals(RustGalWorldPrimitiveRenderer.MATERIAL_MODE_CUTOUT, field(semantics, "materialMode"));
        assertEquals(RustGalWorldPrimitiveRenderer.CULL_NONE, field(semantics, "cullPolicy"));
    }

    @Test
    void playerHandTranslucentCutoutIsAcceptedBySubmissionMaterialCheck() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        Method extract = RustGalWorldPrimitiveRenderer.class.getDeclaredMethod("modelMeshRenderSemantics", RenderType.class);
        extract.setAccessible(true);
        Method accepts = RustGalWorldPrimitiveRenderer.class.getDeclaredMethod("isTranslucentModelMaterial", extract.getReturnType());
        accepts.setAccessible(true);
        ResourceLocation texture = ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");
        assertEquals(true, accepts.invoke(null, extract.invoke(null, RenderType.entityTranslucent(texture))));
        assertEquals(true, accepts.invoke(null, extract.invoke(null, RenderType.itemEntityTranslucentCull(texture))));
        assertEquals(false, accepts.invoke(null, extract.invoke(null, RenderType.entityCutout(texture))));
        assertEquals(false, accepts.invoke(null, extract.invoke(null, RenderType.entitySolid(texture))));
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
            "src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"));
        int start = source.lastIndexOf("public static <S> boolean enqueueStandaloneTranslucentModelMesh(");
        int end = source.indexOf("return enqueueEligibleModelMesh(", start);
        assertTrue(source.substring(start, end).contains("!isTranslucentModelMaterial(semantics)"));
    }

    @Test
    void decalArmorCopiesEqualDepthWritesAndRetainsPrivateAdmission() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        Method extract = RustGalWorldPrimitiveRenderer.class.getDeclaredMethod("modelMeshRenderSemantics", RenderType.class);
        extract.setAccessible(true);
        RenderType type = RenderType.createArmorDecalCutoutNoCull(
            ResourceLocation.withDefaultNamespace("textures/atlas/armor_trims.png"));
        String key = "mattmc.dev.rustArmorFoil", previous = System.getProperty(key);
        var threadField = net.vulkanic.VulkanicAPI.class.getDeclaredField("renderThread");
        threadField.setAccessible(true);
        Object previousThread = threadField.get(null);
        try {
            threadField.set(null, Thread.currentThread());
            System.clearProperty(key);
            assertNull(extract.invoke(null, type));
            System.setProperty(key, "true");
            Object semantics = extract.invoke(null, type);
            assertEquals(net.blaze3d.platform.DepthTestFunction.EQUAL_DEPTH_TEST, type.pipeline().getDepthTestFunction());
            assertTrue(type.pipeline().isWriteDepth());
            assertEquals(RustGalWorldPrimitiveRenderer.DEPTH_POLICY_TEST_EQUAL_WRITE, field(semantics, "depthPolicy"));
            assertEquals(RustGalWorldPrimitiveRenderer.MATERIAL_MODE_CUTOUT, field(semantics, "materialMode"));
            assertEquals(RustGalWorldPrimitiveRenderer.MATERIAL_ID_PER_FACE_MODEL_CUTOUT_TEXTURED, field(semantics, "materialId"));
            assertEquals(RustGalWorldPrimitiveRenderer.CULL_NONE, field(semantics, "cullPolicy"));
            assertNotEquals(0, field(semantics, "viewLayerFlags"));
        } finally {
            threadField.set(null, previousThread);
            if (previous == null) System.clearProperty(key); else System.setProperty(key, previous);
        }
    }

    @Test
    void translucentArmorPreservesCutoutDepthAndPrivateAdmission() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        Method extract = RustGalWorldPrimitiveRenderer.class.getDeclaredMethod("modelMeshRenderSemantics", RenderType.class);
        extract.setAccessible(true);
        RenderType type = RenderType.armorTranslucent(
            ResourceLocation.withDefaultNamespace("textures/entity/equipment/humanoid/diamond.png"));
        String key = "mattmc.dev.rustArmorFoil", previous = System.getProperty(key);
        var threadField = net.vulkanic.VulkanicAPI.class.getDeclaredField("renderThread");
        threadField.setAccessible(true);
        Object previousThread = threadField.get(null);
        try {
            threadField.set(null, Thread.currentThread());
            System.clearProperty(key);
            assertNull(extract.invoke(null, type));
            System.setProperty(key, "true");
            Object semantics = extract.invoke(null, type);
            assertEquals(RustGalWorldPrimitiveRenderer.MATERIAL_MODE_TRANSLUCENT_CUTOUT, field(semantics, "materialMode"));
            assertEquals(RustGalWorldPrimitiveRenderer.MATERIAL_ID_PER_FACE_TRANSLUCENT_CUTOUT_TEXTURED, field(semantics, "materialId"));
            assertRasterMatchesPipeline(type);
            assertNotEquals(0, field(semantics, "viewLayerFlags"));
        } finally {
            threadField.set(null, previousThread);
            if (previous == null) System.clearProperty(key); else System.setProperty(key, previous);
        }
    }

    @Test
    void solidModelMaterialsRemainOpaqueAndCutoutModelsRemainDistinct() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        ResourceLocation texture = ResourceLocation.withDefaultNamespace("textures/entity/shield_base_nopattern.png");
        Method extract = RustGalWorldPrimitiveRenderer.class.getDeclaredMethod("modelMeshRenderSemantics", RenderType.class);
        extract.setAccessible(true);
        for (RenderType type : new RenderType[]{RenderType.entitySolid(texture),
                RenderType.entitySolidZOffsetForward(texture)}) {
            Object semantics = extract.invoke(null, type);
            assertEquals(RustGalWorldPrimitiveRenderer.MATERIAL_MODE_OPAQUE, field(semantics, "materialMode"));
            assertEquals(RustGalWorldPrimitiveRenderer.MATERIAL_ID_OPAQUE_TEXTURED, field(semantics, "materialId"));
            assertRasterMatchesPipeline(type);
        }
        for (RenderType type : new RenderType[]{RenderType.entityCutout(texture),
                RenderType.entityCutoutNoCull(texture), RenderType.entityCutoutNoCullZOffset(texture)}) {
            Object semantics = extract.invoke(null, type);
            assertEquals(RustGalWorldPrimitiveRenderer.MATERIAL_MODE_CUTOUT, field(semantics, "materialMode"));
            assertEquals(type == RenderType.entityCutout(texture)
                ? RustGalWorldPrimitiveRenderer.MATERIAL_ID_MODEL_CUTOUT_TEXTURED
                : RustGalWorldPrimitiveRenderer.MATERIAL_ID_PER_FACE_MODEL_CUTOUT_TEXTURED, field(semantics, "materialId"));
            assertRasterMatchesPipeline(type);
        }
    }

    @Test
    void itemPipelineCarriesBlendPlusCutoutInsteadOfTerrainTranslucency() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        Method extract = RustGalWorldPrimitiveRenderer.class.getDeclaredMethod("modelMeshRenderSemantics", RenderType.class);
        extract.setAccessible(true);
        Object semantics = extract.invoke(null, RenderType.itemEntityTranslucentCull(
            ResourceLocation.withDefaultNamespace("textures/block/stone.png")));
        assertEquals(RustGalWorldPrimitiveRenderer.MATERIAL_MODE_TRANSLUCENT_CUTOUT, field(semantics, "materialMode"));
        assertEquals(RustGalWorldPrimitiveRenderer.MATERIAL_ID_TRANSLUCENT_CUTOUT_TEXTURED, field(semantics, "materialId"));
        assertEquals(RustGalWorldPrimitiveRenderer.DEPTH_POLICY_TEST_WRITE, field(semantics, "depthPolicy"));
        assertEquals(RustGalWorldPrimitiveRenderer.CULL_BACK, field(semantics, "cullPolicy"));
    }

    @Test
    void itemCullingAndEntityDoubleSidednessRemainDistinct() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        ResourceLocation texture = ResourceLocation.withDefaultNamespace("textures/block/glass.png");
        RenderType item = RenderType.itemEntityTranslucentCull(texture);
        RenderType entity = RenderType.entityTranslucent(texture);
        assertTrue(item.pipeline().isCull());
        assertTrue(item.pipeline().isWriteDepth());
        assertFalse(entity.pipeline().isCull());
        Method extract = RustGalWorldPrimitiveRenderer.class.getDeclaredMethod("modelMeshRenderSemantics", RenderType.class);
        extract.setAccessible(true);
        Object entitySemantics = extract.invoke(null, entity);
        assertEquals(RustGalWorldPrimitiveRenderer.MATERIAL_MODE_TRANSLUCENT_CUTOUT, field(entitySemantics, "materialMode"));
        assertEquals(RustGalWorldPrimitiveRenderer.MATERIAL_ID_PER_FACE_TRANSLUCENT_CUTOUT_TEXTURED, field(entitySemantics, "materialId"));
        assertRasterMatchesPipeline(item);
        assertRasterMatchesPipeline(entity);
        assertRasterMatchesPipeline(RenderType.entityTranslucentEmissive(texture));
    }

    @Test
    void eyesPreserveFullbrightTranslucentMaterialIdentityAndRasterState() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        RenderType eyes = RenderType.eyes(
            ResourceLocation.withDefaultNamespace("textures/entity/spider_eyes.png"));
        Method extract = RustGalWorldPrimitiveRenderer.class.getDeclaredMethod("modelMeshRenderSemantics", RenderType.class);
        extract.setAccessible(true);
        Object semantics = extract.invoke(null, eyes);
        assertNotNull(semantics);
        assertEquals(RustGalWorldPrimitiveRenderer.MATERIAL_ID_MODEL_EYES, field(semantics, "materialId"));
        assertEquals(RustGalWorldPrimitiveRenderer.MATERIAL_MODE_TRANSLUCENT, field(semantics, "materialMode"));
        assertEquals(RustGalWorldPrimitiveRenderer.DEPTH_POLICY_TEST_NO_WRITE, field(semantics, "depthPolicy"));
        assertRasterMatchesPipeline(eyes);
    }

    @Test
    void translucentEmissivePreservesFullbrightCutoutCardinalLightingAndRasterState() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        RenderType emissive = RenderType.entityTranslucentEmissive(
            ResourceLocation.withDefaultNamespace("textures/entity/warden/warden_bioluminescent_layer.png"));
        Method extract = RustGalWorldPrimitiveRenderer.class.getDeclaredMethod("modelMeshRenderSemantics", RenderType.class);
        extract.setAccessible(true);
        Object semantics = extract.invoke(null, emissive);
        assertNotNull(semantics);
        assertEquals(RustGalWorldPrimitiveRenderer.MATERIAL_ID_MODEL_TRANSLUCENT_EMISSIVE, field(semantics, "materialId"));
        assertEquals(RustGalWorldPrimitiveRenderer.MATERIAL_MODE_TRANSLUCENT_CUTOUT, field(semantics, "materialMode"));
        assertEquals(RustGalWorldPrimitiveRenderer.DEPTH_POLICY_TEST_NO_WRITE, field(semantics, "depthPolicy"));
        assertEquals(RustGalWorldPrimitiveRenderer.CULL_NONE, field(semantics, "cullPolicy"));
        assertRasterMatchesPipeline(emissive);
    }

    @Test
    void breezeWindPreservesCutoutDepthLightingAndBoundedUvOffset() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        ResourceLocation texture = ResourceLocation.withDefaultNamespace("textures/entity/breeze/breeze_wind.png");
        RenderType wind = RenderType.breezeWind(texture, 0.25F, 0.0F);
        Method extract = RustGalWorldPrimitiveRenderer.class.getDeclaredMethod("modelMeshRenderSemantics", RenderType.class);
        extract.setAccessible(true);
        Object semantics = extract.invoke(null, wind);
        assertNotNull(semantics);
        assertEquals(RustGalWorldPrimitiveRenderer.MATERIAL_ID_MODEL_BREEZE_WIND, field(semantics, "materialId"));
        assertEquals(RustGalWorldPrimitiveRenderer.MATERIAL_MODE_TRANSLUCENT_CUTOUT, field(semantics, "materialMode"));
        assertEquals(RustGalWorldPrimitiveRenderer.DEPTH_POLICY_TEST_WRITE, field(semantics, "depthPolicy"));
        assertEquals(RustGalWorldPrimitiveRenderer.CULL_NONE, field(semantics, "cullPolicy"));
        assertRasterMatchesPipeline(wind);

        Method encode = RustGalWorldPrimitiveRenderer.class.getDeclaredMethod("encodeModelUvOffsetU", float.class);
        encode.setAccessible(true);
        int flags = (int) encode.invoke(null, 0.25F);
        assertTrue((flags & 0x80000000) != 0);
        int quantized = (flags & 0x7FFFFFF0) >>> 4;
        assertEquals(0.25F, quantized / (float)0x07FFFFFF, 0.000001F);
        for (float invalid : new float[] {-0.01F, 1.0F, Float.NaN, Float.POSITIVE_INFINITY}) {
            assertThrows(java.lang.reflect.InvocationTargetException.class, () -> encode.invoke(null, invalid));
        }
    }

    private static void assertRasterMatchesPipeline(RenderType type) throws Exception {
        Method extract = RustGalWorldPrimitiveRenderer.class.getDeclaredMethod("modelMeshRenderSemantics", RenderType.class);
        extract.setAccessible(true);
        Object record = extract.invoke(null, type);
        assertNotNull(record);
        assertEquals(type.pipeline().isCull() ? RustGalWorldPrimitiveRenderer.CULL_BACK : RustGalWorldPrimitiveRenderer.CULL_NONE,
            field(record, "cullPolicy"));
        assertEquals(type.pipeline().isWriteDepth() ? RustGalWorldPrimitiveRenderer.DEPTH_POLICY_TEST_WRITE
            : RustGalWorldPrimitiveRenderer.DEPTH_POLICY_TEST_NO_WRITE, field(record, "depthPolicy"));
    }

    private static int field(Object record, String name) throws Exception {
        Method accessor = record.getClass().getDeclaredMethod(name);
        accessor.setAccessible(true);
        return (int) accessor.invoke(record);
    }
}
