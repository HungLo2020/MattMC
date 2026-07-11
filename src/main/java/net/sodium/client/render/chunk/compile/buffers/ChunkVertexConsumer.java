package net.sodium.client.render.chunk.compile.buffers;

import net.blaze3d.vertex.VertexConsumer;
import net.sodium.api.util.ColorABGR;
import net.sodium.api.util.ColorARGB;
import net.sodium.api.util.NormI8;
import net.sodium.client.model.quad.properties.ModelQuadFacing;
import net.sodium.client.render.chunk.terrain.material.Material;
import net.sodium.client.render.chunk.translucent_sorting.TranslucentGeometryCollector;
import net.sodium.client.render.chunk.vertex.format.NativeSectionMeshBuilder;
import net.sodium.client.render.texture.SpriteFinderCache;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.jetbrains.annotations.NotNull;

public class ChunkVertexConsumer implements VertexConsumer, net.irisshaders.iris.vertices.BlockSensitiveBufferBuilder {
    private static final int ATTRIBUTE_POSITION_BIT = 1 << 0;
    private static final int ATTRIBUTE_COLOR_BIT = 1 << 1;
    private static final int ATTRIBUTE_TEXTURE_BIT = 1 << 2;
    private static final int ATTRIBUTE_LIGHT_BIT = 1 << 3;
    private static final int ATTRIBUTE_NORMAL_BIT = 1 << 4;
    private static final int REQUIRED_ATTRIBUTES = (1 << 5) - 1;

    private final ChunkModelBuilder modelBuilder;
    private final float[] x = new float[4];
    private final float[] y = new float[4];
    private final float[] z = new float[4];
    private final int[] color = new int[4];
    private final float[] ao = new float[4];
    private final float[] u = new float[4];
    private final float[] v = new float[4];
    private final int[] light = new int[4];

    private Material material;
    private int vertexIndex;
    private int writtenAttributes;
    private TranslucentGeometryCollector collector;
    private int blockId;
    private int previousBlockId;
    private byte renderType;
    private byte blockEmission;
    private int localPosX;
    private int localPosY;
    private int localPosZ;
    private boolean ignoreMidBlock;
    private int emittedQuadCount;

    public ChunkVertexConsumer(ChunkModelBuilder modelBuilder) {
        this.modelBuilder = modelBuilder;
    }

    public void setData(Material material, TranslucentGeometryCollector collector) {
        this.material = material;
        this.collector = collector;
    }

    public int getEmittedQuadCount() {
        return this.emittedQuadCount;
    }

    public void resetEmittedQuadCount() {
        this.emittedQuadCount = 0;
    }

    @Override
    public @NotNull VertexConsumer addVertex(float x, float y, float z) {
        this.x[this.vertexIndex] = x;
        this.y[this.vertexIndex] = y;
        this.z[this.vertexIndex] = z;
        this.ao[this.vertexIndex] = 1.0f;
        this.writtenAttributes |= ATTRIBUTE_POSITION_BIT;
        return potentiallyEndVertex();
    }

    // Writing color ignores alpha since alpha is used as a color multiplier by Sodium.
    @Override
    public @NotNull VertexConsumer setColor(int red, int green, int blue, int alpha) {
        this.color[this.vertexIndex] = ColorABGR.pack(red, green, blue, alpha);
        this.writtenAttributes |= ATTRIBUTE_COLOR_BIT;
        return potentiallyEndVertex();
    }

    @Override
    public @NotNull VertexConsumer setColor(float red, float green, float blue, float alpha) {
        this.color[this.vertexIndex] = ColorABGR.pack(red, green, blue, alpha);
        this.writtenAttributes |= ATTRIBUTE_COLOR_BIT;
        return potentiallyEndVertex();
    }

    @Override
    public @NotNull VertexConsumer setColor(int argb) {
        this.color[this.vertexIndex] = ColorARGB.toABGR(argb);
        this.writtenAttributes |= ATTRIBUTE_COLOR_BIT;
        return potentiallyEndVertex();
    }

    @Override
    public @NotNull VertexConsumer setUv(float u, float v) {
        this.u[this.vertexIndex] = u;
        this.v[this.vertexIndex] = v;
        this.writtenAttributes |= ATTRIBUTE_TEXTURE_BIT;
        return potentiallyEndVertex();
    }

    // Overlay is ignored for chunk geometry.
    @Override
    public @NotNull VertexConsumer setUv1(int u, int v) {
        return potentiallyEndVertex();
    }

    @Override
    public @NotNull VertexConsumer setOverlay(int uv) {
        return potentiallyEndVertex();
    }

    @Override
    public @NotNull VertexConsumer setUv2(int u, int v) {
        this.light[this.vertexIndex] = ((v & 0xFFFF) << 16) | (u & 0xFFFF);
        this.writtenAttributes |= ATTRIBUTE_LIGHT_BIT;
        return potentiallyEndVertex();
    }

    @Override
    public @NotNull VertexConsumer setLight(int uv) {
        this.light[this.vertexIndex] = uv;
        this.writtenAttributes |= ATTRIBUTE_LIGHT_BIT;
        return potentiallyEndVertex();
    }

    @Override
    public @NotNull VertexConsumer setNormal(float x, float y, float z) {
        this.writtenAttributes |= ATTRIBUTE_NORMAL_BIT;
        return potentiallyEndVertex();
    }

