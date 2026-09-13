package net.minecraft.client.dev;

import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterials;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import net.minecraft.world.item.equipment.trim.TrimPatterns;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GraphicsAuditEquipmentFixtureTest {
    private static HolderLookup.Provider registries;

    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        registries = new net.minecraft.core.RegistrySetBuilder()
            .add(Registries.TRIM_PATTERN, TrimPatterns::bootstrap)
            .add(Registries.TRIM_MATERIAL, TrimMaterials::bootstrap)
            .build(net.minecraft.core.RegistryAccess.EMPTY);
    }

    @Test
    void ageSelectionRequiresFixtureAndReplicatedAgeMatch() {
        String modeKey = "mattmc.dev.graphicsAuditEquipment";
        String ageKey = "mattmc.dev.graphicsAuditEquipmentAge";
        String oldMode = System.getProperty(modeKey), oldAge = System.getProperty(ageKey);
        try {
            System.clearProperty(modeKey);
            System.setProperty(ageKey, "baby");
            assertThrows(IllegalArgumentException.class, GraphicsAuditEquipmentFixture::ageVariant);
            System.setProperty(modeKey, "foil");
            assertEquals("baby", GraphicsAuditEquipmentFixture.ageVariant());
            assertTrue(GraphicsAuditEquipmentFixture.ageMatches(true));
            assertFalse(GraphicsAuditEquipmentFixture.ageMatches(false));
            System.clearProperty(ageKey);
            assertTrue(GraphicsAuditEquipmentFixture.ageMatches(false));
            assertFalse(GraphicsAuditEquipmentFixture.ageMatches(true));
            for (String invalid : new String[] {"adult", "true", "unknown"}) {
                System.setProperty(ageKey, invalid);
                assertThrows(IllegalArgumentException.class, GraphicsAuditEquipmentFixture::ageVariant);
            }
        } finally {
            if (oldMode == null) System.clearProperty(modeKey); else System.setProperty(modeKey, oldMode);
            if (oldAge == null) System.clearProperty(ageKey); else System.setProperty(ageKey, oldAge);
        }
    }

    @Test
    void leatherFixtureUsesOrdinaryDyeComponentsAndRejectsSubstitution() {
        String modeKey = "mattmc.dev.graphicsAuditEquipment";
        String materialKey = "mattmc.dev.graphicsAuditEquipmentMaterial";
        String oldMode = System.getProperty(modeKey), oldMaterial = System.getProperty(materialKey);
        try {
            System.clearProperty(modeKey);
            System.setProperty(materialKey, "leather-dyed");
            assertThrows(IllegalArgumentException.class, GraphicsAuditEquipmentFixture::materialVariant);
            System.setProperty(modeKey, "base");
            var items = new net.minecraft.world.item.Item[] {Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS};
            for (int i=0; i<4; i++) {
                ItemStack stack = GraphicsAuditEquipmentFixture.materialStack(i);
                assertTrue(stack.is(items[i]));
                assertEquals(0x3366CC, stack.get(DataComponents.DYED_COLOR).rgb());
                assertFalse(stack.hasFoil());
                assertTrue(GraphicsAuditEquipmentFixture.materialMatches(stack, i));
                assertFalse(GraphicsAuditEquipmentFixture.materialMatches(stack, (i+1)%4));
                var dye = stack.remove(DataComponents.DYED_COLOR);
                assertFalse(GraphicsAuditEquipmentFixture.materialMatches(stack, i));
                stack.set(DataComponents.DYED_COLOR, new net.minecraft.world.item.component.DyedItemColor(0x3366CD));
                assertFalse(GraphicsAuditEquipmentFixture.materialMatches(stack, i));
                stack.set(DataComponents.DYED_COLOR, dye);
                assertTrue(GraphicsAuditEquipmentFixture.materialMatches(stack, i));
            }
            System.clearProperty(materialKey);
            var diamond = GraphicsAuditEquipmentFixture.materialStack(0);
            assertTrue(diamond.is(Items.DIAMOND_HELMET));
            assertTrue(GraphicsAuditEquipmentFixture.materialMatches(diamond, 0));
            diamond.set(DataComponents.DYED_COLOR, new net.minecraft.world.item.component.DyedItemColor(0x3366CC));
            assertFalse(GraphicsAuditEquipmentFixture.materialMatches(diamond, 0));
            System.setProperty(materialKey, "unknown");
            assertThrows(IllegalArgumentException.class, GraphicsAuditEquipmentFixture::materialVariant);
        } finally {
            if (oldMode == null) System.clearProperty(modeKey); else System.setProperty(modeKey, oldMode);
            if (oldMaterial == null) System.clearProperty(materialKey); else System.setProperty(materialKey, oldMaterial);
        }
    }

    @Test
    void inlineDecalPatternUsesTheOrdinaryCodecAndRetainsTheAuthoredAsset() {
        var registered = registries.lookupOrThrow(Registries.TRIM_PATTERN).getOrThrow(TrimPatterns.SPIRE);
        assertFalse(registered.value().decal());
        assertSame(registered, GraphicsAuditEquipmentFixture.fixturePattern(registered, false));
        var inline = GraphicsAuditEquipmentFixture.fixturePattern(registered, true);
        assertTrue(inline.unwrapKey().isEmpty());
        assertTrue(inline.value().decal());
        assertEquals(registered.value().assetId(), inline.value().assetId());
        assertEquals(registered.value().description(), inline.value().description());
        var json = TrimPattern.DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, inline.value()).getOrThrow();
        assertEquals(inline.value(), TrimPattern.DIRECT_CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        assertFalse(registered.value().decal(), "fixture cannot mutate the registered baseline pattern");
    }

    @Test
    void readinessRejectsMissingOrSubstitutedTrimComponents() {
        var materials = registries.lookupOrThrow(Registries.TRIM_MATERIAL);
        var patterns = registries.lookupOrThrow(Registries.TRIM_PATTERN);
        var gold = materials.getOrThrow(TrimMaterials.GOLD);
        var spire = patterns.getOrThrow(TrimPatterns.SPIRE);
        ItemStack stack = new ItemStack(Items.DIAMOND_HELMET);
        assertTrue(GraphicsAuditEquipmentFixture.trimMatches(stack, ""));
        assertFalse(GraphicsAuditEquipmentFixture.trimMatches(stack, "gold-spire"));
        stack.set(DataComponents.TRIM, new ArmorTrim(gold, spire));
        assertTrue(GraphicsAuditEquipmentFixture.trimMatches(stack, "gold-spire"));
        assertFalse(GraphicsAuditEquipmentFixture.trimMatches(stack, ""));
        assertFalse(GraphicsAuditEquipmentFixture.trimMatches(stack, "gold-spire-decal"));
        stack.set(DataComponents.TRIM, new ArmorTrim(gold, GraphicsAuditEquipmentFixture.fixturePattern(spire, true)));
        assertTrue(GraphicsAuditEquipmentFixture.trimMatches(stack, "gold-spire-decal"));
        assertFalse(GraphicsAuditEquipmentFixture.trimMatches(stack, "gold-spire"));
        stack.set(DataComponents.TRIM, new ArmorTrim(materials.getOrThrow(TrimMaterials.IRON), spire));
        assertFalse(GraphicsAuditEquipmentFixture.trimMatches(stack, "gold-spire"));
        stack.set(DataComponents.TRIM, new ArmorTrim(gold, patterns.getOrThrow(TrimPatterns.DUNE)));
        assertFalse(GraphicsAuditEquipmentFixture.trimMatches(stack, "gold-spire"));
    }

    @Test
    void trimRequestRequiresAnExplicitSupportedEquipmentScope() {
        String modeKey = "mattmc.dev.graphicsAuditEquipment", trimKey = "mattmc.dev.graphicsAuditEquipmentTrim";
        String oldMode = System.getProperty(modeKey), oldTrim = System.getProperty(trimKey);
        try {
            System.clearProperty(modeKey); System.setProperty(trimKey, "gold-spire");
            assertThrows(IllegalArgumentException.class, GraphicsAuditEquipmentFixture::trimVariant);
            System.setProperty(modeKey, "foil");
            assertEquals("gold-spire", GraphicsAuditEquipmentFixture.trimVariant());
            System.setProperty(trimKey, "gold-spire-decal");
            assertEquals("gold-spire-decal", GraphicsAuditEquipmentFixture.trimVariant());
            System.setProperty(trimKey, "unknown");
            assertThrows(IllegalArgumentException.class, GraphicsAuditEquipmentFixture::trimVariant);
        } finally {
            if (oldMode == null) System.clearProperty(modeKey); else System.setProperty(modeKey, oldMode);
            if (oldTrim == null) System.clearProperty(trimKey); else System.setProperty(trimKey, oldTrim);
        }
    }
}
