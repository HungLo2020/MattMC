package net.minecraft.client.renderer.shaders.loading;

import net.minecraft.client.renderer.shaders.program.ProgramSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for ProgramSet class.
 * Follows Step 15 of NEW-SHADER-PLAN.md.
 */
class ProgramSetTest {
	
	private ProgramSet programSet;
	
	@BeforeEach
	void setUp() {
		programSet = new ProgramSet();
	}
	
	@Test
	void testProgramSetCreation() {
		// Verify empty program set can be created
		assertThat(programSet).isNotNull();
		assertThat(programSet.size()).isZero();
	}
	
	@Test
	void testPutAndGet() {
		// Create a program source
		ProgramSource source = new ProgramSource("test", "void main() {}", null, null, null, "void main() {}");
		
		// Add to set
		programSet.put(ProgramId.Basic, source);
		
		// Retrieve
		Optional<ProgramSource> retrieved = programSet.get(ProgramId.Basic);
		assertThat(retrieved).isPresent();
		assertThat(retrieved.get().getName()).isEqualTo("test");
	}
	
	@Test
	void testGetNonexistent() {
		// Try to get program that doesn't exist
		Optional<ProgramSource> result = programSet.get(ProgramId.Basic);
		assertThat(result).isEmpty();
	}
	
	@Test
	void testFallbackChain() {
		// Set up fallback chain: Basic <- Textured <- TexturedLit <- Terrain
		ProgramSource basicSource = new ProgramSource("basic", "void main() {}", null, null, null, "void main() {}");
		programSet.put(ProgramId.Basic, basicSource);
		
		// Request Terrain (should fall back to Basic)
		Optional<ProgramSource> result = programSet.get(ProgramId.Terrain);
		assertThat(result).isPresent();
		assertThat(result.get().getName()).isEqualTo("basic");
	}
	
	@Test
	void testHasProgram() {
		// Add a program
		ProgramSource source = new ProgramSource("test", "void main() {}", null, null, null, "void main() {}");
		programSet.put(ProgramId.Basic, source);
		
		// Check existence
		assertThat(programSet.has(ProgramId.Basic)).isTrue();
		assertThat(programSet.has(ProgramId.Terrain)).isFalse();
	}
	
	@Test
	void testSize() {
		// Empty set
		assertThat(programSet.size()).isZero();
		
		// Add programs
		ProgramSource source1 = new ProgramSource("test1", "void main() {}", null, null, null, "void main() {}");
		ProgramSource source2 = new ProgramSource("test2", "void main() {}", null, null, null, "void main() {}");
		
		programSet.put(ProgramId.Basic, source1);
		assertThat(programSet.size()).isEqualTo(1);
		
		programSet.put(ProgramId.Terrain, source2);
		assertThat(programSet.size()).isEqualTo(2);
	}
	
	@Test
	void testClear() {
		// Add programs
		ProgramSource source = new ProgramSource("test", "void main() {}", null, null, null, "void main() {}");
		programSet.put(ProgramId.Basic, source);
		programSet.put(ProgramId.Terrain, source);
		
		assertThat(programSet.size()).isEqualTo(2);
		
		// Clear
		programSet.clear();
		assertThat(programSet.size()).isZero();
	}
	
	@Test
	void testPutNullProgramId() {
		// Attempt to put null program ID
		ProgramSource source = new ProgramSource("test", "void main() {}", null, null, null, "void main() {}");
		
		assertThatThrownBy(() -> programSet.put(null, source))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Program ID and source cannot be null");
	}
	
	@Test
	void testPutNullSource() {
		// Attempt to put null source
		assertThatThrownBy(() -> programSet.put(ProgramId.Basic, null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Program ID and source cannot be null");
	}
	
	@Test
	void testGetComposite() {
		// Get empty composite array
		ProgramSource[] composites = programSet.getComposite(ProgramArrayId.Composite);
		assertThat(composites).hasSize(100);  // Default size from IRIS
		assertThat(composites[0]).isNull();
	}
	
	@Test
	void testPutComposite() {
		// Create composite array
		ProgramSource[] sources = new ProgramSource[100];
		sources[0] = new ProgramSource("composite1", "void main() {}", null, null, null, "void main() {}");
		sources[1] = new ProgramSource("composite2", "void main() {}", null, null, null, "void main() {}");
		
		// Put composite
		programSet.putComposite(ProgramArrayId.Composite, sources);
		
		// Retrieve
		ProgramSource[] retrieved = programSet.getComposite(ProgramArrayId.Composite);
		assertThat(retrieved).hasSize(100);
		assertThat(retrieved[0]).isNotNull();
		assertThat(retrieved[0].getName()).isEqualTo("composite1");
		assertThat(retrieved[1].getName()).isEqualTo("composite2");
	}
	
	@Test
	void testPutCompositeNullArrayId() {
		// Attempt to put null array ID
		ProgramSource[] sources = new ProgramSource[100];
		
		assertThatThrownBy(() -> programSet.putComposite(null, sources))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Program array ID and sources cannot be null");
	}
	
	@Test
	void testPutCompositeNullSources() {
		// Attempt to put null sources
		assertThatThrownBy(() -> programSet.putComposite(ProgramArrayId.Composite, null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Program array ID and sources cannot be null");
	}
	
	@Test
	void testGetProgramIds() {
		// Empty set
		assertThat(programSet.getProgramIds()).isEmpty();
		
		// Add programs
		ProgramSource source1 = new ProgramSource("test1", "void main() {}", null, null, null, "void main() {}");
		ProgramSource source2 = new ProgramSource("test2", "void main() {}", null, null, null, "void main() {}");
		
		programSet.put(ProgramId.Basic, source1);
		programSet.put(ProgramId.Terrain, source2);
		
		ProgramId[] ids = programSet.getProgramIds();
		assertThat(ids).hasSize(2);
		assertThat(ids).contains(ProgramId.Basic, ProgramId.Terrain);
	}
}
