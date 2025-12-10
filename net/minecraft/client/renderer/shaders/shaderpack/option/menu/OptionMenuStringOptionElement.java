package net.minecraft.client.renderer.shaders.shaderpack.option.menu;

import net.minecraft.client.renderer.shaders.shaderpack.option.StringOption;
import net.minecraft.client.renderer.shaders.shaderpack.option.OptionValues;

// Stub implementation for OptionMenuStringOptionElement
// Full implementation will be added when shader pack option system is implemented
public class OptionMenuStringOptionElement implements OptionMenuElement {
	public final StringOption option;
	private final OptionValues appliedValues;
	private final OptionValues pendingValues;

	public OptionMenuStringOptionElement(StringOption option, OptionValues appliedValues, OptionValues pendingValues) {
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
