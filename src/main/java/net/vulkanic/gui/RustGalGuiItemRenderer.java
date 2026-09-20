package net.vulkanic.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.dev.GraphicsFrameBenchmark;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.state.GuiItemRenderState;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.model.TridentModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.ChestModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.special.TridentSpecialRenderer;
import net.minecraft.client.renderer.special.ChestSpecialRenderer;
import net.minecraft.client.renderer.special.HangingSignSpecialRenderer;
import net.minecraft.client.renderer.special.StandingSignSpecialRenderer;
import net.minecraft.client.renderer.special.CopperGolemStatueSpecialRenderer;
import net.minecraft.client.renderer.special.BedSpecialRenderer;
import net.minecraft.client.renderer.special.ShieldSpecialRenderer;
import net.minecraft.client.renderer.special.BannerSpecialRenderer;
import net.minecraft.client.renderer.special.SkullSpecialRenderer;
import net.minecraft.client.renderer.special.PlayerHeadSpecialRenderer;
import net.minecraft.client.renderer.special.ShulkerBoxSpecialRenderer;
import net.minecraft.client.renderer.blockentity.ShulkerBoxRenderer;
import net.minecraft.client.renderer.special.ConduitSpecialRenderer;
import net.minecraft.client.renderer.blockentity.ConduitRenderer;
import net.minecraft.client.renderer.special.DecoratedPotSpecialRenderer;
import net.minecraft.client.renderer.special.TaczGlock17SpecialRenderer;
import net.minecraft.client.renderer.blockentity.DecoratedPotRenderer;
import net.minecraft.world.level.block.entity.PotDecorations;
import net.minecraft.client.model.SkullModelBase;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.model.BannerModel;
import net.minecraft.client.model.BannerFlagModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.item.DyeColor;
import net.math.Axis;
import net.minecraft.client.renderer.blockentity.HangingSignRenderer;
import net.minecraft.client.renderer.blockentity.SignRenderer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.vulkanic.bridge.VulkanicGalBridge;
import net.vulkanic.bridge.RustGalFrameScheduler;
import net.sodium.client.model.quad.BakedQuadView;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Java-side immutable GUI-item semantic extraction. Original model geometry,
 * transforms and owned resource declarations cross the boundary; no item
 * renderer, atlas object or backend GPU state crosses into Rust.
 */
public final class RustGalGuiItemRenderer {
	private static final List<Integer> QUAD_INDICES = List.of(0, 1, 2, 2, 3, 0);
	private static final boolean STANDARD_3D_ROUTE_DISABLED = Boolean.getBoolean("mattmc.rustGal.gui.standard3d.disabled");
	private static final boolean DEBUG_STANDARD_3D_ITEM_ENABLED = Boolean.getBoolean("mattmc.rustGal.gui.standard3d.debugItem");
	private static final int MAX_DIAGNOSTIC_ENTRIES = 256;
	private static final Map<String, Boolean> DIAGNOSTICS = new HashMap<>();
	private static final ThreadLocal<TaczGuiPackedStaging> TACZ_GUI_STAGING =
		ThreadLocal.withInitial(TaczGuiPackedStaging::new);

	private RustGalGuiItemRenderer() {
	}

