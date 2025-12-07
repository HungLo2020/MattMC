package net.minecraft.client.data.models;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.resources.WaypointStyle;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.PackOutput.PathProvider;
import net.minecraft.data.PackOutput.Target;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.waypoints.WaypointStyleAsset;
import net.minecraft.world.waypoints.WaypointStyleAssets;

@Environment(EnvType.CLIENT)
public class WaypointStyleProvider implements DataProvider {
	private final PathProvider pathProvider;

	public WaypointStyleProvider(PackOutput packOutput) {
		this.pathProvider = packOutput.createPathProvider(Target.RESOURCE_PACK, "waypoint_style");
	}

	private static void bootstrap(BiConsumer<ResourceKey<WaypointStyleAsset>, WaypointStyle> biConsumer) {
		biConsumer.accept(
			WaypointStyleAssets.DEFAULT,
			new WaypointStyle(
				128,
				332,
				List.of(
					ResourceLocation.withDefaultNamespace("default_0"),
					ResourceLocation.withDefaultNamespace("default_1"),
					ResourceLocation.withDefaultNamespace("default_2"),
					ResourceLocation.withDefaultNamespace("default_3")
				)
			)
		);
		biConsumer.accept(
			WaypointStyleAssets.BOWTIE,
			new WaypointStyle(
				64,
				332,
				List.of(
					ResourceLocation.withDefaultNamespace("bowtie"),
					ResourceLocation.withDefaultNamespace("default_0"),
					ResourceLocation.withDefaultNamespace("default_1"),
					ResourceLocation.withDefaultNamespace("default_2"),
					ResourceLocation.withDefaultNamespace("default_3")
				)
			)
		);
	}

	public CompletableFuture<?> run(CachedOutput cachedOutput) {
		Map<ResourceKey<WaypointStyleAsset>, WaypointStyle> map = new HashMap();
		bootstrap((resourceKey, waypointStyle) -> {
			if (map.putIfAbsent(resourceKey, waypointStyle) != null) {
				throw new IllegalStateException("Tried to register waypoint style twice for id: " + resourceKey);
			}
		});
		return DataProvider.saveAll(cachedOutput, WaypointStyle.CODEC, this.pathProvider::json, map);
	}

	public String getName() {
		return "Waypoint Style Definitions";
	}
}
