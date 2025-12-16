package net.minecraft.client.renderer.sodium.render.chunk.translucent_sorting.data;

import org.joml.Vector3dc;
import org.joml.Vector3fc;

public interface CombinedCameraPos {
    Vector3fc getRelativeCameraPos();

    Vector3dc getAbsoluteCameraPos();
}
