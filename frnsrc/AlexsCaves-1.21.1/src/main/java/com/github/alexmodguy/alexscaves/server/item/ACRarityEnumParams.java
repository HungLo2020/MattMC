package com.github.alexmodguy.alexscaves.server.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.Rarity;

import java.awt.*;
import java.util.function.UnaryOperator;

/**
 * Parameter providers for custom Rarity enum extensions.
 * This class is used by the enum extension system to create custom rarities.
 * 
 * NeoForge 1.21 uses a method-based pattern for Rarity enum extension.
 * Each method returns parameters by index for the Rarity constructor:
 *   index 0: int id (-1 for modded)
 *   index 1: String name (e.g., "alexscaves:demonic")
 *   index 2: UnaryOperator<Style> styleModifier
 * 
 * Original colors from AlexsCaves:
 * - DEMONIC: DARK_RED
 * - NUCLEAR: GREEN  
 * - SWEET: Pink (0xFF8ACD)
 * - RAINBOW: Animated HSB rainbow
 */
public class ACRarityEnumParams {
    
    /**
     * DEMONIC rarity - Dark red color for demonic/fiery items
     * Referenced by enumextensions.json
     */
    @SuppressWarnings("unused") // Referenced by enumextensions.json
    public static Object getDemonicRarityParameter(int idx, Class<?> type) {
        return type.cast(switch (idx) {
            case 0 -> -1;
            case 1 -> "alexscaves:demonic";
            case 2 -> (UnaryOperator<Style>) style -> style.withColor(ChatFormatting.DARK_RED);
            default -> throw new IllegalArgumentException("Unexpected parameter index: " + idx);
        });
    }
    
    /**
     * NUCLEAR rarity - Green color for radioactive/nuclear items
     * Referenced by enumextensions.json
     */
    @SuppressWarnings("unused") // Referenced by enumextensions.json
    public static Object getNuclearRarityParameter(int idx, Class<?> type) {
        return type.cast(switch (idx) {
            case 0 -> -1;
            case 1 -> "alexscaves:nuclear";
            case 2 -> (UnaryOperator<Style>) style -> style.withColor(ChatFormatting.GREEN);
            default -> throw new IllegalArgumentException("Unexpected parameter index: " + idx);
        });
    }
    
    /**
     * SWEET rarity - Pink color for candy/sweet items
     * Referenced by enumextensions.json
     */
    @SuppressWarnings("unused") // Referenced by enumextensions.json
    public static Object getSweetRarityParameter(int idx, Class<?> type) {
        return type.cast(switch (idx) {
            case 0 -> -1;
            case 1 -> "alexscaves:sweet";
            case 2 -> (UnaryOperator<Style>) style -> style.withColor(0xFF8ACD);
            default -> throw new IllegalArgumentException("Unexpected parameter index: " + idx);
        });
    }
    
    /**
     * RAINBOW rarity - Animated rainbow color for special items
     * Note: The rainbow animation is applied via a UnaryOperator that calculates
     * the current color based on system time.
     * Referenced by enumextensions.json
     */
    @SuppressWarnings("unused") // Referenced by enumextensions.json
    public static Object getRainbowRarityParameter(int idx, Class<?> type) {
        return type.cast(switch (idx) {
            case 0 -> -1;
            case 1 -> "alexscaves:rainbow";
            case 2 -> (UnaryOperator<Style>) style -> style.withColor(Color.HSBtoRGB((System.currentTimeMillis() % 5000) / 5000F, 1f, 1F));
            default -> throw new IllegalArgumentException("Unexpected parameter index: " + idx);
        });
    }
}
