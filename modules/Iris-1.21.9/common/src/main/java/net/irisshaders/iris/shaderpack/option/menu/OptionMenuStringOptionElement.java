package net.irisshaders.iris.shaderpack.option.menu;

import net.iris.shaderpack.option.StringOption;
import net.iris.shaderpack.option.values.OptionValues;
import net.iris.shaderpack.properties.ShaderProperties;

public class OptionMenuStringOptionElement extends OptionMenuOptionElement {
	public final StringOption option;

	public OptionMenuStringOptionElement(String elementString, OptionMenuContainer container, ShaderProperties shaderProperties, OptionValues values, StringOption option) {
		super(elementString, container, shaderProperties, values);
		this.option = option;
	}
}
