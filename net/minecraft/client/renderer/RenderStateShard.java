package net.minecraft.client.renderer;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

@Environment(EnvType.CLIENT)
public abstract class RenderStateShard {
	public static final double MAX_ENCHANTMENT_GLINT_SPEED_MILLIS = 8.0;
	protected final String name;
	private final Runnable setupState;
	private final Runnable clearState;
	public static final RenderStateShard.TextureStateShard BLOCK_SHEET_MIPPED = new RenderStateShard.TextureStateShard(TextureAtlas.LOCATION_BLOCKS, true);
	public static final RenderStateShard.TextureStateShard BLOCK_SHEET = new RenderStateShard.TextureStateShard(TextureAtlas.LOCATION_BLOCKS, false);
	public static final RenderStateShard.EmptyTextureStateShard NO_TEXTURE = new RenderStateShard.EmptyTextureStateShard();
	public static final RenderStateShard.TexturingStateShard DEFAULT_TEXTURING = new RenderStateShard.TexturingStateShard("default_texturing", () -> {}, () -> {});
	public static final RenderStateShard.TexturingStateShard GLINT_TEXTURING = new RenderStateShard.TexturingStateShard(
		"glint_texturing", () -> setupGlintTexturing(8.0F), RenderSystem::resetTextureMatrix
	);
	public static final RenderStateShard.TexturingStateShard ENTITY_GLINT_TEXTURING = new RenderStateShard.TexturingStateShard(
		"entity_glint_texturing", () -> setupGlintTexturing(0.5F), RenderSystem::resetTextureMatrix
	);
	public static final RenderStateShard.TexturingStateShard ARMOR_ENTITY_GLINT_TEXTURING = new RenderStateShard.TexturingStateShard(
		"armor_entity_glint_texturing", () -> setupGlintTexturing(0.16F), RenderSystem::resetTextureMatrix
	);
	public static final RenderStateShard.LightmapStateShard LIGHTMAP = new RenderStateShard.LightmapStateShard(true);
	public static final RenderStateShard.LightmapStateShard NO_LIGHTMAP = new RenderStateShard.LightmapStateShard(false);
	public static final RenderStateShard.OverlayStateShard OVERLAY = new RenderStateShard.OverlayStateShard(true);
	public static final RenderStateShard.OverlayStateShard NO_OVERLAY = new RenderStateShard.OverlayStateShard(false);
	public static final RenderStateShard.LayeringStateShard NO_LAYERING = new RenderStateShard.LayeringStateShard("no_layering", () -> {}, () -> {});
	public static final RenderStateShard.LayeringStateShard VIEW_OFFSET_Z_LAYERING = new RenderStateShard.LayeringStateShard("view_offset_z_layering", () -> {
		Matrix4fStack matrix4fStack = RenderSystem.getModelViewStack();
		matrix4fStack.pushMatrix();
		RenderSystem.getProjectionType().applyLayeringTransform(matrix4fStack, 1.0F);
	}, () -> {
		Matrix4fStack matrix4fStack = RenderSystem.getModelViewStack();
		matrix4fStack.popMatrix();
	});
	public static final RenderStateShard.LayeringStateShard VIEW_OFFSET_Z_LAYERING_FORWARD = new RenderStateShard.LayeringStateShard(
		"view_offset_z_layering_forward", () -> {
			Matrix4fStack matrix4fStack = RenderSystem.getModelViewStack();
			matrix4fStack.pushMatrix();
			RenderSystem.getProjectionType().applyLayeringTransform(matrix4fStack, -1.0F);
		}, () -> {
			Matrix4fStack matrix4fStack = RenderSystem.getModelViewStack();
			matrix4fStack.popMatrix();
		}
	);
	public static final RenderStateShard.OutputStateShard MAIN_TARGET = new RenderStateShard.OutputStateShard(
		"main_target", () -> Minecraft.getInstance().getMainRenderTarget()
	);
	public static final RenderStateShard.OutputStateShard OUTLINE_TARGET = new RenderStateShard.OutputStateShard("outline_target", () -> {
		RenderTarget renderTarget = Minecraft.getInstance().levelRenderer.entityOutlineTarget();
		return renderTarget != null ? renderTarget : Minecraft.getInstance().getMainRenderTarget();
	});
	public static final RenderStateShard.OutputStateShard WEATHER_TARGET = new RenderStateShard.OutputStateShard("weather_target", () -> {
		RenderTarget renderTarget = Minecraft.getInstance().levelRenderer.getWeatherTarget();
		return renderTarget != null ? renderTarget : Minecraft.getInstance().getMainRenderTarget();
	});
	public static final RenderStateShard.OutputStateShard ITEM_ENTITY_TARGET = new RenderStateShard.OutputStateShard("item_entity_target", () -> {
		RenderTarget renderTarget = Minecraft.getInstance().levelRenderer.getItemEntityTarget();
		return renderTarget != null ? renderTarget : Minecraft.getInstance().getMainRenderTarget();
	});
	public static final RenderStateShard.LineStateShard DEFAULT_LINE = new RenderStateShard.LineStateShard(OptionalDouble.of(1.0));

