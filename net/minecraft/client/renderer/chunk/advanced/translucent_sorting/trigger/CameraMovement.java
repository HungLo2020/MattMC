package net.minecraft.client.renderer.chunk.advanced.translucent_sorting.trigger;

import org.joml.Vector3dc;

public record CameraMovement(Vector3dc start, Vector3dc end) {
    public boolean hasChanged() {
        return !this.start.equals(this.end);
    }
}
