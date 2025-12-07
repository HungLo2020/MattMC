package net.minecraft.client.renderer.item;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public class TrackingItemStackRenderState extends ItemStackRenderState {
	private final List<Object> modelIdentityElements = new ArrayList();

	@Override
	public void appendModelIdentityElement(Object object) {
		this.modelIdentityElements.add(object);
	}

	public Object getModelIdentity() {
		return this.modelIdentityElements;
	}
}
