package net.minecraft.client.renderer.entity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.world.entity.Display.RenderState;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public abstract class DisplayEntityRenderState extends EntityRenderState {
	@Nullable
	public RenderState renderState;
	public float interpolationProgress;
	public float entityYRot;
	public float entityXRot;
	public float cameraYRot;
	public float cameraXRot;

	public abstract boolean hasSubState();
}
