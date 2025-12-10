package net.minecraft.client.renderer.shaders.program;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for ProgramCache class.
 * Follows Step 13 of NEW-SHADER-PLAN.md.
 */
class ProgramCacheTest {
	
	private ProgramCache cache;
	
	@BeforeEach
	void setUp() {
		cache = new ProgramCache();
	}
	
	@Test
	void testCacheCreation() {
		// When - Create new cache
		ProgramCache newCache = new ProgramCache();
		
		// Then
		assertThat(newCache).isNotNull();
		assertThat(newCache.isEmpty()).isTrue();
		assertThat(newCache.size()).isZero();
	}
	
	@Test
	void testPutAndGet() {
		// Given
		Program program = new Program(123);
		
		// When
		cache.put("test", program);
		Program retrieved = cache.get("test");
		
		// Then
		assertThat(retrieved).isSameAs(program);
		assertThat(cache.size()).isEqualTo(1);
	}
	
	@Test
	void testCacheHit() {
		// Given
		Program program = new Program(123);
		cache.put("test", program);
		
		// When
		cache.get("test");
		
		// Then
		assertThat(cache.getHits()).isEqualTo(1);
		assertThat(cache.getMisses()).isZero();
	}
	
	@Test
	void testCacheMiss() {
		// When
		Program result = cache.get("nonexistent");
		
		// Then
		assertThat(result).isNull();
		assertThat(cache.getHits()).isZero();
		assertThat(cache.getMisses()).isEqualTo(1);
	}
	
	@Test
	void testHitRate() {
		// Given
		Program program = new Program(123);
		cache.put("test", program);
		
		// When
		cache.get("test");  // hit
		cache.get("test");  // hit
		cache.get("missing");  // miss
		
		// Then - 2 hits, 1 miss = 66.7% hit rate
		assertThat(cache.getHitRate()).isCloseTo(0.667, within(0.01));
	}
	
	@Test
	void testContains() {
		// Given
		Program program = new Program(123);
		cache.put("test", program);
		
		// When/Then
		assertThat(cache.contains("test")).isTrue();
		assertThat(cache.contains("missing")).isFalse();
	}
	
	@Test
	void testRemove() {
		// Given
		Program program = new Program(123);
		cache.put("test", program);
		
		// When
		Program removed = cache.remove("test");
		
		// Then
		assertThat(removed).isSameAs(program);
		assertThat(cache.contains("test")).isFalse();
		assertThat(cache.size()).isZero();
	}
	
	@Test
	void testRemoveNonexistent() {
		// When
		Program removed = cache.remove("nonexistent");
		
		// Then
		assertThat(removed).isNull();
	}
	
	@Test
	void testClear() {
		// Given
		cache.put("program1", new Program(1));
		cache.put("program2", new Program(2));
		cache.put("program3", new Program(3));
		
		// When
		cache.clear();
		
		// Then
		assertThat(cache.isEmpty()).isTrue();
		assertThat(cache.size()).isZero();
		assertThat(cache.getHits()).isZero();
		assertThat(cache.getMisses()).isZero();
	}
	
	@Test
	void testMultiplePrograms() {
		// Given
		Program p1 = new Program(1);
		Program p2 = new Program(2);
		Program p3 = new Program(3);
		
		// When
		cache.put("program1", p1);
		cache.put("program2", p2);
		cache.put("program3", p3);
		
		// Then
		assertThat(cache.size()).isEqualTo(3);
		assertThat(cache.get("program1")).isSameAs(p1);
		assertThat(cache.get("program2")).isSameAs(p2);
		assertThat(cache.get("program3")).isSameAs(p3);
	}
	
	@Test
	void testPutNullNameThrowsException() {
		// Given
		Program program = new Program(123);
		
		// When/Then
		assertThatThrownBy(() -> cache.put(null, program))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("cannot be null");
	}
	
	@Test
	void testPutNullProgramThrowsException() {
		// When/Then
		assertThatThrownBy(() -> cache.put("test", null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("cannot be null");
	}
	
	@Test
	void testOverwriteExisting() {
		// Given
		Program program1 = new Program(1);
		Program program2 = new Program(2);
		
		// When
		cache.put("test", program1);
		cache.put("test", program2);  // Overwrite
		
		// Then
		assertThat(cache.size()).isEqualTo(1);
		assertThat(cache.get("test")).isSameAs(program2);
	}
	
	@Test
	void testHitRateWithNoLookups() {
		// When - No lookups performed
		double hitRate = cache.getHitRate();
		
		// Then
		assertThat(hitRate).isZero();
	}
}
