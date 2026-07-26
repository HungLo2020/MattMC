package net.vulkanic.gui;

public record AbsorptionHeartRequest(
	AbsorptionHeartVariant variant,
	GuiHeartState state,
	boolean hardcore,
	boolean flashing,
	int order,
	int x,
	int y
) {
	public AbsorptionHeartRequest {
		if (variant == null) {
			throw new IllegalArgumentException("absorption heart variant must be provided");
		}
		if (state == null) {
			throw new IllegalArgumentException("absorption heart state must be provided");
		}
		if (order < 0) {
			throw new IllegalArgumentException("absorption heart order must be non-negative: " + order);
		}
		if (state == GuiHeartState.CONTAINER && variant != AbsorptionHeartVariant.CONTAINER) {
			throw new IllegalArgumentException("container absorption hearts must use the container variant");
		}
		if (state != GuiHeartState.CONTAINER && variant == AbsorptionHeartVariant.CONTAINER) {
			throw new IllegalArgumentException("filled absorption hearts must use a non-container variant");
		}
	}
}