	/**
	 * Copies the bounded vanilla trident special model into the same Rust GUI
	 * mesh family used by picture-in-picture models.  The special renderer is
	 * inspected only as a Java semantic producer; no renderer, model, or GPU
	 * object is retained after this call.
	 */
	public static List<RustGalGuiElementRenderState> tryEnqueueSpecialItem(
		GuiItemRenderState item, int guiWidth, int guiHeight, @Nullable Integer dynamicLayerOrder
	) {
		if (RustGalGuiRenderer.currentExecutionRoute() != RustGalGuiRenderer.GuiExecutionRoute.RUST_VULKAN_WHOLE_FRAME
			|| !item.itemStackRenderState().hasSpecialRenderer()) return List.of();
		final ItemStackRenderState.SpecialRender[] selected = new ItemStackRenderState.SpecialRender[1];
		final boolean[] foil = new boolean[1];
		item.itemStackRenderState().forEachSemanticLayer(layer -> {
			if (layer.foilType() != ItemStackRenderState.FoilType.NONE) foil[0] = true;
		});
		item.itemStackRenderState().forEachSpecialRenderer(render -> {
			if (selected[0] == null) selected[0] = render;
		});
		Object selectedRenderer = selected[0] == null ? null : (Object)selected[0].renderer();
		if (selected[0] == null || (foil[0]
			&& !(selectedRenderer instanceof TaczGlock17SpecialRenderer)
			&& !(selectedRenderer instanceof TridentSpecialRenderer)
			&& !(selectedRenderer instanceof ShieldSpecialRenderer)
			&& !(selectedRenderer instanceof BannerSpecialRenderer)
			&& !(selectedRenderer instanceof SkullSpecialRenderer)
			&& !(selectedRenderer instanceof PlayerHeadSpecialRenderer)
			&& !(selectedRenderer instanceof ShulkerBoxSpecialRenderer)
			&& !(selectedRenderer instanceof BedSpecialRenderer)
			&& !(selectedRenderer instanceof ChestSpecialRenderer)
			&& !(selectedRenderer instanceof HangingSignSpecialRenderer)
			&& !(selectedRenderer instanceof StandingSignSpecialRenderer)
			&& !(selectedRenderer instanceof CopperGolemStatueSpecialRenderer)
			&& !(selectedRenderer instanceof ConduitSpecialRenderer)
			&& !(selectedRenderer instanceof DecoratedPotSpecialRenderer))) {
			recordDiagnostic(selected[0] == null ? "special-renderer-empty" : foil[0] ? "special-renderer-foil" : "special-renderer-unavailable");
			return List.of();
		}
		ScreenRectangle bounds = item.bounds();
		int left = bounds == null ? item.x() : bounds.left();
		int top = bounds == null ? item.y() : bounds.top();
		int right = bounds == null ? item.x() + 16 : bounds.right();
		int bottom = bounds == null ? item.y() + 16 : bounds.bottom();
		List<RustGalGuiElementRenderState> result;
		String rejectionName;
		if (selectedRenderer instanceof TridentSpecialRenderer trident) {
			List<RustGalGuiElementRenderState> tridentBase = RustGalGuiRenderer.tryEnqueueModelPip(
				trident.model(), TridentModel.TEXTURE, left, top, right, bottom, 1.0F,
				item.pose(), item.scissorArea(), dynamicLayerOrder,
				pose -> {
					pose.mulPose(selected[0].transform().pose());
					pose.scale(1.0F, -1.0F, -1.0F);
				}
			);
			if (foil[0]) {
				List<RustGalGuiElementRenderState> tridentGlint = RustGalGuiRenderer.tryEnqueueModelPip(
					trident.model(), ItemRenderer.ENCHANTED_GLINT_ITEM, left, top, right, bottom, 1.0F,
					item.pose(), item.scissorArea(), dynamicLayerOrder,
					pose -> {
						pose.mulPose(selected[0].transform().pose());
						pose.scale(1.0F, -1.0F, -1.0F);
					}, 0xffffffff, 4
				);
				List<RustGalGuiElementRenderState> combined = new ArrayList<>();
				if (tridentBase != null) combined.addAll(tridentBase);
				if (tridentGlint != null) combined.addAll(tridentGlint);
				result = combined;
			} else {
				result = tridentBase;
			}
			rejectionName = "special-renderer-trident-rejected";
		} else if (selectedRenderer instanceof ChestSpecialRenderer chest) {
			ChestModel model = chest.model();
			model.setupAnim(chest.openness());
			result = RustGalGuiRenderer.tryEnqueueModelPip(
				model, chest.material().texture(), left, top, right, bottom, 1.0F,
				item.pose(), item.scissorArea(), dynamicLayerOrder,
				pose -> pose.mulPose(selected[0].transform().pose())
			);
			if (foil[0]) {
				List<RustGalGuiElementRenderState> glint = RustGalGuiRenderer.tryEnqueueModelPip(
					model, ItemRenderer.ENCHANTED_GLINT_ITEM, left, top, right, bottom, 1.0F,
					item.pose(), item.scissorArea(), dynamicLayerOrder,
					pose -> {
						model.setupAnim(chest.openness());
						pose.mulPose(selected[0].transform().pose());
					}, 0xffffffff, 4
				);
				if (glint != null) {
					List<RustGalGuiElementRenderState> combined = new ArrayList<>(result == null ? List.of() : result);
					combined.addAll(glint);
					result = combined;
				}
			}
			rejectionName = "special-renderer-chest-rejected";
		} else if (selectedRenderer instanceof HangingSignSpecialRenderer hangingSign) {
			result = RustGalGuiRenderer.tryEnqueueModelPip(
				hangingSign.model(), hangingSign.material().texture(), left, top, right, bottom, 1.0F,
				item.pose(), item.scissorArea(), dynamicLayerOrder,
				pose -> {
					HangingSignRenderer.translateBase(pose, 0.0F);
					pose.scale(1.0F, -1.0F, -1.0F);
					pose.mulPose(selected[0].transform().pose());
				}
			);
			if (foil[0]) {
				List<RustGalGuiElementRenderState> glint = RustGalGuiRenderer.tryEnqueueModelPip(
					hangingSign.model(), ItemRenderer.ENCHANTED_GLINT_ITEM, left, top, right, bottom, 1.0F,
					item.pose(), item.scissorArea(), dynamicLayerOrder,
					pose -> {
						HangingSignRenderer.translateBase(pose, 0.0F);
						pose.scale(1.0F, -1.0F, -1.0F);
						pose.mulPose(selected[0].transform().pose());
					}, 0xffffffff, 4
				);
				if (glint != null) {
					List<RustGalGuiElementRenderState> combined = new ArrayList<>(result == null ? List.of() : result);
					combined.addAll(glint);
					result = combined;
				}
			}
			rejectionName = "special-renderer-hanging-sign-rejected";
		} else if (selectedRenderer instanceof StandingSignSpecialRenderer standingSign) {
			result = RustGalGuiRenderer.tryEnqueueModelPip(
				standingSign.model(), standingSign.material().texture(), left, top, right, bottom, 1.0F,
				item.pose(), item.scissorArea(), dynamicLayerOrder,
				pose -> {
					SignRenderer.applyInHandTransforms(pose);
					pose.mulPose(selected[0].transform().pose());
				}
			);
			if (foil[0]) {
				List<RustGalGuiElementRenderState> glint = RustGalGuiRenderer.tryEnqueueModelPip(
					standingSign.model(), ItemRenderer.ENCHANTED_GLINT_ITEM, left, top, right, bottom, 1.0F,
					item.pose(), item.scissorArea(), dynamicLayerOrder,
					pose -> {
						SignRenderer.applyInHandTransforms(pose);
						pose.mulPose(selected[0].transform().pose());
					}, 0xffffffff, 4
				);
				if (glint != null) {
					List<RustGalGuiElementRenderState> combined = new ArrayList<>(result == null ? List.of() : result);
					combined.addAll(glint);
					result = combined;
				}
			}
			rejectionName = "special-renderer-standing-sign-rejected";
		} else if (selectedRenderer instanceof CopperGolemStatueSpecialRenderer copperGolem) {
			result = RustGalGuiRenderer.tryEnqueueModelPip(
				copperGolem.model(), copperGolem.texture(), left, top, right, bottom, 1.0F,
				item.pose(), item.scissorArea(), dynamicLayerOrder,
				pose -> {
					pose.translate(0.5F, 1.5F, 0.5F);
					pose.scale(-1.0F, -1.0F, 1.0F);
					pose.mulPose(selected[0].transform().pose());
				}
			);
			if (foil[0]) {
				List<RustGalGuiElementRenderState> glint = RustGalGuiRenderer.tryEnqueueModelPip(
					copperGolem.model(), ItemRenderer.ENCHANTED_GLINT_ITEM, left, top, right, bottom, 1.0F,
					item.pose(), item.scissorArea(), dynamicLayerOrder,
					pose -> {
						pose.translate(0.5F, 1.5F, 0.5F);
						pose.scale(-1.0F, -1.0F, 1.0F);
						pose.mulPose(selected[0].transform().pose());
					}, 0xffffffff, 4
				);
				if (glint != null) {
					List<RustGalGuiElementRenderState> combined = new ArrayList<>(result == null ? List.of() : result);
					combined.addAll(glint);
					result = combined;
				}
			}
			rejectionName = "special-renderer-copper-golem-rejected";
		} else if (selectedRenderer instanceof BedSpecialRenderer bed) {
			List<RustGalGuiElementRenderState> pieces = new ArrayList<>(2);
			for (int piece = 0; piece < 2; piece++) {
				boolean foot = piece == 1;
				List<RustGalGuiElementRenderState> pieceElements = RustGalGuiRenderer.tryEnqueueModelPip(
					foot ? bed.footModel() : bed.headModel(), bed.material().texture(), left, top, right, bottom, 1.0F,
					item.pose(), item.scissorArea(), dynamicLayerOrder,
					pose -> {
						pose.translate(0.0F, 0.5625F, foot ? -1.0F : 0.0F);
						pose.mulPose(Axis.XP.rotationDegrees(90.0F));
						pose.translate(0.5F, 0.5F, 0.5F);
						pose.mulPose(Axis.ZP.rotationDegrees(180.0F));
						pose.translate(-0.5F, -0.5F, -0.5F);
						pose.mulPose(selected[0].transform().pose());
					}
				);
				if (pieceElements != null) pieces.addAll(pieceElements);
			}
			if (foil[0]) {
				for (int piece = 0; piece < 2; piece++) {
					boolean foot = piece == 1;
					List<RustGalGuiElementRenderState> glint = RustGalGuiRenderer.tryEnqueueModelPip(
						foot ? bed.footModel() : bed.headModel(), ItemRenderer.ENCHANTED_GLINT_ITEM,
						left, top, right, bottom, 1.0F, item.pose(), item.scissorArea(), dynamicLayerOrder,
						pose -> {
							pose.translate(0.0F, 0.5625F, foot ? -1.0F : 0.0F);
							pose.mulPose(Axis.XP.rotationDegrees(90.0F));
							pose.translate(0.5F, 0.5F, 0.5F);
							pose.mulPose(Axis.ZP.rotationDegrees(180.0F));
							pose.translate(-0.5F, -0.5F, -0.5F);
							pose.mulPose(selected[0].transform().pose());
						}, 0xffffffff, 4
					);
					if (glint != null) pieces.addAll(glint);
				}
			}
			result = pieces;
			rejectionName = "special-renderer-bed-rejected";
		} else if (selectedRenderer instanceof ShieldSpecialRenderer shield) {
			return tryEnqueueShieldItem(item, selected[0], shield, foil[0], guiWidth, guiHeight, dynamicLayerOrder);
		} else if (selectedRenderer instanceof BannerSpecialRenderer banner) {
			BannerRenderer renderer = banner.bannerRenderer();
			BannerModel model = renderer.standingModel();
			BannerFlagModel flagModel = renderer.standingFlagModel();
			BannerPatternLayers patterns = selected[0].argument() instanceof BannerPatternLayers layers
				? layers : BannerPatternLayers.EMPTY;
			List<RustGalGuiElementRenderState> bannerLayers = new ArrayList<>();
			List<RustGalGuiElementRenderState> base = RustGalGuiRenderer.tryEnqueueModelPip(
				model, ModelBakery.BANNER_BASE.texture(), left, top, right, bottom, 1.0F,
				item.pose(), item.scissorArea(), dynamicLayerOrder,
				pose -> {
					pose.translate(0.5F, 0.0F, 0.5F);
					pose.scale(0.6666667F, -0.6666667F, -0.6666667F);
					pose.mulPose(selected[0].transform().pose());
				}
			);
			if (base != null) bannerLayers.addAll(base);
			int baseTint = banner.baseColor().getTextureDiffuseColor();
			List<RustGalGuiElementRenderState> flagBase = RustGalGuiRenderer.tryEnqueueModelPip(
				flagModel, ModelBakery.BANNER_BASE.texture(), left, top, right, bottom, 1.0F,
				item.pose(), item.scissorArea(), dynamicLayerOrder,
				pose -> {
					pose.translate(0.5F, 0.0F, 0.5F);
					pose.scale(0.6666667F, -0.6666667F, -0.6666667F);
					pose.mulPose(selected[0].transform().pose());
				}, baseTint
			);
			if (flagBase != null) bannerLayers.addAll(flagBase);
			for (BannerPatternLayers.Layer layer : patterns.layers().subList(0, Math.min(16, patterns.layers().size()))) {
				List<RustGalGuiElementRenderState> pattern = RustGalGuiRenderer.tryEnqueueModelPip(
					flagModel, net.minecraft.client.renderer.Sheets.getBannerMaterial(layer.pattern()).texture(),
					left, top, right, bottom, 1.0F, item.pose(), item.scissorArea(), dynamicLayerOrder,
					pose -> {
						pose.translate(0.5F, 0.0F, 0.5F);
						pose.scale(0.6666667F, -0.6666667F, -0.6666667F);
						pose.mulPose(selected[0].transform().pose());
					}, layer.color().getTextureDiffuseColor()
				);
				if (pattern != null) bannerLayers.addAll(pattern);
			}
			if (foil[0]) {
				List<RustGalGuiElementRenderState> glint = RustGalGuiRenderer.tryEnqueueModelPip(
					flagModel, ItemRenderer.ENCHANTED_GLINT_ITEM, left, top, right, bottom, 1.0F,
					item.pose(), item.scissorArea(), dynamicLayerOrder,
					pose -> {
						pose.translate(0.5F, 0.0F, 0.5F);
						pose.scale(0.6666667F, -0.6666667F, -0.6666667F);
						pose.mulPose(selected[0].transform().pose());
					}, 0xffffffff, 4
				);
				if (glint != null) bannerLayers.addAll(glint);
			}
			result = bannerLayers;
			rejectionName = "special-renderer-banner-rejected";
		} else if (selectedRenderer instanceof SkullSpecialRenderer skull) {
			ResourceLocation texture = skull.texture();
			if (texture == null) {
				recordDiagnostic("special-renderer-skull-texture-unavailable");
				return List.of();
			}
			result = RustGalGuiRenderer.tryEnqueueModelPip(
				skull.model(), texture, left, top, right, bottom, 1.0F,
				item.pose(), item.scissorArea(), dynamicLayerOrder,
				pose -> {
					pose.translate(0.5F, 0.0F, 0.5F);
					pose.scale(-1.0F, -1.0F, 1.0F);
					SkullModelBase.State state = new SkullModelBase.State();
					state.animationPos = skull.animation();
					state.yRot = 180.0F;
					 skull.model().setupAnim(state);
					pose.mulPose(selected[0].transform().pose());
				}
			);
			if (foil[0]) {
				List<RustGalGuiElementRenderState> glint = RustGalGuiRenderer.tryEnqueueModelPip(
					skull.model(), ItemRenderer.ENCHANTED_GLINT_ITEM, left, top, right, bottom, 1.0F,
					item.pose(), item.scissorArea(), dynamicLayerOrder,
					pose -> {
						pose.translate(0.5F, 0.0F, 0.5F);
						pose.scale(-1.0F, -1.0F, 1.0F);
						SkullModelBase.State state = new SkullModelBase.State();
						state.animationPos = skull.animation();
						state.yRot = 180.0F;
						skull.model().setupAnim(state);
						pose.mulPose(selected[0].transform().pose());
					}, 0xffffffff, 4
				);
				if (glint != null) {
					List<RustGalGuiElementRenderState> combined = new ArrayList<>(result == null ? List.of() : result);
					combined.addAll(glint);
					result = combined;
				}
			}
			rejectionName = "special-renderer-skull-rejected";
		} else if (selectedRenderer instanceof PlayerHeadSpecialRenderer playerHead) {
			ResourceLocation texture = DefaultPlayerSkin.getDefaultTexture();
			if (selected[0].argument() instanceof PlayerSkinRenderCache.RenderInfo info) {
				texture = info.playerSkin().body().texturePath();
			}
			result = RustGalGuiRenderer.tryEnqueueModelPip(
				playerHead.model(), texture, left, top, right, bottom, 1.0F,
				item.pose(), item.scissorArea(), dynamicLayerOrder,
				pose -> {
					pose.translate(0.5F, 0.0F, 0.5F);
					pose.scale(-1.0F, -1.0F, 1.0F);
					SkullModelBase.State state = new SkullModelBase.State();
					state.yRot = 180.0F;
					playerHead.model().setupAnim(state);
					pose.mulPose(selected[0].transform().pose());
				}
			);
			if (foil[0]) {
				List<RustGalGuiElementRenderState> glint = RustGalGuiRenderer.tryEnqueueModelPip(
					playerHead.model(), ItemRenderer.ENCHANTED_GLINT_ITEM, left, top, right, bottom, 1.0F,
					item.pose(), item.scissorArea(), dynamicLayerOrder,
					pose -> {
						pose.translate(0.5F, 0.0F, 0.5F);
						pose.scale(-1.0F, -1.0F, 1.0F);
						SkullModelBase.State state = new SkullModelBase.State();
						state.yRot = 180.0F;
						playerHead.model().setupAnim(state);
						pose.mulPose(selected[0].transform().pose());
					}, 0xffffffff, 4
				);
				if (glint != null) {
					List<RustGalGuiElementRenderState> combined = new ArrayList<>(result == null ? List.of() : result);
					combined.addAll(glint);
					result = combined;
				}
			}
			rejectionName = "special-renderer-player-head-rejected";
		} else if (selectedRenderer instanceof ShulkerBoxSpecialRenderer shulker) {
			ShulkerBoxRenderer renderer = shulker.renderer();
			ShulkerBoxRenderer.ShulkerBoxModel model = renderer.model();
			model.setupAnim(shulker.openness());
			result = RustGalGuiRenderer.tryEnqueueModelPip(
				model, shulker.material().texture(), left, top, right, bottom, 1.0F,
				item.pose(), item.scissorArea(), dynamicLayerOrder,
				pose -> {
					pose.translate(0.5F, 0.5F, 0.5F);
					pose.scale(0.9995F, 0.9995F, 0.9995F);
					pose.mulPose(shulker.orientation().getRotation());
					pose.scale(1.0F, -1.0F, -1.0F);
					pose.translate(0.0F, -1.0F, 0.0F);
					pose.mulPose(selected[0].transform().pose());
				}
			);
			if (foil[0]) {
				List<RustGalGuiElementRenderState> glint = RustGalGuiRenderer.tryEnqueueModelPip(
					model, ItemRenderer.ENCHANTED_GLINT_ITEM, left, top, right, bottom, 1.0F,
					item.pose(), item.scissorArea(), dynamicLayerOrder,
					pose -> {
						pose.translate(0.5F, 0.5F, 0.5F);
						pose.scale(0.9995F, 0.9995F, 0.9995F);
						pose.mulPose(shulker.orientation().getRotation());
						pose.scale(1.0F, -1.0F, -1.0F);
						pose.translate(0.0F, -1.0F, 0.0F);
						pose.mulPose(selected[0].transform().pose());
					}, 0xffffffff, 4
				);
				if (glint != null) {
					List<RustGalGuiElementRenderState> combined = new ArrayList<>(result == null ? List.of() : result);
					combined.addAll(glint);
					result = combined;
				}
			}
			rejectionName = "special-renderer-shulker-rejected";
		} else if (selectedRenderer instanceof ConduitSpecialRenderer conduit) {
			result = RustGalGuiRenderer.tryEnqueueModelPartPip(
				conduit.model(), ConduitRenderer.SHELL_TEXTURE.texture(), left, top, right, bottom, 1.0F,
				item.pose(), item.scissorArea(), dynamicLayerOrder,
				pose -> {
					pose.translate(0.5F, 0.5F, 0.5F);
					pose.mulPose(selected[0].transform().pose());
				}
			);
			if (foil[0]) {
				List<RustGalGuiElementRenderState> glint = RustGalGuiRenderer.tryEnqueueModelPip(
					new Model.Simple(conduit.model(), RenderType::entitySolid), ItemRenderer.ENCHANTED_GLINT_ITEM,
					left, top, right, bottom, 1.0F, item.pose(), item.scissorArea(), dynamicLayerOrder,
					pose -> {
						pose.translate(0.5F, 0.5F, 0.5F);
						pose.mulPose(selected[0].transform().pose());
					}, 0xffffffff, 4
				);
				if (glint != null) {
					List<RustGalGuiElementRenderState> combined = new ArrayList<>(result == null ? List.of() : result);
					combined.addAll(glint);
					result = combined;
				}
			}
			rejectionName = "special-renderer-conduit-rejected";
		} else if (selectedRenderer instanceof DecoratedPotSpecialRenderer pot) {
			DecoratedPotRenderer renderer = pot.renderer();
			PotDecorations decorations = selected[0].argument() instanceof PotDecorations value
				? value : PotDecorations.EMPTY;
			List<RustGalGuiElementRenderState> potParts = new ArrayList<>(7);
			List<RustGalGuiElementRenderState> baseParts = enqueueModelParts(
				List.of(renderer.neckPart(), renderer.topPart(), renderer.bottomPart()),
				net.minecraft.client.renderer.Sheets.DECORATED_POT_BASE.texture(), left, top, right, bottom, item, dynamicLayerOrder, selected[0]
			);
			if (baseParts != null) potParts.addAll(baseParts);
			List<RustGalGuiElementRenderState> front = enqueueModelPart(
				renderer.frontSidePart(), renderer.sideMaterial(decorations.front()).texture(), left, top, right, bottom,
				item, dynamicLayerOrder, selected[0]
			);
			if (front != null) potParts.addAll(front);
			List<RustGalGuiElementRenderState> back = enqueueModelPart(
				renderer.backSidePart(), renderer.sideMaterial(decorations.back()).texture(), left, top, right, bottom,
				item, dynamicLayerOrder, selected[0]
			);
			if (back != null) potParts.addAll(back);
			List<RustGalGuiElementRenderState> leftSide = enqueueModelPart(
				renderer.leftSidePart(), renderer.sideMaterial(decorations.left()).texture(), left, top, right, bottom,
				item, dynamicLayerOrder, selected[0]
			);
			if (leftSide != null) potParts.addAll(leftSide);
			List<RustGalGuiElementRenderState> rightSide = enqueueModelPart(
				renderer.rightSidePart(), renderer.sideMaterial(decorations.right()).texture(), left, top, right, bottom,
				item, dynamicLayerOrder, selected[0]
			);
			if (rightSide != null) potParts.addAll(rightSide);
			if (foil[0]) {
				List<net.minecraft.client.model.geom.ModelPart> glintParts = List.of(
					renderer.neckPart(), renderer.topPart(), renderer.bottomPart(), renderer.frontSidePart(),
					renderer.backSidePart(), renderer.leftSidePart(), renderer.rightSidePart());
				for (net.minecraft.client.model.geom.ModelPart part : glintParts) {
					List<RustGalGuiElementRenderState> glint = RustGalGuiRenderer.tryEnqueueModelPip(
						new Model.Simple(part, RenderType::entitySolid), ItemRenderer.ENCHANTED_GLINT_ITEM,
						left, top, right, bottom, 1.0F, item.pose(), item.scissorArea(), dynamicLayerOrder,
						pose -> pose.mulPose(selected[0].transform().pose()), 0xffffffff, 4
					);
					if (glint != null) potParts.addAll(glint);
				}
			}
			result = potParts;
			rejectionName = "special-renderer-decorated-pot-rejected";
		} else if (selectedRenderer instanceof TaczGlock17SpecialRenderer tacz) {
			result = tryEnqueueTaczItem(tacz, selected[0], item, foil[0], left, top, right, bottom, dynamicLayerOrder);
			rejectionName = "special-renderer-tacz-rejected";
		} else {
			recordDiagnostic("special-renderer-unavailable");
			return List.of();
		}
		if (result == null || result.isEmpty()) recordDiagnostic(rejectionName);
		return result == null ? List.of() : result;
	}

