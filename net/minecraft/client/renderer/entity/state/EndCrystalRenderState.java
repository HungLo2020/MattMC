package net.minecraft.client.renderer.entity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class EndCrystalRenderState extends EntityRenderState {
	public boolean showsBottom = true;
	@Nullable
	public Vec3 beamOffset;
}