	public RenderStateShard(String string, Runnable runnable, Runnable runnable2) {
		this.name = string;
		this.setupState = runnable;
		this.clearState = runnable2;
	}

	public void setupRenderState() {
		this.setupState.run();
	}

	public void clearRenderState() {
		this.clearState.run();
	}

	public String toString() {
		return this.name;
	}

	public String getName() {
		return this.name;
	}

	private static void setupGlintTexturing(float f) {
		long l = (long)(Util.getMillis() * Minecraft.getInstance().options.glintSpeed().get() * 8.0);
		float g = (float)(l % 110000L) / 110000.0F;
		float h = (float)(l % 30000L) / 30000.0F;
		Matrix4f matrix4f = new Matrix4f().translation(-g, h, 0.0F);
		matrix4f.rotateZ((float) (Math.PI / 18)).scale(f);
		RenderSystem.setTextureMatrix(matrix4f);
	}

	@Environment(EnvType.CLIENT)
	static class BooleanStateShard extends RenderStateShard {
		private final boolean enabled;

		public BooleanStateShard(String string, Runnable runnable, Runnable runnable2, boolean bl) {
			super(string, runnable, runnable2);
			this.enabled = bl;
		}

		@Override
		public String toString() {
			return this.name + "[" + this.enabled + "]";
		}
	}

	@Environment(EnvType.CLIENT)
	public static class EmptyTextureStateShard extends RenderStateShard {
		public EmptyTextureStateShard(Runnable runnable, Runnable runnable2) {
			super("texture", runnable, runnable2);
		}

		EmptyTextureStateShard() {
			super("texture", () -> {}, () -> {});
		}

		protected Optional<ResourceLocation> cutoutTexture() {
			return Optional.empty();
		}
	}

	@Environment(EnvType.CLIENT)
	public static class LayeringStateShard extends RenderStateShard {
		public LayeringStateShard(String string, Runnable runnable, Runnable runnable2) {
			super(string, runnable, runnable2);
		}
	}

	@Environment(EnvType.CLIENT)
	public static class LightmapStateShard extends RenderStateShard.BooleanStateShard {
		public LightmapStateShard(boolean bl) {
			super("lightmap", () -> {
				if (bl) {
					Minecraft.getInstance().gameRenderer.lightTexture().turnOnLightLayer();
				}
			}, () -> {
				if (bl) {
					Minecraft.getInstance().gameRenderer.lightTexture().turnOffLightLayer();
				}
			}, bl);
		}
	}

	@Environment(EnvType.CLIENT)
	public static class LineStateShard extends RenderStateShard {
		private final OptionalDouble width;

		public LineStateShard(OptionalDouble optionalDouble) {
			super("line_width", () -> {
				if (!Objects.equals(optionalDouble, OptionalDouble.of(1.0))) {
					if (optionalDouble.isPresent()) {
						RenderSystem.lineWidth((float)optionalDouble.getAsDouble());
					} else {
						RenderSystem.lineWidth(Math.max(2.5F, Minecraft.getInstance().getWindow().getWidth() / 1920.0F * 2.5F));
					}
				}
			}, () -> {
				if (!Objects.equals(optionalDouble, OptionalDouble.of(1.0))) {
					RenderSystem.lineWidth(1.0F);
				}
			});
			this.width = optionalDouble;
		}

		@Override
		public String toString() {
			return this.name + "[" + (this.width.isPresent() ? this.width.getAsDouble() : "window_scale") + "]";
		}
	}

	@Environment(EnvType.CLIENT)
	public static class MultiTextureStateShard extends RenderStateShard.EmptyTextureStateShard {
		private final Optional<ResourceLocation> cutoutTexture;

