package net.minecraft.client.renderer.shaders.program;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for ParallelProgramCompiler class.
 * Follows Step 14 of NEW-SHADER-PLAN.md.
 */
class ParallelProgramCompilerTest {
	
	@Test
	void testParallelProgramCompilerClassExists() {
		// Verify class exists and matches IRIS pattern
		assertThatCode(() -> {
			Class.forName("net.minecraft.client.renderer.shaders.program.ParallelProgramCompiler");
		}).doesNotThrowAnyException();
	}
	
	@Test
	void testCompileParallelWithEmptyList() {
		// Given
		List<ProgramSource> sources = new ArrayList<>();
		
		// When
		List<Program> programs = ParallelProgramCompiler.compileParallel(sources);
		
		// Then
		assertThat(programs).isEmpty();
	}
	
	@Test
	void testCompileParallelWithNullList() {
		// When
		List<Program> programs = ParallelProgramCompiler.compileParallel(null);
		
		// Then
		assertThat(programs).isEmpty();
	}
	
	@Test
	void testCompileAndCacheWithNullCache() {
		// Given
		List<ProgramSource> sources = new ArrayList<>();
		sources.add(new ProgramSource("test", "void main() {}", null, null, null, "void main() {}"));
		
		// When/Then
		assertThatThrownBy(() -> ParallelProgramCompiler.compileAndCache(sources, null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Cache cannot be null");
	}
	
	@Test
	void testCompileAndCacheWithEmptyList() {
		// Given
		List<ProgramSource> sources = new ArrayList<>();
		ProgramCache cache = new ProgramCache();
		
		// When
		List<Program> programs = ParallelProgramCompiler.compileAndCache(sources, cache);
		
		// Then
		assertThat(programs).isEmpty();
	}
	
	@Test
	void testCompileAndCacheWithNullList() {
		// Given
		ProgramCache cache = new ProgramCache();
		
		// When
		List<Program> programs = ParallelProgramCompiler.compileAndCache(null, cache);
		
		// Then
		assertThat(programs).isEmpty();
	}
	
	@Test
	void testCompilationExceptionClassExists() {
		// Verify inner exception class exists
		assertThatCode(() -> {
			Class.forName("net.minecraft.client.renderer.shaders.program.ParallelProgramCompiler$CompilationException");
		}).doesNotThrowAnyException();
	}
	
	@Test
	void testCompilationExceptionWithMessage() {
		// When
		ParallelProgramCompiler.CompilationException exception = 
			new ParallelProgramCompiler.CompilationException("Test error");
		
		// Then
		assertThat(exception).hasMessage("Test error");
	}
	
	@Test
	void testCompilationExceptionWithCause() {
		// Given
		Throwable cause = new RuntimeException("Root cause");
		
		// When
		ParallelProgramCompiler.CompilationException exception = 
			new ParallelProgramCompiler.CompilationException("Test error", cause);
		
		// Then
		assertThat(exception).hasMessage("Test error");
		assertThat(exception).hasCause(cause);
	}
	
	@Test
	void testThreadPoolSize() throws Exception {
		// Verify THREAD_POOL_SIZE constant exists and equals 10 (IRIS pattern)
		Class<?> clazz = ParallelProgramCompiler.class;
		var field = clazz.getDeclaredField("THREAD_POOL_SIZE");
		field.setAccessible(true);
		
		int threadPoolSize = (int) field.get(null);
		assertThat(threadPoolSize).isEqualTo(10);
	}
	
	@Test
	void testCompileParallelMethodExists() throws Exception {
		// Verify compileParallel method exists with correct signature
		Class<?> clazz = ParallelProgramCompiler.class;
		var method = clazz.getMethod("compileParallel", List.class);
		
		assertThat(method).isNotNull();
		assertThat(method.getReturnType()).isEqualTo(List.class);
		assertThat(java.lang.reflect.Modifier.isStatic(method.getModifiers())).isTrue();
	}
	
	@Test
	void testCompileAndCacheMethodExists() throws Exception {
		// Verify compileAndCache method exists with correct signature
		Class<?> clazz = ParallelProgramCompiler.class;
		var method = clazz.getMethod("compileAndCache", List.class, ProgramCache.class);
		
		assertThat(method).isNotNull();
		assertThat(method.getReturnType()).isEqualTo(List.class);
		assertThat(java.lang.reflect.Modifier.isStatic(method.getModifiers())).isTrue();
	}
}
