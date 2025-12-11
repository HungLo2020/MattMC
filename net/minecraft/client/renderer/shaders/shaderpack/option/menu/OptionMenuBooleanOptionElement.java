package net.minecraft.client.renderer.shaders.shaderpack.option.menu;

import net.minecraft.client.renderer.shaders.shaderpack.option.BooleanOption;
import net.minecraft.client.renderer.shaders.shaderpack.option.OptionValues;
import net.minecraft.client.renderer.shaders.shaderpack.option.OptionSet;

// Stub implementation for OptionMenuBooleanOptionElement
// Full implementation will be added when shader pack option system is implemented
public class OptionMenuBooleanOptionElement implements OptionMenuElement {
	public final BooleanOption option;
	private final OptionValues appliedValues;
	private final OptionValues pendingValues;

	public OptionMenuBooleanOptionElement(BooleanOption option, OptionValues appliedValues, OptionValues pendingValues) {
		this.option = option;
		this.appliedValues = appliedValues;
		this.pendingValues = pendingValues;
	}

	public OptionValues getAppliedOptionValues() {
		return this.appliedValues;
	}

	public OptionValues getPendingOptionValues() {
		return this.pendingValues;
	}
}
