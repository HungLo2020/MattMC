package net.vulkanic.world;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ordinary play must build legacy (Frozen) DH render data; only exact-atlas capture keeps semantics. */
class DistantHorizonsSemanticPreservationTest {
    @AfterEach void clear() {
        System.clearProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY);
        System.clearProperty(DistantHorizonsSemanticCollector.LEGACY_OBSERVATION_PROPERTY);
        System.clearProperty(DistantHorizonsSemanticCollector.FORCE_SEMANTIC_PRESERVATION_PROPERTY);
    }

    @Test void ordinaryPlayDoesNotPreserveSemanticMaterials() {
        assertFalse(DistantHorizonsSemanticCollector.semanticMaterialPreservationRequired());
    }

    @Test void exactAtlasCaptureAndLegacyObservationPreserveThem() {
        System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
        assertTrue(DistantHorizonsSemanticCollector.semanticMaterialPreservationRequired());
        System.clearProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY);
        System.setProperty(DistantHorizonsSemanticCollector.LEGACY_OBSERVATION_PROPERTY, "true");
        assertTrue(DistantHorizonsSemanticCollector.semanticMaterialPreservationRequired());
    }

    @Test void devSwitchRestoresThePreviousBehaviour() {
        System.setProperty(DistantHorizonsSemanticCollector.FORCE_SEMANTIC_PRESERVATION_PROPERTY, "true");
        assertTrue(DistantHorizonsSemanticCollector.semanticMaterialPreservationRequired());
    }
}
