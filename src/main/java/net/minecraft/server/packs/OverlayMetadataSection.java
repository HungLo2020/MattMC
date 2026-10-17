package net.minecraft.server.packs;

import java.util.List;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.metadata.pack.NativePackFormats;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.util.InclusiveRange;

/**
 * A pack's `overlays` section; Rust decodes and validates its entries
 * ({@link NativePackFormats#overlayCodec}).
 */
public record OverlayMetadataSection(List<OverlayMetadataSection.OverlayEntry> overlays) {
	public static final MetadataSectionType<OverlayMetadataSection> CLIENT_TYPE = new MetadataSectionType<>(
		"overlays", NativePackFormats.overlayCodec(PackType.CLIENT_RESOURCES)
	);
	public static final MetadataSectionType<OverlayMetadataSection> SERVER_TYPE = new MetadataSectionType<>(
		"overlays", NativePackFormats.overlayCodec(PackType.SERVER_DATA)
	);

	public static MetadataSectionType<OverlayMetadataSection> forPackType(PackType packType) {
		return switch (packType) {
			case CLIENT_RESOURCES -> CLIENT_TYPE;
			case SERVER_DATA -> SERVER_TYPE;
		};
	}

	public List<String> overlaysForVersion(PackFormat packFormat) {
		return this.overlays.stream().filter(overlayEntry -> overlayEntry.isApplicable(packFormat)).map(OverlayMetadataSection.OverlayEntry::overlay).toList();
	}

	public record OverlayEntry(InclusiveRange<PackFormat> format, String overlay) {
		public boolean isApplicable(PackFormat packFormat) {
			return this.format.isValueInRange(packFormat);
		}
	}
}
