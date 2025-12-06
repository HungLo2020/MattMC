package net.minecraft.client.renderer.entity.state;

import com.google.common.collect.ImmutableList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public record HitboxesRenderState(double viewX, double viewY, double viewZ, ImmutableList<HitboxRenderState> hitboxes) {
}
