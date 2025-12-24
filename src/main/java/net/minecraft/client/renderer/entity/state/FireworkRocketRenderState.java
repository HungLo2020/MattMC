package net.minecraft.client.renderer.entity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.item.ItemStackRenderState;

@Environment(EnvType.CLIENT)
public class FireworkRocketRenderState extends EntityRenderState {
	public boolean isShotAtAngle;
	public final ItemStackRenderState item = new ItemStackRenderState();
}
