package net.sodium.client.render.chunk;

import net.sodium.client.render.chunk.compile.estimation.MeshResultSize;
import net.sodium.client.render.chunk.compile.executor.ChunkJob;
import net.sodium.client.render.chunk.data.BuiltSectionInfo;
import net.sodium.client.render.chunk.occlusion.GraphDirection;
import net.sodium.client.render.chunk.occlusion.GraphDirectionSet;
import net.sodium.client.render.chunk.occlusion.OcclusionCuller;
import net.sodium.client.render.chunk.region.RenderRegion;
import net.sodium.client.render.chunk.translucent_sorting.data.TranslucentData;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The render state object for a chunk section. This contains all the graphics state for each render pass along with
 * data about the render in the chunk visibility graph.
 */
public class RenderSection {
    // Render Region State
    private final RenderRegion region;
    private final int sectionIndex;

    // Chunk Section State
    private final int chunkX, chunkY, chunkZ;

    // Occlusion Culling State
    private long visibilityData = OcclusionCuller.NULL_VISIBILITY;

    private int incomingDirections;
    private int lastVisibleFrame = -1;
    // Iris: Shadow rendering tracking

    private int adjacentMask;
    public RenderSection
            adjacentDown,
            adjacentUp,
            adjacentNorth,
            adjacentSouth,
            adjacentWest,
            adjacentEast;


    // Rendering State
    private boolean built = false; // merge with the flags?
    private int flags = RenderSectionFlags.NONE;
    private BlockEntity @Nullable[] globalBlockEntities;
    private BlockEntity @Nullable[] culledBlockEntities;
    private TextureAtlasSprite @Nullable[] animatedSprites;
    @Nullable
    private TranslucentData translucentData;

    // Pending Update State
    @Nullable
    private ChunkJob runningJob = null;
    private long lastMeshResultSize = MeshResultSize.NO_DATA;

    private int pendingUpdateType;
    private long pendingUpdateSince;

    private int lastUploadFrame = -1;
    private int lastSubmittedFrame = -1;

    // Lifetime state
    private boolean disposed;

    public RenderSection(RenderRegion region, int chunkX, int chunkY, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkY = chunkY;
        this.chunkZ = chunkZ;

        int rX = this.getChunkX() & RenderRegion.REGION_WIDTH_M;
        int rY = this.getChunkY() & RenderRegion.REGION_HEIGHT_M;
        int rZ = this.getChunkZ() & RenderRegion.REGION_LENGTH_M;

        this.sectionIndex = LocalSectionIndex.pack(rX, rY, rZ);

        this.region = region;
    }

    public int getAdjacentMask() {
        return this.adjacentMask;
    }

    public TranslucentData getTranslucentData() {
        return this.translucentData;
    }

    /**
     * Deletes all data attached to this render and drops any pending tasks. This should be used when the render falls
     * out of view or otherwise needs to be destroyed. After the render has been destroyed, the object can no longer
     * be used.
     */
    public void delete() {
        if (this.runningJob != null) {
            this.runningJob.setCancelled();
            this.runningJob = null;
        }

        this.clearRenderState();
        this.disposed = true;
    }

    public boolean setInfo(@Nullable BuiltSectionInfo info) {
        if (info != null) {
            return this.setRenderState(info);
        } else {
            return this.clearRenderState();
        }
    }

    private boolean setRenderState(@NotNull BuiltSectionInfo info) {
        var prevBuilt = this.built;
        var prevFlags = this.flags;
        var prevVisibilityData = this.visibilityData;

        this.built = true;
        this.flags = info.flags;
        this.visibilityData = info.visibilityData;

        this.globalBlockEntities = info.globalBlockEntities;
        this.culledBlockEntities = info.culledBlockEntities;
        this.animatedSprites = info.animatedSprites;

        // the section is marked as having received graph-relevant changes if it's build state, flags, or connectedness has changed.
        // the entities and sprites don't need to be checked since whether they exist is encoded in the flags.
        return !prevBuilt || prevFlags != this.flags || prevVisibilityData != this.visibilityData;
    }

    private boolean clearRenderState() {
        var wasBuilt = this.built;

        this.built = false;
        this.flags = RenderSectionFlags.NONE;
        this.visibilityData = OcclusionCuller.NULL_VISIBILITY;
        this.globalBlockEntities = null;
        this.culledBlockEntities = null;
        this.animatedSprites = null;

        // changes to data if it moves from built to not built don't matter, so only build state changes matter
        return wasBuilt;
    }

