package net.vulkanic.gui;

public enum AirBubbleState {
	FULL("full", 1.0F),
	PARTIAL("partial", 0.5F),
	EMPTY("empty", 0.0F);

	private final String id;
	private final float progressValue;

	AirBubbleState(String id, float progressValue) {
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
