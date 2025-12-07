package net.minecraft.client.renderer.blockentity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.world.level.block.entity.BoundingBoxRenderable.Mode;
import net.minecraft.world.level.block.entity.BoundingBoxRenderable.RenderableBox;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class BlockEntityWithBoundingBoxRenderState extends BlockEntityRenderState {
	public boolean isVisible;
	public Mode mode;
	public RenderableBox box;
	@Nullable
	public BlockEntityWithBoundingBoxRenderState.InvisibleBlockType[] invisibleBlocks;
	@Nullable
	public boolean[] structureVoids;

	@Environment(EnvType.CLIENT)
	public static enum InvisibleBlockType {
		AIR,
		BARRIER,
		LIGHT,
		STRUCUTRE_VOID;
	}
}
