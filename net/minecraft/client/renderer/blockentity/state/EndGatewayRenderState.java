package net.minecraft.client.renderer.blockentity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public class EndGatewayRenderState extends EndPortalRenderState {
	public int height;
	public float scale;
	public int color;
	public float animationTime;
}
