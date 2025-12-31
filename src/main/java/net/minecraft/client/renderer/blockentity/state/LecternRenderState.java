package net.minecraft.client.renderer.blockentity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public class LecternRenderState extends BlockEntityRenderState {
	public boolean hasBook;
	public float yRot;
}
