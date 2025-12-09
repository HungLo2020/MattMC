package net.minecraft.client.renderer.shaders.option;

/**
 * IRIS 1.21.9 verbatim BooleanOption
 */
public final class BooleanOption extends BaseOption {
private final boolean defaultValue;

public BooleanOption(OptionType type, String name, String comment, boolean defaultValue) {
super(type, name, comment);
this.defaultValue = defaultValue;
}

public boolean getDefaultValue() {
return defaultValue;
}

// For compatibility with Merged classes
public BooleanOption getOption() {
return this;
}

@Override
public String toString() {
return "BooleanOption{name='" + getName() + "', default=" + defaultValue + "}";
}
}
