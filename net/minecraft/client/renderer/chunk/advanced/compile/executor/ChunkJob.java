package net.minecraft.client.renderer.chunk.advanced.compile.executor;

import net.minecraft.client.renderer.chunk.advanced.compile.ChunkBuildContext;
import net.minecraft.client.renderer.sodium.util.task.CancellationToken;

public interface ChunkJob extends CancellationToken {
    void execute(ChunkBuildContext context);

    boolean isStarted();
    
    boolean isBlocking();

    long getEstimatedSize();

    long getEstimatedDuration();
    
    long getEstimatedUploadDuration();
}
