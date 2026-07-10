package net.sodium.client.render.chunk.lists;

import net.sodium.client.render.chunk.RenderSection;
import net.sodium.client.render.chunk.RenderSectionFlags;
import net.sodium.client.render.chunk.region.RenderRegion;
import net.sodium.client.util.iterator.ByteArrayIterator;
import net.sodium.client.util.iterator.ByteIterator;
import net.sodium.client.util.iterator.ReversibleByteArrayIterator;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class ChunkRenderList {
    private final RenderRegion region;

    private final byte[] sectionsWithGeometry = new byte[RenderRegion.REGION_SIZE];
    private final long[] sectionsWithGeometryMap = new long[RenderRegion.REGION_SIZE / Long.SIZE];
    private final long[] prevSectionsWithGeometryMap = new long[RenderRegion.REGION_SIZE / Long.SIZE];
    private int sectionsWithGeometryCount = 0;
    private int prevSectionsWithGeometryCount = 0;
    private int lastRelativeCameraSectionX;
    private int lastRelativeCameraSectionY;
    private int lastRelativeCameraSectionZ;
    private boolean addedSectionsAreSorted = false;

    private final byte[] sectionsWithSprites = new byte[RenderRegion.REGION_SIZE];
    private int sectionsWithSpritesCount = 0;

    private final byte[] sectionsWithEntities = new byte[RenderRegion.REGION_SIZE];
    private int sectionsWithEntitiesCount = 0;

    private int size;

    private int lastVisibleFrame;

    public ChunkRenderList(RenderRegion region) {
        this.region = region;
    }

    public void reset(int frame, boolean addedSectionsAreSorted) {
        this.prevSectionsWithGeometryCount = this.sectionsWithGeometryCount;
        Arrays.fill(this.sectionsWithGeometryMap, 0L);

        this.sectionsWithGeometryCount = 0;
        this.sectionsWithSpritesCount = 0;
        this.sectionsWithEntitiesCount = 0;

        this.size = 0;
        this.lastVisibleFrame = frame;
        this.addedSectionsAreSorted = addedSectionsAreSorted;
    }

    public void prepareForRender(SectionPos cameraPos) {
        int relativeCameraSectionX = this.getRelativeCameraSectionX(cameraPos);
        int relativeCameraSectionY = this.getRelativeCameraSectionY(cameraPos);
        int relativeCameraSectionZ = this.getRelativeCameraSectionZ(cameraPos);

        // invalidate batch cache if the render list changed
        if (this.needsRenderPreparation(relativeCameraSectionX, relativeCameraSectionY, relativeCameraSectionZ)) {
            this.commitRenderPreparation(relativeCameraSectionX, relativeCameraSectionY, relativeCameraSectionZ);

            if (!this.addedSectionsAreSorted) {
                this.sortSections(relativeCameraSectionX, relativeCameraSectionY, relativeCameraSectionZ);
            }
        }
    }

    private void sortSections(int relativeCameraSectionX, int relativeCameraSectionY, int relativeCameraSectionZ) {
        this.sectionsWithGeometryCount = NativeRenderListSorter.sortSections(this.sectionsWithGeometryMap,
                this.sectionsWithGeometry, relativeCameraSectionX, relativeCameraSectionY, relativeCameraSectionZ);
    }

    int getRelativeCameraSectionX(SectionPos cameraPos) {
        // The relative coordinates are clamped to one section larger than the region bounds to also capture cache invalidation that happens
        // when the camera moves from outside the region to inside the region (when seen on all axes independently).
        // This type of cache invalidation stems from different facings of sections being rendered if the camera is aligned with them on an axis.
        // For sorting only the position clamped to inside the region is used.
        return Mth.clamp(cameraPos.getX() - this.region.getChunkX(), -1, RenderRegion.REGION_WIDTH);
    }

    int getRelativeCameraSectionY(SectionPos cameraPos) {
        return Mth.clamp(cameraPos.getY() - this.region.getChunkY(), -1, RenderRegion.REGION_HEIGHT);
    }

    int getRelativeCameraSectionZ(SectionPos cameraPos) {
        return Mth.clamp(cameraPos.getZ() - this.region.getChunkZ(), -1, RenderRegion.REGION_LENGTH);
    }

    boolean needsRenderPreparation(int relativeCameraSectionX, int relativeCameraSectionY,
            int relativeCameraSectionZ) {
        return this.prevSectionsWithGeometryCount != this.sectionsWithGeometryCount ||
                relativeCameraSectionX != this.lastRelativeCameraSectionX ||
                relativeCameraSectionY != this.lastRelativeCameraSectionY ||
                relativeCameraSectionZ != this.lastRelativeCameraSectionZ ||
                !Arrays.equals(this.sectionsWithGeometryMap, this.prevSectionsWithGeometryMap);
    }

    boolean needsNativeSectionSort() {
        return !this.addedSectionsAreSorted;
    }

    long[] getSectionsWithGeometryMap() {
        return this.sectionsWithGeometryMap;
    }

    void commitRenderPreparation(int relativeCameraSectionX, int relativeCameraSectionY,
            int relativeCameraSectionZ) {
        this.region.clearAllCachedBatches();
        this.prevSectionsWithGeometryCount = this.sectionsWithGeometryCount;
        System.arraycopy(this.sectionsWithGeometryMap, 0, this.prevSectionsWithGeometryMap, 0,
                this.sectionsWithGeometryMap.length);
        this.lastRelativeCameraSectionX = relativeCameraSectionX;
        this.lastRelativeCameraSectionY = relativeCameraSectionY;
        this.lastRelativeCameraSectionZ = relativeCameraSectionZ;
    }

    void applyNativeSortedSections(ByteBuffer sortedSections, int offset, int count) {
        for (int index = 0; index < count; index++) {
            this.sectionsWithGeometry[index] = sortedSections.get(offset + index);
        }

        this.sectionsWithGeometryCount = count;
    }

    public void add(RenderSection render) {
        if (this.size >= RenderRegion.REGION_SIZE) {
            throw new ArrayIndexOutOfBoundsException("Render list is full");
        }

        this.size++;

        int index = render.getSectionIndex();
        int flags = render.getFlags();

        if (((flags >>> RenderSectionFlags.HAS_BLOCK_GEOMETRY) & 1) == 1) {
            this.sectionsWithGeometryMap[index >> 6] |= 1L << (index & 0b111111);
            if (this.addedSectionsAreSorted) {
                this.sectionsWithGeometry[this.sectionsWithGeometryCount] = (byte) index;
            }
            this.sectionsWithGeometryCount++;
        }

        this.sectionsWithSprites[this.sectionsWithSpritesCount] = (byte) index;
        this.sectionsWithSpritesCount += (flags >>> RenderSectionFlags.HAS_ANIMATED_SPRITES) & 1;

        this.sectionsWithEntities[this.sectionsWithEntitiesCount] = (byte) index;
        this.sectionsWithEntitiesCount += (flags >>> RenderSectionFlags.HAS_BLOCK_ENTITIES) & 1;
    }

    public @Nullable ByteIterator sectionsWithGeometryIterator(boolean reverse) {
        if (this.sectionsWithGeometryCount == 0) {
            return null;
        }

        return new ReversibleByteArrayIterator(this.sectionsWithGeometry, this.sectionsWithGeometryCount, reverse);
    }

    public @Nullable ByteIterator sectionsWithSpritesIterator() {
        if (this.sectionsWithSpritesCount == 0) {
            return null;
        }

        return new ByteArrayIterator(this.sectionsWithSprites, this.sectionsWithSpritesCount);
    }

    public @Nullable ByteIterator sectionsWithEntitiesIterator() {
        if (this.sectionsWithEntitiesCount == 0) {
            return null;
        }

        return new ByteArrayIterator(this.sectionsWithEntities, this.sectionsWithEntitiesCount);
    }

    public int getSectionsWithGeometryCount() {
        return this.sectionsWithGeometryCount;
    }

    public void copySectionsWithGeometry(ByteBuffer output) {
        if (output.capacity() < this.sectionsWithGeometryCount) {
            throw new IllegalArgumentException("Output buffer is too small for section render list");
        }

        for (int index = 0; index < this.sectionsWithGeometryCount; index++) {
            output.put(index, this.sectionsWithGeometry[index]);
        }
    }

    public int getSectionsWithSpritesCount() {
        return this.sectionsWithSpritesCount;
    }

    public int getSectionsWithEntitiesCount() {
        return this.sectionsWithEntitiesCount;
    }

    public int getLastVisibleFrame() {
        return this.lastVisibleFrame;
    }

    public RenderRegion getRegion() {
        return this.region;
    }

    public int size() {
        return this.size;
    }
}
