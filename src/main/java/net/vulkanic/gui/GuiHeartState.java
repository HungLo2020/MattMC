package net.vulkanic.gui;

public enum GuiHeartState {
	CONTAINER("container", 0.0F),
	HALF("half", 0.5F),
	FULL("full", 1.0F);

	private final String id;
	private final float progressValue;

	GuiHeartState(String id, float progressValue) {
		this.id = id;
		this.progressValue = progressValue;
	}

	public String id() {
		return this.id;
	}

	public float progressValue() {
		return this.progressValue;
	}
}
