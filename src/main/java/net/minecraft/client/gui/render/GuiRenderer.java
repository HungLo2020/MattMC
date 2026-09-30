package net.minecraft.client.gui.render;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import net.blaze3d.ProjectionType;
import net.blaze3d.buffers.GpuBuffer;
import net.blaze3d.buffers.GpuBufferSlice;
import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.pipeline.RenderTarget;
import net.blaze3d.platform.Lighting;
import net.blaze3d.platform.Window;
import net.blaze3d.systems.CommandEncoder;
import net.blaze3d.systems.RenderPass;
import net.blaze3d.systems.RenderSystem;
import net.blaze3d.textures.FilterMode;
import net.blaze3d.textures.GpuTexture;
import net.blaze3d.textures.GpuTextureView;
import net.blaze3d.textures.TextureFormat;
import net.blaze3d.vertex.BufferBuilder;
import net.blaze3d.vertex.ByteBufferBuilder;
import net.blaze3d.vertex.MeshData;
import net.blaze3d.vertex.PoseStack;
import net.blaze3d.vertex.VertexFormat;
import net.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Supplier;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.state.BlitRenderState;
import net.minecraft.client.gui.render.state.ColoredRectangleRenderState;
import net.voxelmap.util.FourColoredRectangleRenderState;
import net.minecraft.client.gui.render.state.GlyphRenderState;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.gui.render.state.GuiItemRenderState;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.TiledBlitRenderState;
import net.minecraft.client.gui.render.state.pip.OversizedItemRenderState;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import net.minecraft.client.gui.render.state.pip.GuiSkinRenderState;
import net.minecraft.client.gui.render.state.pip.GuiBookModelRenderState;
import net.minecraft.client.gui.render.state.pip.GuiSignRenderState;
import net.minecraft.client.gui.render.state.pip.GuiBannerResultRenderState;
import net.minecraft.client.gui.render.state.pip.GuiEntityRenderState;
import net.minecraft.client.renderer.CachedOrthoProjectionMatrixBuffer;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.math.Axis;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicResourceBarriers;
import net.vulkanic.gui.RustGalFrameCoordinator;
import net.vulkanic.gui.RustGalGuiElementRenderState;
import net.vulkanic.gui.RustGalGuiItemRenderer;
import net.vulkanic.gui.RustGalGuiRenderer;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class GuiRenderer implements AutoCloseable {
	private static int rustGalLoadingGridConsumerDiagnostics;
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final VulkanicResourceBarriers OFFSCREEN_COLOR_WRITES_VISIBLE_TO_TEXTURE_FETCH = VulkanicResourceBarriers.of(
		VulkanicResourceBarriers.Barrier.TEXTURE_FETCH
	);
	private static final float MAX_GUI_Z = 10000.0F;
	/** Bounds copied PIP inputs before model/atlas expansion on Rust Vulkan. */
	private static final int MAX_RUST_PICTURE_IN_PICTURE_STATES = 1_024;
	public static final float MIN_GUI_Z = 0.0F;
	private static final float GUI_Z_NEAR = 1000.0F;
	public static final int GUI_3D_Z_FAR = 1000;
	public static final int GUI_3D_Z_NEAR = -1000;
	public static final int DEFAULT_ITEM_SIZE = 16;
	private static final int MINIMUM_ITEM_ATLAS_SIZE = 512;
	private static final int MAXIMUM_ITEM_ATLAS_SIZE = VulkanicAPI.getBackendMaxTextureSize();
	public static final int CLEAR_COLOR = 0;
	private static final Comparator<ScreenRectangle> SCISSOR_COMPARATOR = Comparator.nullsFirst(
		Comparator.comparing(ScreenRectangle::top).thenComparing(ScreenRectangle::bottom).thenComparing(ScreenRectangle::left).thenComparing(ScreenRectangle::right)
	);
	private static final Comparator<TextureSetup> TEXTURE_COMPARATOR = Comparator.nullsFirst(Comparator.comparing(TextureSetup::getSortKey));
	private static final Comparator<GuiElementRenderState> ELEMENT_SORT_COMPARATOR = Comparator.comparing(GuiElementRenderState::scissorArea, SCISSOR_COMPARATOR)
		.thenComparing(GuiElementRenderState::pipeline, Comparator.comparing(RenderPipeline::getSortKey))
		.thenComparing(GuiElementRenderState::textureSetup, TEXTURE_COMPARATOR);
	private final Map<Object, GuiRenderer.AtlasPosition> atlasPositions = new Object2ObjectOpenHashMap<>();
	/** States selected before GUI prepare; selected states never enter Java PIP. */
	private final Set<GuiItemRenderState> rustOwnedStandard3dItems = Collections.newSetFromMap(new IdentityHashMap<>());
	/** Picture-in-picture states already copied into Rust GUI meshes this frame. */
	private final Set<PictureInPictureRenderState> rustOwnedPictureInPictureStates = Collections.newSetFromMap(new IdentityHashMap<>());
	final GuiRenderState renderState;
	private final List<GuiRenderer.DrawStep> draws = new ArrayList();
	private final List<GuiRenderer.PreparedStep> meshesToDraw = new ArrayList();
	private final ByteBufferBuilder byteBufferBuilder = new ByteBufferBuilder(786432);
	private final Map<VertexFormat, MappableRingBuffer> vertexBuffers = new Object2ObjectOpenHashMap<>();
	private int firstDrawIndexAfterBlur = Integer.MAX_VALUE;
	@Nullable
	private final CachedOrthoProjectionMatrixBuffer guiProjectionMatrixBuffer;
	@Nullable
	private final CachedOrthoProjectionMatrixBuffer itemsProjectionMatrixBuffer;
	private final MultiBufferSource.BufferSource bufferSource;
	private final SubmitNodeCollector submitNodeCollector;
	private final FeatureRenderDispatcher featureRenderDispatcher;
	@Nullable
	private GpuTexture itemsAtlas;
	@Nullable
	private GpuTextureView itemsAtlasView;
	@Nullable
	private GpuTexture itemsAtlasDepth;
	@Nullable
	private GpuTextureView itemsAtlasDepthView;
	private int itemAtlasX;
	private int itemAtlasY;
	private int cachedGuiScale;
	private int frameNumber;
	@Nullable
	private ScreenRectangle previousScissorArea = null;
	@Nullable
	private RenderPipeline previousPipeline = null;
	@Nullable
	private TextureSetup previousTextureSetup = null;
	@Nullable
	private String previousShaderInputParityGeometryContext = null;
	@Nullable
	private BufferBuilder bufferBuilder = null;

	public GuiRenderer(
		GuiRenderState guiRenderState,
		MultiBufferSource.BufferSource bufferSource,
		SubmitNodeCollector submitNodeCollector,
		FeatureRenderDispatcher featureRenderDispatcher
	) {
		this.renderState = guiRenderState;
		this.bufferSource = bufferSource;
		this.submitNodeCollector = submitNodeCollector;
		this.featureRenderDispatcher = featureRenderDispatcher;
		// Rust owns GUI projection and mesh lowering for whole-frame Vulkan;
		// avoid constructing Java compatibility UBOs that the semantic route
		// never consumes.
		this.guiProjectionMatrixBuffer = null;
		this.itemsProjectionMatrixBuffer = null;
	}

	public void incrementFrameNumber() {
		this.frameNumber++;
	}

	/**
	 * Extracts text semantics for the Rust whole-frame route without preparing
	 * Java meshes or issuing a Java draw.
	 */
	public void collectRustGalTextSemantics() {
		this.renderState.forEachText(guiTextRenderState -> {
			int dynamicLayerOrder = this.renderState.currentSemanticLayerOrder(GuiRenderState.SemanticPhase.TEXT);
			List<RustGalGuiElementRenderState> elements = RustGalGuiRenderer.tryEnqueueText(
				guiTextRenderState,
				Minecraft.getInstance().getWindow().getGuiScaledWidth(),
				Minecraft.getInstance().getWindow().getGuiScaledHeight(),
				dynamicLayerOrder
			);
			if (elements != null) {
				for (RustGalGuiElementRenderState element : elements) {
					this.renderState.submitGlyphToCurrentLayer(element);
				}
			} else {
				RustGalGuiRenderer.recordUnsupportedElement("text");
			}
		});
	}

	/**
	 * Converts admitted flat and standard-3D vanilla item semantics for a
	 * Rust-owned whole frame. Once a standard-3D item is selected, its Java PIP
	 * renderer is excluded from that frame rather than drawn a second time.
	 */
	public void collectRustGalItemSemantics() {
		// During a resource reload the render state can still contain item models
		// baked against the previous block atlas. The reload overlay owns that
		// transient frame; defer all item semantic admission until the new atlas
		// generation has been published instead of counting stale items as an
		// unsupported whole-frame family.
		if (net.vulkanic.world.RustGalTerrainRenderer.isResourceReloadStaging()) {
			return;
		}
		net.minecraft.client.dev.GraphicsAuditGuiFoilTiming.beginFrame();
		int guiWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
		int guiHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
		this.renderState.forEachItem(guiItemRenderState -> {
			// An item whose copied screen bounds do not intersect the active GUI
			// viewport contributes no visible work.  Omit it before semantic
			// admission; treating an entirely clipped HUD element as an
			// unsupported item would incorrectly reject the whole Rust frame.
			ScreenRectangle bounds = guiItemRenderState.bounds();
			if (bounds != null && (bounds.left() >= guiWidth || bounds.top() >= guiHeight
				|| bounds.right() <= 0 || bounds.bottom() <= 0)) {
				return;
			}
			int dynamicLayerOrder = this.renderState.currentSemanticLayerOrder(GuiRenderState.SemanticPhase.ITEMS);
			boolean hasSpecialRenderer = guiItemRenderState.itemStackRenderState().hasSpecialRenderer();
			List<RustGalGuiElementRenderState> specialElements = hasSpecialRenderer
				? RustGalGuiItemRenderer.tryEnqueueSpecialItem(guiItemRenderState, guiWidth, guiHeight, dynamicLayerOrder)
				: List.of();
			if (hasSpecialRenderer) {
				// A special item has no faithful generic-flat representation. If its
				// explicit Rust collector rejects it, keep the whole family unavailable
				// rather than silently drawing a partial fallback from ordinary layers.
				if (specialElements.isEmpty()) {
					RustGalGuiRenderer.recordUnsupportedElement("item:special-renderer");
				}
				for (RustGalGuiElementRenderState element : specialElements) this.renderState.submitGlyphToCurrentLayer(element);
				return;
			}
			boolean standard3dCandidate = guiItemRenderState.itemStackRenderState().usesBlockLight()
				&& RustGalGuiItemRenderer.standard3dRouteEnabled();
			List<RustGalGuiElementRenderState> elements = standard3dCandidate
				? RustGalGuiItemRenderer.tryEnqueueStandard3dItem(guiItemRenderState, guiWidth, guiHeight, dynamicLayerOrder)
				: RustGalGuiItemRenderer.tryEnqueueFlatItem(guiItemRenderState, guiWidth, guiHeight, dynamicLayerOrder);
			if (standard3dCandidate && !elements.isEmpty()) {
				this.rustOwnedStandard3dItems.add(guiItemRenderState);
			}
			if (elements.isEmpty()) {
				RustGalGuiRenderer.recordUnsupportedElement("item");
			}
			for (RustGalGuiElementRenderState element : elements) {
				this.renderState.submitGlyphToCurrentLayer(element);
			}
		});
	}

	/**
	 * Copies oversized-item picture-in-picture states into the same explicit
	 * Rust GUI item routes used by ordinary item nodes. Whole-frame Vulkan never
	 * prepares the Java off-screen PIP renderer, so admitted states must become
	 * owned semantic elements here or remain absent for the frame.
	 */
	public void collectRustGalPictureInPictureSemantics() {
		int guiWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
		int guiHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
		int[] pictureInPictureCount = {0};
		this.renderState.forEachPictureInPicture(pictureInPictureRenderState -> {
			if (++pictureInPictureCount[0] > MAX_RUST_PICTURE_IN_PICTURE_STATES) {
				throw new IllegalStateException(
					"Rust whole-frame GUI picture-in-picture bound exceeded " + MAX_RUST_PICTURE_IN_PICTURE_STATES
				);
			}
			if (pictureInPictureRenderState instanceof net.minecraft.client.gui.render.state.pip.GuiProfilerChartRenderState chart) {
				int dynamicLayerOrder = this.renderState.currentSemanticLayerOrder(GuiRenderState.SemanticPhase.ELEMENTS);
				List<RustGalGuiElementRenderState> elements = RustGalGuiRenderer.tryEnqueueProfilerChart(
					chart, guiWidth, guiHeight, dynamicLayerOrder
				);
				if (elements != null && !elements.isEmpty()) {
					this.rustOwnedPictureInPictureStates.add(pictureInPictureRenderState);
					for (RustGalGuiElementRenderState element : elements) this.renderState.submitGlyphToCurrentLayer(element);
				} else {
					RustGalGuiRenderer.recordUnsupportedElement("picture-in-picture:profiler-chart");
				}
				return;
			}
			int modelLayerOrder = this.renderState.currentSemanticLayerOrder(GuiRenderState.SemanticPhase.ITEMS);
			if (pictureInPictureRenderState instanceof GuiEntityRenderState entityPip) {
				List<RustGalGuiElementRenderState> elements = RustGalGuiRenderer.tryEnqueueEntityPip(entityPip, modelLayerOrder);
				if (elements != null && !elements.isEmpty()) {
					this.rustOwnedPictureInPictureStates.add(pictureInPictureRenderState);
					for (RustGalGuiElementRenderState element : elements) this.renderState.submitGlyphToCurrentLayer(element);
				} else {
					RustGalGuiRenderer.recordUnsupportedElement("picture-in-picture:entity");
				}
				return;
			}
			if (pictureInPictureRenderState instanceof GuiSkinRenderState skin) {
				List<RustGalGuiElementRenderState> elements = RustGalGuiRenderer.tryEnqueueModelPip(
					skin.playerModel(), skin.texture(), skin.x0(), skin.y0(), skin.x1(), skin.y1(), skin.scale(),
					new Matrix3x2f(), skin.scissorArea(), modelLayerOrder, pose -> {
						pose.translate(0.0F, -skin.pivotY(), 0.0F);
						pose.mulPose(Axis.XP.rotationDegrees(skin.rotationX()));
						pose.translate(0.0F, skin.pivotY(), 0.0F);
						pose.mulPose(Axis.YP.rotationDegrees(-skin.rotationY()));
						pose.translate(0.0F, -1.6010001F, 0.0F);
					});
				if (elements != null && !elements.isEmpty()) {
					this.rustOwnedPictureInPictureStates.add(pictureInPictureRenderState);
					for (RustGalGuiElementRenderState element : elements) this.renderState.submitGlyphToCurrentLayer(element);
				} else {
					RustGalGuiRenderer.recordUnsupportedElement("picture-in-picture:skin");
				}
				return;
			}
			if (pictureInPictureRenderState instanceof GuiBookModelRenderState book) {
				float h = Mth.clamp(Mth.frac(book.flip() + 0.25F) * 1.6F - 0.3F, 0.0F, 1.0F);
				float i = Mth.clamp(Mth.frac(book.flip() + 0.75F) * 1.6F - 0.3F, 0.0F, 1.0F);
				book.bookModel().setupAnim(new net.minecraft.client.model.BookModel.State(0.0F, h, i, book.open()));
				List<RustGalGuiElementRenderState> elements = RustGalGuiRenderer.tryEnqueueModelPip(
					book.bookModel(), book.texture(), book.x0(), book.y0(), book.x1(), book.y1(), book.scale(),
					new Matrix3x2f(), book.scissorArea(), modelLayerOrder, pose -> {
						pose.mulPose(Axis.YP.rotationDegrees(180.0F));
						pose.mulPose(Axis.XP.rotationDegrees(25.0F));
						float open = book.open();
						pose.translate((1.0F - open) * 0.2F, (1.0F - open) * 0.1F, (1.0F - open) * 0.25F);
						pose.mulPose(Axis.YP.rotationDegrees(-(1.0F - open) * 90.0F - 90.0F));
						pose.mulPose(Axis.XP.rotationDegrees(180.0F));
					});
				if (elements != null && !elements.isEmpty()) {
					this.rustOwnedPictureInPictureStates.add(pictureInPictureRenderState);
					for (RustGalGuiElementRenderState element : elements) this.renderState.submitGlyphToCurrentLayer(element);
				} else {
					RustGalGuiRenderer.recordUnsupportedElement("picture-in-picture:book");
				}
				return;
			}
			if (pictureInPictureRenderState instanceof GuiSignRenderState sign) {
				net.minecraft.client.resources.model.Material signMaterial = net.minecraft.client.renderer.Sheets.getSignMaterial(sign.woodType());
				List<RustGalGuiElementRenderState> elements = RustGalGuiRenderer.tryEnqueueModelPip(
					sign.signModel(), signMaterial.texture(), sign.x0(), sign.y0(), sign.x1(), sign.y1(), sign.scale(),
					new Matrix3x2f(), sign.scissorArea(), modelLayerOrder, pose -> pose.translate(0.0F, -0.75F, 0.0F));
				if (elements != null && !elements.isEmpty()) {
					this.rustOwnedPictureInPictureStates.add(pictureInPictureRenderState);
					for (RustGalGuiElementRenderState element : elements) this.renderState.submitGlyphToCurrentLayer(element);
				} else {
					RustGalGuiRenderer.recordUnsupportedElement("picture-in-picture:sign");
				}
				return;
			}
			if (pictureInPictureRenderState instanceof GuiBannerResultRenderState banner) {
				List<RustGalGuiElementRenderState> elements = RustGalGuiRenderer.tryEnqueueBannerPip(banner, modelLayerOrder);
				if (elements != null && !elements.isEmpty()) {
					this.rustOwnedPictureInPictureStates.add(pictureInPictureRenderState);
					for (RustGalGuiElementRenderState element : elements) this.renderState.submitGlyphToCurrentLayer(element);
				} else {
					RustGalGuiRenderer.recordUnsupportedElement("picture-in-picture:banner");
				}
				return;
			}
			if (!(pictureInPictureRenderState instanceof OversizedItemRenderState oversized)) {
				RustGalGuiRenderer.recordUnsupportedElement("picture-in-picture:" + pictureInPictureRenderState.getClass().getName());
				return;
			}
			GuiItemRenderState item = oversized.guiItemRenderState();
			int dynamicLayerOrder = this.renderState.currentSemanticLayerOrder(GuiRenderState.SemanticPhase.ITEMS);
			if (item.itemStackRenderState().hasSpecialRenderer()) {
				List<RustGalGuiElementRenderState> specialElements = RustGalGuiItemRenderer.tryEnqueueSpecialItem(
					item, guiWidth, guiHeight, dynamicLayerOrder
				);
				if (specialElements != null && !specialElements.isEmpty()) {
					this.rustOwnedPictureInPictureStates.add(pictureInPictureRenderState);
					for (RustGalGuiElementRenderState element : specialElements) {
						this.renderState.submitGlyphToCurrentLayer(element);
					}
				} else {
					RustGalGuiRenderer.recordUnsupportedElement("picture-in-picture:oversized-special-item");
				}
				return;
			}
			boolean standard3dCandidate = item.itemStackRenderState().usesBlockLight()
				&& RustGalGuiItemRenderer.standard3dRouteEnabled();
			List<RustGalGuiElementRenderState> elements = standard3dCandidate
				? RustGalGuiItemRenderer.tryEnqueueStandard3dItem(item, guiWidth, guiHeight, dynamicLayerOrder)
				: RustGalGuiItemRenderer.tryEnqueueFlatItem(item, guiWidth, guiHeight, dynamicLayerOrder);
			if (!elements.isEmpty()) {
				this.rustOwnedPictureInPictureStates.add(pictureInPictureRenderState);
				if (standard3dCandidate) {
					this.rustOwnedStandard3dItems.add(item);
				}
			}
			for (RustGalGuiElementRenderState element : elements) {
				this.renderState.submitGlyphToCurrentLayer(element);
			}
			if (elements.isEmpty()) {
				RustGalGuiRenderer.recordUnsupportedElement("picture-in-picture:oversized-item");
			}
		});
	}

	/**
	 * Extracts only exact uniform-color GUI rectangles. Unsupported GUI element
	 * families remain unavailable to the whole-frame route instead of being
	 * reconstructed through Java rendering.
	 */
	public void collectRustGalRectangleSemantics() {
		int guiWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
		int guiHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
		Map<Integer, List<ColoredRectangleRenderState>> largeGroups = new java.util.HashMap<>();
		this.renderState.forEachElement(guiElementRenderState -> {
			if (guiElementRenderState instanceof ColoredRectangleRenderState rectangle) {
				int layer = this.renderState.currentSemanticLayerOrder(GuiRenderState.SemanticPhase.ELEMENTS);
				largeGroups.computeIfAbsent(layer, ignored -> new ArrayList<>()).add(rectangle);
			} else if (guiElementRenderState instanceof FourColoredRectangleRenderState rectangle
				&& rectangle.color00() == rectangle.color10() && rectangle.color00() == rectangle.color01() && rectangle.color00() == rectangle.color11()
				&& rectangle.x0() == Math.rint(rectangle.x0()) && rectangle.y0() == Math.rint(rectangle.y0())
				&& rectangle.x1() == Math.rint(rectangle.x1()) && rectangle.y1() == Math.rint(rectangle.y1())) {
				int layer = this.renderState.currentSemanticLayerOrder(GuiRenderState.SemanticPhase.ELEMENTS);
				largeGroups.computeIfAbsent(layer, ignored -> new ArrayList<>()).add(new ColoredRectangleRenderState(
					rectangle.pipeline(), rectangle.textureSetup(), rectangle.pose(), (int) rectangle.x0(), (int) rectangle.y0(),
					(int) rectangle.x1(), (int) rectangle.y1(), rectangle.color00(), rectangle.color00(), rectangle.scissorArea()));
			}
		}, GuiRenderState.TraverseRange.ALL);
		Set<Integer> groupedLayers = new java.util.HashSet<>();
		this.renderState.forEachElement(guiElementRenderState -> {
			if (guiElementRenderState instanceof net.vulkanic.gui.RustGalLoadingGridRenderState grid) {
				int layer = this.renderState.currentSemanticLayerOrder(GuiRenderState.SemanticPhase.ELEMENTS);
				List<RustGalGuiElementRenderState> elements = RustGalGuiRenderer.tryEnqueueLoadingGrid(grid.colors(), grid.gridSize(), grid.originX(), grid.originY(), grid.cellSize(), grid.stride(), guiWidth, guiHeight);
				if (Boolean.getBoolean("mattmc.dev.graphicsAuditSliceMetrics") && rustGalLoadingGridConsumerDiagnostics++ < 4) {
					System.out.println("[MattMC graphics audit] loading-grid semantic consumer elements=" + (elements == null ? "null" : elements.size()) + " layer=" + layer);
				}
				if (elements != null) for (RustGalGuiElementRenderState element : elements) this.renderState.submitGlyphToCurrentLayer(element);
				else RustGalGuiRenderer.recordUnsupportedElement("loading-grid");
			} else if (guiElementRenderState instanceof FourColoredRectangleRenderState rectangle) {
				int dynamicLayerOrder = this.renderState.currentSemanticLayerOrder(GuiRenderState.SemanticPhase.ELEMENTS);
				List<ColoredRectangleRenderState> group = largeGroups.get(dynamicLayerOrder);
				if (group != null && group.size() >= 1024 && rectangle.color00() == rectangle.color10() && rectangle.color00() == rectangle.color01() && rectangle.color00() == rectangle.color11()
					&& rectangle.x0() == Math.rint(rectangle.x0()) && rectangle.y0() == Math.rint(rectangle.y0()) && rectangle.x1() == Math.rint(rectangle.x1()) && rectangle.y1() == Math.rint(rectangle.y1())) {
					if (groupedLayers.add(dynamicLayerOrder)) {
						List<RustGalGuiElementRenderState> elements = RustGalGuiRenderer.tryEnqueueRectangleGroup(group, guiWidth, guiHeight, dynamicLayerOrder);
						if (elements != null) for (RustGalGuiElementRenderState element : elements) this.renderState.submitGlyphToCurrentLayer(element);
						else RustGalGuiRenderer.recordUnsupportedElement("rectangle-group");
					}
					return;
				}
				List<RustGalGuiElementRenderState> elements = RustGalGuiRenderer.tryEnqueueFourColoredRectangle(
					rectangle, guiWidth, guiHeight, dynamicLayerOrder
				);
				if (elements != null) {
					for (RustGalGuiElementRenderState element : elements) {
						this.renderState.submitGlyphToCurrentLayer(element);
					}
				} else {
					RustGalGuiRenderer.recordUnsupportedElement("four-colored-rectangle");
				}
			} else if (guiElementRenderState instanceof ColoredRectangleRenderState rectangle) {
				int dynamicLayerOrder = this.renderState.currentSemanticLayerOrder(GuiRenderState.SemanticPhase.ELEMENTS);
				List<ColoredRectangleRenderState> group = largeGroups.get(dynamicLayerOrder);
				if (group != null && group.size() >= 1024) {
					if (groupedLayers.add(dynamicLayerOrder)) {
						List<RustGalGuiElementRenderState> elements = RustGalGuiRenderer.tryEnqueueRectangleGroup(group, guiWidth, guiHeight, dynamicLayerOrder);
						if (elements != null) for (RustGalGuiElementRenderState element : elements) this.renderState.submitGlyphToCurrentLayer(element);
						else RustGalGuiRenderer.recordUnsupportedElement("rectangle-group");
					}
					return;
				}
				List<RustGalGuiElementRenderState> elements = RustGalGuiRenderer.tryEnqueueUniformRectangle(
					rectangle, guiWidth, guiHeight, dynamicLayerOrder
				);
				if (elements != null) {
					for (RustGalGuiElementRenderState element : elements) {
						this.renderState.submitGlyphToCurrentLayer(element);
					}
				} else {
					RustGalGuiRenderer.recordUnsupportedElement("rectangle");
				}
			}
		}, GuiRenderState.TraverseRange.ALL);
	}

	/**
	 * Extracts only one-sampler GUI_TEXTURED blits backed by copied resource PNGs
	 * or copied stitched-atlas pixels. All other GUI materials remain absent
	 * rather than crossing the whole-frame boundary through a Java texture view.
	 */
	public void collectRustGalCopiedBlitSemantics() {
		int guiWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
		int guiHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
		this.renderState.forEachElement(guiElementRenderState -> {
			if (guiElementRenderState instanceof BlitRenderState blit) {
				boolean vignette = blit.pipeline() == RenderPipelines.VIGNETTE;
				int dynamicLayerOrder = this.renderState.currentSemanticLayerOrder(GuiRenderState.SemanticPhase.ELEMENTS);
				List<RustGalGuiElementRenderState> elements = RustGalGuiRenderer.tryEnqueueCopiedBlit(
					blit, guiWidth, guiHeight, dynamicLayerOrder
				);
				if (elements != null) {
					for (RustGalGuiElementRenderState element : elements) {
						this.renderState.submitGlyphToCurrentLayer(element);
					}
				} else {
					RustGalGuiRenderer.recordUnsupportedElement("blit");
					RustGalGuiRenderer.recordUnsupportedElementDetail(
						"blit-source:" + (blit.semanticTexture() == null ? "missing-texture" : blit.semanticTexture())
					);
					RustGalGuiRenderer.recordUnsupportedElementDetail("blit-reason:" + RustGalGuiRenderer.copiedBlitFailureDetail(blit));
				}
			} else if (guiElementRenderState instanceof TiledBlitRenderState tiledBlit) {
				int dynamicLayerOrder = this.renderState.currentSemanticLayerOrder(GuiRenderState.SemanticPhase.ELEMENTS);
				List<RustGalGuiElementRenderState> elements = RustGalGuiRenderer.tryEnqueueTiledCopiedBlit(
					tiledBlit, guiWidth, guiHeight, dynamicLayerOrder
				);
				if (elements != null) {
					for (RustGalGuiElementRenderState element : elements) {
						this.renderState.submitGlyphToCurrentLayer(element);
					}
				} else {
					RustGalGuiRenderer.recordUnsupportedElement("tiled-blit");
					RustGalGuiRenderer.recordUnsupportedElementDetail(
						"tiled-blit-source:" + (tiledBlit.semanticTexture() == null ? "missing-texture" : tiledBlit.semanticTexture())
					);
				}
			} else if (guiElementRenderState instanceof GlyphRenderState glyph) {
				int dynamicLayerOrder = this.renderState.currentSemanticLayerOrder(GuiRenderState.SemanticPhase.TEXT);
				List<RustGalGuiElementRenderState> elements = RustGalGuiRenderer.tryEnqueueGlyph(
					glyph, guiWidth, guiHeight, dynamicLayerOrder
				);
				if (elements != null) {
					for (RustGalGuiElementRenderState element : elements) {
						this.renderState.submitGlyphToCurrentLayer(element);
					}
				} else {
					RustGalGuiRenderer.recordUnsupportedElement("glyph");
				}
			} else if (!(guiElementRenderState instanceof net.vulkanic.gui.RustGalGuiElementRenderState)
				&& !(guiElementRenderState instanceof net.vulkanic.gui.RustGalLoadingGridRenderState)
				&& !(guiElementRenderState instanceof ColoredRectangleRenderState)
				&& !(guiElementRenderState instanceof FourColoredRectangleRenderState)) {
				// The Rust collectors add their own scheduler tokens to the same
				// glyph traversal.  Those are already explicit semantic work and
				// must not be counted again; every other state is a callsite that
				// this whole-frame extraction pass does not understand yet.
				// Record it rather than silently dropping a future GUI family.
				RustGalGuiRenderer.recordUnsupportedElement("unclassified-gui-state");
				RustGalGuiRenderer.recordUnsupportedElementDetail(
					"gui-state-class:" + guiElementRenderState.getClass().getName()
				);
			}
		}, GuiRenderState.TraverseRange.ALL);
	}

	/** Retires per-frame ownership markers after Rust has presented the GUI. */
	public void finishRustGalWholeFrame() {
		this.rustOwnedStandard3dItems.clear();
		this.rustOwnedPictureInPictureStates.clear();
	}

	private void prepareText() {
		this.renderState.forEachText(guiTextRenderState -> {
			List<RustGalGuiElementRenderState> rustGalText = RustGalGuiRenderer.tryEnqueueText(
				guiTextRenderState,
				Minecraft.getInstance().getWindow().getGuiScaledWidth(),
				Minecraft.getInstance().getWindow().getGuiScaledHeight()
			);
			if (rustGalText != null) {
				for (RustGalGuiElementRenderState element : rustGalText) {
					this.renderState.submitGlyphToCurrentLayer(element);
				}
				return;
			}
			// A semantic extraction miss is an admission failure for the
			// exclusive Rust presenter. Do not put Java glyph state back into
			// the render state, where it could become a hidden same-frame
			// fallback or be silently dropped by the Rust scheduler.
			RustGalGuiRenderer.recordUnsupportedElement("text");
			return;
		});
	}

	private void enableScissor(ScreenRectangle screenRectangle, RenderPass renderPass) {
		Window window = Minecraft.getInstance().getWindow();
		int i = window.getHeight();
		int j = window.getGuiScale();
		double d = screenRectangle.left() * j;
		double e = i - screenRectangle.bottom() * j;
		double f = screenRectangle.width() * j;
		double g = screenRectangle.height() * j;
		renderPass.enableScissor((int)d, (int)e, Math.max(0, (int)f), Math.max(0, (int)g));
	}

	public void close() {
		this.byteBufferBuilder.close();
		if (this.itemsAtlas != null) {
			this.itemsAtlas.close();
		}

		if (this.itemsAtlasView != null) {
			this.itemsAtlasView.close();
		}

		if (this.itemsAtlasDepth != null) {
			this.itemsAtlasDepth.close();
		}

		if (this.itemsAtlasDepthView != null) {
			this.itemsAtlasDepthView.close();
		}

		if (this.guiProjectionMatrixBuffer != null) {
			this.guiProjectionMatrixBuffer.close();
		}
		if (this.itemsProjectionMatrixBuffer != null) {
			this.itemsProjectionMatrixBuffer.close();
		}

		for (MappableRingBuffer mappableRingBuffer : this.vertexBuffers.values()) {
			mappableRingBuffer.close();
		}

	}

	/** Releases Java GUI GPU state while retaining semantic render-state inputs. */
	public void ensureRustSemanticRoute() {
		if (this.itemsAtlas != null) {
			this.itemsAtlas.close();
			this.itemsAtlas = null;
		}
		if (this.itemsAtlasView != null) {
			this.itemsAtlasView.close();
			this.itemsAtlasView = null;
		}
		if (this.itemsAtlasDepth != null) {
			this.itemsAtlasDepth.close();
			this.itemsAtlasDepth = null;
		}
		if (this.itemsAtlasDepthView != null) {
			this.itemsAtlasDepthView.close();
			this.itemsAtlasDepthView = null;
		}
		if (this.guiProjectionMatrixBuffer != null) this.guiProjectionMatrixBuffer.ensureRustSemanticRoute();
		if (this.itemsProjectionMatrixBuffer != null) this.itemsProjectionMatrixBuffer.ensureRustSemanticRoute();
		for (MappableRingBuffer buffer : this.vertexBuffers.values()) buffer.close();
		this.vertexBuffers.clear();
	}

	@Environment(EnvType.CLIENT)
	static final class AtlasPosition {
		final int x;
		final int y;
		final float u;
		final float v;
		int lastAnimatedOnFrame;

		AtlasPosition(int i, int j, float f, float g, int k) {
			this.x = i;
			this.y = j;
			this.u = f;
			this.v = g;
			this.lastAnimatedOnFrame = k;
		}
	}

	@Environment(EnvType.CLIENT)
	record Draw(
		GpuBuffer vertexBuffer,
		int baseVertex,
		VertexFormat.Mode mode,
		int indexCount,
		RenderPipeline pipeline,
		TextureSetup textureSetup,
		@Nullable ScreenRectangle scissorArea,
		String shaderInputParityGeometryContext
	) implements DrawStep {
	}

	@Environment(EnvType.CLIENT)
	interface DrawStep {
	}

	@Environment(EnvType.CLIENT)
	interface PreparedStep extends AutoCloseable {
		@Override
		default void close() {
		}
	}

	@Environment(EnvType.CLIENT)
	record RustGalDraw(RustGalGuiElementRenderState element) implements DrawStep, PreparedStep {
	}

	@Environment(EnvType.CLIENT)
	record MeshToDraw(
		MeshData mesh,
		RenderPipeline pipeline,
		TextureSetup textureSetup,
		@Nullable ScreenRectangle scissorArea,
		@Nullable String shaderInputParityGeometryContext
	) implements PreparedStep {

		@Override
		public void close() {
			this.mesh.close();
		}
	}
}
