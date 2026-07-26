package net.vulkanic.gui;

public record PlayerHeartRequest(
	PlayerHeartVariant variant,
	GuiHeartState state,
	boolean hardcore,
	boolean flashing,
	int order,
	int x,
	int y
) {
	public PlayerHeartRequest {
		if (variant == null) {
			throw new IllegalArgumentException("player heart variant must be provided");
		}
		if (state == null) {
			throw new IllegalArgumentException("player heart state must be provided");
		}
		if (order < 0) {
			throw new IllegalArgumentException("player heart order must be non-negative: " + order);
		}
		if (state == GuiHeartState.CONTAINER && variant != PlayerHeartVariant.CONTAINER) {
			throw new IllegalArgumentException("container player hearts must use the container variant");
		}
		if (state != GuiHeartState.CONTAINER && variant == PlayerHeartVariant.CONTAINER) {
			throw new IllegalArgumentException("filled player hearts must use a non-container variant");
		}
	}
}
