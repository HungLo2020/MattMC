package net.minecraft.client.renderer.entity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.item.ItemStackRenderState;

@Environment(EnvType.CLIENT)
public class ItemDisplayEntityRenderState extends DisplayEntityRenderState {
	public final ItemStackRenderState item = new ItemStackRenderState();

	@Override
	public boolean hasSubState() {
		return !this.item.isEmpty();
	}
}
