package net.minecraft.client.renderer.item;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public class TrackingItemStackRenderState extends ItemStackRenderState {
	private final List<Object> modelIdentityElements = new ArrayList();
	private Object cachedModelIdentity;

	@Override
	public void clear() {
		super.clear();
		this.modelIdentityElements.clear();
		this.cachedModelIdentity = null;
	}

	@Override
	public void appendModelIdentityElement(Object object) {
		this.modelIdentityElements.add(object);
		this.cachedModelIdentity = null;
	}

	public Object getModelIdentity() {
		if (this.cachedModelIdentity == null) {
			this.cachedModelIdentity = List.copyOf(this.modelIdentityElements);
		}
		return this.cachedModelIdentity;
	}
}
