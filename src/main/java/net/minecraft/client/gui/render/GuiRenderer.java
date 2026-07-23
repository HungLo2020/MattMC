package net.minecraft.client.gui.render;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import net.blaze3d.ProjectionType;
import net.blaze3d.buffers.GpuBuffer;
import net.blaze3d.buffers.GpuBufferSlice;
import net.blaze3d.opengl.GlConst;
import net.blaze3d.pipeline.BlendFunction;
import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.pipeline.RenderTarget;
import net.blaze3d.platform.DepthTestFunction;
import net.blaze3d.platform.Lighting;
import net.blaze3d.platform.LogicOp;
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
import net.blaze3d.vertex.VertexFormatElement;
import net.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import net.minecraft.client.gui.render.pip.OversizedItemRenderer;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.gui.render.pip.Standard3dItemRenderer;
import net.minecraft.client.gui.render.state.BlitRenderState;
import net.minecraft.client.gui.render.state.GlyphRenderState;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.gui.render.state.GuiItemRenderState;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.pip.OversizedItemRenderState;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.CachedOrthoProjectionMatrixBuffer;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.vulkanic.PipelineDescriptor;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicCompatibilityState;
import net.vulkanic.VulkanicGalExecutionRequest;
import net.vulkanic.VulkanicGalV2;
import net.vulkanic.VulkanicIndexType;
import net.vulkanic.VulkanicPassResourceModel;
import net.vulkanic.VulkanicPassResourcePlanner;
import net.vulkanic.VulkanicPrimitiveMode;
import net.vulkanic.VulkanicResourceBarriers;
import net.vulkanic.VulkanicResourceUsage;
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
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final VulkanicResourceBarriers OFFSCREEN_COLOR_WRITES_VISIBLE_TO_TEXTURE_FETCH = VulkanicResourceBarriers.of(
		VulkanicResourceBarriers.Barrier.TEXTURE_FETCH
	);
	private static final boolean EXPLICIT_GAL_V2_GUI_TEXT = Boolean.parseBoolean(System.getProperty("mattmc.gal.v2.guiTextExplicit", "true"));
	private static final int MAX_RETAINED_EXPLICIT_GAL_V2_GUI_OBJECTS = 256;
	private static final float MAX_GUI_Z = 10000.0F;
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
	private final Map<Object, OversizedItemRenderer> oversizedItemRenderers = new Object2ObjectOpenHashMap<>();
	private final Map<Object, Standard3dItemRenderer> standard3dItemRenderers = new Object2ObjectOpenHashMap<>();
	private final Map<String, VulkanicGalV2.RetainedHandle> explicitGalV2GuiObjects = new HashMap<>();
	private final Map<VertexFormat, VulkanicGalV2.VertexLayout> explicitGalV2GuiVertexLayouts = new Object2ObjectOpenHashMap<>();
	final GuiRenderState renderState;
	private final List<GuiRenderer.Draw> draws = new ArrayList();
	private final List<GuiRenderer.MeshToDraw> meshesToDraw = new ArrayList();
	private final ByteBufferBuilder byteBufferBuilder = new ByteBufferBuilder(786432);
	private final Map<VertexFormat, MappableRingBuffer> vertexBuffers = new Object2ObjectOpenHashMap<>();
	private int firstDrawIndexAfterBlur = Integer.MAX_VALUE;
	private final CachedOrthoProjectionMatrixBuffer guiProjectionMatrixBuffer = new CachedOrthoProjectionMatrixBuffer("gui", 1000.0F, 11000.0F, true);
	private final CachedOrthoProjectionMatrixBuffer itemsProjectionMatrixBuffer = new CachedOrthoProjectionMatrixBuffer("items", -1000.0F, 1000.0F, true);
	private final MultiBufferSource.BufferSource bufferSource;
	private final SubmitNodeCollector submitNodeCollector;
	private final FeatureRenderDispatcher featureRenderDispatcher;
	private final Map<Class<? extends PictureInPictureRenderState>, PictureInPictureRenderer<?>> pictureInPictureRenderers;
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
		FeatureRenderDispatcher featureRenderDispatcher,
		List<PictureInPictureRenderer<?>> list
	) {
		this.renderState = guiRenderState;
		this.bufferSource = bufferSource;
		this.submitNodeCollector = submitNodeCollector;
		this.featureRenderDispatcher = featureRenderDispatcher;
		Builder<Class<? extends PictureInPictureRenderState>, PictureInPictureRenderer<?>> builder = ImmutableMap.builder();

		for (PictureInPictureRenderer<?> pictureInPictureRenderer : list) {
			builder.put((Class<? extends PictureInPictureRenderState>)pictureInPictureRenderer.getRenderStateClass(), pictureInPictureRenderer);
		}

		this.pictureInPictureRenderers = builder.buildOrThrow();
	}

	public void incrementFrameNumber() {
		this.frameNumber++;
	}

	public void render(GpuBufferSlice gpuBufferSlice) {
		this.prepare();
		this.draw(gpuBufferSlice);

		for (MappableRingBuffer mappableRingBuffer : this.vertexBuffers.values()) {
			mappableRingBuffer.rotate();
		}

		this.draws.clear();
		this.meshesToDraw.clear();
		this.renderState.reset();
		this.firstDrawIndexAfterBlur = Integer.MAX_VALUE;
		this.clearUnusedOversizedItemRenderers();
		this.clearUnusedStandard3dItemRenderers();
		if (SharedConstants.DEBUG_SHUFFLE_UI_RENDERING_ORDER) {
			RenderPipeline.updateSortKeySeed();
			TextureSetup.updateSortKeySeed();
		}
	}

	private void clearUnusedOversizedItemRenderers() {
		Iterator<Entry<Object, OversizedItemRenderer>> iterator = this.oversizedItemRenderers.entrySet().iterator();

		while (iterator.hasNext()) {
			Entry<Object, OversizedItemRenderer> entry = (Entry<Object, OversizedItemRenderer>)iterator.next();
			OversizedItemRenderer oversizedItemRenderer = (OversizedItemRenderer)entry.getValue();
			if (!oversizedItemRenderer.usedOnThisFrame()) {
				oversizedItemRenderer.close();
				iterator.remove();
			} else {
				oversizedItemRenderer.resetUsedOnThisFrame();
			}
		}
	}

	private void clearUnusedStandard3dItemRenderers() {
		Iterator<Entry<Object, Standard3dItemRenderer>> iterator = this.standard3dItemRenderers.entrySet().iterator();

		while (iterator.hasNext()) {
			Entry<Object, Standard3dItemRenderer> entry = (Entry<Object, Standard3dItemRenderer>)iterator.next();
			Standard3dItemRenderer standard3dItemRenderer = (Standard3dItemRenderer)entry.getValue();
			if (!standard3dItemRenderer.usedOnThisFrame()) {
				standard3dItemRenderer.close();
				iterator.remove();
			} else {
				standard3dItemRenderer.resetUsedOnThisFrame();
			}
		}
	}

	private void prepare() {
		this.bufferSource.endBatch();
		this.preparePictureInPicture();
		this.prepareItemElements();
		this.prepareText();
		this.renderState.sortElements(ELEMENT_SORT_COMPARATOR);
		this.addElementsToMeshes(GuiRenderState.TraverseRange.BEFORE_BLUR);
		this.firstDrawIndexAfterBlur = this.meshesToDraw.size();
		this.addElementsToMeshes(GuiRenderState.TraverseRange.AFTER_BLUR);
		this.recordDraws();
	}

	private void addElementsToMeshes(GuiRenderState.TraverseRange traverseRange) {
		this.previousScissorArea = null;
		this.previousPipeline = null;
		this.previousTextureSetup = null;
		this.previousShaderInputParityGeometryContext = null;
		this.bufferBuilder = null;
		this.renderState.forEachElement(this::addElementToMesh, traverseRange);
		if (this.bufferBuilder != null) {
			this.recordMesh(this.bufferBuilder, this.previousPipeline, this.previousTextureSetup, this.previousScissorArea, this.previousShaderInputParityGeometryContext);
		}
	}

	private void draw(GpuBufferSlice gpuBufferSlice) {
		if (!this.draws.isEmpty()) {
			Minecraft minecraft = Minecraft.getInstance();
			Window window = minecraft.getWindow();
			net.vulkanic.VulkanicAPI.setProjectionMatrix(
				this.guiProjectionMatrixBuffer.getBuffer((float)window.getWidth() / window.getGuiScale(), (float)window.getHeight() / window.getGuiScale()),
				ProjectionType.ORTHOGRAPHIC
			);
			RenderTarget renderTarget = minecraft.getMainRenderTarget();
			int i = 0;

			for (GuiRenderer.Draw draw : this.draws) {
				if (draw.indexCount > i) {
					i = draw.indexCount;
				}
			}

			VulkanicAPI.AutoStorageIndexBuffer autoStorageIndexBuffer = VulkanicAPI.getSequentialBuffer(VertexFormat.Mode.QUADS);
			GpuBuffer gpuBuffer = autoStorageIndexBuffer.getBuffer(i);
			VertexFormat.IndexType indexType = autoStorageIndexBuffer.type();
			GpuBufferSlice gpuBufferSlice2 = VulkanicAPI.getDynamicUniforms()
				.writeTransform(new Matrix4f().setTranslation(0.0F, 0.0F, -11000.0F), new Vector4f(1.0F, 1.0F, 1.0F, 1.0F), new Vector3f(), new Matrix4f(), 0.0F);
			if (this.firstDrawIndexAfterBlur > 0) {
				this.executeDrawRange(
					() -> "GUI before blur", renderTarget, gpuBufferSlice, gpuBufferSlice2, gpuBuffer, indexType, 0, Math.min(this.firstDrawIndexAfterBlur, this.draws.size())
				);
			}

			if (this.draws.size() > this.firstDrawIndexAfterBlur) {
				VulkanicAPI.createCommandEncoder().clearDepthTexture(renderTarget.getDepthTexture(), 1.0);
				minecraft.gameRenderer.processBlurEffect();
				this.executeDrawRange(
					() -> "GUI after blur", renderTarget, gpuBufferSlice, gpuBufferSlice2, gpuBuffer, indexType, this.firstDrawIndexAfterBlur, this.draws.size()
				);
			}
		}
	}

	private void executeDrawRange(
		Supplier<String> supplier,
		RenderTarget renderTarget,
		GpuBufferSlice gpuBufferSlice,
		GpuBufferSlice gpuBufferSlice2,
		GpuBuffer gpuBuffer,
		VertexFormat.IndexType indexType,
		int i,
		int j
	) {
		try (RenderPass renderPass = VulkanicAPI.createRenderPass(
					supplier,
					renderTarget.getColorTextureView(),
					OptionalInt.empty(),
					renderTarget.useDepth ? renderTarget.getDepthTextureView() : null,
					OptionalDouble.empty()
				)) {
			for (int k = i; k < j; k++) {
				GuiRenderer.Draw draw = (GuiRenderer.Draw)this.draws.get(k);
				this.executeDraw(draw, renderPass, renderTarget, gpuBufferSlice, gpuBufferSlice2, gpuBuffer, indexType, k);
			}
		}
	}

	private void addElementToMesh(GuiElementRenderState guiElementRenderState) {
		RenderPipeline renderPipeline = guiElementRenderState.pipeline();
		TextureSetup textureSetup = guiElementRenderState.textureSetup();
		ScreenRectangle screenRectangle = guiElementRenderState.scissorArea();
		String shaderInputParityGeometryContext = VulkanicAPI.isShaderInputParityTracingEnabled()
			? guiElementRenderState.shaderInputParityGeometryContext()
			: "";
		if (renderPipeline != this.previousPipeline
			|| this.scissorChanged(screenRectangle, this.previousScissorArea)
			|| !textureSetup.equals(this.previousTextureSetup)
			|| !java.util.Objects.equals(shaderInputParityGeometryContext, this.previousShaderInputParityGeometryContext)) {
			if (this.bufferBuilder != null) {
				this.recordMesh(this.bufferBuilder, this.previousPipeline, this.previousTextureSetup, this.previousScissorArea, this.previousShaderInputParityGeometryContext);
			}

			this.bufferBuilder = this.getBufferBuilder(renderPipeline);
			this.previousPipeline = renderPipeline;
			this.previousTextureSetup = textureSetup;
			this.previousScissorArea = screenRectangle;
			this.previousShaderInputParityGeometryContext = shaderInputParityGeometryContext;
		}

		guiElementRenderState.buildVertices(this.bufferBuilder);
	}

	private void prepareText() {
		this.renderState.forEachText(guiTextRenderState -> {
			final Matrix3x2f matrix3x2f = guiTextRenderState.pose;
			final ScreenRectangle screenRectangle = guiTextRenderState.scissor;
			guiTextRenderState.ensurePrepared().visit(new Font.GlyphVisitor() {
				@Override
				public void acceptGlyph(TextRenderable textRenderable) {
					this.accept(textRenderable);
				}

				@Override
				public void acceptEffect(TextRenderable textRenderable) {
					this.accept(textRenderable);
				}

				private void accept(TextRenderable textRenderable) {
					GuiRenderer.this.renderState.submitGlyphToCurrentLayer(new GlyphRenderState(matrix3x2f, textRenderable, screenRectangle));
				}
			});
		});
	}

	private void prepareItemElements() {
		int i = this.getGuiScaleInvalidatingItemAtlasIfChanged();
		if (Standard3dItemRenderer.isDebugDumpEnabled()) {
			Standard3dItemRenderer debugStandard3dItemRenderer = (Standard3dItemRenderer)this.standard3dItemRenderers
				.computeIfAbsent("debug_standard_3d_grass_block", object -> new Standard3dItemRenderer(this.bufferSource));
			debugStandard3dItemRenderer.prepareDebugStandardBlockItemDump(this.renderState, i);
		}

		if (VulkanicAPI.isVulkanBackendSelected()) {
			if (!this.renderState.getItemModelIdentities().isEmpty()) {
				this.prepareItemsViaPictureInPicture(i);
			}

			return;
		}

		if (!this.renderState.getItemModelIdentities().isEmpty()) {
			int j = 16 * i;
			int k = this.calculateAtlasSizeInPixels(j);
			if (this.itemsAtlas == null) {
				this.createAtlasTextures(k);
			}

			GpuBufferSlice previousProjectionMatrix = VulkanicAPI.getProjectionMatrixBuffer();
			ProjectionType previousProjectionType = VulkanicAPI.getProjectionType();
			GpuTextureView previousColorTextureOverride = VulkanicAPI.getOutputColorTextureOverride();
			GpuTextureView previousDepthTextureOverride = VulkanicAPI.getOutputDepthTextureOverride();
			MutableBoolean mutableBoolean = new MutableBoolean(false);
			MutableBoolean mutableBoolean2 = new MutableBoolean(false);
			boolean atlasWritten = false;
			try {
				net.vulkanic.VulkanicAPI.setOutputColorTextureOverride(this.itemsAtlasView);
				net.vulkanic.VulkanicAPI.setOutputDepthTextureOverride(this.itemsAtlasDepthView);
				net.vulkanic.VulkanicAPI.setProjectionMatrix(this.itemsProjectionMatrixBuffer.getBuffer(k, k), ProjectionType.ORTHOGRAPHIC);
				Minecraft.getInstance().gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_3D);
				PoseStack poseStack = new PoseStack();
				this.renderState
					.forEachItem(
						guiItemRenderState -> {
							if (guiItemRenderState.oversizedItemBounds() != null) {
								mutableBoolean2.setTrue();
							} else {
								TrackingItemStackRenderState trackingItemStackRenderState = guiItemRenderState.itemStackRenderState();
								GuiRenderer.AtlasPosition atlasPosition = (GuiRenderer.AtlasPosition)this.atlasPositions.get(trackingItemStackRenderState.getModelIdentity());
								if (atlasPosition == null || trackingItemStackRenderState.isAnimated() && atlasPosition.lastAnimatedOnFrame != this.frameNumber) {
									if (this.itemAtlasX + j > k) {
										this.itemAtlasX = 0;
										this.itemAtlasY += j;
									}

									boolean bl = trackingItemStackRenderState.isAnimated() && atlasPosition != null;
									if (!bl && this.itemAtlasY + j > k) {
										if (mutableBoolean.isFalse()) {
											LOGGER.warn("Trying to render too many items in GUI at the same time. Skipping some of them.");
											mutableBoolean.setTrue();
										}
									} else {
										int kx = bl ? atlasPosition.x : this.itemAtlasX;
										int l = bl ? atlasPosition.y : this.itemAtlasY;
										if (bl) {
											VulkanicAPI.createCommandEncoder().clearColorAndDepthTextures(this.itemsAtlas, 0, this.itemsAtlasDepth, 1.0, kx, k - l - j, j, j);
										}

										this.renderItemToAtlas(trackingItemStackRenderState, poseStack, kx, l, j);
										float f = (float)kx / k;
										float g = (float)(k - l) / k;
										this.submitBlitFromItemAtlas(guiItemRenderState, f, g, j, k);
										if (bl) {
											atlasPosition.lastAnimatedOnFrame = this.frameNumber;
										} else {
											this.atlasPositions
												.put(
													guiItemRenderState.itemStackRenderState().getModelIdentity(),
													new GuiRenderer.AtlasPosition(this.itemAtlasX, this.itemAtlasY, f, g, this.frameNumber)
												);
											this.itemAtlasX += j;
										}
									}
								} else {
									this.submitBlitFromItemAtlas(guiItemRenderState, atlasPosition.u, atlasPosition.v, j, k);
								}
							}
						}
					);
				atlasWritten = true;
			} finally {
				net.vulkanic.VulkanicAPI.setOutputColorTextureOverride(previousColorTextureOverride);
				net.vulkanic.VulkanicAPI.setOutputDepthTextureOverride(previousDepthTextureOverride);
				net.vulkanic.VulkanicAPI.setProjectionMatrix(previousProjectionMatrix, previousProjectionType);
				if (atlasWritten) {
					VulkanicAPI.applyResourceBarriers(VulkanicAPI.getCommandContext(), OFFSCREEN_COLOR_WRITES_VISIBLE_TO_TEXTURE_FETCH);
				}
			}

			if (mutableBoolean2.getValue()) {
				this.renderState
					.forEachItem(
						guiItemRenderState -> {
							if (guiItemRenderState.oversizedItemBounds() != null) {
								TrackingItemStackRenderState trackingItemStackRenderState = guiItemRenderState.itemStackRenderState();
								OversizedItemRenderer oversizedItemRenderer = (OversizedItemRenderer)this.oversizedItemRenderers
									.computeIfAbsent(trackingItemStackRenderState.getModelIdentity(), object -> new OversizedItemRenderer(this.bufferSource));
								ScreenRectangle screenRectangle = guiItemRenderState.oversizedItemBounds();
								OversizedItemRenderState oversizedItemRenderState = new OversizedItemRenderState(
									guiItemRenderState, screenRectangle.left(), screenRectangle.top(), screenRectangle.right(), screenRectangle.bottom()
								);
								oversizedItemRenderer.prepare(oversizedItemRenderState, this.renderState, i);
							}
						}
					);
			}
		}
	}

	private void prepareItemsViaPictureInPicture(int i) {
		this.renderState.forEachItem(guiItemRenderState -> {
			TrackingItemStackRenderState trackingItemStackRenderState = guiItemRenderState.itemStackRenderState();
			ScreenRectangle screenRectangle = guiItemRenderState.oversizedItemBounds();
			int j = screenRectangle != null ? screenRectangle.left() : guiItemRenderState.x();
			int k = screenRectangle != null ? screenRectangle.top() : guiItemRenderState.y();
			int l = screenRectangle != null ? screenRectangle.right() : guiItemRenderState.x() + 16;
			int m = screenRectangle != null ? screenRectangle.bottom() : guiItemRenderState.y() + 16;
			OversizedItemRenderState oversizedItemRenderState = new OversizedItemRenderState(guiItemRenderState, j, k, l, m);
			if (screenRectangle == null && trackingItemStackRenderState.usesBlockLight()) {
				Standard3dItemRenderer standard3dItemRenderer = (Standard3dItemRenderer)this.standard3dItemRenderers
					.computeIfAbsent(trackingItemStackRenderState.getModelIdentity(), object -> new Standard3dItemRenderer(this.bufferSource));
				standard3dItemRenderer.prepare(oversizedItemRenderState, this.renderState, i);
			} else {
				OversizedItemRenderer oversizedItemRenderer = (OversizedItemRenderer)this.oversizedItemRenderers
					.computeIfAbsent(trackingItemStackRenderState.getModelIdentity(), object -> new OversizedItemRenderer(this.bufferSource));
				oversizedItemRenderer.prepare(oversizedItemRenderState, this.renderState, i);
			}
		});
	}

	private void preparePictureInPicture() {
		int i = Minecraft.getInstance().getWindow().getGuiScale();
		this.renderState.forEachPictureInPicture(pictureInPictureRenderState -> this.preparePictureInPictureState(pictureInPictureRenderState, i));
	}

	private <T extends PictureInPictureRenderState> void preparePictureInPictureState(T pictureInPictureRenderState, int i) {
		PictureInPictureRenderer<T> pictureInPictureRenderer = (PictureInPictureRenderer<T>)this.pictureInPictureRenderers
			.get(pictureInPictureRenderState.getClass());
		if (pictureInPictureRenderer != null) {
			pictureInPictureRenderer.prepare(pictureInPictureRenderState, this.renderState, i);
		}
	}

	private void renderItemToAtlas(TrackingItemStackRenderState trackingItemStackRenderState, PoseStack poseStack, int i, int j, int k) {
		poseStack.pushPose();
		poseStack.translate(i + k / 2.0F, j + k / 2.0F, 0.0F);
		poseStack.scale(k, -k, k);
		boolean bl = !trackingItemStackRenderState.usesBlockLight();
		if (bl) {
			Minecraft.getInstance().gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_FLAT);
		} else {
			Minecraft.getInstance().gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_3D);
		}

		boolean bl2 = !VulkanicAPI.isVulkanBackendSelected();
		if (bl2) {
			net.vulkanic.VulkanicAPI.enableScissorForRenderTypeDraws(i, this.itemsAtlas.getHeight(0) - j - k, k, k);
		}
		trackingItemStackRenderState.submit(poseStack, this.submitNodeCollector, 15728880, OverlayTexture.NO_OVERLAY, 0);
		this.featureRenderDispatcher.renderAllFeatures();
		this.bufferSource.endBatch();
		if (bl2) {
			net.vulkanic.VulkanicAPI.disableScissorForRenderTypeDraws();
		}
		poseStack.popPose();
	}

	private void submitBlitFromItemAtlas(GuiItemRenderState guiItemRenderState, float f, float g, int i, int j) {
		float h = f + (float)i / j;
		float k = g + (float)(-i) / j;
		this.renderState
			.submitBlitToCurrentLayer(
				new BlitRenderState(
					RenderPipelines.GUI_TEXTURED,
					TextureSetup.singleTexture(this.itemsAtlasView),
					guiItemRenderState.pose(),
					guiItemRenderState.x(),
					guiItemRenderState.y(),
					guiItemRenderState.x() + 16,
					guiItemRenderState.y() + 16,
					f,
					h,
					g,
					k,
					-1,
					guiItemRenderState.scissorArea(),
					null,
					shaderInputParityGuiItemContext("gui-item", guiItemRenderState)
				)
			);
	}

	public static String shaderInputParityGuiItemContext(String source, GuiItemRenderState guiItemRenderState) {
		String name = VulkanicAPI.shaderInputParityDiagnosticLabel(guiItemRenderState.name());
		return source
			+ ":name=" + name
			+ ":pos=" + guiItemRenderState.x() + "x" + guiItemRenderState.y()
			+ ":oversized=" + (guiItemRenderState.oversizedItemBounds() != null);
	}

	private void createAtlasTextures(int i) {
		this.itemsAtlas = VulkanicAPI.createTexture("UI items atlas", 12, TextureFormat.RGBA8, i, i, 1, 1);
		this.itemsAtlas.setTextureFilter(FilterMode.NEAREST, false);
		this.itemsAtlasView = VulkanicAPI.createTextureView(this.itemsAtlas);
		this.itemsAtlasDepth = VulkanicAPI.createTexture("UI items atlas depth", 8, TextureFormat.DEPTH32, i, i, 1, 1);
		this.itemsAtlasDepthView = VulkanicAPI.createTextureView(this.itemsAtlasDepth);
		VulkanicAPI.createCommandEncoder().clearColorAndDepthTextures(this.itemsAtlas, 0, this.itemsAtlasDepth, 1.0);
	}

	private int calculateAtlasSizeInPixels(int i) {
		Set<Object> set = this.renderState.getItemModelIdentities();
		int j;
		if (this.atlasPositions.isEmpty()) {
			j = set.size();
		} else {
			j = this.atlasPositions.size();

			for (Object object : set) {
				if (!this.atlasPositions.containsKey(object)) {
					j++;
				}
			}
		}

		if (this.itemsAtlas != null) {
			int k = this.itemsAtlas.getWidth(0) / i;
			int l = k * k;
			if (j < l) {
				return this.itemsAtlas.getWidth(0);
			}

			this.invalidateItemAtlas();
		}

		int k = set.size();
		int l = Mth.smallestSquareSide(k + k / 2);
		return Math.clamp(Mth.smallestEncompassingPowerOfTwo(l * i), 512, MAXIMUM_ITEM_ATLAS_SIZE);
	}

	private int getGuiScaleInvalidatingItemAtlasIfChanged() {
		int i = Minecraft.getInstance().getWindow().getGuiScale();
		if (i != this.cachedGuiScale) {
			this.invalidateItemAtlas();

			for (OversizedItemRenderer oversizedItemRenderer : this.oversizedItemRenderers.values()) {
				oversizedItemRenderer.invalidateTexture();
			}

			for (Standard3dItemRenderer standard3dItemRenderer : this.standard3dItemRenderers.values()) {
				standard3dItemRenderer.invalidateTexture();
			}

			this.cachedGuiScale = i;
		}

		return i;
	}

	private void invalidateItemAtlas() {
		this.itemAtlasX = 0;
		this.itemAtlasY = 0;
		this.atlasPositions.clear();
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
	}

	private void recordMesh(
		BufferBuilder bufferBuilder,
		RenderPipeline renderPipeline,
		TextureSetup textureSetup,
		@Nullable ScreenRectangle screenRectangle,
		@Nullable String shaderInputParityGeometryContext
	) {
		MeshData meshData = bufferBuilder.build();
		if (meshData != null) {
			this.meshesToDraw.add(new GuiRenderer.MeshToDraw(meshData, renderPipeline, textureSetup, screenRectangle, shaderInputParityGeometryContext));
		}
	}

	private void recordDraws() {
		this.ensureVertexBufferSizes();
		CommandEncoder commandEncoder = VulkanicAPI.createCommandEncoder();
		Object2IntMap<VertexFormat> object2IntMap = new Object2IntOpenHashMap<>();

		for (GuiRenderer.MeshToDraw meshToDraw : this.meshesToDraw) {
			MeshData meshData = meshToDraw.mesh;
			MeshData.DrawState drawState = meshData.drawState();
			VertexFormat vertexFormat = drawState.format();
			MappableRingBuffer mappableRingBuffer = (MappableRingBuffer)this.vertexBuffers.get(vertexFormat);
			if (!object2IntMap.containsKey(vertexFormat)) {
				object2IntMap.put(vertexFormat, 0);
			}

			ByteBuffer byteBuffer = meshData.vertexBuffer();
			int i = byteBuffer.remaining();
			int j = object2IntMap.getInt(vertexFormat);

			try (GpuBuffer.MappedView mappedView = commandEncoder.mapBuffer(mappableRingBuffer.currentBuffer().slice(j, i), false, true)) {
				MemoryUtil.memCopy(byteBuffer, mappedView.data());
			}

			object2IntMap.put(vertexFormat, j + i);
			this.draws
				.add(
					new GuiRenderer.Draw(
						mappableRingBuffer.currentBuffer(),
						j / vertexFormat.getVertexSize(),
						drawState.mode(),
						drawState.indexCount(),
						meshToDraw.pipeline,
						meshToDraw.textureSetup,
						meshToDraw.scissorArea,
						meshToDraw.shaderInputParityGeometryContext
					)
				);
			meshToDraw.close();
		}
	}

	private void ensureVertexBufferSizes() {
		Object2IntMap<VertexFormat> object2IntMap = this.calculatedRequiredVertexBufferSizes();

		for (it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<VertexFormat> entry : object2IntMap.object2IntEntrySet()) {
			VertexFormat vertexFormat = (VertexFormat)entry.getKey();
			int i = entry.getIntValue();
			MappableRingBuffer mappableRingBuffer = (MappableRingBuffer)this.vertexBuffers.get(vertexFormat);
			if (mappableRingBuffer == null || mappableRingBuffer.size() < i) {
				if (mappableRingBuffer != null) {
					mappableRingBuffer.close();
				}

				this.vertexBuffers.put(vertexFormat, new MappableRingBuffer(() -> "GUI vertex buffer for " + vertexFormat, 34, i));
			}
		}
	}

	private Object2IntMap<VertexFormat> calculatedRequiredVertexBufferSizes() {
		Object2IntMap<VertexFormat> object2IntMap = new Object2IntOpenHashMap<>();

		for (GuiRenderer.MeshToDraw meshToDraw : this.meshesToDraw) {
			MeshData.DrawState drawState = meshToDraw.mesh.drawState();
			VertexFormat vertexFormat = drawState.format();
			if (!object2IntMap.containsKey(vertexFormat)) {
				object2IntMap.put(vertexFormat, 0);
			}

			object2IntMap.put(vertexFormat, object2IntMap.getInt(vertexFormat) + drawState.vertexCount() * vertexFormat.getVertexSize());
		}

		return object2IntMap;
	}

	private void executeDraw(
		GuiRenderer.Draw draw,
		RenderPass renderPass,
		RenderTarget renderTarget,
		GpuBufferSlice fogSlice,
		GpuBufferSlice dynamicTransformsSlice,
		GpuBuffer gpuBuffer,
		VertexFormat.IndexType indexType,
		int drawOrdinal
	) {
		if (this.tryExecuteExplicitGalV2GuiDraw(draw, renderPass, renderTarget, fogSlice, dynamicTransformsSlice, gpuBuffer, indexType, drawOrdinal)) {
			return;
		}

		bindLegacyGuiUniforms(renderPass, fogSlice, dynamicTransformsSlice);
		RenderPipeline renderPipeline = draw.pipeline();
		renderPass.setPipeline(renderPipeline);
		renderPass.setVertexBuffer(0, draw.vertexBuffer);
		ScreenRectangle screenRectangle = draw.scissorArea();
		if (screenRectangle != null) {
			this.enableScissor(screenRectangle, renderPass);
		} else {
			renderPass.disableScissor();
		}

		if (draw.textureSetup.texure0() != null) {
			renderPass.bindSampler("Sampler0", draw.textureSetup.texure0());
		}

		if (draw.textureSetup.texure1() != null) {
			renderPass.bindSampler("Sampler1", draw.textureSetup.texure1());
		}

		if (draw.textureSetup.texure2() != null) {
			renderPass.bindSampler("Sampler2", draw.textureSetup.texure2());
		}

		renderPass.setIndexBuffer(gpuBuffer, indexType);
		try (VulkanicAPI.ShaderInputParityScope ignored = VulkanicAPI.pushShaderInputParitySemanticContext("gui:" + draw.shaderInputParityGeometryContext())) {
			renderPass.drawIndexed(draw.baseVertex, 0, draw.indexCount, 1);
		}
	}

	private static void bindLegacyGuiUniforms(RenderPass renderPass, GpuBufferSlice fogSlice, GpuBufferSlice dynamicTransformsSlice) {
		net.vulkanic.VulkanicAPI.bindDefaultUniforms(renderPass);
		renderPass.setUniform("Fog", fogSlice);
		renderPass.setUniform("DynamicTransforms", dynamicTransformsSlice);
	}

	private boolean tryExecuteExplicitGalV2GuiDraw(
		GuiRenderer.Draw draw,
		RenderPass renderPass,
		RenderTarget renderTarget,
		GpuBufferSlice fogSlice,
		GpuBufferSlice dynamicTransformsSlice,
		GpuBuffer indexBuffer,
		VertexFormat.IndexType indexType,
		int drawOrdinal
	) {
		if (!EXPLICIT_GAL_V2_GUI_TEXT
			|| !VulkanicAPI.isVulkanBackendSelected()
			|| !renderPass.supportsExplicitGalV2GraphicsDraw()
			|| draw.indexCount <= 0) {
			return false;
		}

		RenderPipeline pipeline = draw.pipeline();
		PipelineDescriptor descriptor = VulkanicAPI.resolvePrecompiledPipelineDescriptor(pipeline);
		if (descriptor == null) {
			return false;
		}
		int programId = VulkanicAPI.resolvePrecompiledPipelineProgramId(pipeline);
		int vertexBufferHandle = VulkanicAPI.legacyBufferHandleForExplicitGalV2(draw.vertexBuffer);
		int indexBufferHandle = VulkanicAPI.legacyBufferHandleForExplicitGalV2(indexBuffer);
		if (vertexBufferHandle <= 0 || indexBufferHandle <= 0) {
			return false;
		}

		int framebuffer = renderPass.explicitGalV2FramebufferId();
		int colorTexture = VulkanicAPI.legacyTextureHandleForExplicitGalV2(renderTarget.getColorTextureView());
		int depthTexture = renderTarget.useDepth
			? VulkanicAPI.legacyTextureHandleForExplicitGalV2(renderTarget.getDepthTextureView())
			: 0;
		VulkanicGalExecutionRequest.SemanticIdentity identity = new VulkanicGalExecutionRequest.SemanticIdentity(
			"gui",
			"main",
			pipeline.getLocation().toString(),
			pipeline.getVertexFormat().toString(),
			"framebuffer:" + framebuffer,
			"current-frame",
			Math.max(0, drawOrdinal)
		);

		List<VulkanicGalV2.ResourceLayoutBinding> layoutBindings = new ArrayList<>();
		List<VulkanicGalV2.ResourceBinding> resourceBindings = new ArrayList<>();
		List<VulkanicPassResourceModel.ResourceUse> resourceUses = new ArrayList<>();
		if (!this.addGuiDescriptorBindings(
			descriptor,
			draw.textureSetup,
			fogSlice,
			dynamicTransformsSlice,
			layoutBindings,
			resourceBindings,
			resourceUses
		)) {
			return false;
		}
		addGuiBufferUse(resourceUses, "gui-vertices", VulkanicPassResourceModel.ResourceKind.VERTEX_BUFFER, vertexBufferHandle);
		addGuiBufferUse(resourceUses, "gui-indices", VulkanicPassResourceModel.ResourceKind.INDEX_BUFFER, indexBufferHandle);

		VulkanicGalV2.VertexLayout vertexLayout = this.explicitGuiVertexLayout(pipeline.getVertexFormat());
		VulkanicCompatibilityState.FramebufferSnapshot framebufferState =
			explicitGuiFramebufferState(framebuffer, colorTexture, depthTexture);
		VulkanicCompatibilityState.FixedFunctionSnapshot fixedFunction =
			explicitGuiFixedFunction(pipeline, draw.scissorArea());
		String pipelineDescriptorKey = descriptor.getStableCacheKey();
		String programKey = programId > 0
			? "gui-program:" + programId + ":" + pipelineDescriptorKey
			: "gui-descriptor-program:" + pipelineDescriptorKey;
		String vertexLayoutKey = "gui-vertex-layout:" + pipeline.getVertexFormat();
		String resourceLayoutKey = "gui-resource-layout:" + pipelineDescriptorKey;
		String resourceSetKey = "gui-resource-set:" + pipelineDescriptorKey + ":bindings=" + resourceBindings.hashCode();
		String renderTargetKey = "gui-render-target:" + framebufferState.shapeKey();
		String pipelineKey = "gui-pipeline:" + pipelineDescriptorKey + ":fixed=" + fixedFunction.shapeKey();
		String semanticKey = String.join("|", programKey, pipelineKey, vertexLayoutKey, resourceSetKey, renderTargetKey);
		VulkanicGalV2.ExplicitGraphicsObjects objects = VulkanicGalV2.registerExplicitGraphicsObjects(
			new VulkanicGalV2.ExplicitGraphicsDescriptor(
				programId,
				Integer.toUnsignedLong(pipelineDescriptorKey.hashCode()),
				programKey,
				descriptor,
				fixedFunction,
				"fixed:" + fixedFunction.shapeKey(),
				"topology:TRIANGLES:indexType=" + indexType,
				pipelineKey,
				framebuffer,
				framebufferState,
				renderTargetKey,
				vertexLayout,
				vertexLayoutKey,
				layoutBindings,
				resourceLayoutKey,
				resourceBindings,
				resourceSetKey,
				semanticKey
			)
		);
		this.retainExplicitGalV2GuiObjects(objects);

		VulkanicIndexType explicitIndexType = indexType == VertexFormat.IndexType.INT ? VulkanicIndexType.INT : VulkanicIndexType.SHORT;
		VulkanicGalV2.VertexStreamBindings streams = new VulkanicGalV2.VertexStreamBindings(
			List.of(new VulkanicGalV2.VertexStream(0, vertexBufferHandle, 0L, false)),
			Optional.of(new VulkanicGalV2.IndexStream(indexBufferHandle, explicitIndexType, 0L))
		);
		VulkanicGalExecutionRequest.GraphicsDrawCommand command =
			VulkanicGalExecutionRequest.GraphicsDrawCommand.indexed(
				VulkanicPrimitiveMode.TRIANGLES,
				draw.indexCount,
				explicitIndexType,
				0L,
				1,
				draw.baseVertex
			);
		VulkanicGalV2.ExplicitGraphicsDrawRequest request = new VulkanicGalV2.ExplicitGraphicsDrawRequest(
			identity,
			objects.handle(),
			objects.resourceSet(),
			VulkanicGalV2.emptyUniformPayload("gui:" + pipeline.getLocation()),
			new VulkanicGalV2.GraphicsCommandStream(List.of(
				new VulkanicGalV2.BindRenderTargetCommand(objects.renderTarget()),
				new VulkanicGalV2.BindGraphicsPipelineCommand(objects.pipeline()),
				new VulkanicGalV2.BindResourceSetCommand(objects.resourceSet()),
				new VulkanicGalV2.BindVertexStreamsCommand(objects.vertexLayoutHandle(), streams),
				new VulkanicGalV2.BindIndexStreamCommand(streams.indexStream()),
				new VulkanicGalV2.SetDynamicStateCommand(objects.pipelineState().fixedFunctionKey()),
				new VulkanicGalV2.DrawCommand(command)
			)),
			command,
			streams,
			VulkanicPassResourcePlanner.plan(new VulkanicPassResourceModel.PassRequest(
				VulkanicPassResourceModel.PassKind.RENDER,
				identity.label(),
				List.of(),
				resourceUses,
				List.of(),
				List.of(new VulkanicPassResourceModel.Command("gui-explicit-indexed-draw", OptionalInt.of(1), OptionalInt.empty())),
				List.of("gui-producer-issued-explicit-command"),
				false,
				false
			)),
			"gui-explicit-plan:" + identity.label() + ":resources=" + resourceUses.size()
		);
		try (VulkanicAPI.ShaderInputParityScope ignored = VulkanicAPI.pushShaderInputParitySemanticContext("gui:" + draw.shaderInputParityGeometryContext())) {
			return renderPass.executeExplicitGalV2GraphicsDraw(request).successful();
		}
	}

	private boolean addGuiDescriptorBindings(
		PipelineDescriptor descriptor,
		TextureSetup textureSetup,
		GpuBufferSlice fogSlice,
		GpuBufferSlice dynamicTransformsSlice,
		List<VulkanicGalV2.ResourceLayoutBinding> layoutBindings,
		List<VulkanicGalV2.ResourceBinding> resourceBindings,
		List<VulkanicPassResourceModel.ResourceUse> resourceUses
	) {
		for (PipelineDescriptor.ResourceBinding binding : descriptor.getResourceLayout().bindings()) {
			boolean added = switch (binding.type()) {
				case SAMPLER, COMPARISON_SAMPLER -> this.addGuiSampledTextureBinding(layoutBindings, resourceBindings, resourceUses, binding, textureSetup);
				case UNIFORM_BUFFER -> this.addGuiUniformBufferBinding(layoutBindings, resourceBindings, resourceUses, binding, fogSlice, dynamicTransformsSlice);
				case STORAGE_IMAGE, TEXEL_BUFFER -> false;
			};
			if (!added) {
				return false;
			}
		}
		return true;
	}

	private boolean addGuiSampledTextureBinding(
		List<VulkanicGalV2.ResourceLayoutBinding> layoutBindings,
		List<VulkanicGalV2.ResourceBinding> resourceBindings,
		List<VulkanicPassResourceModel.ResourceUse> resourceUses,
		PipelineDescriptor.ResourceBinding binding,
		TextureSetup textureSetup
	) {
		GpuTextureView view = switch (binding.name()) {
			case "Sampler0" -> textureSetup.texure0();
			case "Sampler1" -> textureSetup.texure1();
			case "Sampler2" -> textureSetup.texure2();
			default -> null;
		};
		if (view == null || view.isClosed()) {
			return false;
		}
		int texture = VulkanicAPI.legacyTextureHandleForExplicitGalV2(view);
		if (texture <= 0) {
			return false;
		}
		VulkanicPassResourceModel.CanonicalResourceReference reference =
			VulkanicPassResourceModel.CanonicalResourceReference.sampledTexture(
				binding.name(),
				"gui:" + binding.name() + ":texture=" + texture + ":mip=" + view.baseMipLevel(),
				texture,
				OptionalInt.of(VulkanicAPI.GL_TEXTURE_2D),
				VulkanicPassResourceModel.TargetClass.TEXTURE_2D,
				VulkanicPassResourceModel.Subresource.color(view.baseMipLevel(), Math.max(1, view.mipLevels()), 0, 1),
				OptionalInt.of(binding.binding()),
				OptionalInt.empty(),
				VulkanicAPI.textureGeneration(texture)
			);
		VulkanicPassResourceModel.ResourceUse use = reference.asResourceUse("gui:" + binding.name(), false, resourceUses.size());
		layoutBindings.add(new VulkanicGalV2.ResourceLayoutBinding(
			binding.name(),
			VulkanicPassResourceModel.BindingKind.SAMPLED_TEXTURE,
			VulkanicPassResourceModel.ResourceKind.SAMPLED_TEXTURE,
			OptionalInt.of(binding.set()),
			OptionalInt.of(binding.binding()),
			OptionalInt.of(binding.binding())
		));
		resourceBindings.add(new VulkanicGalV2.ResourceBinding(
			binding.name(),
			use,
			OptionalInt.of(binding.set()),
			OptionalInt.of(binding.binding()),
			Optional.of(reference),
			Optional.empty()
		));
		resourceUses.add(use);
		return true;
	}

	private boolean addGuiUniformBufferBinding(
		List<VulkanicGalV2.ResourceLayoutBinding> layoutBindings,
		List<VulkanicGalV2.ResourceBinding> resourceBindings,
		List<VulkanicPassResourceModel.ResourceUse> resourceUses,
		PipelineDescriptor.ResourceBinding binding,
		GpuBufferSlice fogSlice,
		GpuBufferSlice dynamicTransformsSlice
	) {
		GpuBufferSlice slice = switch (binding.name()) {
			case "Projection" -> VulkanicAPI.getProjectionMatrixBuffer();
			case "Fog" -> fogSlice;
			case "DynamicTransforms" -> dynamicTransformsSlice;
			case "Lighting" -> VulkanicAPI.getShaderLights();
			case "Globals" -> {
				GpuBuffer globals = VulkanicAPI.getGlobalSettingsUniform();
				yield globals == null ? null : globals.slice();
			}
			default -> null;
		};
		if (slice == null || slice.length() <= 0 || slice.buffer().isClosed()) {
			return false;
		}
		int buffer = VulkanicAPI.legacyBufferHandleForExplicitGalV2(slice.buffer());
		if (buffer <= 0) {
			return false;
		}
		VulkanicPassResourceModel.CanonicalResourceReference reference =
			VulkanicPassResourceModel.CanonicalResourceReference.bufferRange(
				binding.name(),
				VulkanicPassResourceModel.ResourceKind.UNIFORM_BUFFER,
				"gui:" + binding.name() + ":buffer=" + buffer + ":offset=" + slice.offset() + ":size=" + slice.length(),
				slice.offset(),
				slice.length(),
				VulkanicPassResourceModel.Access.READ,
				VulkanicResourceUsage.INFERRED,
				OptionalInt.of(binding.binding()),
				OptionalInt.of(buffer),
				VulkanicAPI.bufferGeneration(buffer)
			);
		VulkanicPassResourceModel.ResourceUse use = reference.asResourceUse("gui:" + binding.name(), false, resourceUses.size());
		layoutBindings.add(new VulkanicGalV2.ResourceLayoutBinding(
			binding.name(),
			VulkanicPassResourceModel.BindingKind.BUFFER_RANGE,
			VulkanicPassResourceModel.ResourceKind.UNIFORM_BUFFER,
			OptionalInt.of(binding.set()),
			OptionalInt.of(binding.binding()),
			OptionalInt.of(binding.binding())
		));
		resourceBindings.add(new VulkanicGalV2.ResourceBinding(
			binding.name(),
			use,
			OptionalInt.of(binding.set()),
			OptionalInt.of(binding.binding()),
			Optional.of(reference),
			Optional.empty()
		));
		resourceUses.add(use);
		return true;
	}

	private VulkanicGalV2.VertexLayout explicitGuiVertexLayout(VertexFormat vertexFormat) {
		return this.explicitGalV2GuiVertexLayouts.computeIfAbsent(vertexFormat, format -> {
			List<VulkanicGalV2.VertexBindingLayout> bindings = List.of(
				new VulkanicGalV2.VertexBindingLayout(0, format.getVertexSize(), 0)
			);
			List<VulkanicGalV2.VertexAttributeLayout> attributes = new ArrayList<>();
			List<VertexFormatElement> elements = format.getElements();
			for (int ordinal = 0; ordinal < elements.size(); ordinal++) {
				VertexFormatElement element = elements.get(ordinal);
				attributes.add(new VulkanicGalV2.VertexAttributeLayout(
					format.getShaderAttributeLocation(ordinal),
					0,
					element.count(),
					GlConst.toGl(element.type()),
					explicitGuiAttributeNormalized(element),
					false,
					format.getOffset(element),
					0
				));
			}
			return new VulkanicGalV2.VertexLayout(bindings, attributes, Map.of(), false);
		});
	}

	private static boolean explicitGuiAttributeNormalized(VertexFormatElement element) {
		return element.usage() == VertexFormatElement.Usage.COLOR || element.usage() == VertexFormatElement.Usage.NORMAL;
	}

	private static void addGuiBufferUse(
		List<VulkanicPassResourceModel.ResourceUse> resourceUses,
		String name,
		VulkanicPassResourceModel.ResourceKind kind,
		int buffer
	) {
		VulkanicPassResourceModel.CanonicalResourceReference reference =
			VulkanicPassResourceModel.CanonicalResourceReference.bufferRange(
				name,
				kind,
				"gui:" + name + ":buffer=" + buffer,
				0L,
				1L,
				VulkanicPassResourceModel.Access.READ,
				VulkanicResourceUsage.INFERRED,
				OptionalInt.empty(),
				OptionalInt.of(buffer),
				VulkanicAPI.bufferGeneration(buffer)
			);
		resourceUses.add(reference.asResourceUse(name, false, resourceUses.size()));
	}

	private static VulkanicCompatibilityState.FramebufferSnapshot explicitGuiFramebufferState(int framebuffer, int colorTexture, int depthTexture) {
		Map<Integer, VulkanicCompatibilityState.AttachmentState> attachments = new LinkedHashMap<>();
		if (colorTexture > 0) {
			attachments.put(
				VulkanicAPI.GL_COLOR_ATTACHMENT0,
				new VulkanicCompatibilityState.AttachmentState(VulkanicAPI.GL_COLOR_ATTACHMENT0, colorTexture, 0)
			);
		}
		if (depthTexture > 0) {
			attachments.put(
				VulkanicAPI.GL_DEPTH_ATTACHMENT,
				new VulkanicCompatibilityState.AttachmentState(VulkanicAPI.GL_DEPTH_ATTACHMENT, depthTexture, 0)
			);
		}
		return new VulkanicCompatibilityState.FramebufferSnapshot(
			framebuffer,
			attachments,
			List.of(VulkanicAPI.GL_COLOR_ATTACHMENT0),
			VulkanicAPI.GL_COLOR_ATTACHMENT0,
			"framebuffer=" + framebuffer + ":color0=" + colorTexture + ":depth=" + depthTexture
		);
	}

	private static VulkanicCompatibilityState.FixedFunctionSnapshot explicitGuiFixedFunction(RenderPipeline pipeline, @Nullable ScreenRectangle scissor) {
		BlendFunction blend = pipeline.getBlendFunction().orElse(new BlendFunction(
			net.blaze3d.platform.SourceFactor.ONE,
			net.blaze3d.platform.DestFactor.ZERO
		));
		Optional<VulkanicGalExecutionRequest.Scissor> scissorState = explicitGuiScissor(scissor);
		String shapeKey = "gui-fixed:"
			+ pipeline.getLocation()
			+ ":blend=" + pipeline.getBlendFunction().isPresent()
			+ ":depth=" + pipeline.getDepthTestFunction()
			+ ":cull=" + pipeline.isCull()
			+ ":scissor=" + scissorState
			+ ":poly=" + pipeline.getPolygonMode()
			+ ":write=" + pipeline.isWriteColor() + pipeline.isWriteAlpha() + pipeline.isWriteDepth();
		return new VulkanicCompatibilityState.FixedFunctionSnapshot(
			Optional.empty(),
			scissorState,
			pipeline.getBlendFunction().isPresent(),
			GlConst.toGl(blend.sourceColor()),
			GlConst.toGl(blend.destColor()),
			GlConst.toGl(blend.sourceAlpha()),
			GlConst.toGl(blend.destAlpha()),
			VulkanicAPI.GL_FUNC_ADD,
			VulkanicAPI.GL_FUNC_ADD,
			pipeline.getDepthTestFunction() != DepthTestFunction.NO_DEPTH_TEST,
			GlConst.toGl(pipeline.getDepthTestFunction()),
			pipeline.isWriteDepth(),
			pipeline.isCull(),
			VulkanicAPI.GL_BACK,
			scissorState.isPresent(),
			false,
			pipeline.getColorLogic() != LogicOp.NONE,
			0,
			VulkanicAPI.GL_FRONT_AND_BACK,
			GlConst.toGl(pipeline.getPolygonMode()),
			pipeline.getDepthBiasScaleFactor() != 0.0F || pipeline.getDepthBiasConstant() != 0.0F,
			pipeline.getDepthBiasScaleFactor(),
			pipeline.getDepthBiasConstant(),
			pipeline.isWriteColor(),
			pipeline.isWriteColor(),
			pipeline.isWriteColor(),
			pipeline.isWriteAlpha(),
			Map.of(),
			Map.of(),
			Map.of(),
			shapeKey
		);
	}

	private static Optional<VulkanicGalExecutionRequest.Scissor> explicitGuiScissor(@Nullable ScreenRectangle screenRectangle) {
		if (screenRectangle == null) {
			return Optional.empty();
		}
		Window window = Minecraft.getInstance().getWindow();
		int scale = window.getGuiScale();
		int x = (int)(screenRectangle.left() * scale);
		int y = (int)(window.getHeight() - screenRectangle.bottom() * scale);
		int width = Math.max(0, (int)(screenRectangle.width() * scale));
		int height = Math.max(0, (int)(screenRectangle.height() * scale));
		return Optional.of(new VulkanicGalExecutionRequest.Scissor(x, y, width, height));
	}

	private void retainExplicitGalV2GuiObjects(VulkanicGalV2.ExplicitGraphicsObjects objects) {
		String owner = "gui-text:" + objects.semanticKey();
		VulkanicGalV2.RetainedHandle previous = this.explicitGalV2GuiObjects.get(objects.semanticKey());
		if (previous != null) {
			previous.close();
		}
		while (!this.explicitGalV2GuiObjects.containsKey(objects.semanticKey())
			&& this.explicitGalV2GuiObjects.size() >= MAX_RETAINED_EXPLICIT_GAL_V2_GUI_OBJECTS) {
			Iterator<Map.Entry<String, VulkanicGalV2.RetainedHandle>> iterator = this.explicitGalV2GuiObjects.entrySet().iterator();
			if (!iterator.hasNext()) {
				break;
			}
			Map.Entry<String, VulkanicGalV2.RetainedHandle> evicted = iterator.next();
			evicted.getValue().close();
			iterator.remove();
		}
		this.explicitGalV2GuiObjects.put(objects.semanticKey(), VulkanicGalV2.retain(objects.handle(), owner));
	}

	private BufferBuilder getBufferBuilder(RenderPipeline renderPipeline) {
		return new BufferBuilder(this.byteBufferBuilder, renderPipeline.getVertexFormatMode(), renderPipeline.getVertexFormat());
	}

	private boolean scissorChanged(ScreenRectangle screenRectangle, @Nullable ScreenRectangle screenRectangle2) {
		if (screenRectangle == screenRectangle2) {
			return false;
		} else {
			return screenRectangle != null ? !screenRectangle.equals(screenRectangle2) : true;
		}
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

		this.pictureInPictureRenderers.values().forEach(PictureInPictureRenderer::close);
		this.explicitGalV2GuiObjects.values().forEach(VulkanicGalV2.RetainedHandle::close);
		this.explicitGalV2GuiObjects.clear();
		this.explicitGalV2GuiVertexLayouts.clear();
		this.guiProjectionMatrixBuffer.close();
		this.itemsProjectionMatrixBuffer.close();

		for (MappableRingBuffer mappableRingBuffer : this.vertexBuffers.values()) {
			mappableRingBuffer.close();
		}

		this.oversizedItemRenderers.values().forEach(PictureInPictureRenderer::close);
		this.standard3dItemRenderers.values().forEach(PictureInPictureRenderer::close);
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
	) {
	}

	@Environment(EnvType.CLIENT)
	record MeshToDraw(
		MeshData mesh,
		RenderPipeline pipeline,
		TextureSetup textureSetup,
		@Nullable ScreenRectangle scissorArea,
		@Nullable String shaderInputParityGeometryContext
	) implements AutoCloseable {

		public void close() {
			this.mesh.close();
		}
	}
}
