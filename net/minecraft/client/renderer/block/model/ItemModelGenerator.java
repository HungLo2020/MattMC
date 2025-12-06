package net.minecraft.client.renderer.block.model;

import com.mojang.math.Quadrant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.QuadCollection;
import net.minecraft.client.resources.model.SpriteGetter;
import net.minecraft.client.resources.model.UnbakedGeometry;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class ItemModelGenerator implements UnbakedModel {
	public static final ResourceLocation GENERATED_ITEM_MODEL_ID = ResourceLocation.withDefaultNamespace("builtin/generated");
	public static final List<String> LAYERS = List.of("layer0", "layer1", "layer2", "layer3", "layer4");
	private static final float MIN_Z = 7.5F;
	private static final float MAX_Z = 8.5F;
	private static final TextureSlots.Data TEXTURE_SLOTS = new TextureSlots.Data.Builder().addReference("particle", "layer0").build();
	private static final BlockElementFace.UVs SOUTH_FACE_UVS = new BlockElementFace.UVs(0.0F, 0.0F, 16.0F, 16.0F);
	private static final BlockElementFace.UVs NORTH_FACE_UVS = new BlockElementFace.UVs(16.0F, 0.0F, 0.0F, 16.0F);

	@Override
	public TextureSlots.Data textureSlots() {
		return TEXTURE_SLOTS;
	}

	@Override
	public UnbakedGeometry geometry() {
		return ItemModelGenerator::bake;
	}

	@Nullable
	@Override
	public UnbakedModel.GuiLight guiLight() {
		return UnbakedModel.GuiLight.FRONT;
	}

	private static QuadCollection bake(TextureSlots textureSlots, ModelBaker modelBaker, ModelState modelState, ModelDebugName modelDebugName) {
		return bake(textureSlots, modelBaker.sprites(), modelState, modelDebugName);
	}

	private static QuadCollection bake(TextureSlots textureSlots, SpriteGetter spriteGetter, ModelState modelState, ModelDebugName modelDebugName) {
		List<BlockElement> list = new ArrayList();

		for (int i = 0; i < LAYERS.size(); i++) {
			String string = (String)LAYERS.get(i);
			Material material = textureSlots.getMaterial(string);
			if (material == null) {
				break;
			}

			SpriteContents spriteContents = spriteGetter.get(material, modelDebugName).contents();
			list.addAll(processFrames(i, string, spriteContents));
		}

		return SimpleUnbakedGeometry.bake(list, textureSlots, spriteGetter, modelState, modelDebugName);
	}

	private static List<BlockElement> processFrames(int i, String string, SpriteContents spriteContents) {
		Map<Direction, BlockElementFace> map = Map.of(
			Direction.SOUTH,
			new BlockElementFace(null, i, string, SOUTH_FACE_UVS, Quadrant.R0),
			Direction.NORTH,
			new BlockElementFace(null, i, string, NORTH_FACE_UVS, Quadrant.R0)
		);
		List<BlockElement> list = new ArrayList();
		list.add(new BlockElement(new Vector3f(0.0F, 0.0F, 7.5F), new Vector3f(16.0F, 16.0F, 8.5F), map));
		list.addAll(createSideElements(spriteContents, string, i));
		return list;
	}

	private static List<BlockElement> createSideElements(SpriteContents spriteContents, String string, int i) {
		float f = spriteContents.width();
		float g = spriteContents.height();
		List<BlockElement> list = new ArrayList();

		for (ItemModelGenerator.Span span : getSpans(spriteContents)) {
			float h = 0.0F;
			float j = 0.0F;
			float k = 0.0F;
			float l = 0.0F;
			float m = 0.0F;
			float n = 0.0F;
			float o = 0.0F;
			float p = 0.0F;
			float q = 16.0F / f;
			float r = 16.0F / g;
			float s = span.getMin();
			float t = span.getMax();
			float u = span.getAnchor();
			ItemModelGenerator.SpanFacing spanFacing = span.getFacing();
			switch (spanFacing) {
				case UP:
					m = s;
					h = s;
					k = n = t + 1.0F;
					o = u;
					j = u;
					l = u;
					p = u + 1.0F;
					break;
				case DOWN:
					o = u;
					p = u + 1.0F;
					m = s;
					h = s;
					k = n = t + 1.0F;
					j = u + 1.0F;
					l = u + 1.0F;
					break;
				case LEFT:
					m = u;
					h = u;
					k = u;
					n = u + 1.0F;
					p = s;
					j = s;
					l = o = t + 1.0F;
					break;
				case RIGHT:
					m = u;
					n = u + 1.0F;
					h = u + 1.0F;
					k = u + 1.0F;
					p = s;
					j = s;
					l = o = t + 1.0F;
			}

			h *= q;
			k *= q;
			j *= r;
			l *= r;
			j = 16.0F - j;
			l = 16.0F - l;
			m *= q;
			n *= q;
			o *= r;
			p *= r;
			Map<Direction, BlockElementFace> map = Map.of(
				spanFacing.getDirection(), new BlockElementFace(null, i, string, new BlockElementFace.UVs(m, o, n, p), Quadrant.R0)
			);
			switch (spanFacing) {
				case UP:
					list.add(new BlockElement(new Vector3f(h, j, 7.5F), new Vector3f(k, j, 8.5F), map));
					break;
				case DOWN:
					list.add(new BlockElement(new Vector3f(h, l, 7.5F), new Vector3f(k, l, 8.5F), map));
					break;
				case LEFT:
					list.add(new BlockElement(new Vector3f(h, j, 7.5F), new Vector3f(h, l, 8.5F), map));
					break;
				case RIGHT:
					list.add(new BlockElement(new Vector3f(k, j, 7.5F), new Vector3f(k, l, 8.5F), map));
			}
		}

		return list;
	}

	private static List<ItemModelGenerator.Span> getSpans(SpriteContents spriteContents) {
		int i = spriteContents.width();
		int j = spriteContents.height();
		List<ItemModelGenerator.Span> list = new ArrayList();
		spriteContents.getUniqueFrames().forEach(k -> {
			for (int l = 0; l < j; l++) {
				for (int m = 0; m < i; m++) {
					boolean bl = !isTransparent(spriteContents, k, m, l, i, j);
					checkTransition(ItemModelGenerator.SpanFacing.UP, list, spriteContents, k, m, l, i, j, bl);
					checkTransition(ItemModelGenerator.SpanFacing.DOWN, list, spriteContents, k, m, l, i, j, bl);
					checkTransition(ItemModelGenerator.SpanFacing.LEFT, list, spriteContents, k, m, l, i, j, bl);
					checkTransition(ItemModelGenerator.SpanFacing.RIGHT, list, spriteContents, k, m, l, i, j, bl);
				}
			}
		});
		return list;
	}

	private static void checkTransition(
		ItemModelGenerator.SpanFacing spanFacing, List<ItemModelGenerator.Span> list, SpriteContents spriteContents, int i, int j, int k, int l, int m, boolean bl
	) {
		boolean bl2 = isTransparent(spriteContents, i, j + spanFacing.getXOffset(), k + spanFacing.getYOffset(), l, m) && bl;
		if (bl2) {
			createOrExpandSpan(list, spanFacing, j, k);
		}
	}

	private static void createOrExpandSpan(List<ItemModelGenerator.Span> list, ItemModelGenerator.SpanFacing spanFacing, int i, int j) {
		ItemModelGenerator.Span span = null;

		for (ItemModelGenerator.Span span2 : list) {
			if (span2.getFacing() == spanFacing) {
				int k = spanFacing.isHorizontal() ? j : i;
				if (span2.getAnchor() == k) {
					span = span2;
					break;
				}
			}
		}

		int l = spanFacing.isHorizontal() ? j : i;
		int m = spanFacing.isHorizontal() ? i : j;
		if (span == null) {
			list.add(new ItemModelGenerator.Span(spanFacing, m, l));
		} else {
			span.expand(m);
		}
	}

	private static boolean isTransparent(SpriteContents spriteContents, int i, int j, int k, int l, int m) {
		return j >= 0 && k >= 0 && j < l && k < m ? spriteContents.isTransparent(i, j, k) : true;
	}

	@Environment(EnvType.CLIENT)
	static class Span {
		private final ItemModelGenerator.SpanFacing facing;
		private int min;
		private int max;
		private final int anchor;

		public Span(ItemModelGenerator.SpanFacing spanFacing, int i, int j) {
			this.facing = spanFacing;
			this.min = i;
			this.max = i;
			this.anchor = j;
		}

		public void expand(int i) {
			if (i < this.min) {
				this.min = i;
			} else if (i > this.max) {
				this.max = i;
			}
		}

		public ItemModelGenerator.SpanFacing getFacing() {
			return this.facing;
		}

		public int getMin() {
			return this.min;
		}

		public int getMax() {
			return this.max;
		}

		public int getAnchor() {
			return this.anchor;
		}
	}

	@Environment(EnvType.CLIENT)
	static enum SpanFacing {
		UP(Direction.UP, 0, -1),
		DOWN(Direction.DOWN, 0, 1),
		LEFT(Direction.EAST, -1, 0),
		RIGHT(Direction.WEST, 1, 0);

		private final Direction direction;
		private final int xOffset;
		private final int yOffset;

		private SpanFacing(final Direction direction, final int j, final int k) {
			this.direction = direction;
			this.xOffset = j;
			this.yOffset = k;
		}

		public Direction getDirection() {
			return this.direction;
		}

		public int getXOffset() {
			return this.xOffset;
		}

		public int getYOffset() {
			return this.yOffset;
		}

		boolean isHorizontal() {
			return this == DOWN || this == UP;
		}
	}
}
