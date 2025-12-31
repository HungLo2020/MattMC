package net.caffeinemc.mods.sodium.client.render.chunk.vertex.format;

public interface ChunkVertexEncoder {
    long write(long ptr, int materialBits, Vertex[] vertices, int sectionIndex);

    class Vertex implements net.irisshaders.iris.vertices.sodium.terrain.ChunkVertexExtension {
        public float x;
        public float y;
        public float z;
        public int color;
        public float ao;
        public float u;
        public float v;
        public int light;
        
        // Iris: From MixinChunkVertex - extension fields
        private byte blockEmission;
        private int blockId;
        private byte renderType;
        private int localPosX, localPosY, localPosZ;
        private boolean ignoresMidBlock = false;

        public static Vertex[] uninitializedQuad() {
            Vertex[] vertices = new Vertex[4];

            for (int i = 0; i < 4; i++) {
                vertices[i] = new Vertex();
            }

            return vertices;
        }

        public static void copyVertexTo(Vertex from, Vertex to) {
            to.x = from.x;
            to.y = from.y;
            to.z = from.z;
            to.color = from.color;
            to.ao = from.ao;
            to.u = from.u;
            to.v = from.v;
            to.light = from.light;
            
            // Iris: From MixinChunkVertex - copy extension data
            ((net.irisshaders.iris.vertices.sodium.terrain.ChunkVertexExtension) from).iris$copyData((net.irisshaders.iris.vertices.sodium.terrain.ChunkVertexExtension) to);
        }

        public static void writeVertex(Vertex targetA, float newX, float newY, float newZ, int newColor, float newAo, float newU, float newV, int newLight) {
            targetA.x = newX;
            targetA.y = newY;
            targetA.z = newZ;
            targetA.color = newColor;
            targetA.ao = newAo;
            targetA.u = newU;
            targetA.v = newV;
            targetA.light = newLight;
        }
        
        // Iris: From MixinChunkVertex - ChunkVertexExtension implementation
        @Override
        public void iris$setData(byte blockEmission, byte renderType, int blockId, int localX, int localY, int localZ) {
            this.blockEmission = blockEmission;
            this.renderType = renderType;
            this.blockId = blockId;
            this.localPosX = localX;
            this.localPosY = localY;
            this.localPosZ = localZ;
        }

        @Override
        public void iris$ignoresMidBlock(boolean setIgnore) {
            this.ignoresMidBlock = setIgnore;
        }

        @Override
        public void iris$copyData(net.irisshaders.iris.vertices.sodium.terrain.ChunkVertexExtension dest) {
            dest.iris$setData(blockEmission, renderType, blockId, localPosX, localPosY, localPosZ);
        }

        @Override
        public int getLocalPosX() {
            return localPosX;
        }

        @Override
        public int getLocalPosY() {
            return localPosY;
        }

        @Override
        public int getLocalPosZ() {
            return localPosZ;
        }

        @Override
        public int getBlockId() {
            return blockId;
        }

        @Override
        public byte getRenderType() {
            return renderType;
        }

        @Override
        public byte getBlockEmission() {
            return blockEmission;
        }

        @Override
        public boolean ignoreMidBlock() {
            return ignoresMidBlock;
        }
    }
}
