package net.vulkanic.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CloudSemanticRasterStateTest {
	@Test
	void fancyCloudFacesUseCullingAndReverseOnlyNearbyInsideGeometry() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"));
		int method = source.indexOf("private static void appendCloudFace(");
		int record = source.indexOf("new VulkanicGalBridge.WorldMaterialQuadRecord(", method);
		int end = source.indexOf("\n\t/** Capture-only receipt for the already-selected whole-frame submission. */", method);
		String body = source.substring(method, end);

		assertTrue(body.contains("int winding = inside ? WORLD_WINDING_CW : WORLD_WINDING_CCW;"),
			"nearby inside cloud faces must retain Frozen's explicit reversed winding");
		assertFalse(body.contains("for (int left = 0, right = 9;"),
			"cloud faces must retain canonical vertex order");
		assertTrue(body.contains("CULL_BACK,"),
			"the cloud semantic record must preserve Frozen fancy-cloud back-face culling");
		assertFalse(body.substring(record - method).contains("face == 0 && !inside ? CULL_NONE"),
			"ordinary bottom cloud faces must not become double-sided");
		assertTrue(source.contains("if (Math.abs(dx) + Math.abs(dz) <= 1)"),
			"nearby inside cloud faces must match Frozen Sodium's taxicab-distance-one topology");
		assertFalse(source.contains("if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1)"),
			"diagonal cloud cells must remain exterior-only in the Frozen Sodium topology");
	}

	@Test
	void captureReceiptContainsAllFrozenCloudMeshInputs() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"));
		String capture = Files.readString(Path.of("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java"));
		assertTrue(source.contains("int cameraRelation,"),
			"cloud receipt must retain Frozen's relative-camera mesh input");
		assertTrue(source.contains("int centerCellX,") && source.contains("int centerCellZ,"),
			"cloud receipt must retain Frozen's wrapped cloud-cell origin");
		assertTrue(source.contains("float offsetX,") && source.contains("float verticalOffset,") && source.contains("float offsetZ"),
			"cloud receipt must retain Frozen's camera-relative mesh offsets");
		assertTrue(source.contains("float fogCloudsEnd"),
			"cloud receipt must retain the cloud-only fog range consumed by the Rust material shader");
		assertTrue(capture.contains("\\\"cameraRelation\\\""),
			"deterministic capture must serialize the semantic receipt for cross-repository comparison");
		assertTrue(source.contains("int cloudColorArgb,"),
			"cloud receipt must retain the exact CloudRenderer colour input");
		assertTrue(capture.contains("\\\"cloudColorArgb\\\""),
			"deterministic capture must serialize the cloud colour input for paired diagnosis");
	}

	@Test
	void cloudFacesPreserveFrozenFaceColorAlphaAfterOpaqueCloudInfo() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"));
		int emitter = source.indexOf("private static void appendCloudCellFaces(");
		int color = source.indexOf("ARGB.alpha(cloudColorArgb) * 0.8F", emitter);
		int firstFace = source.indexOf("appendCloudFace(quads, fingerprint, dx, dz", color);
		assertTrue(color > emitter && firstFace > color,
			"Rust cloud faces must retain Frozen's faceColors alpha after opaque CloudInfo input");
		assertFalse(source.substring(emitter, firstFace).contains("ARGB.opaque(cloudColorArgb)"),
			"Rust cloud semantics must not discard Frozen's per-face alpha multiplier");
	}

	@Test
	void fastCloudsUseFrozenSodiumTopFaceColourFlag() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"));
		int emitter = source.indexOf("private static void appendCloudCellFaces(");
		int fastBranch = source.indexOf("if (!fancy)", emitter);
		int returnStatement = source.indexOf("return;", fastBranch);
		String fastBranchBody = source.substring(fastBranch, returnStatement);

		assertTrue(fastBranchBody.contains("cloudFaceColor(colorArgb, 1.0F)"),
			"Frozen Sodium encodes its fast-cloud DOWN face with FLAG_USE_TOP_COLOR");
		assertFalse(fastBranchBody.contains("false, colorArgb,"),
			"fast clouds must not accidentally use Frozen's darker bottom-face colour");
		assertTrue(fastBranchBody.contains("CULL_NONE"),
			"Frozen's FLAT_CLOUDS pipeline disables culling for the fast-cloud face");
	}

	@Test
	void captureFingerprintsTheExactFaceColoursSubmittedToVulkanicGal() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"));
		String capture = Files.readString(Path.of("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java"));
		assertTrue(source.contains("private static long cloudFaceColorFingerprint("),
			"cloud diagnosis must hash the colours in the actual semantic quad payload, not reconstructed GPU state");
		assertTrue(source.contains("cloudFaceColorFingerprint(quads)"),
			"each non-empty cloud receipt must describe the submitted face-colour stream");
		assertTrue(source.contains("long faceColorFingerprint"),
			"the explicit cloud receipt must retain its face-colour identity");
		assertTrue(capture.contains("\\\"faceColorFingerprint\\\""),
			"deterministic captures must serialize the face-colour identity for Frozen parity diagnosis");
	}
}
