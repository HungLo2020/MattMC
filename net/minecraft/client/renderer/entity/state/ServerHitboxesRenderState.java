package net.minecraft.client.renderer.entity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public record ServerHitboxesRenderState(
	boolean missing,
	double serverEntityX,
	double serverEntityY,
	double serverEntityZ,
	double deltaMovementX,
	double deltaMovementY,
	double deltaMovementZ,
	float eyeHeight,
	@Nullable HitboxesRenderState hitboxes
) {
	public ServerHitboxesRenderState(boolean bl) {
		this(bl, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0F, null);
	}
}
