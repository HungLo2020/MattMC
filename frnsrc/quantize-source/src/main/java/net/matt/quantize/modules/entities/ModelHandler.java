package net.matt.quantize.modules.entities;

import net.matt.quantize.utils.ResourceIdentifier;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.matt.quantize.Quantize;
import net.matt.quantize.entities.models.CrabModel;
import net.minecraftforge.client.event.EntityRenderersEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

@Mod.EventBusSubscriber(modid = Quantize.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModelHandler {

	private static final Map<ModelLayerLocation, Layer> layers = new HashMap<>();
	public static ModelLayerLocation crab;

	private static boolean modelsInitted = false;

	private static void initModels() {
		if (modelsInitted) return;

		crab = addModel("crab", CrabModel::createBodyLayer, CrabModel::new);

		modelsInitted = true;
	}

	private static ModelLayerLocation addModel(String name, Supplier<LayerDefinition> supplier, Function<ModelPart, EntityModel<?>> modelConstructor) {
		return addLayer(name, new Layer(supplier, modelConstructor));
	}

	private static ModelLayerLocation addLayer(String name, Layer layer) {
		ModelLayerLocation loc = new ModelLayerLocation(new ResourceIdentifier(name), "main");
		layers.put(loc, layer);
		return loc;
	}

	@SubscribeEvent
	public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
		initModels();

		for (Map.Entry<ModelLayerLocation, Layer> entry : layers.entrySet()) {
			event.registerLayerDefinition(entry.getKey(), entry.getValue().definition);
		}
	}

	@SuppressWarnings("unchecked")
	public static <T extends Mob, M extends EntityModel<T>> M model(ModelLayerLocation location) {
		initModels();

		Layer layer = layers.get(location);
		return (M) layer.modelConstructor.apply(net.minecraft.client.Minecraft.getInstance().getEntityModels().bakeLayer(location));
	}

	private static class Layer {
		final Supplier<LayerDefinition> definition;
		final Function<ModelPart, EntityModel<?>> modelConstructor;

		public Layer(Supplier<LayerDefinition> definition, Function<ModelPart, EntityModel<?>> modelConstructor) {
			this.definition = definition;
			this.modelConstructor = modelConstructor;
		}
	}
}