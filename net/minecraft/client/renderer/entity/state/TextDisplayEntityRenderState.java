package net.minecraft.client.renderer.entity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.Display.TextDisplay.CachedInfo;
import net.minecraft.world.entity.Display.TextDisplay.TextRenderState;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class TextDisplayEntityRenderState extends DisplayEntityRenderState {
	@Nullable
	public TextRenderState textRenderState;
	@Nullable
	public CachedInfo cachedInfo;

	@Override
	public boolean hasSubState() {
		return this.textRenderState != null;
	}
}