	/**
	 * Captures TACZ's already-semantic Bedrock quad stream into a GUI mesh. The
	 * collector accepts only explicit textured quad batches; arbitrary callbacks
	 * remain rejected by the existing whole-frame boundary.
	 */
	private static List<RustGalGuiElementRenderState> tryEnqueueTaczItem(
		TaczGlock17SpecialRenderer renderer,
		ItemStackRenderState.SpecialRender selected,
		GuiItemRenderState item,
		boolean foil,
		int left,
		int top,
		int right,
		int bottom,
		@Nullable Integer dynamicLayerOrder
	) {
		TaczGuiQuadCapture capture = new TaczGuiQuadCapture();
		TaczGuiSubmitCollector collector = new TaczGuiSubmitCollector(capture);
		PoseStack poseStack = new PoseStack();
		poseStack.last().pose().set(selected.transform().pose());
		try {
			renderer.submit(ItemDisplayContext.GUI, poseStack, collector, 15728880, 0, false, 0);
		} catch (RuntimeException error) {
			recordDiagnostic("special-renderer-tacz-rejected=" + error.getClass().getSimpleName());
			return List.of();
		}
		return capture.enqueue(item, foil, left, top, right, bottom, dynamicLayerOrder);
	}

	private static final class TaczGuiQuadCapture {
		private static final int MAX_QUADS = 4096;
		private final List<Batch> batches = new ArrayList<>();

