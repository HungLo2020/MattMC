package net.vulkanic.gui;

public enum HungerIconVariant {
	NORMAL("normal"),
	HUNGER_EFFECT("hunger-effect");

	private final String id;

	HungerIconVariant(String id) {
		this.id = id;
	}

	public String id() {
		return this.id;
	}
}
