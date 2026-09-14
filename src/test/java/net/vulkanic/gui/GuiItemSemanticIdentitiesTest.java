package net.vulkanic.gui;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GuiItemSemanticIdentitiesTest {
    @Test void equalityNotHashCollisionsNamesModelsAndReloadNeverReusesNames() {
        GuiItemSemanticIdentities.clear();
        try {
            long first = GuiItemSemanticIdentities.identityOrZero(List.of("Aa"));
            assertEquals(first, GuiItemSemanticIdentities.identityOrZero(List.of("Aa")));
            assertEquals("Aa".hashCode(), "BB".hashCode());
            assertNotEquals(first, GuiItemSemanticIdentities.identityOrZero(List.of("BB")));
            GuiItemSemanticIdentities.clear();
            assertNotEquals(first, GuiItemSemanticIdentities.identityOrZero(List.of("Aa")));
        } finally { GuiItemSemanticIdentities.clear(); }
    }

    @Test void capacityFallsBackWithoutPublishingAndExistingNamesRemainUsable() {
        GuiItemSemanticIdentities.clear();
        try {
            long first = GuiItemSemanticIdentities.identityOrZero(List.of(0));
            for (int i = 1; i < 63; i++) assertNotEquals(0, GuiItemSemanticIdentities.identityOrZero(List.of(i)));
            assertEquals(0, GuiItemSemanticIdentities.identityOrZero(List.of(63)));
            assertEquals(first, GuiItemSemanticIdentities.identityOrZero(List.of(0)));
        } finally { GuiItemSemanticIdentities.clear(); }
    }
}