		private boolean add(ResourceLocation texture, float[] vertices, float[] uvs, @Nullable float[] normals, int[] colors) {
			if (texture == null || vertices == null || uvs == null || colors == null) {
				recordDiagnostic("tacz-quad-missing-array");
				return false;
			}
			int quadCount = vertices.length / 12;
			if (vertices.length == 0 || vertices.length % 12 != 0 || uvs.length != vertices.length / 3 * 2
				|| (normals != null && normals.length != vertices.length)
				|| (colors.length != quadCount && colors.length != quadCount * 4)
				|| quadCount + totalQuads() > MAX_QUADS) {
				recordDiagnostic("tacz-quad-shape=" + vertices.length + "/" + uvs.length + "/" + colors.length);
				return false;
			}
			RustGalGuiRawImageAssets.Asset asset = RustGalGuiRawImageAssets.resolve(texture);
			if (asset == null && "minecraft".equals(texture.getNamespace())) {
				asset = RustGalGuiRawImageAssets.loadVanillaResource(texture,
					Minecraft.getInstance().getVanillaPackResources().asProvider());
			}
			if (asset == null) {
				recordDiagnostic("tacz-asset-missing=" + texture);
				return false;
			}
			int[] perVertexColors = colors.length == quadCount * 4 ? colors.clone() : new int[quadCount * 4];
			if (colors.length == quadCount) {
				for (int quad = 0; quad < quadCount; quad++) {
					for (int vertex = 0; vertex < 4; vertex++) perVertexColors[quad * 4 + vertex] = colors[quad];
				}
			}
			// The private TACZ submitter hands this capture freshly materialized
			// primitive arrays and never observes them again. Transfer those arrays
			// directly; the capture remains the sole owner until immutable bridge
			// records copy the values required by Rust submission.
			batches.add(new Batch(asset, vertices, uvs, normals, perVertexColors));
			return true;
		}

