package net.minecraft.client.renderer.texture.atlas.sources;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public record SingleFile(ResourceLocation resourceId, Optional<ResourceLocation> spriteId) implements SpriteSource {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final MapCodec<SingleFile> MAP_CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
				ResourceLocation.CODEC.fieldOf("resource").forGetter(SingleFile::resourceId),
				ResourceLocation.CODEC.optionalFieldOf("sprite").forGetter(SingleFile::spriteId)
			)
			.apply(instance, SingleFile::new)
	);

	public SingleFile(ResourceLocation resourceLocation) {
		this(resourceLocation, Optional.empty());
	}

	@Override
	public void run(ResourceManager resourceManager, SpriteSource.Output output) {
		ResourceLocation resourceLocation = TEXTURE_ID_CONVERTER.idToFile(this.resourceId);
		Optional<Resource> optional = resourceManager.getResource(resourceLocation);
		if (optional.isPresent()) {
			output.add((ResourceLocation)this.spriteId.orElse(this.resourceId), (Resource)optional.get());
		} else {
			LOGGER.warn("Missing sprite: {}", resourceLocation);
		}
	}

	@Override
	public MapCodec<SingleFile> codec() {
		return MAP_CODEC;
	}
}
