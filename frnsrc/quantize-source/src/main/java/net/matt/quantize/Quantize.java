package net.matt.quantize;

import com.mojang.logging.LogUtils;
import net.matt.quantize.block.QBlocks;
import net.matt.quantize.commands.QGameRules;
import net.matt.quantize.effects.QEffects;
import net.matt.quantize.entities.*;
import net.matt.quantize.gui.ModScreens;
import net.matt.quantize.item.QCreativeModeTabs;
import net.matt.quantize.item.QItems;
import net.matt.quantize.modules.biomes.BiomeGenerationConfig;
import net.matt.quantize.modules.config.ConfigScreen;
import net.matt.quantize.modules.config.QClientConfig;
import net.matt.quantize.modules.config.QCommonConfig;
import net.matt.quantize.modules.config.QServerConfig;
import net.matt.quantize.modules.dark.DClientProxy;
import net.matt.quantize.modules.storage.QFolders;
import net.matt.quantize.network.NetworkHandler;
import net.matt.quantize.particle.QParticleRenderers;
import net.matt.quantize.particle.QParticles;
import net.matt.quantize.proxy.ServerProxy;
import net.matt.quantize.sounds.QSounds;
import net.matt.quantize.worldgen.QTerrablender;
import net.matt.quantize.worldgen.structures.ACStructurePieceRegistry;
import net.matt.quantize.worldgen.structures.ACStructureProcessorRegistry;
import net.matt.quantize.worldgen.surfacerules.ModSurfaceRules;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import net.matt.quantize.recipes.QRecipes;
import net.matt.quantize.gui.ModMenus;
import net.matt.quantize.entities.QEntities;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraft.network.chat.Component;
import net.matt.quantize.block.QBlockEntities;
import net.minecraft.client.renderer.Sheets;
import net.matt.quantize.worldgen.QBiomes;
import net.matt.quantize.proxy.ClientProxy;
import net.matt.quantize.worldgen.QFeatures;
import net.matt.quantize.worldgen.structures.ACStructureRegistry;
import net.matt.quantize.modules.biomes.ACSurfaceRuleConditionRegistry;
import net.matt.quantize.events.CommonEvents;
import net.matt.quantize.modules.storage.ACWorldData;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.matt.quantize.modules.biomes.ModCompatBridge;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import java.util.Calendar;
import java.util.Date;

import java.io.IOException;

@Mod(Quantize.MOD_ID)
public class Quantize
{
    public static final String MOD_ID = "quantize";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final ClientProxy PROXY = new ClientProxy();
    public static final ServerProxy SPROXY = new ServerProxy();

    private static boolean isAprilFools = false;
    private static boolean isHalloween = false;

    public Quantize(FMLJavaModLoadingContext context)
    {
        // Set up util vars
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        isAprilFools = calendar.get(Calendar.MONTH) + 1 == 4 && calendar.get(Calendar.DATE) == 1;
        isHalloween = calendar.get(Calendar.MONTH) + 1 == 10 && calendar.get(Calendar.DATE) >= 29 && calendar.get(Calendar.DATE) <= 31;


        @SuppressWarnings("removal")
        ModLoadingContext modLoadingContext = ModLoadingContext.get();
        IEventBus modEventBus = context.getModEventBus();

        // register config screen
        modLoadingContext.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, screen) -> new ConfigScreen(Component.translatable("config.quantize.title"))));

        // dark mode stuff
        modLoadingContext.registerExtensionPoint(IExtensionPoint.DisplayTest.class, () -> new IExtensionPoint.DisplayTest(() -> "ANY", (remote, isServer) -> true));
        if (FMLEnvironment.dist == Dist.CLIENT){
            new DClientProxy(context);
        }
        modLoadingContext.registerConfig(ModConfig.Type.CLIENT, QClientConfig.CLIENT.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, QServerConfig.SERVER_CONFIG);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, QCommonConfig.COMMON_CONFIG);

        // my mod
        modEventBus.addListener(this::loadComplete);
        modEventBus.addListener(this::loadConfig);
        modEventBus.addListener(this::reloadConfig);
        modEventBus.addListener(this::setupEntityModelLayers);
        MinecraftForge.EVENT_BUS.register(new CommonEvents());
        //ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, QServerConfig.SERVER_CONFIG);
        //ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, QCommonConfig.COMMON_CONFIG);
        QCreativeModeTabs.register(modEventBus);
        QEntities.register(modEventBus);
        QItems.register(modEventBus);
        QBlocks.register(modEventBus);
        QBlockEntities.register(modEventBus);
        ModMenus.register(modEventBus);
        QRecipes.register(modEventBus);
        QSounds.register(modEventBus);
        QEntityRenderers.register(modEventBus);
        QBiomeModifiers.register(modEventBus);
        QParticles.register(modEventBus);
        QEffects.EFFECT_DEF_REG.register(modEventBus);
        modEventBus.addListener(QParticleRenderers::registerParticles);
        QTerrablender.registerBiomes();
        QFeatures.register(modEventBus);
        ACSurfaceRuleConditionRegistry.DEF_REG.register(modEventBus);
        BiomeGenerationConfig.reloadConfig();
        ACStructureRegistry.DEF_REG.register(modEventBus);
        ACStructurePieceRegistry.DEF_REG.register(modEventBus);
        ACStructureProcessorRegistry.DEF_REG.register(modEventBus);
        QFolders.init();
        QBiomes.init();



        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void loadConfig(final ModConfigEvent.Loading event) {
        BiomeGenerationConfig.reloadConfig();
    }
    private void reloadConfig(final ModConfigEvent.Reloading event) {
        BiomeGenerationConfig.reloadConfig();
    }

    @SubscribeEvent
    public void loadComplete(FMLLoadCompleteEvent event) {
        event.enqueueWork(ModCompatBridge::afterAllModsLoaded);
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
        event.enqueueWork(() -> {
            NetworkHandler.init();
            Sheets.addWoodType(QBlocks.PEWEN_WOOD_TYPE);
            ModSurfaceRules.setup();
            ForgeChunkManager.setForcedChunkLoadingCallback(MOD_ID, ACWorldData::clearLoadedChunksCallback);
            PROXY.clientInit();
            QGameRules.init();
            QFolders.run();
        });
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            ModScreens.registerScreens();
        }
    }

    private void setupEntityModelLayers(final EntityRenderersEvent.RegisterLayerDefinitions event) {
        QModelLayers.register(event);
    }



    // Utils and Helpers
    public static boolean isAprilFools() {
        return isAprilFools || QServerConfig.SUPER_SECRET_SETTINGS.get();
    }

    public static boolean isHalloween() {
        return isHalloween || QServerConfig.SUPER_SECRET_SETTINGS.get();
    }
}