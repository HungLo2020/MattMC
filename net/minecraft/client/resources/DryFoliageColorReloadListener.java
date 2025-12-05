package net.minecraft.client.resources;

import java.io.IOException;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.DryFoliageColor;

@Environment(EnvType.CLIENT)
public class DryFoliageColorReloadListener extends SimplePreparableReloadListener<int[]> {
	private static final ResourceLocation LOCATION = ResourceLocation.withDefaultNamespace("textures/colormap/dry_foliage.png");

	protected int[] prepare(ResourceManager resourceManager, ProfilerFiller profilerFiller) {
		try {
			return LegacyStuffWrapper.getPixels(resourceManager, LOCATION);
		} catch (IOException var4) {
			throw new IllegalStateException("Failed to load dry foliage color texture", var4);
		}
	}

	protected void apply(int[] is, ResourceManager resourceManager, ProfilerFiller profilerFiller) {
		DryFoliageColor.init(is);
	}
}
