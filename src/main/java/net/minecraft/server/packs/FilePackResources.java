package net.minecraft.server.packs;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.Nullable;

public class FilePackResources extends AbstractPackResources {
	private final NativePackBackend nativeBackend;

	FilePackResources(PackLocationInfo packLocationInfo, File file, String prefix) {
		super(packLocationInfo);
		this.nativeBackend = NativePackBackend.openZip(packLocationInfo, file.toPath(), prefix);
	}

	@Nullable
	@Override
	public IoSupplier<InputStream> getRootResource(String... strings) {
		return this.nativeBackend.getRootResource(String.join("/", strings));
	}

	@Override
	public IoSupplier<InputStream> getResource(PackType packType, ResourceLocation resourceLocation) {
		return this.nativeBackend.getResource(packType, resourceLocation);
	}

	@Override
	public Set<String> getNamespaces(PackType packType) {
		return this.nativeBackend.getNamespaces(packType);
	}

	@Override
	public void listResources(PackType packType, String string, String string2, PackResources.ResourceOutput resourceOutput) {
		this.nativeBackend.listResources(packType, string, string2, resourceOutput);
	}

	@Override
	public void close() {
		this.nativeBackend.close();
	}

	public static class FileResourcesSupplier implements Pack.ResourcesSupplier {
		private final File content;

		public FileResourcesSupplier(Path path) {
			this(path.toFile());
		}

		public FileResourcesSupplier(File file) {
			this.content = file;
		}

		@Override
		public PackResources openPrimary(PackLocationInfo packLocationInfo) {
			return new FilePackResources(packLocationInfo, this.content, "");
		}

		@Override
		public PackResources openFull(PackLocationInfo packLocationInfo, Pack.Metadata metadata) {
			PackResources packResources = this.openPrimary(packLocationInfo);
			List<String> list = metadata.overlays();
			if (list.isEmpty()) {
				return packResources;
			} else {
				List<PackResources> list2 = new ArrayList(list.size());

				for (String string : list) {
					list2.add(new FilePackResources(packLocationInfo, this.content, string));
				}

				return new CompositePackResources(packResources, list2);
			}
		}
	}
}
