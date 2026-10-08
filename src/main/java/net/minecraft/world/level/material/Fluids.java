package net.minecraft.world.level.material;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

public class Fluids {
	private static final java.util.Map<String, Fluid> VIEWS = registerNativeDefinitions();
	public static final Fluid EMPTY = VIEWS.get("minecraft:empty");
	public static final FlowingFluid FLOWING_WATER = (FlowingFluid)VIEWS.get("minecraft:flowing_water");
	public static final FlowingFluid WATER = (FlowingFluid)VIEWS.get("minecraft:water");
	public static final FlowingFluid FLOWING_LAVA = (FlowingFluid)VIEWS.get("minecraft:flowing_lava");
	public static final FlowingFluid LAVA = (FlowingFluid)VIEWS.get("minecraft:lava");

	private static java.util.Map<String, Fluid> registerNativeDefinitions() {
		var views = new java.util.HashMap<String, Fluid>();
		for (var definition : NativeFluidDefinitions.definitions()) {
			// Adapter choice binds the remaining Java world callbacks to a native
			// family. It does not declare registry order, domains or state facts.
			Fluid fluid = switch (definition.family()) {
				case 0 -> new EmptyFluid(definition);
				case 1 -> definition.source() ? new WaterFluid.Source(definition) : new WaterFluid.Flowing(definition);
				case 2 -> definition.source() ? new LavaFluid.Source(definition) : new LavaFluid.Flowing(definition);
				default -> throw new IllegalStateException("Unsupported native fluid family");
			};
			Registry.register(BuiltInRegistries.FLUID, ResourceLocation.parse(definition.name()), fluid);
			if (BuiltInRegistries.FLUID.getId(fluid) != definition.id() || views.put(definition.name(), fluid) != null) {
				throw new IllegalStateException("Native fluid registry projection changed");
			}
		}
		return java.util.Map.copyOf(views);
	}

	static {
		for (Fluid fluid : BuiltInRegistries.FLUID) {
			int next = fluid.nativeDefinition.firstState();
			for (FluidState fluidState : fluid.getStateDefinition().getPossibleStates()) {
				Fluid.FLUID_STATE_REGISTRY.add(fluidState);
				if (Fluid.FLUID_STATE_REGISTRY.getId(fluidState) != next++) {
					throw new IllegalStateException("Native fluid state IDs changed");
				}
			}
		}
	}
}
