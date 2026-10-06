package net.vulkanic.world;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TexturePayloadCacheTest {
	private final Map<String, Integer> reads = new HashMap<>();

	private byte[] read(String identity) {
		reads.merge(identity, 1, Integer::sum);
		return switch (identity) {
			case "missing" -> null;
			case "empty" -> new byte[0];
			default -> identity.getBytes();
		};
	}

	@Test
	void repeatedLookupsReadEachIdentityOnceIncludingMissingTextures() {
		var cache = new TexturePayloadCache<String>(8, 1024);
		byte[] first = cache.get("glint", this::read);
		assertSame(first, cache.get("glint", this::read));
		assertNull(cache.get("missing", this::read));
		assertNull(cache.get("missing", this::read));
		assertNull(cache.get("empty", this::read));
		assertEquals(Map.of("glint", 1, "missing", 1, "empty", 1), reads);
		assertArrayEquals("glint".getBytes(), first);
	}

	@Test
	void reloadClearForcesFreshReads() {
		var cache = new TexturePayloadCache<String>(8, 1024);
		cache.get("glint", this::read);
		cache.get("missing", this::read);
		cache.clear();
		assertEquals(0, cache.size());
		assertEquals(0, cache.retainedBytes());
		cache.get("glint", this::read);
		cache.get("missing", this::read);
		assertEquals(Map.of("glint", 2, "missing", 2), reads);
	}

	@Test
	void entryAndByteBoundsEvictLeastRecentlyUsed() {
		var cache = new TexturePayloadCache<String>(2, 1024);
		cache.get("a", this::read);
		cache.get("b", this::read);
		cache.get("a", this::read);
		cache.get("c", this::read);
		assertEquals(2, cache.size());
		cache.get("a", this::read);
		cache.get("b", this::read);
		assertEquals(Map.of("a", 1, "b", 2, "c", 1), reads);

		var small = new TexturePayloadCache<String>(8, 10);
		small.get("12345", this::read);
		small.get("67890", this::read);
		small.get("abc", this::read);
		assertTrue(small.retainedBytes() <= 10);
		// A payload larger than the whole bound is returned but not retained.
		assertArrayEquals("eleven-byte".getBytes(), small.get("eleven-byte", this::read));
		assertTrue(small.retainedBytes() <= 10);
	}
}
