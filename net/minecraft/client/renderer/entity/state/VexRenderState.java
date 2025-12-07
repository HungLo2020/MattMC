package net.minecraft.client.renderer.entity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public class VexRenderState extends ArmedEntityRenderState {
	public boolean isCharging;
}
