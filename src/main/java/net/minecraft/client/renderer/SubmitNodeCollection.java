package net.minecraft.client.renderer;

import net.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HitboxesRenderState;
import net.minecraft.client.renderer.feature.CustomFeatureRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.feature.ModelPartFeatureRenderer;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
// Sodium FRAPI imports
import net.sodium.client.render.frapi.render.MeshItemCommand;
import net.sodium.client.render.frapi.render.OrderedSubmitNodeCollectorExtension;
import net.sodium.client.render.frapi.render.SubmitNodeCollectionExtension;
import net.fabricmc.fabric.api.renderer.v1.mesh.MeshView;

@Environment(EnvType.CLIENT)
public class SubmitNodeCollection implements OrderedSubmitNodeCollector, OrderedSubmitNodeCollectorExtension, SubmitNodeCollectionExtension {
	private final List<SubmitNodeStorage.ShadowSubmit> shadowSubmits = new ArrayList();
	private final List<SubmitNodeStorage.FlameSubmit> flameSubmits = new ArrayList();
	private final NameTagFeatureRenderer.Storage nameTagSubmits = new NameTagFeatureRenderer.Storage();
	private final List<SubmitNodeStorage.TextSubmit> textSubmits = new ArrayList();
	private final List<SubmitNodeStorage.HitboxSubmit> hitboxSubmits = new ArrayList();
	private final List<SubmitNodeStorage.LeashSubmit> leashSubmits = new ArrayList();
	private final List<SubmitNodeStorage.BlockSubmit> blockSubmits = new ArrayList();
	private final List<SubmitNodeStorage.MovingBlockSubmit> movingBlockSubmits = new ArrayList();
	private final List<SubmitNodeStorage.BlockModelSubmit> blockModelSubmits = new ArrayList();
	private final List<SubmitNodeStorage.ItemSubmit> itemSubmits = new ArrayList();
	private final List<SubmitNodeCollector.ParticleGroupRenderer> particleGroupRenderers = new ArrayList();
	private final ModelFeatureRenderer.Storage modelSubmits = new ModelFeatureRenderer.Storage();
	private final ModelPartFeatureRenderer.Storage modelPartSubmits = new ModelPartFeatureRenderer.Storage();
	private final CustomFeatureRenderer.Storage customGeometrySubmits = new CustomFeatureRenderer.Storage();
	private final SubmitNodeStorage submitNodeStorage;
	private boolean wasUsed = false;
	// Sodium FRAPI: Mesh item commands for fabric rendering API
	private final List<MeshItemCommand> meshItemCommands = new ArrayList<>();

	public SubmitNodeCollection(SubmitNodeStorage submitNodeStorage) {
		this.submitNodeStorage = submitNodeStorage;
	}

	@Override
	public void submitHitbox(PoseStack poseStack, EntityRenderState entityRenderState, HitboxesRenderState hitboxesRenderState) {
		this.wasUsed = true;
		this.hitboxSubmits.add(new SubmitNodeStorage.HitboxSubmit(new Matrix4f(poseStack.last().pose()), entityRenderState, hitboxesRenderState));
	}

	@Override
	public void submitShadow(PoseStack poseStack, float f, List<EntityRenderState.ShadowPiece> list) {
		this.wasUsed = true;
		PoseStack.Pose pose = poseStack.last();
		this.shadowSubmits.add(new SubmitNodeStorage.ShadowSubmit(new Matrix4f(pose.pose()), f, list));
	}

	@Override
	public void submitNameTag(
		PoseStack poseStack, @Nullable Vec3 vec3, int i, Component component, boolean bl, int j, double d, CameraRenderState cameraRenderState
	) {
		this.wasUsed = true;
		this.nameTagSubmits.add(poseStack, vec3, i, component, bl, j, d, cameraRenderState);
	}

