package net.minecraft.client.renderer.entity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public class WitchRenderState extends HoldingEntityRenderState {
	public int entityId;
	public boolean isHoldingItem;
	public boolean isHoldingPotion;
}
