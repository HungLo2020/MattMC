package net.minecraft.client.dev;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GraphicsAuditEntityPreviewFixtureTest {
    @org.junit.jupiter.api.BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void modeIsExplicitAndUnknownValuesFailClosed() {
        String key = "mattmc.dev.graphicsAuditEntityPreview";
        String previous = System.getProperty(key);
        try {
            System.clearProperty(key);
            assertEquals("", GraphicsAuditEntityPreviewFixture.mode());
            assertFalse(GraphicsAuditEntityPreviewFixture.requested());
            System.setProperty(key, "horse");
            assertEquals("horse", GraphicsAuditEntityPreviewFixture.mode());
            assertTrue(GraphicsAuditEntityPreviewFixture.requested());
            assertFalse(GraphicsAuditEntityPreviewFixture.smithingMode());
            System.setProperty(key, "horse-black");
            assertEquals("horse-black", GraphicsAuditEntityPreviewFixture.mode());
            assertTrue(GraphicsAuditEntityPreviewFixture.horseMode());
            System.setProperty(key, "horse-brown");
            assertEquals("horse-brown", GraphicsAuditEntityPreviewFixture.mode());
            assertTrue(GraphicsAuditEntityPreviewFixture.horseMode());
            System.setProperty(key, "horse-creamy");
            assertEquals("horse-creamy", GraphicsAuditEntityPreviewFixture.mode());
            assertTrue(GraphicsAuditEntityPreviewFixture.horseMode());
            System.setProperty(key, "horse-chestnut");
            assertEquals("horse-chestnut", GraphicsAuditEntityPreviewFixture.mode());
            assertTrue(GraphicsAuditEntityPreviewFixture.horseMode());
            System.setProperty(key, "horse-gray");
            assertEquals("horse-gray", GraphicsAuditEntityPreviewFixture.mode());
            assertTrue(GraphicsAuditEntityPreviewFixture.horseMode());
            System.setProperty(key, "horse-dark-brown");
            assertEquals("horse-dark-brown", GraphicsAuditEntityPreviewFixture.mode());
            assertTrue(GraphicsAuditEntityPreviewFixture.horseMode());
            System.setProperty(key, "horse-marking-white");
            assertEquals("horse-marking-white", GraphicsAuditEntityPreviewFixture.mode());
            assertTrue(GraphicsAuditEntityPreviewFixture.horseMode());
            System.setProperty(key, "horse-marking-white-field");
            assertEquals("horse-marking-white-field", GraphicsAuditEntityPreviewFixture.mode());
            assertTrue(GraphicsAuditEntityPreviewFixture.horseMode());
            System.setProperty(key, "horse-marking-black-dots");
            assertEquals("horse-marking-black-dots", GraphicsAuditEntityPreviewFixture.mode());
            assertTrue(GraphicsAuditEntityPreviewFixture.horseMode());
            System.setProperty(key, "skeleton-horse-saddled");
            assertEquals("skeleton-horse-saddled", GraphicsAuditEntityPreviewFixture.mode());
            assertTrue(GraphicsAuditEntityPreviewFixture.horseMode());
            System.setProperty(key, "skeleton-horse-baby-saddled");
            assertEquals("skeleton-horse-baby-saddled", GraphicsAuditEntityPreviewFixture.mode());
            assertTrue(GraphicsAuditEntityPreviewFixture.horseMode());
            System.setProperty(key, "zombie-horse-saddled");
            assertEquals("zombie-horse-saddled", GraphicsAuditEntityPreviewFixture.mode());
            assertTrue(GraphicsAuditEntityPreviewFixture.horseMode());
            System.setProperty(key, "zombie-horse-baby-saddled");
            assertEquals("zombie-horse-baby-saddled", GraphicsAuditEntityPreviewFixture.mode());
            assertTrue(GraphicsAuditEntityPreviewFixture.horseMode());
            System.setProperty(key, "horse-baby-equipped");
            assertEquals("horse-baby-equipped", GraphicsAuditEntityPreviewFixture.mode());
            assertTrue(GraphicsAuditEntityPreviewFixture.horseMode());
            assertFalse(GraphicsAuditEntityPreviewFixture.smithingMode());
            System.setProperty(key, "horse-baby-iron-equipped");
            assertEquals("horse-baby-iron-equipped", GraphicsAuditEntityPreviewFixture.mode());
            assertTrue(GraphicsAuditEntityPreviewFixture.horseMode());
            assertFalse(GraphicsAuditEntityPreviewFixture.smithingMode());
            System.setProperty(key, "horse-baby-gold-equipped");
            assertEquals("horse-baby-gold-equipped", GraphicsAuditEntityPreviewFixture.mode());
            System.setProperty(key, "horse-baby-copper-equipped");
            assertEquals("horse-baby-copper-equipped", GraphicsAuditEntityPreviewFixture.mode());
            System.setProperty(key, "horse-baby-leather-equipped");
            assertEquals("horse-baby-leather-equipped", GraphicsAuditEntityPreviewFixture.mode());
            System.setProperty(key, "horse-baby-dyed-leather-equipped");
            assertEquals("horse-baby-dyed-leather-equipped", GraphicsAuditEntityPreviewFixture.mode());
            assertTrue(GraphicsAuditEntityPreviewFixture.horseMode());
            assertFalse(GraphicsAuditEntityPreviewFixture.smithingMode());
            System.setProperty(key, "horse-iron-equipped");
            assertEquals("horse-iron-equipped", GraphicsAuditEntityPreviewFixture.mode());
            assertTrue(GraphicsAuditEntityPreviewFixture.horseMode());
            System.setProperty(key, "horse-gold-equipped");
            assertEquals("horse-gold-equipped", GraphicsAuditEntityPreviewFixture.mode());
            assertTrue(GraphicsAuditEntityPreviewFixture.horseMode());
            System.setProperty(key, "horse-copper-equipped");
            assertEquals("horse-copper-equipped", GraphicsAuditEntityPreviewFixture.mode());
            assertTrue(GraphicsAuditEntityPreviewFixture.horseMode());
            System.setProperty(key, "horse-leather-equipped");
            assertEquals("horse-leather-equipped", GraphicsAuditEntityPreviewFixture.mode());
            System.setProperty(key, "horse-dyed-leather-equipped");
            assertEquals("horse-dyed-leather-equipped", GraphicsAuditEntityPreviewFixture.mode());
            assertTrue(GraphicsAuditEntityPreviewFixture.horseMode());
            System.setProperty(key, "horse-marked-equipped");
            assertEquals("horse-marked-equipped", GraphicsAuditEntityPreviewFixture.mode());
            assertTrue(GraphicsAuditEntityPreviewFixture.horseMode());
            assertFalse(GraphicsAuditEntityPreviewFixture.smithingMode());
            System.setProperty(key, "horse-baby-marked-equipped");
            assertEquals("horse-baby-marked-equipped", GraphicsAuditEntityPreviewFixture.mode());
            assertTrue(GraphicsAuditEntityPreviewFixture.horseMode());
            assertFalse(GraphicsAuditEntityPreviewFixture.smithingMode());
            System.setProperty(key, "donkey-chested-equipped");
            assertEquals("donkey-chested-equipped", GraphicsAuditEntityPreviewFixture.mode());
            System.setProperty(key, "donkey-baby-chested-equipped");
            assertEquals("donkey-baby-chested-equipped", GraphicsAuditEntityPreviewFixture.mode());
            assertTrue(GraphicsAuditEntityPreviewFixture.horseMode());
            assertFalse(GraphicsAuditEntityPreviewFixture.smithingMode());
            System.setProperty(key, "mule-chested-equipped");
            assertEquals("mule-chested-equipped", GraphicsAuditEntityPreviewFixture.mode());
            assertTrue(GraphicsAuditEntityPreviewFixture.horseMode());
            assertFalse(GraphicsAuditEntityPreviewFixture.smithingMode());
            System.setProperty(key, "mule-baby-chested-equipped");
            assertEquals("mule-baby-chested-equipped", GraphicsAuditEntityPreviewFixture.mode());
            assertTrue(GraphicsAuditEntityPreviewFixture.horseMode());
            assertFalse(GraphicsAuditEntityPreviewFixture.smithingMode());
            System.setProperty(key, "llama-chested-blue-carpet");
            assertEquals("llama-chested-blue-carpet", GraphicsAuditEntityPreviewFixture.mode());
            assertTrue(GraphicsAuditEntityPreviewFixture.horseMode());
            assertFalse(GraphicsAuditEntityPreviewFixture.smithingMode());
            System.setProperty(key, "llama-baby-chested-blue-carpet");
            assertEquals("llama-baby-chested-blue-carpet", GraphicsAuditEntityPreviewFixture.mode());
            assertTrue(GraphicsAuditEntityPreviewFixture.horseMode());
            assertFalse(GraphicsAuditEntityPreviewFixture.smithingMode());
            System.setProperty(key, "horse-baby-marked");
            assertEquals("horse-baby-marked", GraphicsAuditEntityPreviewFixture.mode());
            assertTrue(GraphicsAuditEntityPreviewFixture.horseMode());
            assertFalse(GraphicsAuditEntityPreviewFixture.smithingMode());
            System.setProperty(key, "horse-marked");
            assertEquals("horse-marked", GraphicsAuditEntityPreviewFixture.mode());
            assertTrue(GraphicsAuditEntityPreviewFixture.horseMode());
            assertFalse(GraphicsAuditEntityPreviewFixture.smithingMode());
            assertTrue(GraphicsAuditEntityPreviewFixture.horseMode());
            System.setProperty(key, "horse-equipped");
            assertEquals("horse-equipped", GraphicsAuditEntityPreviewFixture.mode());
            assertTrue(GraphicsAuditEntityPreviewFixture.horseMode());
            assertFalse(GraphicsAuditEntityPreviewFixture.smithingMode());
            System.setProperty(key, "horse-baby");
            assertEquals("horse-baby", GraphicsAuditEntityPreviewFixture.mode());
            assertTrue(GraphicsAuditEntityPreviewFixture.horseMode());
            assertFalse(GraphicsAuditEntityPreviewFixture.smithingMode());
            System.setProperty(key, "smithing");
            assertEquals("smithing", GraphicsAuditEntityPreviewFixture.mode());
            assertTrue(GraphicsAuditEntityPreviewFixture.requested());
            assertTrue(GraphicsAuditEntityPreviewFixture.smithingMode());
            assertFalse(GraphicsAuditEntityPreviewFixture.horseMode());
            System.setProperty(key, "smithing-netherite-chestplate");
            assertEquals("smithing-netherite-chestplate", GraphicsAuditEntityPreviewFixture.mode());
            assertTrue(GraphicsAuditEntityPreviewFixture.requested());
            assertTrue(GraphicsAuditEntityPreviewFixture.smithingMode());
            for (String value : new String[] {"true", "player", "HORSE"}) {
                System.setProperty(key, value);
                assertThrows(IllegalArgumentException.class, GraphicsAuditEntityPreviewFixture::mode);
            }
        } finally {
            if (previous == null) System.clearProperty(key); else System.setProperty(key, previous);
        }
    }

    @Test
    void smithingPreviewEnablesTheSameBoundedObservationPath() {
        String fixture = "mattmc.dev.graphicsAuditEntityPreview";
        String timing = "mattmc.dev.equipmentFoilTiming";
        String oldFixture = System.getProperty(fixture), oldTiming = System.getProperty(timing);
        try {
            System.setProperty(fixture, "smithing");
            System.setProperty(timing, "true");
            GraphicsAuditInventoryPreviewInputs.beginFrame(11);
            var state = new net.minecraft.client.renderer.entity.state.ArmorStandRenderState();
            state.showArms = true;
            state.showBasePlate = false;
            GraphicsAuditInventoryPreviewInputs.observeScreenMouse("background", 213, 120, 213, 120);
            GraphicsAuditInventoryPreviewInputs.observeScreenMouse("render-return", 213, 120, 213, 120);
            GraphicsAuditInventoryPreviewInputs.observe(state, 25, new org.joml.Vector3f(),
                new org.joml.Quaternionf(), null, 1, 2, 41, 62);
            var receipt = GraphicsAuditInventoryPreviewInputs.snapshot();
            assertTrue(receipt.get("enabled").getAsBoolean());
            assertTrue(receipt.get("complete").getAsBoolean());
            assertEquals(1, receipt.getAsJsonArray("observations").size());
            var armorStand = receipt.getAsJsonArray("observations").get(0).getAsJsonObject().getAsJsonObject("armorStand");
            assertTrue(armorStand.get("showArms").getAsBoolean());
            assertFalse(armorStand.get("showBasePlate").getAsBoolean());
        } finally {
            GraphicsAuditInventoryPreviewInputs.beginFrame(0);
            GraphicsAuditInventoryPreviewInputs.resetReadiness();
            if (oldFixture == null) System.clearProperty(fixture); else System.setProperty(fixture, oldFixture);
            if (oldTiming == null) System.clearProperty(timing); else System.setProperty(timing, oldTiming);
        }
    }

    @Test
    void equippedSmithingPreviewEnablesEquipmentGeometryObservation() {
        String fixture="mattmc.dev.graphicsAuditEntityPreview", timing="mattmc.dev.equipmentFoilTiming";
        String oldFixture=System.getProperty(fixture), oldTiming=System.getProperty(timing);
        try {
            System.setProperty(fixture,"smithing-netherite-chestplate");
            System.setProperty(timing,"true");
            GraphicsAuditEquipmentGeometry.beginFrame(13);
            assertTrue(GraphicsAuditEquipmentGeometry.snapshot().get("enabled").getAsBoolean());
        } finally {
            GraphicsAuditEquipmentGeometry.beginFrame(0);
            if(oldFixture==null)System.clearProperty(fixture);else System.setProperty(fixture,oldFixture);
            if(oldTiming==null)System.clearProperty(timing);else System.setProperty(timing,oldTiming);
        }
    }

    @Test
    void equippedHorsePreviewEnablesEquipmentGeometryObservation() {
        String fixture="mattmc.dev.graphicsAuditEntityPreview", timing="mattmc.dev.equipmentFoilTiming";
        String oldFixture=System.getProperty(fixture), oldTiming=System.getProperty(timing);
        try {
            System.setProperty(timing,"true");
            for (String fixtureMode : new String[] {"horse-equipped", "horse-iron-equipped", "horse-gold-equipped", "horse-copper-equipped", "horse-leather-equipped", "horse-dyed-leather-equipped", "horse-baby-iron-equipped", "horse-baby-gold-equipped", "horse-baby-copper-equipped", "horse-baby-leather-equipped", "horse-baby-dyed-leather-equipped", "donkey-chested-equipped", "donkey-baby-chested-equipped", "mule-chested-equipped", "mule-baby-chested-equipped", "llama-chested-blue-carpet", "llama-baby-chested-blue-carpet"}) {
                System.setProperty(fixture,fixtureMode);
                GraphicsAuditEquipmentGeometry.beginFrame(14);
                assertTrue(GraphicsAuditEquipmentGeometry.snapshot().get("enabled").getAsBoolean());
                var timingReceipt=com.google.gson.JsonParser.parseString(GraphicsAuditEquipmentFoilTiming.snapshot()).getAsJsonObject();
                assertTrue(timingReceipt.has("modelGeometry"));
                assertTrue(timingReceipt.getAsJsonObject("modelGeometry").get("enabled").getAsBoolean());
            }
        } finally {
            GraphicsAuditEquipmentGeometry.beginFrame(0);
            if(oldFixture==null)System.clearProperty(fixture);else System.setProperty(fixture,oldFixture);
            if(oldTiming==null)System.clearProperty(timing);else System.setProperty(timing,oldTiming);
        }
    }

    @Test
    void babyHorsePreviewEnablesBaseGeometryObservation() {
        String fixture="mattmc.dev.graphicsAuditEntityPreview", timing="mattmc.dev.equipmentFoilTiming";
        String oldFixture=System.getProperty(fixture), oldTiming=System.getProperty(timing);
        try {
            System.setProperty(fixture,"horse-baby");
            System.setProperty(timing,"true");
            GraphicsAuditEquipmentGeometry.beginFrame(15);
            assertTrue(GraphicsAuditEquipmentGeometry.snapshot().get("enabled").getAsBoolean());
            var timingReceipt=com.google.gson.JsonParser.parseString(GraphicsAuditEquipmentFoilTiming.snapshot()).getAsJsonObject();
            assertTrue(timingReceipt.has("modelGeometry"));
            assertTrue(timingReceipt.getAsJsonObject("modelGeometry").get("enabled").getAsBoolean());
        } finally {
            GraphicsAuditEquipmentGeometry.beginFrame(0);
            if(oldFixture==null)System.clearProperty(fixture);else System.setProperty(fixture,oldFixture);
            if(oldTiming==null)System.clearProperty(timing);else System.setProperty(timing,oldTiming);
        }
    }

    @Test
    void markedHorsePreviewEnablesBaseAndLayerGeometryObservation() {
        String fixture="mattmc.dev.graphicsAuditEntityPreview", timing="mattmc.dev.equipmentFoilTiming";
        String oldFixture=System.getProperty(fixture), oldTiming=System.getProperty(timing);
        try {
            System.setProperty(fixture,"horse-marked");
            System.setProperty(timing,"true");
            GraphicsAuditEquipmentGeometry.beginFrame(16);
            assertTrue(GraphicsAuditEquipmentGeometry.snapshot().get("enabled").getAsBoolean());
            var timingReceipt=com.google.gson.JsonParser.parseString(GraphicsAuditEquipmentFoilTiming.snapshot()).getAsJsonObject();
            assertTrue(timingReceipt.has("modelGeometry"));
            assertTrue(timingReceipt.getAsJsonObject("modelGeometry").get("enabled").getAsBoolean());
        } finally {
            GraphicsAuditEquipmentGeometry.beginFrame(0);
            if(oldFixture==null)System.clearProperty(fixture);else System.setProperty(fixture,oldFixture);
            if(oldTiming==null)System.clearProperty(timing);else System.setProperty(timing,oldTiming);
        }
    }

    @Test
    void blackHorsePreviewEnablesBaseGeometryObservation() {
        String fixture="mattmc.dev.graphicsAuditEntityPreview", timing="mattmc.dev.equipmentFoilTiming";
        String oldFixture=System.getProperty(fixture), oldTiming=System.getProperty(timing);
        try {
            System.setProperty(fixture,"horse-black");
            System.setProperty(timing,"true");
            GraphicsAuditEquipmentGeometry.beginFrame(17);
            assertTrue(GraphicsAuditEquipmentGeometry.snapshot().get("enabled").getAsBoolean());
            var timingReceipt=com.google.gson.JsonParser.parseString(GraphicsAuditEquipmentFoilTiming.snapshot()).getAsJsonObject();
            assertTrue(timingReceipt.has("modelGeometry"));
            assertTrue(timingReceipt.getAsJsonObject("modelGeometry").get("enabled").getAsBoolean());
        } finally {
            GraphicsAuditEquipmentGeometry.beginFrame(0);
            if(oldFixture==null)System.clearProperty(fixture);else System.setProperty(fixture,oldFixture);
            if(oldTiming==null)System.clearProperty(timing);else System.setProperty(timing,oldTiming);
        }
    }

    @Test
    void brownHorsePreviewEnablesBaseGeometryObservation() {
        String fixture="mattmc.dev.graphicsAuditEntityPreview", timing="mattmc.dev.equipmentFoilTiming";
        String oldFixture=System.getProperty(fixture), oldTiming=System.getProperty(timing);
        try {
            System.setProperty(fixture,"horse-brown");System.setProperty(timing,"true");
            GraphicsAuditEquipmentGeometry.beginFrame(22);
            assertTrue(GraphicsAuditEquipmentGeometry.snapshot().get("enabled").getAsBoolean());
        } finally {
            GraphicsAuditEquipmentGeometry.beginFrame(0);
            if(oldFixture==null)System.clearProperty(fixture);else System.setProperty(fixture,oldFixture);
            if(oldTiming==null)System.clearProperty(timing);else System.setProperty(timing,oldTiming);
        }
    }

    @Test
    void creamyHorsePreviewEnablesBaseGeometryObservation() {
        String fixture="mattmc.dev.graphicsAuditEntityPreview", timing="mattmc.dev.equipmentFoilTiming";
        String oldFixture=System.getProperty(fixture), oldTiming=System.getProperty(timing);
        try {
            System.setProperty(fixture,"horse-creamy");System.setProperty(timing,"true");
            GraphicsAuditEquipmentGeometry.beginFrame(23);
            assertTrue(GraphicsAuditEquipmentGeometry.snapshot().get("enabled").getAsBoolean());
        } finally {
            GraphicsAuditEquipmentGeometry.beginFrame(0);
            if(oldFixture==null)System.clearProperty(fixture);else System.setProperty(fixture,oldFixture);
            if(oldTiming==null)System.clearProperty(timing);else System.setProperty(timing,oldTiming);
        }
    }

    @Test
    void chestnutHorsePreviewEnablesBaseGeometryObservation() {
        String fixture="mattmc.dev.graphicsAuditEntityPreview", timing="mattmc.dev.equipmentFoilTiming";
        String oldFixture=System.getProperty(fixture), oldTiming=System.getProperty(timing);
        try {
            System.setProperty(fixture,"horse-chestnut");System.setProperty(timing,"true");
            GraphicsAuditEquipmentGeometry.beginFrame(24);
            assertTrue(GraphicsAuditEquipmentGeometry.snapshot().get("enabled").getAsBoolean());
        } finally {
            GraphicsAuditEquipmentGeometry.beginFrame(0);
            if(oldFixture==null)System.clearProperty(fixture);else System.setProperty(fixture,oldFixture);
            if(oldTiming==null)System.clearProperty(timing);else System.setProperty(timing,oldTiming);
        }
    }

    @Test
    void grayHorsePreviewEnablesBaseGeometryObservation() {
        String fixture="mattmc.dev.graphicsAuditEntityPreview", timing="mattmc.dev.equipmentFoilTiming";
        String oldFixture=System.getProperty(fixture), oldTiming=System.getProperty(timing);
        try {
            System.setProperty(fixture,"horse-gray");System.setProperty(timing,"true");
            GraphicsAuditEquipmentGeometry.beginFrame(25);
            assertTrue(GraphicsAuditEquipmentGeometry.snapshot().get("enabled").getAsBoolean());
        } finally {
            GraphicsAuditEquipmentGeometry.beginFrame(0);
            if(oldFixture==null)System.clearProperty(fixture);else System.setProperty(fixture,oldFixture);
            if(oldTiming==null)System.clearProperty(timing);else System.setProperty(timing,oldTiming);
        }
    }

    @Test
    void darkBrownHorsePreviewEnablesBaseGeometryObservation() {
        String fixture="mattmc.dev.graphicsAuditEntityPreview", timing="mattmc.dev.equipmentFoilTiming";
        String oldFixture=System.getProperty(fixture), oldTiming=System.getProperty(timing);
        try {
            System.setProperty(fixture,"horse-dark-brown");System.setProperty(timing,"true");
            GraphicsAuditEquipmentGeometry.beginFrame(26);
            assertTrue(GraphicsAuditEquipmentGeometry.snapshot().get("enabled").getAsBoolean());
        } finally {
            GraphicsAuditEquipmentGeometry.beginFrame(0);
            if(oldFixture==null)System.clearProperty(fixture);else System.setProperty(fixture,oldFixture);
            if(oldTiming==null)System.clearProperty(timing);else System.setProperty(timing,oldTiming);
        }
    }

    @Test
    void whiteMarkingHorsePreviewEnablesBaseAndLayerGeometryObservation() {
        String fixture="mattmc.dev.graphicsAuditEntityPreview", timing="mattmc.dev.equipmentFoilTiming";
        String oldFixture=System.getProperty(fixture), oldTiming=System.getProperty(timing);
        try {
            System.setProperty(fixture,"horse-marking-white");System.setProperty(timing,"true");
            GraphicsAuditEquipmentGeometry.beginFrame(27);
            assertTrue(GraphicsAuditEquipmentGeometry.snapshot().get("enabled").getAsBoolean());
        } finally {
            GraphicsAuditEquipmentGeometry.beginFrame(0);
            if(oldFixture==null)System.clearProperty(fixture);else System.setProperty(fixture,oldFixture);
            if(oldTiming==null)System.clearProperty(timing);else System.setProperty(timing,oldTiming);
        }
    }

    @Test
    void whiteFieldHorsePreviewEnablesBaseAndLayerGeometryObservation() {
        assertMarkedGeometryEnabled("horse-marking-white-field",28);
    }

    @Test
    void blackDotsHorsePreviewEnablesBaseAndLayerGeometryObservation() {
        assertMarkedGeometryEnabled("horse-marking-black-dots",29);
    }

    private static void assertMarkedGeometryEnabled(String mode, long frame) {
        String fixture="mattmc.dev.graphicsAuditEntityPreview", timing="mattmc.dev.equipmentFoilTiming";
        String oldFixture=System.getProperty(fixture), oldTiming=System.getProperty(timing);
        try {
            System.setProperty(fixture,mode);System.setProperty(timing,"true");
            GraphicsAuditEquipmentGeometry.beginFrame(frame);
            assertTrue(GraphicsAuditEquipmentGeometry.snapshot().get("enabled").getAsBoolean());
        } finally {
            GraphicsAuditEquipmentGeometry.beginFrame(0);
            if(oldFixture==null)System.clearProperty(fixture);else System.setProperty(fixture,oldFixture);
            if(oldTiming==null)System.clearProperty(timing);else System.setProperty(timing,oldTiming);
        }
    }

    @Test
    void saddledSkeletonHorsePreviewEnablesBaseAndSaddleGeometryObservation() {
        String fixture="mattmc.dev.graphicsAuditEntityPreview", timing="mattmc.dev.equipmentFoilTiming";
        String oldFixture=System.getProperty(fixture), oldTiming=System.getProperty(timing);
        try {
            System.setProperty(fixture,"skeleton-horse-saddled");
            System.setProperty(timing,"true");
            GraphicsAuditEquipmentGeometry.beginFrame(18);
            assertTrue(GraphicsAuditEquipmentGeometry.snapshot().get("enabled").getAsBoolean());
            var timingReceipt=com.google.gson.JsonParser.parseString(GraphicsAuditEquipmentFoilTiming.snapshot()).getAsJsonObject();
            assertTrue(timingReceipt.has("modelGeometry"));
            assertTrue(timingReceipt.getAsJsonObject("modelGeometry").get("enabled").getAsBoolean());
        } finally {
            GraphicsAuditEquipmentGeometry.beginFrame(0);
            if(oldFixture==null)System.clearProperty(fixture);else System.setProperty(fixture,oldFixture);
            if(oldTiming==null)System.clearProperty(timing);else System.setProperty(timing,oldTiming);
        }
    }

    @Test
    void saddledBabySkeletonHorsePreviewEnablesBabyGeometryObservation() {
        String fixture="mattmc.dev.graphicsAuditEntityPreview", timing="mattmc.dev.equipmentFoilTiming";
        String oldFixture=System.getProperty(fixture), oldTiming=System.getProperty(timing);
        try {
            System.setProperty(fixture,"skeleton-horse-baby-saddled");
            System.setProperty(timing,"true");
            GraphicsAuditEquipmentGeometry.beginFrame(19);
            assertTrue(GraphicsAuditEquipmentGeometry.snapshot().get("enabled").getAsBoolean());
            var timingReceipt=com.google.gson.JsonParser.parseString(GraphicsAuditEquipmentFoilTiming.snapshot()).getAsJsonObject();
            assertTrue(timingReceipt.has("modelGeometry"));
            assertTrue(timingReceipt.getAsJsonObject("modelGeometry").get("enabled").getAsBoolean());
        } finally {
            GraphicsAuditEquipmentGeometry.beginFrame(0);
            if(oldFixture==null)System.clearProperty(fixture);else System.setProperty(fixture,oldFixture);
            if(oldTiming==null)System.clearProperty(timing);else System.setProperty(timing,oldTiming);
        }
    }

    @Test
    void saddledZombieHorsePreviewEnablesBaseAndSaddleGeometryObservation() {
        String fixture="mattmc.dev.graphicsAuditEntityPreview", timing="mattmc.dev.equipmentFoilTiming";
        String oldFixture=System.getProperty(fixture), oldTiming=System.getProperty(timing);
        try {
            System.setProperty(fixture,"zombie-horse-saddled");
            System.setProperty(timing,"true");
            GraphicsAuditEquipmentGeometry.beginFrame(20);
            assertTrue(GraphicsAuditEquipmentGeometry.snapshot().get("enabled").getAsBoolean());
            var timingReceipt=com.google.gson.JsonParser.parseString(GraphicsAuditEquipmentFoilTiming.snapshot()).getAsJsonObject();
            assertTrue(timingReceipt.has("modelGeometry"));
            assertTrue(timingReceipt.getAsJsonObject("modelGeometry").get("enabled").getAsBoolean());
        } finally {
            GraphicsAuditEquipmentGeometry.beginFrame(0);
            if(oldFixture==null)System.clearProperty(fixture);else System.setProperty(fixture,oldFixture);
            if(oldTiming==null)System.clearProperty(timing);else System.setProperty(timing,oldTiming);
        }
    }

    @Test
    void saddledBabyZombieHorsePreviewEnablesBabyGeometryObservation() {
        String fixture="mattmc.dev.graphicsAuditEntityPreview", timing="mattmc.dev.equipmentFoilTiming";
        String oldFixture=System.getProperty(fixture), oldTiming=System.getProperty(timing);
        try {
            System.setProperty(fixture,"zombie-horse-baby-saddled");
            System.setProperty(timing,"true");
            GraphicsAuditEquipmentGeometry.beginFrame(21);
            assertTrue(GraphicsAuditEquipmentGeometry.snapshot().get("enabled").getAsBoolean());
        } finally {
            GraphicsAuditEquipmentGeometry.beginFrame(0);
            if(oldFixture==null)System.clearProperty(fixture);else System.setProperty(fixture,oldFixture);
            if(oldTiming==null)System.clearProperty(timing);else System.setProperty(timing,oldTiming);
        }
    }

    @Test
    void horsePreviewEnablesBoundedInputObservation() {
        String fixture = "mattmc.dev.graphicsAuditEntityPreview";
        String timing = "mattmc.dev.equipmentFoilTiming";
        String oldFixture = System.getProperty(fixture), oldTiming = System.getProperty(timing);
        try {
            System.setProperty(fixture, "horse");
            System.setProperty(timing, "true");
            GraphicsAuditInventoryPreviewInputs.beginFrame(9);
            var state = new net.minecraft.client.renderer.entity.state.LivingEntityRenderState();
            GraphicsAuditInventoryPreviewInputs.observeScreenMouse("background", 213, 120, 213, 120);
            GraphicsAuditInventoryPreviewInputs.observeScreenMouse("render-return", 213, 120, 213, 120);
            GraphicsAuditInventoryPreviewInputs.observe(state, 17, new org.joml.Vector3f(),
                new org.joml.Quaternionf(), new org.joml.Quaternionf(), 1, 2, 53, 54);
            var receipt = GraphicsAuditInventoryPreviewInputs.snapshot();
            assertTrue(receipt.get("enabled").getAsBoolean());
            assertTrue(receipt.get("complete").getAsBoolean());
            assertEquals(1, receipt.getAsJsonArray("observations").size());
        } finally {
            GraphicsAuditInventoryPreviewInputs.beginFrame(0);
            GraphicsAuditInventoryPreviewInputs.resetReadiness();
            if (oldFixture == null) System.clearProperty(fixture); else System.setProperty(fixture, oldFixture);
            if (oldTiming == null) System.clearProperty(timing); else System.setProperty(timing, oldTiming);
        }
    }
}
