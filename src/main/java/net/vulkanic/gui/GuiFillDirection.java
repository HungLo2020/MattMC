package net.vulkanic.gui;

public enum GuiFillDirection {
	NONE("none"),
	HORIZONTAL_LEFT_TO_RIGHT("horizontal-left-to-right"),
	VERTICAL_BOTTOM_TO_TOP("vertical-bottom-to-top");

	private final String id;

	GuiFillDirection(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}
}