    public VertexConsumer potentiallyEndVertex() {
        if (this.writtenAttributes != REQUIRED_ATTRIBUTES) {
            return this;
        }

        this.vertexIndex++;
        this.writtenAttributes = 0;

        if (this.vertexIndex == 4) {
            this.emittedQuadCount++;
            int normal = calculateNormal();

            ModelQuadFacing cullFace = ModelQuadFacing.fromPackedNormal(normal);

            NativeSectionMeshBuilder.FacingBuffer vertexBuffer = this.modelBuilder.getVertexBuffer(cullFace);
            if (this.material.isTranslucent() && this.collector != null) {
                if (this.appendNativeQuad(vertexBuffer, this.collector, cullFace, normal)) {
                    return this;
                }
            } else {
                this.appendNativeQuad(vertexBuffer, null, null, 0);
            }

            float u = 0;
            float v = 0;

            for (int index = 0; index < 4; index++) {
                u += this.u[index];
                v += this.v[index];
            }

            TextureAtlasSprite sprite = SpriteFinderCache.forBlockAtlas().find(u * 0.25f, v * 0.25f);

            if (sprite != null) {
                this.modelBuilder.addSprite(sprite);
            }

            this.vertexIndex = 0;
        }

        return this;
    }

    private int calculateNormal() {
        final float x0 = this.x[0];
        final float y0 = this.y[0];
        final float z0 = this.z[0];

        final float x1 = this.x[1];
        final float y1 = this.y[1];
        final float z1 = this.z[1];

        final float x2 = this.x[2];
        final float y2 = this.y[2];
        final float z2 = this.z[2];

        final float x3 = this.x[3];
        final float y3 = this.y[3];
        final float z3 = this.z[3];

        final float dx0 = x2 - x0;
        final float dy0 = y2 - y0;
        final float dz0 = z2 - z0;
        final float dx1 = x3 - x1;
        final float dy1 = y3 - y1;
        final float dz1 = z3 - z1;

        float normX = dy0 * dz1 - dz0 * dy1;
        float normY = dz0 * dx1 - dx0 * dz1;
        float normZ = dx0 * dy1 - dy0 * dx1;

        // normalize by length for the packed normal
        float length = (float) Math.sqrt(normX * normX + normY * normY + normZ * normZ);
        if (length != 0.0 && length != 1.0) {
            normX /= length;
            normY /= length;
            normZ /= length;
        }

        return NormI8.pack(normX, normY, normZ);
    }

    private boolean appendNativeQuad(NativeSectionMeshBuilder.FacingBuffer vertexBuffer,
            TranslucentGeometryCollector collector, ModelQuadFacing collectorFacing, int packedNormal) {
        if (collector != null) {
            return vertexBuffer.appendFlatTranslucentQuad(this.material.bits(), collector, collectorFacing,
                    packedNormal, this.blockEmission, this.renderType, this.ignoreMidBlock, this.blockId,
                    this.localPosX, this.localPosY, this.localPosZ,
                    this.x[0], this.y[0], this.z[0], this.color[0], this.ao[0], this.u[0], this.v[0], this.light[0],
                    this.x[1], this.y[1], this.z[1], this.color[1], this.ao[1], this.u[1], this.v[1], this.light[1],
                    this.x[2], this.y[2], this.z[2], this.color[2], this.ao[2], this.u[2], this.v[2], this.light[2],
                    this.x[3], this.y[3], this.z[3], this.color[3], this.ao[3], this.u[3], this.v[3], this.light[3]);
        }

        vertexBuffer.appendFlatQuad(this.material.bits(), this.blockEmission, this.renderType, this.ignoreMidBlock,
                this.blockId, this.localPosX, this.localPosY, this.localPosZ,
                this.x[0], this.y[0], this.z[0], this.color[0], this.ao[0], this.u[0], this.v[0], this.light[0],
                this.x[1], this.y[1], this.z[1], this.color[1], this.ao[1], this.u[1], this.v[1], this.light[1],
                this.x[2], this.y[2], this.z[2], this.color[2], this.ao[2], this.u[2], this.v[2], this.light[2],
                this.x[3], this.y[3], this.z[3], this.color[3], this.ao[3], this.u[3], this.v[3], this.light[3]);
        return false;
    }
    
    // Iris: BlockSensitiveBufferBuilder interface implementation
    @Override
    public void beginBlock(int block, byte renderType, byte blockEmission, int localPosX, int localPosY, int localPosZ) {
        this.blockId = block;
        this.renderType = renderType;
        this.blockEmission = blockEmission;
        this.localPosX = localPosX;
        this.localPosY = localPosY;
        this.localPosZ = localPosZ;
        ((net.irisshaders.iris.vertices.BlockSensitiveBufferBuilder) modelBuilder).beginBlock(block, renderType, blockEmission, localPosX, localPosY, localPosZ);
    }

    @Override
    public void overrideBlock(int block) {
        this.previousBlockId = this.blockId;
        this.blockId = block;
        ((net.irisshaders.iris.vertices.BlockSensitiveBufferBuilder) modelBuilder).overrideBlock(block);
    }

    @Override
    public void restoreBlock() {
        this.blockId = this.previousBlockId;
        ((net.irisshaders.iris.vertices.BlockSensitiveBufferBuilder) modelBuilder).restoreBlock();
    }

    @Override
    public void endBlock() {
        ((net.irisshaders.iris.vertices.BlockSensitiveBufferBuilder) modelBuilder).endBlock();
    }

    @Override
    public void ignoreMidBlock(boolean b) {
        this.ignoreMidBlock = b;
        ((net.irisshaders.iris.vertices.BlockSensitiveBufferBuilder) modelBuilder).ignoreMidBlock(b);
    }
}
