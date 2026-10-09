package net.minecraft.client.resources;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

@Environment(EnvType.CLIENT)
public class MapTextureManager implements AutoCloseable {
	private final Int2ObjectMap<MapTextureManager.MapInstance> maps = new Int2ObjectOpenHashMap<>();
	final TextureManager textureManager;

	public MapTextureManager(TextureManager textureManager) {
		this.textureManager = textureManager;
	}

	public void update(MapId mapId, MapItemSavedData mapItemSavedData) {
		this.getOrCreateMapInstance(mapId, mapItemSavedData).forceUpload();
	}

	public ResourceLocation prepareMapTexture(MapId mapId, MapItemSavedData mapItemSavedData) {
		MapTextureManager.MapInstance mapInstance = this.getOrCreateMapInstance(mapId, mapItemSavedData);
		mapInstance.updateTextureIfNeeded();
		return mapInstance.location;
	}

	public void resetData() {
		for (MapTextureManager.MapInstance mapInstance : this.maps.values()) {
			mapInstance.close();
		}

		this.maps.clear();
	}

	private MapTextureManager.MapInstance getOrCreateMapInstance(MapId mapId, MapItemSavedData mapItemSavedData) {
		return this.maps.compute(mapId.id(), (integer, mapInstance) -> {
			if (mapInstance == null) {
				return new MapTextureManager.MapInstance(integer, mapItemSavedData);
			} else {
				mapInstance.replaceMapData(mapItemSavedData);
				return mapInstance;
			}
		});
	}

	public void close() {
		this.resetData();
	}

	@Environment(EnvType.CLIENT)
	class MapInstance implements AutoCloseable {
		private MapItemSavedData data;
		private boolean requiresUpload = true;
		final ResourceLocation location;

		MapInstance(final int i, final MapItemSavedData mapItemSavedData) {
			this.data = mapItemSavedData;
			this.location = ResourceLocation.withDefaultNamespace("map/" + i);
		}


		void replaceMapData(MapItemSavedData mapItemSavedData) {
			boolean bl = this.data != mapItemSavedData;
			this.data = mapItemSavedData;
			this.requiresUpload |= bl;
		}

		public void forceUpload() {
			this.requiresUpload = true;
		}

		void updateTextureIfNeeded() {
			if (!this.requiresUpload && net.vulkanic.gui.RustGalGuiRawImageAssets.hasCpuMapColor8(this.location)) return;
			if (this.data == null || this.data.colors == null || this.data.colors.length != 128 * 128)
				throw new IllegalStateException("Rust semantic map image staging requires exactly 128x128 map color data");
			if (!net.vulkanic.gui.RustGalGuiRawImageAssets.stageCpuMapColor8(this.location, this.data.colors))
				throw new IllegalStateException("Rust semantic map image staging rejected bounded indexed colors");
			this.requiresUpload = false;
		}

		public void close() {
			net.vulkanic.gui.RustGalGuiRawImageAssets.releaseCpuRgba8(this.location);
		}
	}
}
