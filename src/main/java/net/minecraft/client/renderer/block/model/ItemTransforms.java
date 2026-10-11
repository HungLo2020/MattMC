package net.minecraft.client.renderer.block.model;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.world.item.ItemDisplayContext;

@Environment(EnvType.CLIENT)
public record ItemTransforms(
	ItemTransform thirdPersonLeftHand,
	ItemTransform thirdPersonRightHand,
	ItemTransform firstPersonLeftHand,
	ItemTransform firstPersonRightHand,
	ItemTransform head,
	ItemTransform gui,
	ItemTransform ground,
	ItemTransform fixed,
	ItemTransform fixedFromBottom
) {
	public static final ItemTransforms NO_TRANSFORMS = new ItemTransforms(
		ItemTransform.NO_TRANSFORM,
		ItemTransform.NO_TRANSFORM,
		ItemTransform.NO_TRANSFORM,
		ItemTransform.NO_TRANSFORM,
		ItemTransform.NO_TRANSFORM,
		ItemTransform.NO_TRANSFORM,
		ItemTransform.NO_TRANSFORM,
		ItemTransform.NO_TRANSFORM,
		ItemTransform.NO_TRANSFORM
	);

	public ItemTransform getTransform(ItemDisplayContext itemDisplayContext) {
		return switch (itemDisplayContext) {
			case THIRD_PERSON_LEFT_HAND -> this.thirdPersonLeftHand;
			case THIRD_PERSON_RIGHT_HAND -> this.thirdPersonRightHand;
			case FIRST_PERSON_LEFT_HAND -> this.firstPersonLeftHand;
			case FIRST_PERSON_RIGHT_HAND -> this.firstPersonRightHand;
			case HEAD -> this.head;
			case GUI -> this.gui;
			case GROUND -> this.ground;
			case FIXED -> this.fixed;
			case ON_SHELF -> this.fixedFromBottom;
			default -> ItemTransform.NO_TRANSFORM;
		};
	}

}