	@Override
	public void submitText(
		PoseStack poseStack, float f, float g, FormattedCharSequence formattedCharSequence, boolean bl, Font.DisplayMode displayMode, int i, int j, int k, int l
	) {
		SubmitNodeStorage.TextSubmit textSubmit = this.copyTextSubmit(poseStack, f, g, formattedCharSequence, bl, displayMode, i, j, k, l);
		// Iris: Capture model storage (merged from MixinModelStorageTrigger)
		((net.irisshaders.iris.mixinterface.ModelStorage) textSubmit).iris$capture();
		this.textSubmits.add(textSubmit);
	}

	/**
	 * Stores copied text semantics from a whole-frame extraction callback. The
	 * selected Rust route must not capture Iris model state because it neither
	 * borrows Iris programs nor invokes the Java text renderer.
	 */
	public void submitTextSemantic(
		PoseStack poseStack, float f, float g, FormattedCharSequence formattedCharSequence, boolean bl, Font.DisplayMode displayMode, int i, int j, int k, int l
	) {
		this.textSubmits.add(this.copyTextSubmit(poseStack, f, g, formattedCharSequence, bl, displayMode, i, j, k, l));
	}

	private SubmitNodeStorage.TextSubmit copyTextSubmit(
		PoseStack poseStack, float f, float g, FormattedCharSequence formattedCharSequence, boolean bl, Font.DisplayMode displayMode, int i, int j, int k, int l
	) {
		this.wasUsed = true;
		return new SubmitNodeStorage.TextSubmit(new Matrix4f(poseStack.last().pose()), f, g, formattedCharSequence, bl, displayMode, i, j, k, l);
	}

	@Override
	public void submitFlame(PoseStack poseStack, EntityRenderState entityRenderState, Quaternionf quaternionf) {
		this.wasUsed = true;
		this.flameSubmits.add(new SubmitNodeStorage.FlameSubmit(poseStack.last().copy(), entityRenderState, quaternionf));
	}

	@Override
	public void submitLeash(PoseStack poseStack, EntityRenderState.LeashState leashState) {
		this.wasUsed = true;
		this.leashSubmits.add(new SubmitNodeStorage.LeashSubmit(new Matrix4f(poseStack.last().pose()), leashState));
	}

