package net.sodium.client.render.chunk.data;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.sodium.api.texture.SpriteUtil;
import net.sodium.client.render.chunk.RenderSectionFlags;
import net.sodium.client.render.chunk.occlusion.OcclusionCuller;
import net.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.minecraft.client.renderer.chunk.VisibilitySet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.IntFunction;

/**
 * The render data for a chunk render container containing all the information about which meshes are attached, the
 * block entities contained by it, and any data used for occlusion testing.
 */
public class BuiltSectionInfo {
    public static final BuiltSectionInfo EMPTY = createEmptyData();

    public final int flags;
    public final long visibilityData;

    public final BlockEntity @Nullable[] globalBlockEntities;
    public final BlockEntity @Nullable[] culledBlockEntities;
    public final TextureAtlasSprite @Nullable[] animatedSprites;
    public final int nativeMeshingFallbackBlocks;
    public final int nativeMeshingFallbackQuads;
    public final int nativeMeshingFluidBlocks;
    public final long @Nullable[] nativeMeshingProfile;

    private BuiltSectionInfo(@NotNull Collection<TerrainRenderPass> blockRenderPasses,
                             @NotNull Collection<BlockEntity> globalBlockEntities,
                             @NotNull Collection<BlockEntity> culledBlockEntities,
                             @NotNull Collection<TextureAtlasSprite> animatedSprites,
                             @NotNull VisibilitySet occlusionData,
                             int nativeMeshingFallbackBlocks,
                             int nativeMeshingFallbackQuads,
                             int nativeMeshingFluidBlocks,
                             long @Nullable[] nativeMeshingProfile) {
        this.globalBlockEntities = toArray(globalBlockEntities, BlockEntity[]::new);
        this.culledBlockEntities = toArray(culledBlockEntities, BlockEntity[]::new);
        this.animatedSprites = toArray(animatedSprites, TextureAtlasSprite[]::new);
        this.nativeMeshingFallbackBlocks = nativeMeshingFallbackBlocks;
        this.nativeMeshingFallbackQuads = nativeMeshingFallbackQuads;
        this.nativeMeshingFluidBlocks = nativeMeshingFluidBlocks;
        this.nativeMeshingProfile = nativeMeshingProfile == null ? null : nativeMeshingProfile.clone();

        int flags = 0;

        if (!blockRenderPasses.isEmpty()) {
            flags |= 1 << RenderSectionFlags.HAS_BLOCK_GEOMETRY;
        }

        if (!culledBlockEntities.isEmpty()) {
            flags |= 1 << RenderSectionFlags.HAS_BLOCK_ENTITIES;
        }

        if (!animatedSprites.isEmpty()) {
            flags |= 1 << RenderSectionFlags.HAS_ANIMATED_SPRITES;
        }

        this.flags = flags;

        this.visibilityData = OcclusionCuller.encodeVisibility(occlusionData);
    }

    public static class Builder {
        private final List<TerrainRenderPass> blockRenderPasses = new ArrayList<>();
        private final List<BlockEntity> globalBlockEntities = new ArrayList<>();
        private final List<BlockEntity> culledBlockEntities = new ArrayList<>();
        private final Set<TextureAtlasSprite> animatedSprites = new ObjectOpenHashSet<>();

        private VisibilitySet occlusionData;
        private int nativeMeshingFallbackBlocks;
        private int nativeMeshingFallbackQuads;
        private int nativeMeshingFluidBlocks;
        private long @Nullable[] nativeMeshingProfile;

        public void addRenderPass(TerrainRenderPass pass) {
            this.blockRenderPasses.add(pass);
        }

        public void setOcclusionData(VisibilitySet data) {
            this.occlusionData = data;
        }

        /**
         * Adds a sprite to this data container for tracking. If the sprite is tickable, it will be ticked every frame
         * before rendering as necessary.
         * @param sprite The sprite
         */
        public void addSprite(@NotNull TextureAtlasSprite sprite) {
            if (SpriteUtil.INSTANCE.hasAnimation(sprite)) {
                this.animatedSprites.add(sprite);
            }
        }

        /**
         * Adds a block entity to the data container.
         * @param entity The block entity itself
         * @param cull True if the block entity can be culled to this chunk render's volume, otherwise false
         */
        public void addBlockEntity(BlockEntity entity, boolean cull) {
            (cull ? this.culledBlockEntities : this.globalBlockEntities).add(entity);
        }

        public void setNativeMeshingFallbackCounts(int blocks, int quads) {
            this.nativeMeshingFallbackBlocks = blocks;
            this.nativeMeshingFallbackQuads = quads;
        }

        public void setNativeMeshingFluidBlocks(int blocks) {
            this.nativeMeshingFluidBlocks = blocks;
        }

        public void setNativeMeshingProfile(long[] profile) {
            this.nativeMeshingProfile = profile == null ? null : profile.clone();
        }

        public BuiltSectionInfo build() {
            return new BuiltSectionInfo(this.blockRenderPasses, this.globalBlockEntities, this.culledBlockEntities,
                    this.animatedSprites, this.occlusionData, this.nativeMeshingFallbackBlocks,
                    this.nativeMeshingFallbackQuads, this.nativeMeshingFluidBlocks, this.nativeMeshingProfile);
        }
    }

    private static BuiltSectionInfo createEmptyData() {
        VisibilitySet occlusionData = new VisibilitySet();
        occlusionData.add(EnumSet.allOf(Direction.class));

        Builder meshInfo = new Builder();
        meshInfo.setOcclusionData(occlusionData);

        return meshInfo.build();
    }

    private static <T> T[] toArray(Collection<T> collection, IntFunction<T[]> allocator) {
        if (collection.isEmpty()) {
            return null;
        }

        return collection.toArray(allocator);
    }
}
