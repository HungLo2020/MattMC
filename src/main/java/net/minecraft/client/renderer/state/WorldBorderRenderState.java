package net.minecraft.client.renderer.state;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.core.Direction;

@Environment(EnvType.CLIENT)
public class WorldBorderRenderState {
	public double minX;
	public double maxX;
	public double minZ;
	public double maxZ;
	public int tint;
	public double alpha;

	public void reset() {
		this.alpha = 0.0;
	}

	@Environment(EnvType.CLIENT)
	public record DistancePerDirection(Direction direction, double distance) {
	}
}
