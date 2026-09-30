package net.sodium.client.render.chunk.region;

import net.sodium.client.render.chunk.RenderSection;
import net.sodium.client.render.chunk.lists.ChunkRenderList;
import net.sodium.client.render.chunk.vertex.format.ChunkMeshFormats;
import net.sodium.api.util.MathUtil;
import net.minecraft.core.SectionPos;
import org.apache.commons.lang3.Validate;
import java.util.Arrays;

/**
 * A fixed block of sections grouped for visibility and render-list
 * traversal. Rust VulkanicGAL owns all terrain geometry; a region holds no
 * GPU resources.
 */
public class RenderRegion {
    public static final int SECTION_VERTEX_COUNT_ESTIMATE = 756;
    public static final int SECTION_INDEX_COUNT_ESTIMATE = (SECTION_VERTEX_COUNT_ESTIMATE / 4) * 6;
    public static final int SECTION_BUFFER_ESTIMATE = SECTION_VERTEX_COUNT_ESTIMATE * ChunkMeshFormats.COMPACT.getVertexFormat().getStride() + SECTION_INDEX_COUNT_ESTIMATE * Integer.BYTES;

    public static final int REGION_WIDTH = 8;
    public static final int REGION_HEIGHT = 4;
    public static final int REGION_LENGTH = 8;

    public static final int REGION_WIDTH_M = RenderRegion.REGION_WIDTH - 1;
    public static final int REGION_HEIGHT_M = RenderRegion.REGION_HEIGHT - 1;
    public static final int REGION_LENGTH_M = RenderRegion.REGION_LENGTH - 1;

    public static final int REGION_WIDTH_SH = Integer.bitCount(REGION_WIDTH_M);
    public static final int REGION_HEIGHT_SH = Integer.bitCount(REGION_HEIGHT_M);
    public static final int REGION_LENGTH_SH = Integer.bitCount(REGION_LENGTH_M);

    public static final int REGION_SIZE = REGION_WIDTH * REGION_HEIGHT * REGION_LENGTH;

    static {
        Validate.isTrue(MathUtil.isPowerOfTwo(REGION_WIDTH));
        Validate.isTrue(MathUtil.isPowerOfTwo(REGION_HEIGHT));
        Validate.isTrue(MathUtil.isPowerOfTwo(REGION_LENGTH));
    }

    private final int x, y, z;

    private final ChunkRenderList renderList;

    private final RenderSection[] sections = new RenderSection[RenderRegion.REGION_SIZE];
    private int sectionCount;

    public RenderRegion(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;

        this.renderList = new ChunkRenderList(this);
    }

    public static long key(int x, int y, int z) {
        return SectionPos.asLong(x, y, z);
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getZ() {
        return this.z;
    }

    public int getChunkX() {
        return this.x << REGION_WIDTH_SH;
    }

    public int getChunkY() {
        return this.y << REGION_HEIGHT_SH;
    }

    public int getChunkZ() {
        return this.z << REGION_LENGTH_SH;
    }

    public int getOriginX() {
        return this.getChunkX() << 4;
    }

    public int getOriginY() {
        return this.getChunkY() << 4;
    }

    public int getOriginZ() {
        return this.getChunkZ() << 4;
    }

    public void delete() {
        Arrays.fill(this.sections, null);
    }

    public boolean isEmpty() {
        return this.sectionCount == 0;
    }

    public void removeSection(RenderSection section) {
        var sectionIndex = section.getSectionIndex();
        var prev = this.sections[sectionIndex];

        if (prev == null) {
            throw new IllegalStateException("Section was not loaded within the region");
        } else if (prev != section) {
            throw new IllegalStateException("Tried to remove the wrong section");
        }

        this.sections[sectionIndex] = null;
        this.sectionCount--;
    }

    public RenderSection getSection(int id) {
        return this.sections[id];
    }


    public ChunkRenderList getRenderList() {
        return this.renderList;
    }
}
