package net.minecraft.client.resources;

import net.blaze3d.platform.NativeImage;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.MapColor;
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
		private final DynamicTexture texture;
		private boolean requiresUpload = true;
		final ResourceLocation location;

		MapInstance(final int i, final MapItemSavedData mapItemSavedData) {
			this.data = mapItemSavedData;
			this.texture = semanticRustRoute()
				? null : new DynamicTexture(() -> "Map " + i, 128, 128, true);
			this.location = ResourceLocation.withDefaultNamespace("map/" + i);
			if (!semanticRustRoute()) MapTextureManager.this.textureManager.register(this.location, this.texture);
		}

		private boolean semanticRustRoute() {
			return net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
				|| net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled();
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
			if (this.requiresUpload) {
				if (semanticRustRoute()) {
					if (this.data == null || this.data.colors == null || this.data.colors.length != 128 * 128) {
						throw new IllegalStateException("Rust semantic map image staging requires exactly 128x128 map color data");
					}
					byte[] pixels = new byte[128 * 128 * 4];
					for (int i = 0; i < 128; i++) {
						for (int j = 0; j < 128; j++) {
							int color = net.minecraft.world.level.material.MapColor.getColorFromPackedId(this.data.colors[j + i * 128]);
							int offset = (j + i * 128) * 4;
							pixels[offset] = (byte) color;
							pixels[offset + 1] = (byte) (color >>> 8);
							pixels[offset + 2] = (byte) (color >>> 16);
							pixels[offset + 3] = (byte) (color >>> 24);
						}
					}
					if (!net.vulkanic.gui.RustGalGuiRawImageAssets.stageCpuRgba8(this.location, 128, 128, pixels)) {
						throw new IllegalStateException("Rust semantic map image staging rejected bounded CPU pixels");
					}
					this.requiresUpload = false;
					return;
				}
				NativeImage nativeImage = this.texture.getPixels();
				if (nativeImage != null) {
					for (int i = 0; i < 128; i++) {
						for (int j = 0; j < 128; j++) {
							int k = j + i * 128;
							nativeImage.setPixel(j, i, MapColor.getColorFromPackedId(this.data.colors[k]));
						}
					}
				}

				this.texture.upload();
				this.requiresUpload = false;
			}
		}

		public void close() {
			if (this.texture != null) this.texture.close();
		}
	}
}
