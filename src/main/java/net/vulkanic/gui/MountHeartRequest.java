package net.vulkanic.gui;

public record MountHeartRequest(
	MountHeartVariant variant,
	MountHeartState state,
	boolean visible,
	int row,
	int order,
	int x,
	int y
) {
	public MountHeartRequest {
		if (variant == null) {
			throw new IllegalArgumentException("mount heart variant must be provided");
		}
		if (state == null) {
			throw new IllegalArgumentException("mount heart state must be provided");
		}
		if (row < 0) {
			throw new IllegalArgumentException("mount heart row must be non-negative: " + row);
		}
		if (order < 0) {
			throw new IllegalArgumentException("mount heart order must be non-negative: " + order);
		}
	}
}
