package net.minecraft.client.renderer.shaders.pack;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for AbsolutePackPath.
 * Tests path normalization and resolution.
 */
class AbsolutePackPathTest {
	
	@Test
	void testFromAbsolutePath() {
		// When
		AbsolutePackPath path = AbsolutePackPath.fromAbsolutePath("/shaders/lib/common.glsl");
		
		// Then
		assertThat(path.getPathString()).isEqualTo("/shaders/lib/common.glsl");
	}
	
	@Test
	void testFromAbsolutePathThrowsOnRelativePath() {
		// When & Then
		assertThatThrownBy(() -> AbsolutePackPath.fromAbsolutePath("shaders/lib/common.glsl"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Not an absolute path");
	}
	
	@Test
	void testNormalizationRemovesDotSegments() {
		// When
		AbsolutePackPath path = AbsolutePackPath.fromAbsolutePath("/shaders/./lib/./common.glsl");
		
		// Then
		assertThat(path.getPathString()).isEqualTo("/shaders/lib/common.glsl");
	}
	
	@Test
	void testNormalizationRemovesDotDotSegments() {
		// When
		AbsolutePackPath path = AbsolutePackPath.fromAbsolutePath("/shaders/lib/../lib/common.glsl");
		
		// Then
		assertThat(path.getPathString()).isEqualTo("/shaders/lib/common.glsl");
	}
	
	@Test
	void testNormalizationMultipleDotDot() {
		// When
		AbsolutePackPath path = AbsolutePackPath.fromAbsolutePath("/shaders/a/b/c/../../lib/common.glsl");
		
		// Then
		assertThat(path.getPathString()).isEqualTo("/shaders/a/lib/common.glsl");
	}
	
	@Test
	void testNormalizationRoot() {
		// When
		AbsolutePackPath path = AbsolutePackPath.fromAbsolutePath("/");
		
		// Then
		assertThat(path.getPathString()).isEqualTo("/");
	}
	
	@Test
	void testParent() {
		// Given
		AbsolutePackPath path = AbsolutePackPath.fromAbsolutePath("/shaders/lib/common.glsl");
		
		// When
		AbsolutePackPath parent = path.parent().orElseThrow();
		
		// Then
		assertThat(parent.getPathString()).isEqualTo("/shaders/lib");
	}
	
	@Test
	void testParentOfRoot() {
		// Given
		AbsolutePackPath path = AbsolutePackPath.fromAbsolutePath("/");
		
		// When
		var parent = path.parent();
		
		// Then
		assertThat(parent).isEmpty();
	}
	
	@Test
	void testResolveRelativePath() {
		// Given
		AbsolutePackPath base = AbsolutePackPath.fromAbsolutePath("/shaders/");
		
		// When
		AbsolutePackPath resolved = base.resolve("lib/common.glsl");
		
		// Then
		assertThat(resolved.getPathString()).isEqualTo("/shaders/lib/common.glsl");
	}
	
	@Test
	void testResolveAbsolutePath() {
		// Given
		AbsolutePackPath base = AbsolutePackPath.fromAbsolutePath("/shaders/programs/");
		
		// When
		AbsolutePackPath resolved = base.resolve("/lib/common.glsl");
		
		// Then - Absolute path ignores base
		assertThat(resolved.getPathString()).isEqualTo("/lib/common.glsl");
	}
	
	@Test
	void testEquality() {
		// Given
		AbsolutePackPath path1 = AbsolutePackPath.fromAbsolutePath("/shaders/lib/common.glsl");
		AbsolutePackPath path2 = AbsolutePackPath.fromAbsolutePath("/shaders/lib/common.glsl");
		AbsolutePackPath path3 = AbsolutePackPath.fromAbsolutePath("/shaders/lib/other.glsl");
		
		// Then
		assertThat(path1).isEqualTo(path2);
		assertThat(path1).isNotEqualTo(path3);
		assertThat(path1.hashCode()).isEqualTo(path2.hashCode());
	}
	
	@Test
	void testToString() {
		// Given
		AbsolutePackPath path = AbsolutePackPath.fromAbsolutePath("/shaders/lib/common.glsl");
		
		// When
		String str = path.toString();
		
		// Then
		assertThat(str).contains("/shaders/lib/common.glsl");
	}
}
