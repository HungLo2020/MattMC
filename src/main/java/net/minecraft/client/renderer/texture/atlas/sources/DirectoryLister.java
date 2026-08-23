package net.minecraft.client.renderer.texture.atlas.sources;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

@Environment(EnvType.CLIENT)
public record DirectoryLister(String sourcePath, String idPrefix) implements SpriteSource {
	public static final MapCodec<DirectoryLister> MAP_CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
				Codec.STRING.fieldOf("source").forGetter(DirectoryLister::sourcePath), Codec.STRING.fieldOf("prefix").forGetter(DirectoryLister::idPrefix)
			)
			.apply(instance, DirectoryLister::new)
	);

	@Override
	public void run(ResourceManager resourceManager, SpriteSource.Output output) {
		FileToIdConverter fileToIdConverter = new FileToIdConverter("textures/" + this.sourcePath, ".png");
		// Filter PBR suffixes at the resource boundary without consulting Iris
		// runtime classes. Rust shader-pack preparation owns the copied PBR assets.
		fileToIdConverter.listMatchingResources(resourceManager).forEach((resourceLocation, resource) -> {
			String basePath = removePbrSuffix(resourceLocation.getPath());
			if (basePath != null) {
				ResourceLocation baseLocation = resourceLocation.withPath(basePath);
				if (resourceManager.getResource(baseLocation).isPresent()) {
					// Skip PBR suffix textures if base texture exists
					return;
				}
			}
			ResourceLocation resourceLocation2 = fileToIdConverter.fileToId(resourceLocation).withPrefix(this.idPrefix);
			output.add(resourceLocation2, resource);
		});
	}

	private static String removePbrSuffix(String path) {
		if (path.endsWith("_n.png")) return path.substring(0, path.length() - "_n.png".length()) + ".png";
		if (path.endsWith("_s.png")) return path.substring(0, path.length() - "_s.png".length()) + ".png";
		return null;
	}

	@Override
	public MapCodec<DirectoryLister> codec() {
		return MAP_CODEC;
	}
}
