package net.vulkanic.world;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import net.blaze3d.vertex.PoseStack;
import net.blaze3d.vertex.SheetedDecalTextureGenerator;
import net.blaze3d.vertex.VertexConsumer;
import net.math.Axis;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class CrumblingProjectionSemanticsTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void copiedProjectionMatchesVanillaSheetedDecalForTranslatedAndRotatedModel() {
        PoseStack camera = new PoseStack();
        camera.translate(13.25F, -4.5F, 8.75F);
        camera.mulPose(Axis.YP.rotationDegrees(23.0F));

        PoseStack entity = new PoseStack();
        entity.last().set(camera.last());
        entity.translate(1.5F, 2.25F, -0.75F);
        entity.mulPose(Axis.XP.rotationDegrees(-31.0F));
        entity.mulPose(Axis.ZP.rotationDegrees(17.0F));

        var projection = new RustGalWorldPrimitiveRenderer.CrumblingProjection(
            entity.last(), camera.last(), 5
        );
        Vector3f localPosition = new Vector3f(0.375F, -0.625F, 1.125F);
        for (Vector3f localNormal : new Vector3f[] {
            new Vector3f(1.0F, 0.0F, 0.0F),
            new Vector3f(0.0F, 1.0F, 0.0F),
            new Vector3f(0.0F, 0.0F, -1.0F),
        }) {
            RecordingVertexConsumer delegate = new RecordingVertexConsumer();
            SheetedDecalTextureGenerator vanilla = new SheetedDecalTextureGenerator(
                delegate, camera.last(), 1.0F
            );
            Vector3f worldPosition = entity.last().pose().transformPosition(
                localPosition, new Vector3f()
            );
            Vector3f worldNormal = entity.last().transformNormal(
                localNormal, new Vector3f()
            );
            vanilla.addVertex(worldPosition.x(), worldPosition.y(), worldPosition.z());
            vanilla.setNormal(worldNormal.x(), worldNormal.y(), worldNormal.z());

            assertArrayEquals(
                new float[] {delegate.u, delegate.v},
                projection.project(localPosition, localNormal),
                1.0e-5F
            );
        }
    }

    private static final class RecordingVertexConsumer implements VertexConsumer {
        private float u;
        private float v;

        @Override public VertexConsumer addVertex(float x, float y, float z) { return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { this.u = u; this.v = v; return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }
}
