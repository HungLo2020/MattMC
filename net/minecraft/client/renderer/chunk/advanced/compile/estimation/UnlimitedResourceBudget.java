package net.minecraft.client.renderer.chunk.advanced.compile.estimation;

public class UnlimitedResourceBudget implements UploadResourceBudget {
    public static final UnlimitedResourceBudget INSTANCE = new UnlimitedResourceBudget();

    @Override
    public boolean isAvailable() {
        return true; // always available
    }

    @Override
    public void consume(long duration, long size) {
        // no-op, unlimited budget means no consumption
    }
}
