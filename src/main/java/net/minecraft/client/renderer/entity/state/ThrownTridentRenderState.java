package net.minecraft.client.renderer.entity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public class ThrownTridentRenderState extends EntityRenderState {
	public float xRot;
	public float yRot;
	public boolean isFoil;
}
