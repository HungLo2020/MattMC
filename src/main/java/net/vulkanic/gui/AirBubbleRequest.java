package net.vulkanic.gui;

public record AirBubbleRequest(
	AirBubbleState state,
	boolean popping,
	boolean visible,
	int order,
	int x,
	int y
) {
	public AirBubbleRequest {
		if (state == null) {
			throw new IllegalArgumentException("air bubble state must be provided");
		}
		if (popping && state != AirBubbleState.PARTIAL) {
			throw new IllegalArgumentException("only partial air bubbles may be marked popping");
		}
		if (order < 0) {
			throw new IllegalArgumentException("air bubble order must be non-negative: " + order);
		}
	}
}
