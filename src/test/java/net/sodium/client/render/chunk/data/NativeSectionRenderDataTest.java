package net.sodium.client.render.chunk.data;

import net.minecraft.client.renderer.chunk.VisibilitySet;
import net.minecraft.core.Direction;
import net.sodium.client.gl.device.MultiDrawBatch;
import net.sodium.client.model.quad.properties.ModelQuadFacing;
import net.sodium.client.render.chunk.RenderSection;
import net.sodium.client.render.chunk.buffer.ChunkBufferAllocation;
import net.sodium.client.render.chunk.lists.ChunkRenderList;
import net.sodium.client.render.chunk.region.RenderRegion;
import net.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.sodium.client.render.viewport.CameraTransform;
import org.lwjgl.system.MemoryUtil;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeSectionRenderDataTest {
    @Test
    void sectionRenderDataUnsafeClassWasRemovedFromJava() {
        assertFalse(Files.exists(Path.of(
                "src/main/java/net/sodium/client/render/chunk/data/SectionRenderDataUnsafe.java")));
    }

    @Test
    void visibleFaceMaskUsesRustImplementation() {
        int mask = SectionRenderDataStorage.getVisibleFaces(0, 0, 0, 1, 1, 1);

        assertEquals(
                (1 << ModelQuadFacing.UNASSIGNED.ordinal())
                        | (1 << ModelQuadFacing.NEG_X.ordinal())
                        | (1 << ModelQuadFacing.NEG_Y.ordinal())
                        | (1 << ModelQuadFacing.NEG_Z.ordinal()),
                mask);
    }

    @Test
    void defaultChunkRendererUsesNativeDrawCommandAssembly() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/net/sodium/client/render/chunk/DefaultChunkRenderer.java"));

        assertTrue(source.contains("renderDataStorage.fillDrawCommandBuffer("));
        assertFalse(source.contains("SectionRenderDataUnsafe"));
        assertFalse(source.contains("addLocalIndexedDrawCommands"));
        assertFalse(source.contains("addSharedIndexedDrawCommands"));
    }

    @Test
    void nativeDrawCommandAssemblyFillsSharedIndexBatchFromJava() {
        RenderFixture fixture = RenderFixture.create(false);

        try {
            fixture.storage.setVertexData(0, new FakeAllocation(12, 32), vertexSegments());
            fixture.storage.fillDrawCommandBuffer(fixture.batch, fixture.region, fixture.renderList,
                    new CameraTransform(0.0, 0.0, 0.0), false, false, false);

            assertEquals(1, fixture.batch.size);
            assertEquals(30, MemoryUtil.memGetInt(fixture.batch.pElementCount));
            assertEquals(12, MemoryUtil.memGetInt(fixture.batch.pBaseVertex));
            assertEquals(0, MemoryUtil.memGetAddress(fixture.batch.pElementPointer));
        } finally {
            fixture.close();
        }
    }

    @Test
    void nativeDrawCommandAssemblyFillsLocalIndexBatchFromJava() {
        RenderFixture fixture = RenderFixture.create(true);

        try {
            fixture.storage.setVertexData(0, new FakeAllocation(12, 32), vertexSegments());
            fixture.storage.setIndexData(0, new FakeAllocation(20, 32));
            fixture.storage.fillDrawCommandBuffer(fixture.batch, fixture.region, fixture.renderList,
                    new CameraTransform(0.0, 0.0, 0.0), false, false, true);

            assertEquals(4, fixture.batch.size);
            assertEquals(6, MemoryUtil.memGetInt(fixture.batch.pElementCount));
            assertEquals(12, MemoryUtil.memGetInt(fixture.batch.pBaseVertex));
            assertEquals(80, MemoryUtil.memGetAddress(fixture.batch.pElementPointer));
        } finally {
            fixture.close();
        }
    }

    private static int[] vertexSegments() {
        return new int[] {
                4, ModelQuadFacing.POS_X.ordinal(),
                8, ModelQuadFacing.POS_Y.ordinal(),
                0, ModelQuadFacing.POS_Z.ordinal(),
                4, ModelQuadFacing.NEG_X.ordinal(),
                0, ModelQuadFacing.NEG_Y.ordinal(),
                0, ModelQuadFacing.NEG_Z.ordinal(),
                4, ModelQuadFacing.UNASSIGNED.ordinal()
        };
    }

    private record RenderFixture(RenderRegion region, ChunkRenderList renderList, SectionRenderDataStorage storage,
            MultiDrawBatch batch) implements AutoCloseable {
        static RenderFixture create(boolean storesIndices) {
            RenderRegion region = new RenderRegion(0, 0, 0, null);
            ChunkRenderList renderList = region.getRenderList();
            RenderSection section = new RenderSection(region, 0, 0, 0);
            BuiltSectionInfo.Builder builder = new BuiltSectionInfo.Builder();
            VisibilitySet visibility = new VisibilitySet();
            visibility.add(EnumSet.allOf(Direction.class));
            builder.setOcclusionData(visibility);
            builder.addRenderPass(DefaultTerrainRenderPasses.SOLID);
            section.setInfo(builder.build());
            renderList.reset(1, true);
            renderList.add(section);

            return new RenderFixture(region, renderList, new SectionRenderDataStorage(storesIndices),
                    new MultiDrawBatch(16));
        }

        @Override
        public void close() {
            this.batch.delete();
            this.storage.delete();
        }
    }

    private record FakeAllocation(long offset, long length) implements ChunkBufferAllocation {
        @Override
        public long getOffset() {
            return this.offset;
        }

        @Override
        public long getLength() {
            return this.length;
        }

        @Override
        public void delete() {
        }
    }
}
