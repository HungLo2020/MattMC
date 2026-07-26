package net.vulkanic.gui;

public enum ArmorIconState {
	EMPTY("empty"),
	HALF("half"),
	FULL("full");

	private final String id;

	ArmorIconState(String id) {
		this.id = id;
	}

	public String id() {
		return this.id;
	}
}
