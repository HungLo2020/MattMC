package net.minecraft.client.renderer.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

@Environment(EnvType.CLIENT)
public class CameraRenderState implements FabricRenderState {
	public BlockPos blockPos = BlockPos.ZERO;
	public Vec3 pos = new Vec3(0.0, 0.0, 0.0);
	public boolean initialized;
	public Vec3 entityPos = new Vec3(0.0, 0.0, 0.0);
	public Quaternionf orientation = new Quaternionf();
}