    public long getLastMeshResultSize() {
        return this.lastMeshResultSize;
    }

    /**
     * Returns the chunk section position which this render refers to in the level.
     */
    public SectionPos getPosition() {
        return SectionPos.of(this.chunkX, this.chunkY, this.chunkZ);
    }

    /**
     * Returns the packed section position without allocating a temporary
     * {@link SectionPos}. Use this for per-frame identity lookups.
     */
    public long getPositionAsLong() {
        return SectionPos.asLong(this.chunkX, this.chunkY, this.chunkZ);
    }

    /**
     * @return The x-coordinate of the origin position of this chunk render
     */
    public int getOriginX() {
        return this.chunkX << 4;
    }

    /**
     * @return The y-coordinate of the origin position of this chunk render
     */
    public int getOriginY() {
        return this.chunkY << 4;
    }

    /**
     * @return The z-coordinate of the origin position of this chunk render
     */
    public int getOriginZ() {
        return this.chunkZ << 4;
    }

    /**
     * @return The squared distance from the center of this chunk in the level to the center of the block position
     * given by {@param pos}
     */
    public float getSquaredDistance(BlockPos pos) {
        return this.getSquaredDistance(pos.getX() + 0.5f, pos.getY() + 0.5f, pos.getZ() + 0.5f);
    }

    /**
     * @return The squared distance from the center of this chunk to the given block position
     */
    public float getSquaredDistance(float x, float y, float z) {
        float xDist = x - this.getCenterX();
        float yDist = y - this.getCenterY();
        float zDist = z - this.getCenterZ();

        return (xDist * xDist) + (yDist * yDist) + (zDist * zDist);
    }

    /**
     * @return The x-coordinate of the center position of this chunk render
     */
    public int getCenterX() {
        return this.getOriginX() + 8;
    }

    /**
     * @return The y-coordinate of the center position of this chunk render
     */
    public int getCenterY() {
        return this.getOriginY() + 8;
    }

    /**
     * @return The z-coordinate of the center position of this chunk render
     */
    public int getCenterZ() {
        return this.getOriginZ() + 8;
    }

    public int getChunkX() {
        return this.chunkX;
    }

    public int getChunkY() {
        return this.chunkY;
    }

    public int getChunkZ() {
        return this.chunkZ;
    }

    @Override
    public String toString() {
        return String.format("RenderSection at chunk (%d, %d, %d) from (%d, %d, %d) to (%d, %d, %d)",
                this.chunkX, this.chunkY, this.chunkZ,
                this.getOriginX(), this.getOriginY(), this.getOriginZ(),
                this.getOriginX() + 15, this.getOriginY() + 15, this.getOriginZ() + 15);
    }

    public boolean isBuilt() {
        return this.built;
    }

    public int getSectionIndex() {
        return this.sectionIndex;
    }

    public RenderRegion getRegion() {
        return this.region;
    }

    public void setLastVisibleFrame(int frame) {
        this.lastVisibleFrame = frame;
    }

    public int getLastVisibleFrame() {
        return this.lastVisibleFrame;
    }

    public int getIncomingDirections() {
        return this.incomingDirections;
    }

    public void addIncomingDirections(int directions) {
        this.incomingDirections |= directions;
    }

    public void setIncomingDirections(int directions) {
        this.incomingDirections = directions;
    }

    /**
     * Returns a bitfield containing the {@link RenderSectionFlags} for this built section.
     */
    public int getFlags() {
        return this.flags;
    }

    /**
     * Returns the occlusion culling data which determines this chunk's connectedness on the visibility graph.
     */
    public long getVisibilityData() {
        return this.visibilityData;
    }

    /**
     * Returns the collection of animated sprites contained by this rendered chunk section.
     */
    public TextureAtlasSprite @Nullable[] getAnimatedSprites() {
        return this.animatedSprites;
    }

    public @Nullable ChunkJob getRunningJob() {
        return this.runningJob;
    }

    public void prepareTrigger(boolean isDirectTrigger) {
        if (this.translucentData != null) {
            this.translucentData.prepareTrigger(isDirectTrigger);
        }
    }

}
