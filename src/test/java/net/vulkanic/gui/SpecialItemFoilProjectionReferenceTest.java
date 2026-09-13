package net.vulkanic.gui;

import net.blaze3d.vertex.PoseStack;
import net.blaze3d.vertex.SheetedDecalTextureGenerator;
import net.blaze3d.vertex.VertexConsumer;
import net.math.MatrixUtil;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/** Supplemental numeric reference, not admission or end-to-end parity.
 * SheetedDecalTextureGenerator and Direction were byte-compared with Frozen's
 * unchanged Java OpenGL sources before establishing these native test vectors.
 */
class SpecialItemFoilProjectionReferenceTest {
    @Test void frozenDecalConsumerAgreesWithNativeSixFaceAndContextVectors() {
        float[][] normals={{0,-1,0},{0,1,0},{0,0,-1},{0,0,1},{-1,0,0},{1,0,0},
            {0,0,0},{1,1,1},{1,-1,1},{1,0,-1}};
        float[][] expected={{2,-5},{2,5},{-2,-3},{2,-3},{-5,-3},{5,-3},
            {-2,-3},{2,5},{2,-5},{-2,-3}};
        for (float scale : new float[]{0.5F,0.75F,1F}) {
            for (int i=0;i<normals.length;i++) {
                var pose=new PoseStack().last().copy();
                MatrixUtil.mulComponentWise(pose.pose(),scale);
                float[] actual=project(pose,new float[]{2,3,5},normals[i]);
                assertArrayEquals(new float[]{expected[i][0]/scale/128F,expected[i][1]/scale/128F},
                    actual,1.0e-6F,"face="+i+" scale="+scale);
            }
        }
    }

    @Test void frozenUsesTheIndependentNormalPoseAndComponentWiseGuiScale() {
        var pose=new PoseStack().last().copy();
        pose.pose().scaling(2,-3,4).setTranslation(10,20,30);
        pose.normal().scaling(0.5F,-1F/3F,0.25F);
        MatrixUtil.mulComponentWise(pose.pose(),0.5F);
        assertArrayEquals(new float[]{4F/128F,10F/128F},
            project(pose,new float[]{14,11,50},new float[]{0,-1F/3F,0}),1.0e-6F);
        pose.pose().set(new float[]{0,2,0,0,-3,0,0,0,0,0,4,0,10,20,30,1});
        pose.normal().set(new float[]{0,0.5F,0,-1F/3F,0,0,0,0,0.25F});
        MatrixUtil.mulComponentWise(pose.pose(),0.5F);
        assertArrayEquals(new float[]{4F/128F,10F/128F},
            project(pose,new float[]{1,24,50},new float[]{-1F/3F,0,0}),1.0e-6F);
    }

    @Test void worldBulkDecalConsumesQuantizedEmittedNormals() {
        // The same independent model/normal poses and local packed normals are
        // consumed by the native world_item_foil conformance test.
        for (boolean trusted : new boolean[]{true, false}) {
            for (float scale : new float[]{1F, 0.75F}) {
                var pose = new PoseStack.Pose();
                pose.pose().scaling(2,-3,4).setTranslation(10,20,30);
                pose.normal().scaling(1,0.25F,2);
                pose.trustedNormals = trusted;
                int[] normals = {0x003f4040, 0, 0x00007f00, 0x007f0000};
                float[][] axes = {trusted ? new float[]{2,5} : new float[]{5,-3}, {-2,-3},{2,5},{2,-3}};
                for (int i=0;i<normals.length;i++) {
                    var sink = new UvSink();
                    var decalPose = pose.copy();
                    MatrixUtil.mulComponentWise(decalPose.pose(), scale);
                    var decal = new SheetedDecalTextureGenerator(sink, decalPose, 1F/128F);
                    int normal = net.sodium.api.math.MatrixHelper.transformNormal(
                        pose.normal(), pose.trustedNormals, normals[i]);
                    try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
                        long ptr = stack.nmalloc(net.sodium.api.vertex.format.common.EntityVertex.STRIDE);
                        net.sodium.api.vertex.format.common.EntityVertex.write(ptr, 14,11,50,
                            0x12345678, 99, -99, 0, 0, normal);
                        decal.push(stack, ptr, 1, net.sodium.api.vertex.format.common.EntityVertex.FORMAT);
                    }
                    assertArrayEquals(new float[]{axes[i][0]/scale/128F, axes[i][1]/scale/128F},
                        sink.uv, 1e-6F, "trusted="+trusted+" normal="+Integer.toHexString(normals[i]));
                }
            }
        }
    }

    private static float[] project(PoseStack.Pose pose,float[] position,float[] normal) {
        var sink=new UvSink();
        var decal=new SheetedDecalTextureGenerator(sink,pose,0.0078125F);
        decal.addVertex(position[0],position[1],position[2]);
        decal.setNormal(normal[0],normal[1],normal[2]);
        return sink.uv;
    }

    private static final class UvSink implements VertexConsumer, net.sodium.api.vertex.buffer.VertexBufferWriter {
        public void push(org.lwjgl.system.MemoryStack stack, long ptr, int count, net.blaze3d.vertex.VertexFormat format) {
            int offset = format.getOffset(net.blaze3d.vertex.VertexFormatElement.UV0);
            uv = new float[]{org.lwjgl.system.MemoryUtil.memGetFloat(ptr+offset),
                org.lwjgl.system.MemoryUtil.memGetFloat(ptr+offset+4)};
        }
        float[] uv;
        public VertexConsumer addVertex(float x,float y,float z) { return this; }
        public VertexConsumer setColor(int r,int g,int b,int a) { return this; }
        public VertexConsumer setUv(float u,float v) { uv=new float[]{u,v};return this; }
        public VertexConsumer setUv1(int u,int v) { return this; }
        public VertexConsumer setUv2(int u,int v) { return this; }
        public VertexConsumer setNormal(float x,float y,float z) { return this; }
    }
}
