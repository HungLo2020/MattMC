package net.minecraft.client.tacz;

import net.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;

final class TaczModifiedKeyMapping extends KeyMapping {
	private final InputConstants.Key modifier;

	TaczModifiedKeyMapping(String name, InputConstants.Type type, int key, InputConstants.Key modifier, Category category) {
		super(name, type, key, category);
		this.modifier = modifier;
	}

	@Override
	protected InputConstants.Key getKeyModifier() {
		return this.modifier;
	}

	@Override
	public Component getTranslatedKeyMessage() {
		return Component.empty().append(this.modifier.getDisplayName()).append(" + ").append(super.getTranslatedKeyMessage());
	}
}
