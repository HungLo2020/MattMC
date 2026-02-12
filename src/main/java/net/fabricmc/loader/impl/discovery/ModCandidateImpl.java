package net.fabricmc.loader.impl.discovery;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.impl.metadata.AbstractModMetadata;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;

public final class ModCandidateImpl {
	static final Comparator<ModCandidateImpl> ID_VERSION_COMPARATOR = new Comparator<ModCandidateImpl>() {
		@Override
		public int compare(ModCandidateImpl a, ModCandidateImpl b) {
			int cmp = a.getId().compareTo(b.getId());

			return cmp != 0 ? cmp : a.getVersion().compareTo(b.getVersion());
		}
	};

	private final List<Path> originPaths;
	private List<Path> paths;
	private final String localPath;
	private final long hash;
	private final LoaderModMetadata metadata;
	private final boolean requiresRemap;
	private final Collection<ModCandidateImpl> nestedMods;
	private final Collection<ModCandidateImpl> parentMods;
	private int minNestLevel;
	private SoftReference<ByteBuffer> dataRef;

	// createBuiltin removed - not needed for integrated mod approach

	public static ModCandidateImpl createPlain(List<Path> paths, LoaderModMetadata metadata, boolean requiresRemap, Collection<ModCandidateImpl> nestedMods) {
		return new ModCandidateImpl(paths, null, -1, metadata, requiresRemap, nestedMods);
	}

	static ModCandidateImpl createNested(String localPath, long hash, LoaderModMetadata metadata, boolean requiresRemap, Collection<ModCandidateImpl> nestedMods) {
		return new ModCandidateImpl(null, localPath, hash, metadata, requiresRemap, nestedMods);
	}

	static long hash(ZipEntry entry) {
		if (entry.getSize() < 0 || entry.getCrc() < 0) throw new IllegalArgumentException("uninitialized entry: "+entry);

		return entry.getCrc() << 32 | entry.getSize();
	}

	private static long getSize(long hash) {
		return hash & 0xffffffffL;
	}

	private ModCandidateImpl(List<Path> paths, String localPath, long hash, LoaderModMetadata metadata, boolean requiresRemap, Collection<ModCandidateImpl> nestedMods) {
		this.originPaths = paths;
		this.paths = paths;
		this.localPath = localPath;
		this.metadata = metadata;
		this.hash = hash;
		this.requiresRemap = requiresRemap;
		this.nestedMods = nestedMods;
		this.parentMods = paths == null ? new ArrayList<>() : Collections.emptyList();
		this.minNestLevel = paths != null ? 0 : Integer.MAX_VALUE;
	}

	public List<Path> getOriginPaths() {
		return originPaths;
	}

	public boolean hasPath() {
		return paths != null;
	}

	public List<Path> getPaths() {
		if (paths == null) throw new IllegalStateException("no path set");

		return paths;
	}

	public void setPaths(List<Path> paths) {
		if (paths == null) throw new NullPointerException("null paths");

		this.paths = paths;
		clearCachedData();
	}

	public String getLocalPath() {
		if (localPath != null) {
			return localPath;
		} else if (paths.size() == 1) {
			return paths.get(0).toString();
		} else {
			return paths.toString();
		}
	}

	public LoaderModMetadata getMetadata() {
		return metadata;
	}

	public String getId() {
		return metadata.getId();
	}

	public Version getVersion() {
		return metadata.getVersion();
	}

	public Collection<String> getProvides() {
		return metadata.getProvides();
	}

	public boolean isBuiltin() {
		return metadata.getType().equals(AbstractModMetadata.TYPE_BUILTIN);
	}

	// getLoadCondition removed - not needed for integrated mod approach

	public Collection<ModDependency> getDependencies() {
		return metadata.getDependencies();
	}

	public boolean getRequiresRemap() {
		return requiresRemap;
	}

	public Collection<ModCandidateImpl> getNestedMods() {
		return nestedMods;
	}

	public Collection<ModCandidateImpl> getParentMods() {
		return parentMods;
	}

	// Nested mod methods removed - not needed for single integrated mod

	public int getMinNestLevel() {
		return minNestLevel;
	}

	public boolean isRoot() {
		return minNestLevel == 0;
	}

	void setData(ByteBuffer data) {
		this.dataRef = new SoftReference<>(data);
	}

	void clearCachedData() {
		this.dataRef = null;
	}

	public Path copyToDir(Path outputDir, boolean temp) throws IOException {
		Files.createDirectories(outputDir);
		Path ret = null;

		try {
			if (temp) {
				ret = Files.createTempFile(outputDir, getId(), ".jar");
			} else {
				ret = outputDir.resolve(getDefaultFileName());

				if (Files.exists(ret)) {
					if (Files.size(ret) == getSize(hash)) {
						return ret;
					} else {
						Files.deleteIfExists(ret);
					}
				}
			}

			copyToFile(ret);
		} catch (Throwable t) {
			if (ret != null) Files.deleteIfExists(ret);

			throw t;
		}

		return ret;
	}

	String getDefaultFileName() {
		String ret = String.format("%s-%s-%s.jar",
				getId(),
				FILE_NAME_SANITIZING_PATTERN.matcher(getVersion().getFriendlyString()).replaceAll("_"),
				Long.toHexString(mixHash(hash)));

		if (ret.length() > 64) {
			ret = ret.substring(0, 32).concat(ret.substring(ret.length() - 32));
		}

		return ret;
	}

	private static long mixHash(long hash) {
		hash ^= (hash >>> 33);
		hash *= 0xff51afd7ed558ccdL;
		hash ^= (hash >>> 33);
		hash *= 0xc4ceb9fe1a85ec53L;
		hash ^= (hash >>> 33);

		return hash;
	}

	private static final Pattern FILE_NAME_SANITIZING_PATTERN = Pattern.compile("[^\\w\\.\\-\\+]+");

	private void copyToFile(Path out) throws IOException {
		// Simplified for integrated mod - no nested mods
		if (paths == null || paths.isEmpty()) {
			throw new UnsupportedOperationException("Nested mods not supported in simplified integrated mod loader");
		}
		
		if (paths.size() != 1) {
			throw new UnsupportedOperationException("Multiple paths not supported for " + this);
		}
		
		Files.copy(paths.get(0), out);
	}

	private ByteBuffer getData() throws IOException {
		// Nested mods not supported in simplified loader
		throw new UnsupportedOperationException("Nested mods not supported in simplified integrated mod loader");
	}

	private ModCandidateImpl getBestSourcingParent() {
		// Nested mods not supported in simplified loader
		throw new UnsupportedOperationException("Nested mods not supported in simplified integrated mod loader");
	}

	@Override
	public String toString() {
		return String.format("%s %s", getId(), getVersion());
	}
}
