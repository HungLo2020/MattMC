package net.minecraft.world.level.levelgen.feature;

/** Unused world services fail explicitly. Keeps reflection out of placement timings. */
abstract class UnsupportedOreWorld implements net.minecraft.world.level.WorldGenLevel {
    @Override public boolean isClientSide() { throw new UnsupportedOperationException("isClientSide"); }
    @Override public boolean isFluidAtPosition(net.minecraft.core.BlockPos p0, java.util.function.Predicate p1) { throw new UnsupportedOperationException("isFluidAtPosition"); }
    @Override public boolean isStateAtPosition(net.minecraft.core.BlockPos p0, java.util.function.Predicate p1) { throw new UnsupportedOperationException("isStateAtPosition"); }
    @Override public boolean destroyBlock(net.minecraft.core.BlockPos p0, boolean p1, net.minecraft.world.entity.Entity p2, int p3) { throw new UnsupportedOperationException("destroyBlock"); }
    @Override public boolean removeBlock(net.minecraft.core.BlockPos p0, boolean p1) { throw new UnsupportedOperationException("removeBlock"); }
    @Override public boolean setBlock(net.minecraft.core.BlockPos p0, net.minecraft.world.level.block.state.BlockState p1, int p2, int p3) { throw new UnsupportedOperationException("setBlock"); }
    @Override public float getShade(net.minecraft.core.Direction p0, boolean p1) { throw new UnsupportedOperationException("getShade"); }
    @Override public int getHeight(net.minecraft.world.level.levelgen.Heightmap.Types p0, int p1, int p2) { throw new UnsupportedOperationException("getHeight"); }
    @Override public int getSeaLevel() { throw new UnsupportedOperationException("getSeaLevel"); }
    @Override public int getSkyDarken() { throw new UnsupportedOperationException("getSkyDarken"); }
    @Override public java.util.List getEntities(net.minecraft.world.entity.Entity p0, net.minecraft.world.phys.AABB p1, java.util.function.Predicate p2) { throw new UnsupportedOperationException("getEntities"); }
    @Override public java.util.List getEntities(net.minecraft.world.level.entity.EntityTypeTest p0, net.minecraft.world.phys.AABB p1, java.util.function.Predicate p2) { throw new UnsupportedOperationException("getEntities"); }
    @Override public java.util.List players() { throw new UnsupportedOperationException("players"); }
    @Override public long nextSubTickCount() { throw new UnsupportedOperationException("nextSubTickCount"); }
    @Override public long getSeed() { throw new UnsupportedOperationException("getSeed"); }
    @Override public net.minecraft.core.Holder getUncachedNoiseBiome(int p0, int p1, int p2) { throw new UnsupportedOperationException("getUncachedNoiseBiome"); }
    @Override public net.minecraft.core.RegistryAccess registryAccess() { throw new UnsupportedOperationException("registryAccess"); }
    @Override public net.minecraft.server.MinecraftServer getServer() { throw new UnsupportedOperationException("getServer"); }
    @Override public net.minecraft.server.level.ServerLevel getLevel() { throw new UnsupportedOperationException("getLevel"); }
    @Override public net.minecraft.util.RandomSource getRandom() { throw new UnsupportedOperationException("getRandom"); }
    @Override public net.minecraft.world.DifficultyInstance getCurrentDifficultyAt(net.minecraft.core.BlockPos p0) { throw new UnsupportedOperationException("getCurrentDifficultyAt"); }
    @Override public net.minecraft.world.flag.FeatureFlagSet enabledFeatures() { throw new UnsupportedOperationException("enabledFeatures"); }
    @Override public net.minecraft.world.level.biome.BiomeManager getBiomeManager() { throw new UnsupportedOperationException("getBiomeManager"); }
    @Override public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(net.minecraft.core.BlockPos p0) { throw new UnsupportedOperationException("getBlockEntity"); }
    @Override public net.minecraft.world.level.block.state.BlockState getBlockState(net.minecraft.core.BlockPos p0) { throw new UnsupportedOperationException("getBlockState"); }
    @Override public net.minecraft.world.level.border.WorldBorder getWorldBorder() { throw new UnsupportedOperationException("getWorldBorder"); }
    @Override public net.minecraft.world.level.chunk.ChunkAccess getChunk(int p0, int p1, net.minecraft.world.level.chunk.status.ChunkStatus p2, boolean p3) { throw new UnsupportedOperationException("getChunk"); }
    @Override public net.minecraft.world.level.chunk.ChunkSource getChunkSource() { throw new UnsupportedOperationException("getChunkSource"); }
    @Override public net.minecraft.world.level.dimension.DimensionType dimensionType() { throw new UnsupportedOperationException("dimensionType"); }
    @Override public net.minecraft.world.level.lighting.LevelLightEngine getLightEngine() { throw new UnsupportedOperationException("getLightEngine"); }
    @Override public net.minecraft.world.level.material.FluidState getFluidState(net.minecraft.core.BlockPos p0) { throw new UnsupportedOperationException("getFluidState"); }
    @Override public net.minecraft.world.level.storage.LevelData getLevelData() { throw new UnsupportedOperationException("getLevelData"); }
    @Override public net.minecraft.world.ticks.LevelTickAccess getBlockTicks() { throw new UnsupportedOperationException("getBlockTicks"); }
    @Override public net.minecraft.world.ticks.LevelTickAccess getFluidTicks() { throw new UnsupportedOperationException("getFluidTicks"); }
    @Override public void addParticle(net.minecraft.core.particles.ParticleOptions p0, double p1, double p2, double p3, double p4, double p5, double p6) { throw new UnsupportedOperationException("addParticle"); }
    @Override public void gameEvent(net.minecraft.core.Holder p0, net.minecraft.world.phys.Vec3 p1, net.minecraft.world.level.gameevent.GameEvent.Context p2) { throw new UnsupportedOperationException("gameEvent"); }
    @Override public void levelEvent(net.minecraft.world.entity.Entity p0, int p1, net.minecraft.core.BlockPos p2, int p3) { throw new UnsupportedOperationException("levelEvent"); }
    @Override public void playSound(net.minecraft.world.entity.Entity p0, net.minecraft.core.BlockPos p1, net.minecraft.sounds.SoundEvent p2, net.minecraft.sounds.SoundSource p3, float p4, float p5) { throw new UnsupportedOperationException("playSound"); }
}