		MultiTextureStateShard(List<RenderStateShard.MultiTextureStateShard.Entry> list) {
			super(() -> {
				for (int i = 0; i < list.size(); i++) {
					RenderStateShard.MultiTextureStateShard.Entry entry = (RenderStateShard.MultiTextureStateShard.Entry)list.get(i);
					TextureManager textureManager = Minecraft.getInstance().getTextureManager();
					AbstractTexture abstractTexture = textureManager.getTexture(entry.id);
					abstractTexture.setUseMipmaps(entry.mipmap);
					RenderSystem.setShaderTexture(i, abstractTexture.getTextureView());
				}
			}, () -> {});
			this.cutoutTexture = list.isEmpty() ? Optional.empty() : Optional.of(((RenderStateShard.MultiTextureStateShard.Entry)list.getFirst()).id);
		}

		@Override
		protected Optional<ResourceLocation> cutoutTexture() {
			return this.cutoutTexture;
		}

		public static RenderStateShard.MultiTextureStateShard.Builder builder() {
			return new RenderStateShard.MultiTextureStateShard.Builder();
		}

		@Environment(EnvType.CLIENT)
		public static final class Builder {
			private final ImmutableList.Builder<RenderStateShard.MultiTextureStateShard.Entry> builder = new ImmutableList.Builder<>();

			public RenderStateShard.MultiTextureStateShard.Builder add(ResourceLocation resourceLocation, boolean bl) {
				this.builder.add(new RenderStateShard.MultiTextureStateShard.Entry(resourceLocation, bl));
				return this;
			}

			public RenderStateShard.MultiTextureStateShard build() {
				return new RenderStateShard.MultiTextureStateShard(this.builder.build());
			}
		}

		@Environment(EnvType.CLIENT)
		record Entry(ResourceLocation id, boolean mipmap) {
		}
	}

	@Environment(EnvType.CLIENT)
	public static final class OffsetTexturingStateShard extends RenderStateShard.TexturingStateShard {
		public OffsetTexturingStateShard(float f, float g) {
			super("offset_texturing", () -> RenderSystem.setTextureMatrix(new Matrix4f().translation(f, g, 0.0F)), () -> RenderSystem.resetTextureMatrix());
		}
	}

	@Environment(EnvType.CLIENT)
	public static class OutputStateShard extends RenderStateShard {
		private final Supplier<RenderTarget> renderTargetSupplier;

		public OutputStateShard(String string, Supplier<RenderTarget> supplier) {
			super(string, () -> {}, () -> {});
			this.renderTargetSupplier = supplier;
		}

		public RenderTarget getRenderTarget() {
			return (RenderTarget)this.renderTargetSupplier.get();
		}
	}

	@Environment(EnvType.CLIENT)
	public static class OverlayStateShard extends RenderStateShard.BooleanStateShard {
		public OverlayStateShard(boolean bl) {
			super("overlay", () -> {
				if (bl) {
					Minecraft.getInstance().gameRenderer.overlayTexture().setupOverlayColor();
				}
			}, () -> {
				if (bl) {
					Minecraft.getInstance().gameRenderer.overlayTexture().teardownOverlayColor();
				}
			}, bl);
		}
	}

	@Environment(EnvType.CLIENT)
	public static class TextureStateShard extends RenderStateShard.EmptyTextureStateShard {
		private final Optional<ResourceLocation> texture;
		private final boolean mipmap;

		public TextureStateShard(ResourceLocation resourceLocation, boolean bl) {
			super(() -> {
				TextureManager textureManager = Minecraft.getInstance().getTextureManager();
				AbstractTexture abstractTexture = textureManager.getTexture(resourceLocation);
				abstractTexture.setUseMipmaps(bl);
				RenderSystem.setShaderTexture(0, abstractTexture.getTextureView());
			}, () -> {});
			this.texture = Optional.of(resourceLocation);
			this.mipmap = bl;
		}

		@Override
		public String toString() {
			return this.name + "[" + this.texture + "(mipmap=" + this.mipmap + ")]";
		}

		@Override
		protected Optional<ResourceLocation> cutoutTexture() {
			return this.texture;
		}
	}

	@Environment(EnvType.CLIENT)
	public static class TexturingStateShard extends RenderStateShard {
		public TexturingStateShard(String string, Runnable runnable, Runnable runnable2) {
			super(string, runnable, runnable2);
		}
	}
}
