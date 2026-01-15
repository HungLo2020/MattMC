package net.distanthorizons.common.wrappers.worldGeneration.mimicObject;


import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.world.level.ChunkPos;

import java.util.concurrent.CompletableFuture;

public class DhGenerationChunkHolder extends GenerationChunkHolder
{
	
	public DhGenerationChunkHolder(ChunkPos pos) { super(pos); }
	
	@Override 
	public int getTicketLevel() { return 0; }
	@Override 
	public int getQueueLevel() { return 0; }
	
	@Override
	protected void addSaveDependency(CompletableFuture<?> completableFuture) { }
	
}

