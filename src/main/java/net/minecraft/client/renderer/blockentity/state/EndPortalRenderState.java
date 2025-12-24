package net.minecraft.client.renderer.blockentity.state;

import java.util.EnumSet;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.core.Direction;

@Environment(EnvType.CLIENT)
public class EndPortalRenderState extends BlockEntityRenderState {
	public EnumSet<Direction> facesToShow = EnumSet.noneOf(Direction.class);
}