		private int totalQuads() { return batches.stream().mapToInt(batch -> batch.vertices.length / 12).sum(); }

		private List<RustGalGuiElementRenderState> enqueue(
			GuiItemRenderState item, boolean foil, int left, int top, int right, int bottom, @Nullable Integer dynamicLayerOrder
		) {
			if (batches.isEmpty()) return List.of();
			int guiWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
			int guiHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
			int guiScale = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
			int width = Math.max(2, (right - left) * guiScale + 2);
			int height = Math.max(2, (bottom - top) * guiScale + 2);
			Matrix4f transform = new Matrix4f().translate(width / 2.0F, height / 2.0F, 0.0F)
				.scale(guiScale * 16.0F, guiScale * 16.0F, -guiScale * 16.0F);
			org.joml.Matrix3f normalTransform = new org.joml.Matrix3f(transform).invert().transpose();
			RustGalGuiRawImageAssets.Asset glintAsset = null;
			int glintColor = 0xffffffff;
			if (foil) {
				glintAsset = RustGalGuiRawImageAssets.resolve(ItemRenderer.ENCHANTED_GLINT_ITEM);
				if (glintAsset == null) return List.of();
				int strength = Mth.clamp((int)Math.round(Minecraft.getInstance().options.glintStrength().get() * 255.0F), 0, 255);
				glintColor = ARGB.color(strength, 255, 255, 255);
			}
			List<VulkanicGalBridge.GuiMeshBatchRecord> records = new ArrayList<>(batches.size());
			TaczGuiPackedStaging staging = TACZ_GUI_STAGING.get();
			for (Batch batch : batches) {
				int maximumVertices = batch.vertices.length / 3;
				staging.ensureCapacity(maximumVertices, glintAsset != null);
				float[] positions = staging.positions;
				float[] atlasUvs = staging.atlasUvs;
				float[] localUvs = staging.localUvs;
				int[] colors = staging.colors;
				int[] normals = staging.normals;
				float[] glintUvs = glintAsset == null ? null : staging.glintUvs;
				int vertexCount = 0;
				for (int quad = 0; quad < batch.vertices.length / 12; quad++) {
					int floatOffset = quad * 12;
					Vector3f normal = batch.normals == null
						? reconstructedNormal(batch.vertices, floatOffset, transform)
						: normalTransform.transform(batch.normals[floatOffset], batch.normals[floatOffset + 1], batch.normals[floatOffset + 2], new Vector3f()).normalize();
					Vector3f first = transformedPosition(batch.vertices, floatOffset, transform);
					Vector3f second = transformedPosition(batch.vertices, floatOffset + 3, transform);
					Vector3f third = transformedPosition(batch.vertices, floatOffset + 6, transform);
					float alignment = new Vector3f(second).sub(first).cross(new Vector3f(third).sub(first)).dot(normal);
					// Bedrock permits zero-thickness cubes whose collapsed faces cannot
					// rasterize. Frozen submits those no-op triangles; omit them before
					// Rust's stricter copied-normal/winding validation.
					if (!Float.isFinite(alignment)) return List.of();
					if (Math.abs(alignment) <= 1.0e-6F) continue;
					for (int corner = 0; corner < 4; corner++) {
						int vertex = quad * 4 + corner;
						Vector3f position = transformedPosition(batch.vertices, vertex * 3, transform);
						Vector3f vertexNormal = batch.normals == null ? normal : normalTransform.transform(
							batch.normals[vertex * 3], batch.normals[vertex * 3 + 1], batch.normals[vertex * 3 + 2], new Vector3f()).normalize();
						if (!Float.isFinite(vertexNormal.x()) || !Float.isFinite(vertexNormal.y()) || !Float.isFinite(vertexNormal.z())) return List.of();
						int positionOffset = vertexCount * 3;
						positions[positionOffset] = position.x;
						positions[positionOffset + 1] = position.y;
						positions[positionOffset + 2] = position.z;
						int uvOffset = vertexCount * 2;
						atlasUvs[uvOffset] = localUvs[uvOffset] = batch.uvs[vertex * 2];
						atlasUvs[uvOffset + 1] = localUvs[uvOffset + 1] = batch.uvs[vertex * 2 + 1];
						colors[vertexCount] = batch.colors[vertex];
						normals[vertexCount] = packGuiNormal(vertexNormal.x(), vertexNormal.y(), vertexNormal.z());
						if (glintUvs != null) {
							glintUvs[uvOffset] = -batch.vertices[vertex * 3] * ItemRenderer.SPECIAL_FOIL_TEXTURE_SCALE;
							glintUvs[uvOffset + 1] = -batch.vertices[vertex * 3 + 1] * ItemRenderer.SPECIAL_FOIL_TEXTURE_SCALE;
						}
						vertexCount++;
					}
				}
				if (vertexCount == 0) continue;
				int[] indexValues = staging.indices;
				int indexCount = vertexCount / 4 * 6;
				for (int vertex = 0, index = 0; vertex < vertexCount; vertex += 4) {
					indexValues[index++] = vertex; indexValues[index++] = vertex + 1; indexValues[index++] = vertex + 2;
					indexValues[index++] = vertex + 2; indexValues[index++] = vertex + 3; indexValues[index++] = vertex;
				}
				List<VulkanicGalBridge.GuiMeshVertexRecord> copied = VulkanicGalBridge.packedGuiMeshVertices(
					positions, atlasUvs, localUvs, colors, normals, vertexCount);
				List<Integer> indices = VulkanicGalBridge.packedGuiMeshIndices(indexValues, indexCount);
				int layerOrder = dynamicLayerOrder == null ? GuiRenderStratum.GUI_ITEM.order() : dynamicLayerOrder;
				records.add(new VulkanicGalBridge.GuiMeshBatchRecord(layerOrder, records.size(),
					VulkanicGalBridge.GUI_MESH_MATERIAL_ENTITY_CUTOUT_NO_CULL,
					VulkanicGalBridge.GUI_MESH_LIGHTING_ENTITY_PREVIEW,
					batch.asset.assetId(), 0L, 0.1F, identity(), new float[] {
						item.pose().m00(), item.pose().m01(), item.pose().m10(), item.pose().m11(), item.pose().m20(), item.pose().m21()
					},
					left, top, right, bottom, guiWidth, guiHeight, width, height, 1, 0, 0, 0, 0, 0,
					copied, indices));
				if (glintAsset != null) {
					int[] glintColors = staging.glintColors;
					java.util.Arrays.fill(glintColors, glintColor);
					List<VulkanicGalBridge.GuiMeshVertexRecord> glintVertices = VulkanicGalBridge.packedGuiMeshVertices(
						positions, glintUvs, glintUvs, glintColors, normals, vertexCount);
					records.add(new VulkanicGalBridge.GuiMeshBatchRecord(layerOrder, records.size(), 4, 1,
						glintAsset.assetId(), 0L, 0.1F, identity(), new float[] {
							item.pose().m00(), item.pose().m01(), item.pose().m10(), item.pose().m11(), item.pose().m20(), item.pose().m21()
						}, left, top, right, bottom, guiWidth, guiHeight, width, height, 1, 0, 0, 0, 0, 0,
						glintVertices, indices));
				}
			}
			RustGalFrameScheduler.Token token = RustGalFrameCoordinator.enqueueGuiMeshItemRequest(records,
				GuiRenderStratum.GUI_ITEM.id(), dynamicLayerOrder == null ? GuiRenderStratum.GUI_ITEM.order() : dynamicLayerOrder, System.nanoTime());
			// Commit copied item/glint images only after the complete special-item
			// mesh has entered the scheduler. Rejected requests must not retain
			// texture assets without corresponding Rust GUI work.
			for (Batch batch : batches) RustGalGuiRawImageAssets.stage(batch.asset());
			if (glintAsset != null) RustGalGuiRawImageAssets.stage(glintAsset);
			return List.of(new RustGalGuiElementRenderState(token, GuiRenderStratum.GUI_ITEM, "minecraft.gui.tacz-bedrock",
				-1, -1.0F, GuiFillDirection.NONE, left, top, right - left, bottom - top, guiWidth, guiHeight));
		}

