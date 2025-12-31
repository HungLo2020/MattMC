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
		// Iris: Filter out PBR texture suffixes if base texture exists
		fileToIdConverter.listMatchingResources(resourceManager).forEach((resourceLocation, resource) -> {
			// Iris PBR: Check if this is a PBR suffix texture
			String basePath = net.irisshaders.iris.pbr.texture.PBRType.removeSuffix(resourceLocation.getPath());
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

	@Override
	public MapCodec<DirectoryLister> codec() {
		return MAP_CODEC;
	}
}
