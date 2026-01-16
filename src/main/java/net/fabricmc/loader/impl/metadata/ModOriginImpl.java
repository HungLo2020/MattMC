package net.fabricmc.loader.impl.metadata;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import net.fabricmc.loader.api.metadata.ModOrigin;

public final class ModOriginImpl implements ModOrigin {
	private final Kind kind;
	private List<Path> paths;
	private String parentModId;
	private String parentSubLocation;

	public ModOriginImpl() {
		this.kind = Kind.UNKNOWN;
	}

	public ModOriginImpl(List<Path> paths) {
		this.kind = Kind.PATH;
		this.paths = paths;
	}

	public ModOriginImpl(String parentModId, String parentSubLocation) {
		this.kind = Kind.NESTED;
		this.parentModId = parentModId;
		this.parentSubLocation = parentSubLocation;
	}

	@Override
	public Kind getKind() {
		return kind;
	}

	@Override
	public List<Path> getPaths() {
		if (kind != Kind.PATH) throw new UnsupportedOperationException("kind "+kind.name()+" doesn't have paths");

		return paths;
	}

	@Override
	public String getParentModId() {
		if (kind != Kind.NESTED) throw new UnsupportedOperationException("kind "+kind.name()+" doesn't have a parent mod");

		return parentModId;
	}

	@Override
	public String getParentSubLocation() {
		if (kind != Kind.NESTED) throw new UnsupportedOperationException("kind "+kind.name()+" doesn't have a parent sub-location");

		return parentSubLocation;
	}

	@Override
	public String toString() {
		switch (getKind()) {
		case PATH:
			return paths.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator));
		case NESTED:
			return String.format("%s:%s", parentModId, parentSubLocation);
		default:
			return "unknown";
		}
	}
}