		private static int packGuiNormal(float x, float y, float z) {
			int px = Math.round(Mth.clamp(x, -1.0F, 1.0F) * 127.0F) & 0xff;
			int py = Math.round(Mth.clamp(y, -1.0F, 1.0F) * 127.0F) & 0xff;
			int pz = Math.round(Mth.clamp(z, -1.0F, 1.0F) * 127.0F) & 0xff;
			return px | (py << 8) | (pz << 16);
		}

		private static Vector3f reconstructedNormal(float[] vertices, int offset, Matrix4f transform) {
			Vector3f first = transformedPosition(vertices, offset, transform);
			Vector3f second = transformedPosition(vertices, offset + 3, transform);
			Vector3f third = transformedPosition(vertices, offset + 6, transform);
			return new Vector3f(second).sub(first).cross(new Vector3f(third).sub(first)).normalize();
		}

		private static Vector3f transformedPosition(float[] vertices, int offset, Matrix4f transform) {
			return transform.transformPosition(vertices[offset], vertices[offset + 1], vertices[offset + 2], new Vector3f());
		}

		private static float[] identity() { return new float[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1}; }
		private record Batch(RustGalGuiRawImageAssets.Asset asset, float[] vertices, float[] uvs, @Nullable float[] normals, int[] colors) {}
	}

	/** Reused render-thread CPU scratch; packed list factories take owned copies before the next batch reuses it. */
	private static final class TaczGuiPackedStaging {
		private float[] positions = new float[0];
		private float[] atlasUvs = new float[0];
		private float[] localUvs = new float[0];
		private float[] glintUvs = new float[0];
		private int[] colors = new int[0];
		private int[] normals = new int[0];
		private int[] glintColors = new int[0];
		private int[] indices = new int[0];

		private void ensureCapacity(int vertices, boolean foil) {
			if (positions.length < vertices * 3) positions = new float[vertices * 3];
			if (atlasUvs.length < vertices * 2) atlasUvs = new float[vertices * 2];
			if (localUvs.length < vertices * 2) localUvs = new float[vertices * 2];
			if (colors.length < vertices) colors = new int[vertices];
			if (normals.length < vertices) normals = new int[vertices];
			if (indices.length < vertices / 4 * 6) indices = new int[vertices / 4 * 6];
			if (foil) {
				if (glintUvs.length < vertices * 2) glintUvs = new float[vertices * 2];
				if (glintColors.length < vertices) glintColors = new int[vertices];
			}
		}
	}

	private static final class TaczGuiSubmitCollector extends SubmitNodeCollection implements SubmitNodeCollector {
		private final TaczGuiQuadCapture capture;

		private TaczGuiSubmitCollector(TaczGuiQuadCapture capture) {
			super(null);
			this.capture = capture;
		}

		@Override
		public OrderedSubmitNodeCollector order(int ignored) { return this; }

		@Override
		public boolean submitTexturedQuads(PoseStack poseStack, RenderType renderType, ResourceLocation texture,
			float[] vertices, float[] uvs, int[] colors, int lightCoords) {
			return capture.add(texture, vertices, uvs, null, colors);
		}

		@Override
		public boolean submitTexturedQuadsWithNormalsSemantic(PoseStack poseStack, RenderType renderType, ResourceLocation texture,
			float[] vertices, float[] uvs, float[] normals, int[] colors, int lightCoords) {
			return capture.add(texture, vertices, uvs, normals, colors);
		}

		@Override
		public boolean submitTexturedQuad(PoseStack poseStack, RenderType renderType, ResourceLocation texture,
			float[] vertices, float[] uvs, int color, int lightCoords) {
			return capture.add(texture, vertices, uvs, null, new int[] {color, color, color, color});
		}


		@Override
		public void submitCustomGeometry(PoseStack poseStack, RenderType renderType, SubmitNodeCollector.CustomGeometryRenderer customGeometryRenderer) {
			throw new IllegalStateException("TACZ GUI semantic capture encountered arbitrary custom geometry");
		}
	}

	private static List<RustGalGuiElementRenderState> enqueueModelPart(
		net.minecraft.client.model.geom.ModelPart part, ResourceLocation texture, int left, int top, int right, int bottom,
		GuiItemRenderState item, @Nullable Integer dynamicLayerOrder, ItemStackRenderState.SpecialRender selected
	) {
		return RustGalGuiRenderer.tryEnqueueModelPartPip(
			part, texture, left, top, right, bottom, 1.0F, item.pose(), item.scissorArea(), dynamicLayerOrder,
			pose -> pose.mulPose(selected.transform().pose())
		);
	}

	private static List<RustGalGuiElementRenderState> enqueueModelParts(
		List<net.minecraft.client.model.geom.ModelPart> parts, ResourceLocation texture, int left, int top, int right, int bottom,
		GuiItemRenderState item, @Nullable Integer dynamicLayerOrder, ItemStackRenderState.SpecialRender selected
	) {
		List<RustGalGuiElementRenderState> result = new ArrayList<>();
		for (net.minecraft.client.model.geom.ModelPart part : parts) {
			List<RustGalGuiElementRenderState> elements = enqueueModelPart(part, texture, left, top, right, bottom, item, dynamicLayerOrder, selected);
			if (elements != null) result.addAll(elements);
		}
		return result;
	}

	public static boolean standard3dRouteEnabled() {
		// This is a normal producer route for Rust's exclusive Vulkan frame. An
		// unsupported item remains Java-owned before selection; selected items
		// never enter Java's PIP renderer in the same frame.
		return !STANDARD_3D_ROUTE_DISABLED
			&& RustGalGuiRenderer.currentExecutionRoute() == RustGalGuiRenderer.GuiExecutionRoute.RUST_VULKAN_WHOLE_FRAME;
	}

