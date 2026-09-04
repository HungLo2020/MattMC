package net.vulkanic.world;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the cloud producer's pre-allocation resource admission boundary. */
final class CloudSemanticAdmissionTest {
	@Test
	void cloudExpansionPreflightsWorstCaseFacesBeforeTemporaryListAllocation() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"));
		int method = source.indexOf("public static void enqueueWorldCloudFaces(");
		int preflight = source.indexOf("long worstCaseFaces = candidateCells * 12L;", method);
		int rejection = source.indexOf("cloud route exceeds bounded material-quad frame capacity before expansion", preflight);
		int allocation = source.indexOf("List<VulkanicGalBridge.WorldMaterialQuadRecord> quads = new ArrayList<>();", method);
		assertTrue(method >= 0 && preflight > method && rejection > preflight && allocation > rejection,
			"cloud admission must reject oversized worst-case expansion before allocating temporary quads");
	}
}
