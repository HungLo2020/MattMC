package net.minecraft.world.item;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingWandRegistrationTest {
    private static final Path SRC_MAIN_JAVA = Path.of("src/main/java");
    private static final Path ASSETS = Path.of("src/main/resources/assets/minecraft");

    @Test
    void buildingWandIsRegisteredAsStackSizeOneItem() throws IOException {
        String source = Files.readString(SRC_MAIN_JAVA.resolve("net/minecraft/world/item/Items.java"));
        int declarationStart = source.indexOf("BUILDING_WAND");
        int declarationEnd = source.indexOf(";", declarationStart);
        String declaration = source.substring(declarationStart, declarationEnd);

        assertTrue(source.contains("public static final Item BUILDING_WAND"));
        assertTrue(source.contains("registerItem(\"building_wand\", new Item.Properties().stacksTo(1))"));
        assertFalse(declaration.contains("durability("), "Building Wand should not have durability");
        assertFalse(declaration.contains("MAX_DAMAGE"), "Building Wand should not have damage components");
    }

    @Test
    void buildingWandAppearsWithTrowelInToolsTab() throws IOException {
        String source = Files.readString(SRC_MAIN_JAVA.resolve("net/minecraft/world/item/CreativeModeTabs.java"));
        int trowelIndex = source.indexOf("output.accept(Items.TROWEL)");
        int buildingWandIndex = source.indexOf("output.accept(Items.BUILDING_WAND)");

        assertTrue(trowelIndex >= 0, "Trowel should remain in the tools tab");
        assertTrue(buildingWandIndex > trowelIndex, "Building Wand should be placed immediately after Trowel in the tools tab");
    }

    @Test
    void buildingWandHasLanguageAndModelResources() throws IOException {
        String language = Files.readString(ASSETS.resolve("lang/en_us.json"));
        String itemDefinition = Files.readString(ASSETS.resolve("items/building_wand.json"));
        String itemModel = Files.readString(ASSETS.resolve("models/item/building_wand.json"));

        assertTrue(language.contains("\"item.minecraft.building_wand\": \"Building Wand\""));
        assertTrue(itemDefinition.contains("\"model\": \"minecraft:item/building_wand\""));
        assertTrue(itemModel.contains("\"parent\": \"minecraft:item/handheld\""));
        assertTrue(itemModel.contains("\"layer0\": \"minecraft:item/building_wand\""));
    }

    @Test
    void buildingWandTextureUsesDedicatedAsset() throws IOException {
        byte[] trowel = Files.readAllBytes(ASSETS.resolve("textures/item/trowel.png"));
        byte[] buildingWand = Files.readAllBytes(ASSETS.resolve("textures/item/building_wand.png"));

        assertTrue(buildingWand.length > 0, "Building Wand texture should not be empty");
        assertFalse(Arrays.equals(trowel, buildingWand), "Building Wand should use the supplied dedicated texture");
    }

    @Test
    void buildingWandHasDiamondCraftingRecipe() throws IOException {
        String recipe = Files.readString(Path.of("src/main/resources/data/minecraft/recipe/crafting/building_wand.json"));

        assertTrue(recipe.contains("\"I\": \"minecraft:diamond\""));
        assertTrue(recipe.contains("\"S\": \"minecraft:stick\""));
        assertTrue(recipe.contains("\"id\": \"minecraft:building_wand\""));
    }
}
