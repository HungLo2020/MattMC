package net.minecraft.client.model.animal.nautilus;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;

public class NautilusSaddleModel extends NautilusModel {
	public NautilusSaddleModel(ModelPart root) {
		super(root);
	}

	public static LayerDefinition createSaddleLayer() {
		MeshDefinition meshDefinition = createBodyMesh();
		meshDefinition.getRoot()
			.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 29.0F, -6.0F))
			.addOrReplaceChild(
				"shell",
				CubeListBuilder.create().texOffs(0, 0).addBox(-7.0F, -10.0F, -7.0F, 14.0F, 10.0F, 16.0F, new CubeDeformation(0.2F)),
				PartPose.offset(0.0F, -13.0F, 5.0F)
			);
		return LayerDefinition.create(meshDefinition, 128, 128);
	}
}
