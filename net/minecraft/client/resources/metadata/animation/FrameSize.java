package net.minecraft.client.resources.metadata.animation;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public record FrameSize(int width, int height) {
}
