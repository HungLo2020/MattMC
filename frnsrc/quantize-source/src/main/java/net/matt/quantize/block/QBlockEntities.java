package net.matt.quantize.block;

import net.matt.quantize.Quantize;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.matt.quantize.block.BlockEntity.*;

public class QBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Quantize.MOD_ID);

    public static final RegistryObject<BlockEntityType<SolarPanelBlockEntity>> SOLAR_PANEL = BLOCK_ENTITIES.register("solar_panel", () -> BlockEntityType.Builder.of(SolarPanelBlockEntity::new, QBlocks.SOLAR_PANEL.get()).build(null));
    public static final RegistryObject<BlockEntityType<ElectricFurnaceBlockEntity>> ELECTRIC_FURNACE = BLOCK_ENTITIES.register("electric_furnace", () -> BlockEntityType.Builder.of(ElectricFurnaceBlockEntity::new, QBlocks.ELECTRIC_FURNACE.get()).build(null));
    public static final RegistryObject<BlockEntityType<BatteryBlockEntity>> BATTERY = BLOCK_ENTITIES.register("battery", () -> BlockEntityType.Builder.of(BatteryBlockEntity::new, QBlocks.BATTERY.get()).build(null));
    public static final RegistryObject<BlockEntityType<PulverizerBlockEntity>> PULVERIZER = BLOCK_ENTITIES.register("pulverizer", () -> BlockEntityType.Builder.of(PulverizerBlockEntity::new, QBlocks.PULVERIZER.get()).build(null));
    public static final RegistryObject<BlockEntityType<EnergyConduitBlockEntity>> ENERGY_CONDUIT = BLOCK_ENTITIES.register("energy_conduit", () -> BlockEntityType.Builder.of(EnergyConduitBlockEntity::new, QBlocks.ENERGY_CONDUIT.get()).build(null));
    public static final RegistryObject<BlockEntityType<WirelessCapacitorBlockEntity>> WIRELESS_CAPACITOR = BLOCK_ENTITIES.register("wireless_capacitor", () -> BlockEntityType.Builder.of(WirelessCapacitorBlockEntity::new, QBlocks.WIRELESS_CAPACITOR.get()).build(null));
    public static final RegistryObject<BlockEntityType<TileEntityLeafcutterAnthill>> LEAFCUTTER_ANTHILL = BLOCK_ENTITIES.register("leafcutter_anthill", () -> BlockEntityType.Builder.of(TileEntityLeafcutterAnthill::new, QBlocks.LEAFCUTTER_ANTHILL.get()).build(null));
    public static final RegistryObject<BlockEntityType<ElevatorTileEntity>> ELEVATOR = BLOCK_ENTITIES.register("elevator", () -> BlockEntityType.Builder.of(ElevatorTileEntity::new, QBlocks.ELEVATOR.get()).build(null));
    public static final RegistryObject<BlockEntityType<VolcanicCoreBlockEntity>> VOLCANIC_CORE = BLOCK_ENTITIES.register("volcanic_core", () -> BlockEntityType.Builder.of(VolcanicCoreBlockEntity::new, QBlocks.VOLCANIC_CORE.get()).build(null));
    public static final RegistryObject<BlockEntityType<AmbersolBlockEntity>> AMBERSOL = BLOCK_ENTITIES.register("ambersol", () -> BlockEntityType.Builder.of(AmbersolBlockEntity::new, QBlocks.AMBERSOL.get()).build(null));
    public static final RegistryObject<BlockEntityType<AmberMonolithBlockEntity>> AMBER_MONOLITH = BLOCK_ENTITIES.register("amber_monolith", () -> BlockEntityType.Builder.of(AmberMonolithBlockEntity::new, QBlocks.AMBER_MONOLITH.get()).build(null));
    public static final RegistryObject<BlockEntityType<BotanyPotBlockEntity>> BOTANY_POT = BLOCK_ENTITIES.register("botany_pot", () -> BlockEntityType.Builder.of(BotanyPotBlockEntity::new, QBlocks.BOTANY_POT.get()).build(null));
    public static final RegistryObject<BlockEntityType<HydroponicsBasinBlockEntity>> HYDROPONICS_BASIN = BLOCK_ENTITIES.register("hydroponics_basin", () -> BlockEntityType.Builder.of(HydroponicsBasinBlockEntity::new, QBlocks.HYDROPONICS_BASIN.get()).build(null));
    public static final RegistryObject<BlockEntityType<CrafterBlockEntity>> CRAFTER = BLOCK_ENTITIES.register("crafter", () -> BlockEntityType.Builder.of(CrafterBlockEntity::new, QBlocks.CRAFTER.get()).build(null));


    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}