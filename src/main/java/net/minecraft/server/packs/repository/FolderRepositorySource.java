package net.minecraft.server.packs.repository;

import net.logging.LogUtils;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.FileUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.NativePackResources;
import net.minecraft.server.packs.NativePacks;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.level.validation.ContentValidationException;
import net.minecraft.world.level.validation.DirectoryValidator;
import net.minecraft.world.level.validation.ForbiddenSymlinkInfo;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class FolderRepositorySource implements RepositorySource {
	static final Logger LOGGER = LogUtils.getLogger();
	private static final PackSelectionConfig DISCOVERED_PACK_SELECTION_CONFIG = new PackSelectionConfig(false, Pack.Position.TOP, false);
	private final Path folder;
	private final PackType packType;
	private final PackSource packSource;
	private final DirectoryValidator validator;

	public FolderRepositorySource(Path path, PackType packType, PackSource packSource, DirectoryValidator directoryValidator) {
		this.folder = path;
		this.packType = packType;
		this.packSource = packSource;
		this.validator = directoryValidator;
	}

	private static String nameFromPath(Path path) {
		return path.getFileName().toString();
	}

	@Override
	public void loadPacks(Consumer<Pack> consumer) {
		try {
			FileUtil.createDirectoriesSafe(this.folder);
			discoverPacks(this.folder, this.validator, (path, resourcesSupplier) -> {
				PackLocationInfo packLocationInfo = this.createDiscoveredFilePackInfo(path);
				Pack pack = Pack.readMetaAndCreate(packLocationInfo, resourcesSupplier, this.packType, DISCOVERED_PACK_SELECTION_CONFIG);
				if (pack != null) {
					consumer.accept(pack);
				}
			});
		} catch (IOException var3) {
			LOGGER.warn("Failed to list packs in {}", this.folder, var3);
		}
	}

	private PackLocationInfo createDiscoveredFilePackInfo(Path path) {
		String string = nameFromPath(path);
		return new PackLocationInfo("file/" + string, Component.literal(string), this.packSource, Optional.empty());
	}

	/**
	 * Detects the packs in a folder (Rust lists and classifies the entries;
	 * links and directories pass the Java validator first). A folder inside
	 * the game jar lists that archive's entries.
	 */
	public static void discoverPacks(Path path, DirectoryValidator directoryValidator, BiConsumer<Path, Pack.ResourcesSupplier> biConsumer) throws IOException {
		if (path.getFileSystem() != FileSystems.getDefault()) {
			discoverArchivePacks(path, directoryValidator, biConsumer);
			return;
		}
		for (NativePacks.Detection entry : NativePacks.discover(path.toString())) {
			Path entryPath = Path.of(entry.path());
			try {
				List<ForbiddenSymlinkInfo> list = new ArrayList();
				Pack.ResourcesSupplier resourcesSupplier = supplier(detect(entryPath, entry, directoryValidator, list));
				if (!list.isEmpty()) {
					LOGGER.warn("Ignoring potential pack entry: {}", ContentValidationException.getMessage(entryPath, list));
				} else if (resourcesSupplier != null) {
					biConsumer.accept(entryPath, resourcesSupplier);
				} else {
					LOGGER.info("Found non-pack entry '{}', ignoring", entryPath);
				}
			} catch (IOException var10) {
				LOGGER.warn("Failed to read properties of '{}', ignoring", entryPath, var10);
			}
		}
	}

	/** A detected pack: its (link-resolved) path and whether it is an archive. */
	private record Detected(Path path, boolean zip) {
	}

	/** `PackDetector.detectPackResources` over Rust's classification. */
	@Nullable
	private static Detected detect(Path path, NativePacks.Detection detection, DirectoryValidator validator, List<ForbiddenSymlinkInfo> list)
		throws IOException {
		if (detection.kind() == NativePacks.DETECT_LINK) {
			validator.validateSymlink(path, list);
			if (!list.isEmpty()) {
				return null;
			}
			detection = NativePacks.detect(detection.target(), NativePacks.MODE_LINK_TARGET);
		}
		return switch (detection.kind()) {
			case NativePacks.DETECT_KNOWN_DIRECTORY -> {
				Path directory = Path.of(detection.path());
				validator.validateKnownDirectory(directory, list);
				yield list.isEmpty() && NativePacks.detect(detection.path(), NativePacks.MODE_DIRECTORY_PACK).kind() == NativePacks.DETECT_DIRECTORY
					? new Detected(directory, false)
					: null;
			}
			case NativePacks.DETECT_ZIP -> new Detected(Path.of(detection.path()), true);
			default -> null;
		};
	}

	@Nullable
	private static Pack.ResourcesSupplier supplier(@Nullable Detected detected) {
		if (detected == null) {
			return null;
		}
		return detected.zip() ? new NativePackResources.ZipSupplier(detected.path()) : new NativePackResources.DirectorySupplier(detected.path());
	}

	/**
	 * The pack a dropped path names (its link target when it is a link), or
	 * null; forbidden links are added to `list`.
	 */
	@Nullable
	public static Path detectPackPath(Path path, DirectoryValidator validator, List<ForbiddenSymlinkInfo> list) throws IOException {
		Detected detected = detect(path, NativePacks.detect(path.toString(), NativePacks.MODE_DETECT), validator, list);
		return detected == null ? null : detected.path();
	}

	/**
	 * A folder inside an archive: directories holding a `pack.mcmeta` are
	 * packs; archives inside an archive cannot be opened.
	 */
	private static void discoverArchivePacks(Path path, DirectoryValidator directoryValidator, BiConsumer<Path, Pack.ResourcesSupplier> biConsumer)
		throws IOException {
		String uri = path.toUri().toString();
		int separator = uri.indexOf("!/");
		if (!uri.startsWith("jar:") || separator < 0) {
			throw new IOException("Unsupported pack folder " + uri);
		}
		Path archive = Path.of(java.net.URI.create(uri.substring("jar:".length(), separator)));
		String inner = uri.substring(separator + 2);
		String prefix = inner.isEmpty() || inner.endsWith("/") ? inner : inner + "/";
		List<NativePacks.ArchiveChild> children = NativePacks.discoverArchive(archive.toString(), prefix);
		if (children == null) {
			throw new IOException("Failed to open archive " + archive);
		}
		for (NativePacks.ArchiveChild child : children) {
			Path childPath = path.resolve(child.name());
			if (child.directory() && child.hasPackMeta()) {
				biConsumer.accept(childPath, new NativePackResources.DirectorySupplier(childPath));
				continue;
			}
			if (!child.directory() && child.name().endsWith(".zip")) {
				LOGGER.info("Can't open pack archive at {}", childPath);
			}
			LOGGER.info("Found non-pack entry '{}', ignoring", childPath);
		}
	}
}