	/**
	 * Adds one ordinary vanilla GUI item state before whole-frame semantic
	 * collection. This is capture-only infrastructure: it exercises the same
	 * ItemModelResolver and GUI item records as production items, but never
	 * invokes Java's PIP renderer or any Java draw path.
	 */
	public static void enqueueDebugStandard3dItem(GuiRenderState guiRenderState) {
		if (!DEBUG_STANDARD_3D_ITEM_ENABLED || !standard3dRouteEnabled()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (!minecraft.getModelManager().hasLoadedModels() || minecraft.level == null || minecraft.player == null) {
			return;
		}
		TrackingItemStackRenderState itemState = new TrackingItemStackRenderState();
		minecraft.getItemModelResolver().updateForTopItem(
			itemState, new ItemStack(Blocks.GRASS_BLOCK), ItemDisplayContext.GUI, minecraft.level, minecraft.player, 0
		);
		guiRenderState.submitItem(new GuiItemRenderState(
			"rust_gal_debug_standard_3d_grass_block", new Matrix3x2f(), itemState, 16, 16, null
		));
	}

	/**
	 * Extracts flat-lit GUI items into native semantic meshes, including authored
	 * transforms and standard foil. Block-lit and special models use their own
	 * collectors; unsupported projected foil remains explicitly unavailable.
	 */
	public static List<RustGalGuiElementRenderState> tryEnqueueFlatItem(
		GuiItemRenderState item,
		int guiWidth,
		int guiHeight
	) {
		return tryEnqueueFlatItem(item, guiWidth, guiHeight, null);
	}

	public static List<RustGalGuiElementRenderState> tryEnqueueFlatItem(
		GuiItemRenderState item,
		int guiWidth,
		int guiHeight,
		@Nullable Integer dynamicLayerOrder
	) {
		if (RustGalGuiRenderer.currentExecutionRoute() != RustGalGuiRenderer.GuiExecutionRoute.RUST_VULKAN_WHOLE_FRAME) {
			return List.of();
		}
		if (item.itemStackRenderState().displayContext() != ItemDisplayContext.GUI) {
			recordDiagnostic("display-context");
			return List.of();
		}
		// All flat items share original mesh/resource semantics and native lighting.
		return tryEnqueueNativeFlatItem(item, guiWidth, guiHeight, dynamicLayerOrder);
	}

	/**
	 * Converts one ordinary block-lit GUI item into a single ordered semantic
	 * mesh item. Its material layers remain nested under one scheduler token so
	 * Rust owns the offscreen raster and final composition ordering.
	 */
	public static List<RustGalGuiElementRenderState> tryEnqueueStandard3dItem(
		GuiItemRenderState item,
		int guiWidth,
		int guiHeight
	) {
		return tryEnqueueStandard3dItem(item, guiWidth, guiHeight, null);
	}

	public static List<RustGalGuiElementRenderState> tryEnqueueStandard3dItem(
		GuiItemRenderState item,
		int guiWidth,
		int guiHeight,
		@Nullable Integer dynamicLayerOrder
	) {
		if (!standard3dRouteEnabled()) {
			return List.of();
		}
		boolean benchmarkTiming = GraphicsFrameBenchmark.isFrameBenchmarkActive();
		long collectionStarted = benchmarkTiming ? System.nanoTime() : 0L;
		GuiItemMeshSemanticCollector.CollectionResult collected = GuiItemMeshSemanticCollector.collectStandard3d(
			item, Math.max(1, Minecraft.getInstance().getWindow().getGuiScale()));
		if (benchmarkTiming) {
			GraphicsFrameBenchmark.recordPhaseSample(
				"gui.item.standard3d.collect",
				Math.max(0L, System.nanoTime() - collectionStarted)
			);
		}
		if (!collected.accepted()) {
			recordDiagnostic("mesh-" + collected.rejection());
			return List.of();
		}
		GuiItemMeshSemanticCollector.GuiItemMesh mesh = collected.mesh();
		boolean cacheableRaster = !item.itemStackRenderState().isAnimated()
			&& mesh.layers().stream().noneMatch(layer -> layer.itemFoil() != null);
		long cacheIdentity = cacheableRaster
			? GuiItemSemanticIdentities.identityOrZero(item.itemStackRenderState().getModelIdentity()) : 0;
		VulkanicGalBridge.GuiItemCacheRecord itemCache = cacheIdentity != 0
			? new VulkanicGalBridge.GuiItemCacheRecord(cacheIdentity, false) : null;
		long batchStarted = benchmarkTiming ? System.nanoTime() : 0L;
		var clip = item.scissorArea();
		List<VulkanicGalBridge.GuiMeshBatchRecord> batches = new ArrayList<>();
		float[] guiPose = mesh.guiPose();
		int requestLayerOrder = dynamicLayerOrder == null ? GuiRenderStratum.GUI_ITEM.order()
			: RustGalGuiRenderer.dynamicLayerOrder(dynamicLayerOrder);
		int batchLayerIndex = 0;
		for (int layerIndex = 0; layerIndex < mesh.layers().size(); layerIndex++) {
			GuiItemMeshSemanticCollector.GuiItemMeshLayer layer = mesh.layers().get(layerIndex);
			float[] modelTransform = layer.modelTransform();
			for (GuiItemMeshSemanticCollector.GuiItemMeshQuad quad : layer.quads()) {
				List<VulkanicGalBridge.GuiMeshVertexRecord> vertices = layer.sourceFoilType() == 0
					? quad.bridgeVertices() : quad.bridgeFoilVertices();
				batches.add(new VulkanicGalBridge.GuiMeshBatchRecord(
					requestLayerOrder, batchLayerIndex++,
					guiMaterialMode(layer.materialMode()),
					// Ordinary inventory light space; Rust owns the light vectors.
					layer.blockLight() ? VulkanicGalBridge.GUI_MESH_LIGHTING_INVENTORY_BLOCK : 1, quad.assetId(), 0L,
					(layer.materialMode() == GuiItemMeshSemanticCollector.MaterialMode.CUTOUT
						|| layer.materialMode() == GuiItemMeshSemanticCollector.MaterialMode.GLINT) ? 0.1F : 0.0F,
					modelTransform, guiPose, mesh.left(), mesh.top(), mesh.right(), mesh.bottom(),
					guiWidth, guiHeight, 0, 0, 0,
					clip == null ? 0 : 1, clip == null ? 0 : clip.left(), clip == null ? 0 : clip.top(),
					clip == null ? 0 : clip.width(), clip == null ? 0 : clip.height(),
					vertices, QUAD_INDICES,
					layer.itemFoil(), 0, null, mesh.blockItemRaster(), itemCache
				));
			}
		}
		if (batches.isEmpty()) return List.of();
		if (benchmarkTiming) {
			GraphicsFrameBenchmark.recordPhaseSample(
				"gui.item.standard3d.batch-build",
				Math.max(0L, System.nanoTime() - batchStarted)
			);
		}
		long startedNanos = System.nanoTime();
		var token = dynamicLayerOrder == null
			? RustGalFrameCoordinator.enqueueGuiMeshItemRequest(batches, GuiRenderStratum.GUI_ITEM, startedNanos)
			: RustGalFrameCoordinator.enqueueGuiMeshItemRequest(
				batches, RustGalGuiRenderer.dynamicLayerId(dynamicLayerOrder),
				requestLayerOrder, startedNanos);
		mesh.sources().forEach(GuiItemTextureSource::stage);
		for (var batch : batches) {
			var foil = batch.itemFoil();
			if (foil != null) {
				net.minecraft.client.dev.GraphicsAuditGuiFoilTiming.observeSemanticClock(
					item.x(), item.y(), foil.clockMillis(), foil.speed(), foil.strength());
				break;
			}
		}
		for (var use : mesh.atlasUses()) {
			net.vulkanic.world.RustGalWorldPrimitiveRenderer.recordAtlasSpriteUse(
				use.resource(), use.atlas(), use.name());
		}
		if (Boolean.getBoolean("mattmc.dev.guiItemRasterTrace")) {
			item.itemStackRenderState().forEachSemanticLayer(layer -> {
				for (BakedQuad face : layer.quads()) net.minecraft.client.dev.GraphicsAuditGuiFoilSource.record(face);
			});
		}
		recordDiagnostic("mesh-accepted-layers=" + batches.size());
		return List.of(new RustGalGuiElementRenderState(
			token, GuiRenderStratum.GUI_ITEM, "minecraft.gui.item.standard3d", -1, -1.0F, GuiFillDirection.NONE,
			mesh.left(), mesh.top(), mesh.right() - mesh.left(), mesh.bottom() - mesh.top(), guiWidth, guiHeight
		));
	}

	private static List<RustGalGuiElementRenderState> tryEnqueueShieldItem(
		GuiItemRenderState item, ItemStackRenderState.SpecialRender selected, ShieldSpecialRenderer shield,
		boolean hasFoil, int guiWidth, int guiHeight, @Nullable Integer dynamicLayerOrder) {
		// Static shield model layers and ENTITY foil are native GUI items.
		// Animated sprite sources still require the separate native atlas contract.
		int order = dynamicLayerOrder == null ? GuiRenderStratum.GUI_ITEM.order() : RustGalGuiRenderer.dynamicLayerOrder(dynamicLayerOrder);
		GuiFlatItemMeshCollector.Snapshot snapshot;
		try {
			var options = Minecraft.getInstance().options;
			var foil = hasFoil ? new VulkanicGalBridge.StandardItemFoilRecord(net.minecraft.Util.getMillis(),
				options.glintSpeed().get(), options.glintStrength().get().floatValue(), VulkanicGalBridge.StandardFoilKind.ENTITY) : null;
			snapshot = GuiShieldItemSemanticCollector.collect(item, selected, shield, guiWidth, guiHeight,
				Minecraft.getInstance().getWindow().getGuiScale(), order, foil);
		} catch (IllegalArgumentException | IllegalStateException rejection) {
			recordDiagnostic("shield-gui-rejected:" + rejection.getMessage());
			return List.of();
		}
		var token = dynamicLayerOrder == null
			? RustGalFrameCoordinator.enqueueGuiMeshItemRequest(snapshot.batches(), GuiRenderStratum.GUI_ITEM, System.nanoTime())
			: RustGalFrameCoordinator.enqueueGuiMeshItemRequest(snapshot.batches(), RustGalGuiRenderer.dynamicLayerId(dynamicLayerOrder), order, System.nanoTime());
		snapshot.sources().forEach(GuiItemTextureSource::stage);
		return List.of(new RustGalGuiElementRenderState(token, GuiRenderStratum.GUI_ITEM, "minecraft.gui.item.shield",
			-1, -1.0F, GuiFillDirection.NONE, item.x(), item.y(), 16, 16, guiWidth, guiHeight));
	}

	private static List<RustGalGuiElementRenderState> tryEnqueueNativeFlatItem(
		GuiItemRenderState item, int guiWidth, int guiHeight, @Nullable Integer dynamicLayerOrder) {
		int order = dynamicLayerOrder == null ? GuiRenderStratum.GUI_ITEM.order() : RustGalGuiRenderer.dynamicLayerOrder(dynamicLayerOrder);
		GuiFlatItemMeshCollector.Snapshot snapshot;
		try {
			var options = Minecraft.getInstance().options;
			var foil = item.itemStackRenderState().hasFoil()
				? new VulkanicGalBridge.StandardItemFoilRecord(net.minecraft.Util.getMillis(), options.glintSpeed().get(), options.glintStrength().get().floatValue())
				: null;
			snapshot = GuiFlatItemMeshCollector.collect(item, guiWidth, guiHeight,
				Minecraft.getInstance().getWindow().getGuiScale(), order, foil);
		} catch (IllegalArgumentException | IllegalStateException rejection) {
			recordDiagnostic("flat-mesh-rejected:" + rejection.getMessage());
			return List.of();
		}
		long startedNanos = System.nanoTime();
		var token = dynamicLayerOrder == null
			? RustGalFrameCoordinator.enqueueGuiMeshItemRequest(snapshot.batches(), GuiRenderStratum.GUI_ITEM, startedNanos)
			: RustGalFrameCoordinator.enqueueGuiMeshItemRequest(snapshot.batches(), RustGalGuiRenderer.dynamicLayerId(dynamicLayerOrder), order, startedNanos);
		snapshot.sources().forEach(GuiItemTextureSource::stage);
		for (var batch : snapshot.batches()) {
			var foil = batch.itemFoil();
			if (foil != null) {
				net.minecraft.client.dev.GraphicsAuditGuiFoilTiming.observeSemanticClock(
					item.x(), item.y(), foil.clockMillis(), foil.speed(), foil.strength());
				// Every face in this immutable item snapshot uses the same
				// supplied foil payload; observe the item once, not each face.
				break;
			}
		}
		item.itemStackRenderState().forEachSemanticLayer(layer -> {
			for (BakedQuad face : layer.quads()) {
				net.minecraft.client.dev.GraphicsAuditGuiFoilSource.record(face);
				var sprite = ((BakedQuadView)(Object)face).getSprite();
				net.vulkanic.world.RustGalWorldPrimitiveRenderer.recordAtlasSpriteUse(
					sprite.semanticAnimationResource(), sprite.atlasLocation(), sprite.contents().name());
			}
		});
		recordDiagnostic("flat-mesh-accepted-layers=" + snapshot.batches().size());
		return List.of(new RustGalGuiElementRenderState(token, GuiRenderStratum.GUI_ITEM, "minecraft.gui.item.flat",
			-1, -1.0F, GuiFillDirection.NONE, item.x(), item.y(), 16, 16, guiWidth, guiHeight));
	}

	private static int guiMaterialMode(GuiItemMeshSemanticCollector.MaterialMode mode) {
		return switch (mode) {
			case OPAQUE -> 1;
			case CUTOUT -> 2;
			case TRANSLUCENT -> 3;
			case GLINT -> 4;
		};
	}

	public static void invalidateAssets() {
		RustGalGuiRawImageAssets.invalidate();
	}

	static boolean supportedGuiRenderType(RenderType renderType) {
		return renderType != null && flatItemMaterial(renderType.pipeline()) != 0;
	}

	static int flatItemMaterial(net.blaze3d.pipeline.RenderPipeline pipeline) {
		if (pipeline == net.minecraft.client.renderer.RenderPipelines.ITEM_ENTITY_TRANSLUCENT_CULL) return 1;
		if (pipeline == net.minecraft.client.renderer.RenderPipelines.ENTITY_CUTOUT) return 2;
		return 0;
	}


	/**
	 * Resolves and stages a copied semantic image for the Rust-owned mesh
	 * layer. The returned key is stable across a resource generation; neither a
	 * sprite nor an atlas object escapes this Java boundary.
	 */
	public static long stageSemanticImage(ResourceLocation sprite) {
		long assetId = resolveSemanticImage(sprite);
		if (assetId == 0L) return 0L;
		RustGalGuiRawImageAssets.Asset asset = RustGalGuiRawImageAssets.resolve(sprite);
		if (asset != null) RustGalGuiRawImageAssets.stage(asset);
		return assetId;
	}

	/** Resolves a semantic image identity without publishing it to the Rust frame. */
	public static long resolveSemanticImage(ResourceLocation sprite) {
		RustGalGuiRawImageAssets.Asset asset = RustGalGuiRawImageAssets.resolve(sprite);
		if (asset == null) {
			var texture = Minecraft.getInstance().getTextureManager().getTexture(sprite);
			if (texture instanceof DynamicTexture dynamic) {
				RustGalGuiRawImageAssets.registerDynamicTextureUnstaged(sprite, dynamic);
				RustGalGuiRawImageAssets.prepareDynamicTexture(dynamic);
				asset = RustGalGuiRawImageAssets.resolve(sprite);
			}
		}
		if (asset == null) {
			return 0L;
		}
		return asset.assetId();
	}

	/** Stages one current animated-sprite frame under a stable semantic identity. */
	public static long stageSemanticImage(TextureAtlasSprite sprite) {
		if (sprite == null || sprite.contents() == null) return 0L;
		RustGalGuiRawImageAssets.Asset asset = sprite.contents().isAnimated()
			? RustGalGuiRawImageAssets.resolveAnimatedSprite(sprite)
			: RustGalGuiRawImageAssets.resolve(sprite.contents().name());
		if (asset == null) return 0L;
		RustGalGuiRawImageAssets.stage(asset);
		return asset.assetId();
	}

	/** Exact semantic conversion; callers reject unsupported ranges, never clamp them. */
	static float itemLocalUv(float coordinate, float start, float end) {
		return (coordinate-start)/(end-start);
	}

	private static synchronized void recordDiagnostic(String detail) {
		if (DIAGNOSTICS.containsKey(detail) || DIAGNOSTICS.size() >= MAX_DIAGNOSTIC_ENTRIES) return;
		DIAGNOSTICS.put(detail, Boolean.TRUE);
		RustGalFrameCoordinator.auditMessage("gui.item.route " + detail);
	}

}
