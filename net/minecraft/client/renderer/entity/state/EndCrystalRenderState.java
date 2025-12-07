package net.minecraft.client.renderer.entity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class EndCrystalRenderState extends EntityRenderState {
	public boolean showsBottom = true;
	@Nullable
	public Vec3 beamOffset;
}
