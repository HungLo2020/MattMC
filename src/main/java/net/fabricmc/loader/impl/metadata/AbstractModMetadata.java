package net.fabricmc.loader.impl.metadata;

import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModMetadata;

public abstract class AbstractModMetadata implements ModMetadata {
	public static final String TYPE_BUILTIN = "builtin";
	public static final String TYPE_FABRIC_MOD = "fabric";

	@Override
	public boolean containsCustomElement(String key) {
		return containsCustomValue(key);
	}

	@Override
	public boolean containsCustomValue(String key) {
		return getCustomValues().containsKey(key);
	}

	@Override
	public CustomValue getCustomValue(String key) {
		return getCustomValues().get(key);
	}
}
