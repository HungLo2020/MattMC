package frnsrc.sodium;

import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildContext;
import net.caffeinemc.mods.sodium.client.util.task.CancellationToken;

public interface ChunkJob extends CancellationToken {
    void execute(ChunkBuildContext context);

    boolean isStarted();
    
    boolean isBlocking();

    long getEstimatedSize();

    long getEstimatedDuration();
    
    long getEstimatedUploadDuration();
}
