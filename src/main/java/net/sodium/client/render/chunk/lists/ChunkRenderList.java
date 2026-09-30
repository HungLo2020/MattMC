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
import java.util.Locale;
import java.util.zip.CRC32;

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

    private static void updateCrcInt(CRC32 crc, int value) {
        crc.update(value & 0xFF);
        crc.update((value >>> 8) & 0xFF);
        crc.update((value >>> 16) & 0xFF);
        crc.update((value >>> 24) & 0xFF);
    }

    private static void updateCrcLong(CRC32 crc, long value) {
        updateCrcInt(crc, (int) value);
        updateCrcInt(crc, (int) (value >>> 32));
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

    public int getSectionsWithGeometryCount() {
        return this.sectionsWithGeometryCount;
    }
}
