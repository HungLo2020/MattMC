package com.seibel.distanthorizons.common.wrappers.worldGeneration.mimicObject;

import net.minecraft.world.level.lighting.*;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;

import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;

public class DummyLightEngine extends LevelLightEngine
{
	
	public DummyLightEngine(LightGetterAdaptor genRegion)
	{
		super(genRegion, false, false);
	}
	
	
	@Override
	public int runLightUpdates() { return 0; }
	
	@Override
	public void setLightEnabled(ChunkPos $$0, boolean $$1) { }
	
	@Override
	public void propagateLightSources(ChunkPos arg) { }
	
	public boolean lightOnInSection(SectionPos $$0) { return false; }
	
	@Override
	public void queueSectionData(LightLayer lightLayer, SectionPos sectionPos, @Nullable DataLayer dataLayer) { }
	
	@Override
	public void checkBlock(BlockPos blockPos) { }
	
	@Override
	public boolean hasLightWork() { return false; }
	
	@Override
	public void updateSectionStatus(SectionPos sectionPos, boolean bl) { }
	
	@Override
	public LayerLightEventListener getLayerListener(LightLayer lightLayer) { return LayerLightEventListener.DummyLightLayerEventListener.INSTANCE; }
	
	@Override
	public int getRawBrightness(BlockPos blockPos, int i) { return 0; }
	
	public void lightChunk(ChunkAccess chunkAccess, boolean needLightBlockUpdate) { }
	
	@Override
	public String getDebugData(LightLayer lightLayer, SectionPos sectionPos) { throw new UnsupportedOperationException("This should never be used!"); }
	@Override
	public void retainData(ChunkPos chunkPos, boolean bl) { }
	
	@Override
	public int getLightSectionCount() { throw new UnsupportedOperationException("This should never be used!"); }
	@Override
	public int getMinLightSection() { throw new UnsupportedOperationException("This should never be used!"); }
	@Override
	public int getMaxLightSection() { throw new UnsupportedOperationException("This should never be used!"); }
	
}