	@Override
	public <S> void submitModel(
		Model<? super S> model,
		S object,
		PoseStack poseStack,
		RenderType renderType,
		int i,
		int j,
		int k,
		@Nullable TextureAtlasSprite textureAtlasSprite,
		int l,
		@Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay
	) {
		boolean rustEligible = net.vulkanic.world.RustGalWorldPrimitiveRenderer.isModelMeshEligible(
			model, renderType, textureAtlasSprite, j, l, crumblingOverlay
		);
		net.vulkanic.world.WorldRenderRoutePolicy.Route rustRoute =
			net.vulkanic.world.WorldRenderRoutePolicy.currentModelMeshRoute(rustEligible);
		if (rustRoute.usesRustWholeFrameVulkan()) {
			if (!net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueModelMesh(
				model, object, poseStack.last(), renderType, textureAtlasSprite, i, j, k
			)) {
				throw new IllegalStateException("Rust whole-frame model route selected without a copied indexed mesh request");
			}
			net.vulkanic.world.RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
				"rust-vulkan-whole-frame", textureAtlasSprite.contents().name(), model.getClass().getName(), true, true, false
			);
			return;
		}
		if (rustEligible) {
			net.vulkanic.world.RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
				rustRoute == net.vulkanic.world.WorldRenderRoutePolicy.Route.DISABLED ? "disabled" : "java-legacy",
				textureAtlasSprite.contents().name(), model.getClass().getName(), false, false,
				rustRoute.usesJavaCompatibility()
			);
		}
		// Iris: Change render type if rendering block entities (merged from MixinModelStorageTrigger)
		if (net.irisshaders.iris.vertices.ImmediateState.isRenderingBEs) {
			renderType = net.irisshaders.iris.layer.OuterWrappedRenderType.wrapExactlyOnce("iris:block_entity", renderType, net.irisshaders.iris.layer.BlockEntityRenderStateShard.INSTANCE);
		}
		
		this.wasUsed = true;
		SubmitNodeStorage.ModelSubmit<S> modelSubmit = new SubmitNodeStorage.ModelSubmit<>(
			poseStack.last().copy(), model, object, i, j, k, textureAtlasSprite, l, crumblingOverlay
		);
		// Iris: Capture model storage (merged from MixinModelStorageTrigger)
		((net.irisshaders.iris.mixinterface.ModelStorage) (Object) modelSubmit).iris$capture();
		this.modelSubmits.add(renderType, modelSubmit);
	}

	@Override
	public void submitModelPart(
		ModelPart modelPart,
		PoseStack poseStack,
		RenderType renderType,
		int i,
		int j,
		@Nullable TextureAtlasSprite textureAtlasSprite,
		boolean bl,
		boolean bl2,
		int k,
		@Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay,
		int l
	) {
		String rustEligibility = net.vulkanic.world.RustGalWorldPrimitiveRenderer.modelPartMeshEligibilityReason(
			modelPart, renderType, textureAtlasSprite, j, bl, bl2, k, crumblingOverlay
		);
		boolean rustEligible = "eligible".equals(rustEligibility);
		net.vulkanic.world.WorldRenderRoutePolicy.Route rustRoute =
			net.vulkanic.world.WorldRenderRoutePolicy.currentModelPartMeshRoute(rustEligible);
		net.vulkanic.world.RustGalWorldPrimitiveRenderer.recordModelPartMeshTraversal(
			rustRoute.name().toLowerCase(java.util.Locale.ROOT),
			rustEligibility,
			textureAtlasSprite == null ? null : textureAtlasSprite.contents().name(),
			renderType == null ? null : renderType.toString()
		);
		if (rustRoute.usesRustWholeFrameVulkan()) {
			if (!net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueModelPartMesh(
				modelPart, poseStack.last(), renderType, textureAtlasSprite, i, j, bl, bl2, l, crumblingOverlay, k
			)) {
				throw new IllegalStateException("Rust whole-frame ModelPart route selected without a copied indexed mesh request");
			}
			net.vulkanic.world.RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
				"rust-vulkan-whole-frame", textureAtlasSprite.contents().name(), modelPart.getClass().getName(), true, true, false
			);
			return;
		}
		if (rustEligible) {
			net.vulkanic.world.RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
				rustRoute == net.vulkanic.world.WorldRenderRoutePolicy.Route.DISABLED ? "disabled" : "java-legacy",
				textureAtlasSprite.contents().name(), modelPart.getClass().getName(), false, false, rustRoute.usesJavaCompatibility()
			);
		}
		this.wasUsed = true;
		SubmitNodeStorage.ModelPartSubmit modelPartSubmit = new SubmitNodeStorage.ModelPartSubmit(poseStack.last().copy(), modelPart, i, j, textureAtlasSprite, bl, bl2, k, crumblingOverlay, l);
		// Iris: Capture model storage (merged from MixinModelStorageTrigger)
		((net.irisshaders.iris.mixinterface.ModelStorage) (Object) modelPartSubmit).iris$capture();
		this.modelPartSubmits.add(renderType, modelPartSubmit);
	}

	@Override
	public void submitBlock(PoseStack poseStack, BlockState blockState, int i, int j, int k) {
		this.wasUsed = true;
		this.blockSubmits.add(new SubmitNodeStorage.BlockSubmit(poseStack.last().copy(), blockState, i, j, k, SubmitNodeStorage.BlockSubmitSource.ORDINARY, BlockPos.ZERO));
		((SpecialBlockModelRenderer)Minecraft.getInstance().getModelManager().specialBlockModelRenderer().get())
			.renderByBlock(blockState.getBlock(), ItemDisplayContext.NONE, poseStack, this.submitNodeStorage, i, j, k);
	}

	@Override
	public void submitBlockDisplay(PoseStack poseStack, BlockState blockState, int i, int j, int k) {
		this.submitBlockDisplay(poseStack, blockState, i, j, k, BlockPos.ZERO);
	}

	@Override
	public void submitBlockDisplay(PoseStack poseStack, BlockState blockState, int i, int j, int k, BlockPos tintPos) {
		this.wasUsed = true;
		this.blockSubmits.add(new SubmitNodeStorage.BlockSubmit(poseStack.last().copy(), blockState, i, j, k, SubmitNodeStorage.BlockSubmitSource.BLOCK_DISPLAY, tintPos));
		((SpecialBlockModelRenderer)Minecraft.getInstance().getModelManager().specialBlockModelRenderer().get())
			.renderByBlock(blockState.getBlock(), ItemDisplayContext.NONE, poseStack, this.submitNodeStorage, i, j, k);
	}

	@Override
	public void submitPrimedTntBlock(PoseStack poseStack, BlockState blockState, int i, int j, int k) {
		this.wasUsed = true;
		this.blockSubmits.add(new SubmitNodeStorage.BlockSubmit(poseStack.last().copy(), blockState, i, j, k, SubmitNodeStorage.BlockSubmitSource.PRIMED_TNT, BlockPos.ZERO));
		((SpecialBlockModelRenderer)Minecraft.getInstance().getModelManager().specialBlockModelRenderer().get())
			.renderByBlock(blockState.getBlock(), ItemDisplayContext.NONE, poseStack, this.submitNodeStorage, i, j, k);
	}

	@Override
	public void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState movingBlockRenderState) {
		this.submitMovingBlock(poseStack, movingBlockRenderState, SubmitNodeStorage.MovingBlockSubmitSource.UNKNOWN);
	}

	@Override
	public void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState movingBlockRenderState, SubmitNodeStorage.MovingBlockSubmitSource source) {
		this.wasUsed = true;
		this.movingBlockSubmits.add(new SubmitNodeStorage.MovingBlockSubmit(new Matrix4f(poseStack.last().pose()), movingBlockRenderState, source));
	}

	@Override
	public void submitBlockModel(PoseStack poseStack, RenderType renderType, BlockStateModel blockStateModel, float f, float g, float h, int i, int j, int k) {
		this.wasUsed = true;
		this.blockModelSubmits.add(new SubmitNodeStorage.BlockModelSubmit(poseStack.last().copy(), renderType, blockStateModel, f, g, h, i, j, k));
	}

	@Override
	public void submitItem(
		PoseStack poseStack,
		ItemDisplayContext itemDisplayContext,
		int i,
		int j,
		int k,
		int[] is,
		List<BakedQuad> list,
		RenderType renderType,
		ItemStackRenderState.FoilType foilType
	) {
		if (net.vulkanic.world.RustGalWorldPrimitiveRenderer.isItemEntitySubmissionActive()) {
			String itemEntityIneligibility = net.vulkanic.world.RustGalWorldPrimitiveRenderer.itemEntityMeshIneligibility(
				itemDisplayContext, i, j, k, is, list, renderType, foilType
			);
			boolean rustEligible = itemEntityIneligibility == null;
			net.vulkanic.world.WorldRenderRoutePolicy.Route rustRoute =
				net.vulkanic.world.WorldRenderRoutePolicy.currentItemEntityMeshRoute(rustEligible);
			if (rustRoute.usesRustWholeFrameVulkan()) {
				boolean rustQueued = net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueItemEntityMesh(
					poseStack.last(), itemDisplayContext, i, j, k, is, list, renderType, foilType
				);
				net.vulkanic.world.RustGalWorldPrimitiveRenderer.recordItemEntityRouteDecision(
					rustRoute.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-'), true, null, true, rustQueued, false
				);
				if (!rustQueued) {
					throw new IllegalStateException("Rust whole-frame item-entity route selected without a copied indexed mesh request");
				}
				return;
			}
			net.vulkanic.world.RustGalWorldPrimitiveRenderer.recordItemEntityRouteDecision(
				rustRoute.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-'), rustEligible, itemEntityIneligibility, false, false, true
			);
		}
		this.wasUsed = true;
		SubmitNodeStorage.ItemSubmit itemSubmit = new SubmitNodeStorage.ItemSubmit(poseStack.last().copy(), itemDisplayContext, i, j, k, is, list, renderType, foilType);
		// Iris: Capture model storage (merged from MixinModelStorageTrigger)
		((net.irisshaders.iris.mixinterface.ModelStorage) itemSubmit).iris$capture();
		this.itemSubmits.add(itemSubmit);
	}

	@Override
	public void submitCustomGeometry(PoseStack poseStack, RenderType renderType, SubmitNodeCollector.CustomGeometryRenderer customGeometryRenderer) {
		// Iris: Change render type if rendering block entities (merged from MixinModelStorageTrigger)
		if (net.irisshaders.iris.vertices.ImmediateState.isRenderingBEs) {
			renderType = net.irisshaders.iris.layer.OuterWrappedRenderType.wrapExactlyOnce("iris:block_entity", renderType, net.irisshaders.iris.layer.BlockEntityRenderStateShard.INSTANCE);
		}
		
		this.wasUsed = true;
		this.customGeometrySubmits.add(poseStack, renderType, customGeometryRenderer);
	}

	@Override
	public void submitParticleGroup(SubmitNodeCollector.ParticleGroupRenderer particleGroupRenderer) {
		this.wasUsed = true;
		this.particleGroupRenderers.add(particleGroupRenderer);
	}

	public List<SubmitNodeStorage.ShadowSubmit> getShadowSubmits() {
		return this.shadowSubmits;
	}

	public List<SubmitNodeStorage.FlameSubmit> getFlameSubmits() {
		return this.flameSubmits;
	}

	public NameTagFeatureRenderer.Storage getNameTagSubmits() {
		return this.nameTagSubmits;
	}

	public List<SubmitNodeStorage.TextSubmit> getTextSubmits() {
		return this.textSubmits;
	}

	public List<SubmitNodeStorage.HitboxSubmit> getHitboxSubmits() {
		return this.hitboxSubmits;
	}

	public List<SubmitNodeStorage.LeashSubmit> getLeashSubmits() {
		return this.leashSubmits;
	}

	public List<SubmitNodeStorage.BlockSubmit> getBlockSubmits() {
		return this.blockSubmits;
	}

	public List<SubmitNodeStorage.MovingBlockSubmit> getMovingBlockSubmits() {
		return this.movingBlockSubmits;
	}

	public List<SubmitNodeStorage.BlockModelSubmit> getBlockModelSubmits() {
		return this.blockModelSubmits;
	}

	public ModelPartFeatureRenderer.Storage getModelPartSubmits() {
		return this.modelPartSubmits;
	}

	public List<SubmitNodeStorage.ItemSubmit> getItemSubmits() {
		return this.itemSubmits;
	}

	public List<SubmitNodeCollector.ParticleGroupRenderer> getParticleGroupRenderers() {
		return this.particleGroupRenderers;
	}

	public ModelFeatureRenderer.Storage getModelSubmits() {
		return this.modelSubmits;
	}

	public CustomFeatureRenderer.Storage getCustomGeometrySubmits() {
		return this.customGeometrySubmits;
	}

	/**
	 * A copied inventory of feature work collected during this frame. The Rust
	 * whole-frame route uses this only to make unported producer families
	 * explicit before presentation; no renderer object crosses this boundary.
	 */
	public WorldFeatureCoverageSnapshot worldFeatureCoverageSnapshot() {
		int ordinaryBlockSubmits = 0;
		for (SubmitNodeStorage.BlockSubmit submit : this.blockSubmits) {
			if (submit.source() == SubmitNodeStorage.BlockSubmitSource.ORDINARY) {
				ordinaryBlockSubmits++;
			}
		}
		return new WorldFeatureCoverageSnapshot(
			this.modelSubmits.totalSubmitCount(),
			this.modelPartSubmits.totalSubmitCount(),
			this.blockModelSubmits.size(),
			ordinaryBlockSubmits,
			this.itemSubmits.size() + this.meshItemCommands.size(),
			this.customGeometrySubmits.totalSubmitCount(),
			this.shadowSubmits.size(),
			this.flameSubmits.size(),
			this.nameTagSubmits.totalSubmitCount(),
			this.textSubmits.size(),
			this.hitboxSubmits.size(),
			this.leashSubmits.size(),
			this.particleGroupRenderers.size()
		);
	}

	public boolean wasUsed() {
		return this.wasUsed;
	}

	public void clear() {
		this.shadowSubmits.clear();
		this.flameSubmits.clear();
		this.nameTagSubmits.clear();
		this.textSubmits.clear();
		this.hitboxSubmits.clear();
		this.leashSubmits.clear();
		this.blockSubmits.clear();
		this.movingBlockSubmits.clear();
		this.blockModelSubmits.clear();
		this.itemSubmits.clear();
		this.particleGroupRenderers.clear();
		this.modelSubmits.clear();
		this.customGeometrySubmits.clear();
		this.modelPartSubmits.clear();
		// Sodium FRAPI: Clear mesh item commands
		this.meshItemCommands.clear();
	}
	
	// Sodium FRAPI: OrderedSubmitNodeCollectorExtension implementation
	@Override
	public void fabric_submitItem(PoseStack matrices, ItemDisplayContext displayContext, int light, int overlay, 
			int outlineColors, int[] tintLayers, List<BakedQuad> quads, RenderType renderLayer, 
			ItemStackRenderState.FoilType foilType, MeshView mesh) {
		this.wasUsed = true;
		this.meshItemCommands.add(new MeshItemCommand(matrices.last().copy(), displayContext, light, overlay, 
			outlineColors, tintLayers, quads, renderLayer, foilType, mesh));
	}
	
	// Sodium FRAPI: SubmitNodeCollectionExtension implementation
	@Override
	public List<MeshItemCommand> sodium_getMeshItemCommands() {
		return this.meshItemCommands;
	}

	public void endFrame() {
		this.modelSubmits.endFrame();
		this.modelPartSubmits.endFrame();
		this.customGeometrySubmits.endFrame();
		this.wasUsed = false;
	}

	@Environment(EnvType.CLIENT)
	public record WorldFeatureCoverageSnapshot(
		int modelSubmits,
		int modelPartSubmits,
		int blockModelSubmits,
		int ordinaryBlockSubmits,
		int itemSubmits,
		int customGeometrySubmits,
		int shadowSubmits,
		int flameSubmits,
		int nameTagSubmits,
		int textSubmits,
		int hitboxSubmits,
		int leashSubmits,
		int particleGroupSubmits
	) {
		public WorldFeatureCoverageSnapshot plus(WorldFeatureCoverageSnapshot other) {
			if (other == null) {
				return this;
			}
			return new WorldFeatureCoverageSnapshot(
				this.modelSubmits + other.modelSubmits,
				this.modelPartSubmits + other.modelPartSubmits,
				this.blockModelSubmits + other.blockModelSubmits,
				this.ordinaryBlockSubmits + other.ordinaryBlockSubmits,
				this.itemSubmits + other.itemSubmits,
				this.customGeometrySubmits + other.customGeometrySubmits,
				this.shadowSubmits + other.shadowSubmits,
				this.flameSubmits + other.flameSubmits,
				this.nameTagSubmits + other.nameTagSubmits,
				this.textSubmits + other.textSubmits,
				this.hitboxSubmits + other.hitboxSubmits,
				this.leashSubmits + other.leashSubmits,
				this.particleGroupSubmits + other.particleGroupSubmits
			);
		}

		public boolean hasUnsupportedRustWholeFrameWork() {
			return modelSubmits != 0
				|| modelPartSubmits != 0
				|| blockModelSubmits != 0
				|| ordinaryBlockSubmits != 0
				|| itemSubmits != 0
				|| customGeometrySubmits != 0
				|| shadowSubmits != 0
				|| flameSubmits != 0
				|| nameTagSubmits != 0
				|| textSubmits != 0
				|| hitboxSubmits != 0
				|| leashSubmits != 0
				|| particleGroupSubmits != 0;
		}
	}
}
