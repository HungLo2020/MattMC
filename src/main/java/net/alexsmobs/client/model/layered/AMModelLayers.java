package net.alexsmobs.client.model.layered;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.ResourceLocation;

public class AMModelLayers {

    public static final ModelLayerLocation UNDERMINER = createLocation("underminer", "main");

    private static ModelLayerLocation createLocation(String model, String layer) {
        return new ModelLayerLocation(ResourceLocation.withDefaultNamespace(model), layer);
    }

    // Static method to get the layer definition for underminer
    public static LayerDefinition getUnderminerLayerDefinition() {
        return LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0.05F), 64, 64);
    }
}
