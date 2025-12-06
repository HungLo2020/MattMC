package net.minecraft.client.renderer.block.model;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.QuadCollection;
import net.minecraft.client.resources.model.SpriteGetter;
import net.minecraft.client.resources.model.UnbakedGeometry;
import net.minecraft.core.Direction;

@Environment(EnvType.CLIENT)
public record SimpleUnbakedGeometry(List<BlockElement> elements) implements UnbakedGeometry {
	@Override
	public QuadCollection bake(TextureSlots textureSlots, ModelBaker modelBaker, ModelState modelState, ModelDebugName modelDebugName) {
		return bake(this.elements, textureSlots, modelBaker.sprites(), modelState, modelDebugName);
	}

	public static QuadCollection bake(
		List<BlockElement> list, TextureSlots textureSlots, SpriteGetter spriteGetter, ModelState modelState, ModelDebugName modelDebugName
	) {
		QuadCollection.Builder builder = new QuadCollection.Builder();

		for (BlockElement blockElement : list) {
			blockElement.faces()
				.forEach(
					(direction, blockElementFace) -> {
						TextureAtlasSprite textureAtlasSprite = spriteGetter.resolveSlot(textureSlots, blockElementFace.texture(), modelDebugName);
						if (blockElementFace.cullForDirection() == null) {
							builder.addUnculledFace(bakeFace(blockElement, blockElementFace, textureAtlasSprite, direction, modelState));
						} else {
							builder.addCulledFace(
								Direction.rotate(modelState.transformation().getMatrix(), blockElementFace.cullForDirection()),
								bakeFace(blockElement, blockElementFace, textureAtlasSprite, direction, modelState)
							);
						}
					}
				);
		}

		return builder.build();
	}

	private static BakedQuad bakeFace(
		BlockElement blockElement, BlockElementFace blockElementFace, TextureAtlasSprite textureAtlasSprite, Direction direction, ModelState modelState
	) {
		return FaceBakery.bakeQuad(
			blockElement.from(),
			blockElement.to(),
			blockElementFace,
			textureAtlasSprite,
			direction,
			modelState,
			blockElement.rotation(),
			blockElement.shade(),
			blockElement.lightEmission()
		);
	}
}
