package com.github.alexthe666.citadel;

import com.github.alexthe666.citadel.config.ConfigHolder;
import com.github.alexthe666.citadel.config.ServerConfig;
import com.github.alexthe666.citadel.item.ItemCitadelBook;
import com.github.alexthe666.citadel.item.ItemCitadelDebug;
import com.github.alexthe666.citadel.item.ItemCustomRender;
import com.github.alexthe666.citadel.item.component.CustomRenderDisplay;
import com.github.alexthe666.citadel.server.CitadelEvents;
import com.github.alexthe666.citadel.server.block.CitadelLecternBlock;
import com.github.alexthe666.citadel.server.block.CitadelLecternBlockEntity;
import com.github.alexthe666.citadel.server.block.LecternBooks;
import com.github.alexthe666.citadel.web.WebHelper;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Main Citadel mod class - Fabric version.
 * Converted from NeoForge to Fabric API.
 */
public class Citadel {
    public static final String MODID = "citadel";
    public static final Logger LOGGER = LogManager.getLogger("citadel");
    
    public static ServerProxy PROXY = createProxy();
    public static List<String> PATREONS = new ArrayList<>();
    
    // Registered items
    public static Item DEBUG_ITEM;
    public static Item CITADEL_BOOK;
    public static Item EFFECT_ITEM;
    public static Item FANCY_ITEM;
    public static Item ICON_ITEM;
    
    // Registered blocks
    public static Block LECTERN;
    
    // Registered block entities
    public static BlockEntityType<CitadelLecternBlockEntity> LECTERN_BE;
    
    // Registered data components
    public static DataComponentType<CustomRenderDisplay> CUSTOM_RENDER_DISPLAY;
    public static DataComponentType<ResourceLocation> ICON_LOCATION;
    public static DataComponentType<ResourceKey<MobEffect>> DISPLAY_EFFECT;
    
    /**
     * Common initialization - called on both client and server
     */
    public static void commonInit() {
        LOGGER.info("Citadel common initialization starting");
        
        // Register items
        registerItems();
        
        // Register blocks
        registerBlocks();
        
        // Register block entities
        registerBlockEntities();
        
        // Register data components
        registerDataComponents();
        
        // Initialize config
        initConfig();
        
        // Register events
        registerEvents();
        
        // Pre-init proxy
        PROXY.onPreInit();
        
        // Initialize lectern books
        LecternBooks.init();
        
        // Load patreon list
        loadPatreons();
        
        LOGGER.info("Citadel common initialization complete");
    }
    
    /**
     * Client-only initialization
     */
    @Environment(EnvType.CLIENT)
    public static void clientInit() {
        LOGGER.info("Citadel client initialization starting");
        PROXY.onClientInit();
        LOGGER.info("Citadel client initialization complete");
    }
    
    /**
     * Server-only initialization
     */
    public static void serverInit() {
        LOGGER.info("Citadel server initialization");
        // Server-specific initialization if needed
    }
    
    private static void registerItems() {
        DEBUG_ITEM = Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation.fromNamespaceAndPath(MODID, "debug"),
            new ItemCitadelDebug(new Item.Properties())
        );
        
        CITADEL_BOOK = Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation.fromNamespaceAndPath(MODID, "citadel_book"),
            new ItemCitadelBook(new Item.Properties().stacksTo(1))
        );
        
        EFFECT_ITEM = Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation.fromNamespaceAndPath(MODID, "effect_item"),
            new ItemCustomRender(new Item.Properties().stacksTo(1))
        );
        
        FANCY_ITEM = Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation.fromNamespaceAndPath(MODID, "fancy_item"),
            new ItemCustomRender(new Item.Properties().stacksTo(1))
        );
        
        ICON_ITEM = Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation.fromNamespaceAndPath(MODID, "icon_item"),
            new ItemCustomRender(new Item.Properties().stacksTo(1))
        );
    }
    
    private static void registerBlocks() {
        LECTERN = Registry.register(
            BuiltInRegistries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(MODID, "lectern"),
            new CitadelLecternBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.LECTERN))
        );
    }
    
    private static void registerBlockEntities() {
        LECTERN_BE = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(MODID, "lectern"),
            BlockEntityType.Builder.of(CitadelLecternBlockEntity::new, LECTERN).build(null)
        );
    }
    
    private static void registerDataComponents() {
        CUSTOM_RENDER_DISPLAY = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            ResourceLocation.fromNamespaceAndPath(MODID, "custom_render_display"),
            DataComponentType.<CustomRenderDisplay>builder()
                .persistent(CustomRenderDisplay.CODEC)
                .build()
        );
        
        ICON_LOCATION = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            ResourceLocation.fromNamespaceAndPath(MODID, "icon_location"),
            DataComponentType.<ResourceLocation>builder()
                .persistent(ResourceLocation.CODEC)
                .networkSynchronized(ResourceLocation.STREAM_CODEC)
                .build()
        );
        
        DISPLAY_EFFECT = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            ResourceLocation.fromNamespaceAndPath(MODID, "display_effect"),
            DataComponentType.<ResourceKey<MobEffect>>builder()
                .persistent(ResourceKey.codec(Registries.MOB_EFFECT))
                .networkSynchronized(ResourceKey.streamCodec(Registries.MOB_EFFECT))
                .build()
        );
    }
    
    private static void initConfig() {
        // TODO: Implement Fabric config system
        // For now, use default values
        ServerConfig.skipWarnings = false;
        ServerConfig.citadelEntityTrack = true;
        ServerConfig.chunkGenSpawnModifierVal = 1.0;
        ServerConfig.aprilFools = false;
    }
    
    private static void registerEvents() {
        // TODO: Register Fabric events
        // For now, skip event registration
        // CitadelEvents will need to be converted to use Fabric callbacks
    }
    
    private static void loadPatreons() {
        BufferedReader urlContents = WebHelper.getURLContents(
            "https://raw.githubusercontent.com/Alex-the-666/Citadel/master/src/main/resources/assets/citadel/patreon.txt",
            "assets/citadel/patreon.txt"
        );
        if (urlContents != null) {
            try {
                String line;
                while ((line = urlContents.readLine()) != null) {
                    PATREONS.add(line);
                }
            } catch (IOException e) {
                LOGGER.warn("Failed to load patreon contributor perks", e);
            }
        } else {
            LOGGER.warn("Failed to load patreon contributor perks");
        }
    }
    
    /**
     * Create proxy based on current environment
     */
    private static ServerProxy createProxy() {
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            return new ClientProxy();
        } else {
            return new ServerProxy();
        }
    }
}
