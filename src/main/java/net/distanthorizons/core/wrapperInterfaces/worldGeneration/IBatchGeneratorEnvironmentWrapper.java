package net.distanthorizons.core.wrapperInterfaces.worldGeneration;

import net.distanthorizons.api.enums.worldGeneration.EDhApiDistantGeneratorMode;
import net.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import net.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public interface IBatchGeneratorEnvironmentWrapper extends AutoCloseable
{
	void updateAllFutures();
	
	CompletableFuture<Void> queueGenEvent(
			int minX, int minZ, int genSize, 
			EDhApiDistantGeneratorMode generatorMode, EDhApiWorldGenerationStep targetStep,
			ExecutorService worldGeneratorThreadPool, Consumer<IChunkWrapper> resultConsumer);
	
	void close();
	
}
