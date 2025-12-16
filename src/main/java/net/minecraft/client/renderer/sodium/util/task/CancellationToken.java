package net.minecraft.client.renderer.sodium.util.task;

public interface CancellationToken {
    boolean isCancelled();

    void setCancelled();
}
