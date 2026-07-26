package net.vulkanic.gui;

public record HungerIconRequest(
	HungerIconVariant variant,
	HungerIconState state,
	boolean flashing,
	int jitterOffset,
	int order,
	int x,
	int y
) {
	public HungerIconRequest {
		if (variant == null) {
			throw new IllegalArgumentException("hunger icon variant must be provided");
		}
		if (state == null) {
			throw new IllegalArgumentException("hunger icon state must be provided");
		}
		if (jitterOffset < -1 || jitterOffset > 1) {
			throw new IllegalArgumentException("hunger icon jitter offset must be in -1..1: " + jitterOffset);
		}
		if (order < 0) {
			throw new IllegalArgumentException("hunger icon order must be non-negative: " + order);
		}
	}
}
