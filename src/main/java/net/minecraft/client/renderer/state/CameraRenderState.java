package net.minecraft.client.renderer.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

@Environment(EnvType.CLIENT)
public class CameraRenderState {
	public BlockPos blockPos = BlockPos.ZERO;
	public Vec3 pos = new Vec3(0.0, 0.0, 0.0);
	public boolean initialized;
	public Vec3 entityPos = new Vec3(0.0, 0.0, 0.0);
	public Quaternionf orientation = new Quaternionf();
}
