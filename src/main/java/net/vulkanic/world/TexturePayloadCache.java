package net.vulkanic.world;

import java.util.LinkedHashMap;
import java.util.function.Function;

/**
 * Bounded CPU copies of resource texture bytes, including known-missing
 * identities. Frame extraction asks for the same item, glint and equipment
 * textures every frame; reading them through the resource manager inflated
 * the pack archive each time. Resource reload must clear this cache.
 *
 * <p>Returned arrays are shared and must be treated as immutable.
 */
final class TexturePayloadCache<K> {
	private static final byte[] MISSING = new byte[0];
	private final int maxEntries;
	private final long maxBytes;
	private final LinkedHashMap<K, byte[]> entries = new LinkedHashMap<>(16, 0.75F, true);
	private long bytes;

	TexturePayloadCache(int maxEntries, long maxBytes) {
		if (maxEntries < 1 || maxBytes < 1) throw new IllegalArgumentException("positive texture payload cache bounds required");
		this.maxEntries = maxEntries;
		this.maxBytes = maxBytes;
	}

	/**
	 * Returns the cached payload or reads it once. The read runs under this
	 * cache's monitor so a concurrent {@link #clear()} cannot be followed by an
	 * entry read from the replaced resource set.
	 */
	synchronized byte[] get(K identity, Function<K, byte[]> reader) {
		byte[] cached = entries.get(identity);
		if (cached != null) return cached == MISSING ? null : cached;
		byte[] payload = reader.apply(identity);
		byte[] stored = payload == null || payload.length == 0 ? MISSING : payload;
		if (stored.length > maxBytes) return payload;
		entries.put(identity, stored);
		bytes += stored.length;
		var eldest = entries.entrySet().iterator();
		while ((entries.size() > maxEntries || bytes > maxBytes) && eldest.hasNext()) {
			var entry = eldest.next();
			if (entry.getKey().equals(identity)) continue;
			bytes -= entry.getValue().length;
			eldest.remove();
		}
		return stored == MISSING ? null : stored;
	}

	synchronized void clear() {
		entries.clear();
		bytes = 0;
	}

	synchronized int size() {
		return entries.size();
	}

	synchronized long retainedBytes() {
		return bytes;
	}
}
