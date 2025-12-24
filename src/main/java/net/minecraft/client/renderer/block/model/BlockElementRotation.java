package net.minecraft.client.renderer.block.model;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.core.Direction.Axis;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public record BlockElementRotation(Vector3f origin, Axis axis, float angle, boolean rescale) {
}
