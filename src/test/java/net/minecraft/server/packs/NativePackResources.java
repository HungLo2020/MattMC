package net.minecraft.server.packs;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

final class NativePackResources implements Closeable {
	private long handle;
	private final NativePackBridge.OpenStats openStats;

	private NativePackResources(NativePackBridge.OpenStats openStats) {
		this.handle = openStats.handle();
		this.openStats = openStats;
	}

	static NativePackResources openDirectory(Path path) throws IOException {
		return new NativePackResources(NativePackBridge.openDirectory(path));
	}

	static NativePackResources openZip(Path path, String prefix) throws IOException {
		return new NativePackResources(NativePackBridge.openZip(path, prefix));
	}

	NativePackBridge.OpenStats openStats() {
		return this.openStats;
	}

	List<String> listNamespaces(PackType type) throws IOException {
		return NativePackBridge.listNamespaces(this.handle(), type);
	}

	List<String> listResources(PackType type, String namespace, String prefix) throws IOException {
		return NativePackBridge.listResources(this.handle(), type, namespace, prefix);
	}

	boolean exists(PackType type, String namespace, String path) throws IOException {
		return NativePackBridge.exists(this.handle(), type, namespace, path);
	}

	byte[] readResource(PackType type, String namespace, String path) throws IOException {
		return NativePackBridge.readResource(this.handle(), type, namespace, path);
	}

	byte[] readRootResource(String path) throws IOException {
		return NativePackBridge.readRootResource(this.handle(), path);
	}

	NativePackBridge.Counters counters() throws IOException {
		return NativePackBridge.counters(this.handle());
	}

	@Override
	public void close() throws IOException {
		long handle = this.handle;
		if (handle == 0L) {
			return;
		}
		this.handle = 0L;
		NativePackBridge.close(handle);
	}

	void closeRawForTest() throws IOException {
		NativePackBridge.close(this.handle);
	}

	private long handle() throws IOException {
		if (this.handle == 0L) {
			throw new IOException("Native pack handle is closed");
		}
		return this.handle;
	}
}
