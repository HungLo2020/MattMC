package net.minecraft.client.resources.metadata.texture;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.server.packs.metadata.MetadataSectionType;

@Environment(EnvType.CLIENT)
public record TextureMetadataSection(boolean blur, boolean clamp) {
	public static final boolean DEFAULT_BLUR = false;
	public static final boolean DEFAULT_CLAMP = false;
	public static final Codec<TextureMetadataSection> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
				Codec.BOOL.optionalFieldOf("blur", false).forGetter(TextureMetadataSection::blur),
				Codec.BOOL.optionalFieldOf("clamp", false).forGetter(TextureMetadataSection::clamp)
			)
			.apply(instance, TextureMetadataSection::new)
	);
	public static final MetadataSectionType<TextureMetadataSection> TYPE = new MetadataSectionType("texture", CODEC);
}
