package net.sodium.client.render.chunk.translucent_sorting.quad;

import net.sodium.client.model.quad.properties.ModelQuadFacing;
import net.sodium.client.render.chunk.vertex.format.NativeChunkMeshEncoder;

public class RegularTQuad extends TQuad {
    float[] vertexPositions;

    RegularTQuad(ModelQuadFacing facing, int packedNormal) {
        super(facing, packedNormal);
    }

    public static RegularTQuad fromNativeQuad(long nativeQuadAddress, ModelQuadFacing facing, int packedNormal) {
        float[] positions = new float[12];
        for (int vertexIndex = 0; vertexIndex < 4; vertexIndex++) {
            int positionIndex = vertexIndex * 3;
            positions[positionIndex] = NativeChunkMeshEncoder.nativeQuadX(nativeQuadAddress, vertexIndex);
            positions[positionIndex + 1] = NativeChunkMeshEncoder.nativeQuadY(nativeQuadAddress, vertexIndex);
            positions[positionIndex + 2] = NativeChunkMeshEncoder.nativeQuadZ(nativeQuadAddress, vertexIndex);
        }
        return fromPositions(positions, facing, packedNormal);
    }

    public static RegularTQuad fromPositions(float[] positions, ModelQuadFacing facing, int packedNormal) {
        if (positions.length != 12) {
            throw new IllegalArgumentException("Expected 12 position floats, got " + positions.length);
        }

        var quad = new RegularTQuad(facing, packedNormal);

        var sameVertexMap = quad.initExtentsAndCenter(positions);
        if (isInvalid(sameVertexMap)) {
            return null;
        }

        quad.initVertexPositions(positions, sameVertexMap);
        quad.initDotProduct();

        return quad;
    }

    void initVertexPositions(float[] positions, int sameVertexMap) {
        // check if we need to store vertex positions for this quad, only necessary if it's unaligned or rotated (yet aligned)
        var needsVertexPositions = (sameVertexMap != 0 || !this.facing.isAligned());
        if (!needsVertexPositions) {
            float posXExtent = this.extents[0];
            float posYExtent = this.extents[1];
            float posZExtent = this.extents[2];
            float negXExtent = this.extents[3];
            float negYExtent = this.extents[4];
            float negZExtent = this.extents[5];

            for (int i = 0; i < 4; i++) {
                int positionIndex = i * 3;
                float x = positions[positionIndex];
                float y = positions[positionIndex + 1];
                float z = positions[positionIndex + 2];
                if (x != posYExtent && x != negYExtent ||
                        y != posZExtent && y != negZExtent ||
                        z != posXExtent && z != negXExtent) {
                    needsVertexPositions = true;
                    break;
                }
            }
        }

        if (needsVertexPositions) {
            var vertexPositions = new float[12];
            this.vertexPositions = vertexPositions;
            System.arraycopy(positions, 0, vertexPositions, 0, vertexPositions.length);
        }
    }

    public float[] getVertexPositions() {
        // calculate vertex positions from extents if there's no cached value
        // (we don't want to be preemptively collecting vertex positions for all aligned quads)
        if (this.vertexPositions == null) {
            this.vertexPositions = new float[12];

            var facingAxis = this.facing.getAxis();
            var xRange = facingAxis == 0 ? 0 : 3;
            var yRange = facingAxis == 1 ? 0 : 3;
            var zRange = facingAxis == 2 ? 0 : 3;

            var itemIndex = 0;
            for (int x = 0; x <= xRange; x += 3) {
                for (int y = 0; y <= yRange; y += 3) {
                    for (int z = 0; z <= zRange; z += 3) {
                        this.vertexPositions[itemIndex++] = this.extents[x];
                        this.vertexPositions[itemIndex++] = this.extents[y + 1];
                        this.vertexPositions[itemIndex++] = this.extents[z + 2];
                    }
                }
            }
        }
        return this.vertexPositions;
    }
}
