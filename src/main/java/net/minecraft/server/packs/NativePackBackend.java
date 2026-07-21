package net.minecraft.server.packs;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.Nullable;

public final class NativePackBackend implements AutoCloseable {
	private final String packId;
	private final String kind;
	private final long handle;
	private final AtomicBoolean closed = new AtomicBoolean();

	private NativePackBackend(String packId, String kind, long handle) {
		this.packId = packId;
		this.kind = kind;
		this.handle = handle;
	}

	@Nullable
	static NativePackBackend openDirectory(PackLocationInfo location, Path root) {
		if (root.getFileSystem() != FileSystems.getDefault()) {
			ResourcePackDiagnostics.unsupported("non-default-filesystem-directory");
			return null;
		}
		ResourcePackDiagnostics.eligible("directory");
		try {
			NativePackBridge.OpenStats stats = NativePackBridge.openDirectory(root);
			ResourcePackDiagnostics.opened(stats);
			return new NativePackBackend(location.id(), "directory", stats.handle());
		} catch (IOException | UnsatisfiedLinkError exception) {
			ResourcePackDiagnostics.nativeFailure("open directory pack", location.id(), exception);
			throw backendFailure("open directory pack", location.id(), exception);
		}
	}

	static NativePackBackend openZip(PackLocationInfo location, Path path, String prefix) {
		ResourcePackDiagnostics.eligible("zip");
		try {
			NativePackBridge.OpenStats stats = NativePackBridge.openZip(path, prefix);
			ResourcePackDiagnostics.opened(stats);
			return new NativePackBackend(location.id() + (prefix.isEmpty() ? "" : "#" + prefix), "zip", stats.handle());
		} catch (IOException | UnsatisfiedLinkError exception) {
			ResourcePackDiagnostics.nativeFailure("open zip pack", location.id(), exception);
			throw backendFailure("open zip pack", location.id(), exception);
		}
	}

	public static void recordUnsupportedIfNeeded(PackResources packResources) {
		if (packResources instanceof PathPackResources || packResources instanceof FilePackResources || packResources instanceof CompositePackResources) {
			return;
		}
		ResourcePackDiagnostics.unsupported("custom-pack");
	}

	Set<String> getNamespaces(PackType type) {
		try {
			return new LinkedHashSet<>(NativePackBridge.listNamespaces(this.openHandle(), type));
		} catch (IOException exception) {
			this.nativeFailure("list namespaces", exception);
			throw this.backendFailure("list namespaces", exception);
		}
	}

	@Nullable
	IoSupplier<InputStream> getRootResource(String path) {
		try {
			boolean present = NativePackBridge.rootExists(this.openHandle(), path);
			ResourcePackDiagnostics.panamaCall(0L);
			if (!present) {
				return null;
			}
			return () -> this.readNativeBytes(path, () -> NativePackBridge.readRootResource(this.openHandle(), path));
		} catch (IOException exception) {
			this.nativeFailure("root resource lookup", exception);
			throw this.backendFailure("root resource lookup", exception);
		}
	}

	@Nullable
	IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
		String path = location.getPath();
		try {
			boolean present = NativePackBridge.exists(this.openHandle(), type, location.getNamespace(), path);
			ResourcePackDiagnostics.panamaCall(0L);
			if (!present) {
				return null;
			}
			return () -> this.readNativeBytes(
				location.toString(),
				() -> NativePackBridge.readResource(this.openHandle(), type, location.getNamespace(), path)
			);
		} catch (IOException exception) {
			this.nativeFailure("resource lookup", exception);
			throw this.backendFailure("resource lookup", exception);
		}
	}

	void listResources(PackType type, String namespace, String prefix, PackResources.ResourceOutput output) {
		try {
			for (ResourceLocation location : rustResourceLocations(type, namespace, prefix)) {
				output.accept(
					location,
					() -> this.readNativeBytes(
						location.toString(),
						() -> NativePackBridge.readResource(this.openHandle(), type, location.getNamespace(), location.getPath())
					)
				);
			}
		} catch (IOException exception) {
			this.nativeFailure("list resources", exception);
			throw this.backendFailure("list resources", exception);
		}
	}

	@Override
	public void close() {
		if (!this.closed.compareAndSet(false, true)) {
			return;
		}
		try {
			NativePackBridge.close(this.handle);
			ResourcePackDiagnostics.closed();
		} catch (IOException exception) {
			this.nativeFailure("close", exception);
		}
	}

	private List<ResourceLocation> rustResourceLocations(PackType type, String namespace, String prefix) throws IOException {
		List<String> paths = NativePackBridge.listResources(this.openHandle(), type, namespace, prefix);
		List<ResourceLocation> locations = new ArrayList<>(paths.size());
		for (String path : paths) {
			ResourceLocation location = ResourceLocation.tryBuild(namespace, path);
			if (location != null) {
				locations.add(location);
			}
		}
		return locations;
	}

	private InputStream readNativeBytes(String path, ByteRead read) throws IOException {
		if (this.closed.get()) {
			ResourcePackDiagnostics.staleHandle(this.packId);
			throw new IOException("Native resource pack is closed: " + this.packId + " " + path);
		}
		try {
			byte[] bytes = read.read();
			ResourcePackDiagnostics.panamaCall(bytes == null ? 0L : bytes.length);
			if (bytes == null) {
				throw new IOException("Native resource disappeared after lookup: " + this.packId + " " + path);
			}
			return new ByteArrayInputStream(bytes);
		} catch (IOException exception) {
			this.nativeFailure("read", exception);
			throw exception;
		}
	}

	private long openHandle() throws IOException {
		if (this.closed.get()) {
			throw new IOException("Native resource pack is closed: " + this.packId + " (" + this.kind + ")");
		}
		return this.handle;
	}

	private void nativeFailure(String operation, Throwable throwable) {
		if (throwable instanceof IOException exception && exception.getMessage() != null && exception.getMessage().contains("status " + NativePackBridge.INVALID_PATH)) {
			ResourcePackDiagnostics.invalidPath(this.packId, operation);
		} else {
			ResourcePackDiagnostics.nativeFailure(operation, this.packId, throwable);
		}
	}

	private IllegalStateException backendFailure(String operation, Throwable throwable) {
		return backendFailure(operation, this.packId, throwable);
	}

	private static IllegalStateException backendFailure(String operation, String packId, Throwable throwable) {
		return new IllegalStateException("Native resource-pack backend failed during " + operation + " for " + packId, throwable);
	}

	@FunctionalInterface
	private interface ByteRead {
		@Nullable
		byte[] read() throws IOException;
	}
}
