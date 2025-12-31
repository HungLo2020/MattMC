package net.minecraft.client.renderer.item;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.AABB.Builder;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

@Environment(EnvType.CLIENT)
public class ItemStackRenderState implements net.irisshaders.iris.mixinterface.ItemContextState {
	ItemDisplayContext displayContext = ItemDisplayContext.NONE;
	private int activeLayerCount;
	private boolean animated;
	private boolean oversizedInGui;
	@Nullable
	private AABB cachedModelBoundingBox;
	private ItemStackRenderState.LayerRenderState[] layers = new ItemStackRenderState.LayerRenderState[]{new ItemStackRenderState.LayerRenderState()};
	// Iris: ItemContextState fields
	private net.minecraft.world.item.Item iris_displayStack;
	private net.minecraft.resources.ResourceLocation iris_displayModelId;

	public void ensureCapacity(int i) {
		int j = this.layers.length;
		int k = this.activeLayerCount + i;
		if (k > j) {
			this.layers = (ItemStackRenderState.LayerRenderState[])Arrays.copyOf(this.layers, k);

			for (int l = j; l < k; l++) {
				this.layers[l] = new ItemStackRenderState.LayerRenderState();
			}
		}
	}

	public ItemStackRenderState.LayerRenderState newLayer() {
		this.ensureCapacity(1);
		return this.layers[this.activeLayerCount++];
	}

	public void clear() {
		this.displayContext = ItemDisplayContext.NONE;

		for (int i = 0; i < this.activeLayerCount; i++) {
			this.layers[i].clear();
		}

		this.activeLayerCount = 0;
		this.animated = false;
		this.oversizedInGui = false;
		this.cachedModelBoundingBox = null;
		// Iris: Clear display stack
		this.iris_displayStack = null;
		this.iris_displayModelId = null;
	}

	public void setAnimated() {
		this.animated = true;
	}

	public boolean isAnimated() {
		return this.animated;
	}

	public void appendModelIdentityElement(Object object) {
	}

	private ItemStackRenderState.LayerRenderState firstLayer() {
		return this.layers[0];
	}

	public boolean isEmpty() {
		return this.activeLayerCount == 0;
	}

	public boolean usesBlockLight() {
		return this.firstLayer().usesBlockLight;
	}

	@Nullable
	public TextureAtlasSprite pickParticleIcon(RandomSource randomSource) {
		return this.activeLayerCount == 0 ? null : this.layers[randomSource.nextInt(this.activeLayerCount)].particleIcon;
	}

	public void visitExtents(Consumer<Vector3fc> consumer) {
		Vector3f vector3f = new Vector3f();
		PoseStack.Pose pose = new PoseStack.Pose();

		for (int i = 0; i < this.activeLayerCount; i++) {
			ItemStackRenderState.LayerRenderState layerRenderState = this.layers[i];
			layerRenderState.transform.apply(this.displayContext.leftHand(), pose);
			Matrix4f matrix4f = pose.pose();
			Vector3f[] vector3fs = (Vector3f[])layerRenderState.extents.get();

			for (Vector3f vector3f2 : vector3fs) {
				consumer.accept(vector3f.set(vector3f2).mulPosition(matrix4f));
			}

			pose.setIdentity();
		}
	}

	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, int j, int k) {
		for (int l = 0; l < this.activeLayerCount; l++) {
			this.layers[l].submit(poseStack, submitNodeCollector, i, j, k);
		}
	}

	public AABB getModelBoundingBox() {
		if (this.cachedModelBoundingBox != null) {
			return this.cachedModelBoundingBox;
		} else {
			Builder builder = new Builder();
			this.visitExtents(builder::include);
			AABB aABB = builder.build();
			this.cachedModelBoundingBox = aABB;
			return aABB;
		}
	}

	public void setOversizedInGui(boolean bl) {
		this.oversizedInGui = bl;
	}

	public boolean isOversizedInGui() {
		return this.oversizedInGui;
	}

	@Environment(EnvType.CLIENT)
	public static enum FoilType {
		NONE,
		STANDARD,
		SPECIAL;
	}

	@Environment(EnvType.CLIENT)
	public class LayerRenderState implements net.fabricmc.fabric.api.renderer.v1.render.FabricLayerRenderState, net.caffeinemc.mods.sodium.client.render.frapi.render.AccessLayerRenderState {
		private static final Vector3f[] NO_EXTENTS = new Vector3f[0];
		public static final Supplier<Vector3f[]> NO_EXTENTS_SUPPLIER = () -> NO_EXTENTS;
		private final List<BakedQuad> quads = new ArrayList();
		boolean usesBlockLight;
		@Nullable
		TextureAtlasSprite particleIcon;
		ItemTransform transform = ItemTransform.NO_TRANSFORM;
		@Nullable
		private RenderType renderType;
		private ItemStackRenderState.FoilType foilType = ItemStackRenderState.FoilType.NONE;
		private int[] tintLayers = new int[0];
		@Nullable
		private SpecialModelRenderer<Object> specialRenderer;
		@Nullable
		private Object argumentForSpecialRendering;
		Supplier<Vector3f[]> extents = NO_EXTENTS_SUPPLIER;
		// Fabric Rendering API support (from ItemLayerRenderStateMixin)
		private final net.caffeinemc.mods.sodium.client.render.frapi.mesh.MutableMeshImpl mutableMesh = new net.caffeinemc.mods.sodium.client.render.frapi.mesh.MutableMeshImpl();

		public void clear() {
			this.quads.clear();
			this.renderType = null;
			this.foilType = ItemStackRenderState.FoilType.NONE;
			this.specialRenderer = null;
			this.argumentForSpecialRendering = null;
			Arrays.fill(this.tintLayers, -1);
			this.usesBlockLight = false;
			this.particleIcon = null;
			this.transform = ItemTransform.NO_TRANSFORM;
			this.extents = NO_EXTENTS_SUPPLIER;
			// Clear mutable mesh (from ItemLayerRenderStateMixin)
			this.mutableMesh.clear();
		}

		public List<BakedQuad> prepareQuadList() {
			return this.quads;
		}

		public void setRenderType(RenderType renderType) {
			this.renderType = renderType;
		}

		public void setUsesBlockLight(boolean bl) {
			this.usesBlockLight = bl;
		}

		public void setExtents(Supplier<Vector3f[]> supplier) {
			this.extents = supplier;
		}

		public void setParticleIcon(TextureAtlasSprite textureAtlasSprite) {
			this.particleIcon = textureAtlasSprite;
		}

		public void setTransform(ItemTransform itemTransform) {
			this.transform = itemTransform;
		}

		public <T> void setupSpecialModel(SpecialModelRenderer<T> specialModelRenderer, @Nullable T object) {
			this.specialRenderer = eraseSpecialRenderer(specialModelRenderer);
			this.argumentForSpecialRendering = object;
		}

		private static SpecialModelRenderer<Object> eraseSpecialRenderer(SpecialModelRenderer<?> specialModelRenderer) {
			return (SpecialModelRenderer<Object>)specialModelRenderer;
		}

		public void setFoilType(ItemStackRenderState.FoilType foilType) {
			this.foilType = foilType;
		}

		public int[] prepareTintLayers(int i) {
			if (i > this.tintLayers.length) {
				this.tintLayers = new int[i];
				Arrays.fill(this.tintLayers, -1);
			}

			return this.tintLayers;
		}

		void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, int j, int k) {
			// Iris: Save block entity state before rendering (from ItemStackStateLayerMixin)
			int lastBState = net.irisshaders.iris.uniforms.CapturedRenderingState.INSTANCE.getCurrentRenderedBlockEntity();
			iris$setupId(ItemStackRenderState.this.iris_displayStack, ItemStackRenderState.this.iris_displayModelId);
			
			poseStack.pushPose();
			this.transform.apply(ItemStackRenderState.this.displayContext.leftHand(), poseStack.last());
			if (this.specialRenderer != null) {
				this.specialRenderer
					.submit(
						this.argumentForSpecialRendering,
						ItemStackRenderState.this.displayContext,
						poseStack,
						submitNodeCollector,
						i,
						j,
						this.foilType != ItemStackRenderState.FoilType.NONE,
						k
					);
			} else if (this.renderType != null) {
				// Fabric Rendering API support (from ItemLayerRenderStateMixin redirect)
				if (this.mutableMesh.size() > 0 && submitNodeCollector instanceof net.caffeinemc.mods.sodium.client.render.frapi.render.OrderedSubmitNodeCollectorExtension access) {
					// We don't have to copy the mesh here because vanilla doesn't copy the tint array or quad list either.
					access.fabric_submitItem(poseStack, ItemStackRenderState.this.displayContext, i, j, k, this.tintLayers, this.quads, this.renderType, this.foilType, this.mutableMesh);
				} else {
					submitNodeCollector.submitItem(poseStack, ItemStackRenderState.this.displayContext, i, j, k, this.tintLayers, this.quads, this.renderType, this.foilType);
				}
			}

			poseStack.popPose();
			
			// Iris: Restore state after rendering (from ItemStackStateLayerMixin)
			net.irisshaders.iris.uniforms.CapturedRenderingState.INSTANCE.setCurrentBlockEntity(lastBState);
			net.irisshaders.iris.uniforms.CapturedRenderingState.INSTANCE.setCurrentRenderedItem(0);
		}
		
		// Iris: Helper method from ItemStackStateLayerMixin
		private void iris$setupId(net.minecraft.world.item.Item item, net.minecraft.resources.ResourceLocation modelId) {
			if (net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings.INSTANCE.getItemIds() == null) return;

			if (item instanceof net.minecraft.world.item.BlockItem blockItem && !(item instanceof net.minecraft.world.item.SolidBucketItem)) {
				if (net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings.INSTANCE.getBlockStateIds() == null) return;

				net.irisshaders.iris.uniforms.CapturedRenderingState.INSTANCE.setCurrentBlockEntity(1);
				net.irisshaders.iris.uniforms.CapturedRenderingState.INSTANCE.setCurrentRenderedItem(net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings.INSTANCE.getBlockStateIds().getOrDefault(blockItem.getBlock().defaultBlockState(), 0));
			} else {
				net.minecraft.resources.ResourceLocation location = modelId != null ? modelId : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
				net.irisshaders.iris.uniforms.CapturedRenderingState.INSTANCE.setCurrentRenderedItem(net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings.INSTANCE.getItemIds().applyAsInt(new net.irisshaders.iris.shaderpack.materialmap.NamespacedId(location.getNamespace(), location.getPath())));
			}
		}
		
		// Fabric Rendering API support (from ItemLayerRenderStateMixin)
		@Override
		public net.caffeinemc.mods.sodium.client.render.frapi.mesh.MutableMeshImpl fabric_getMutableMesh() {
			return this.mutableMesh;
		}
	}
	
	// Iris: ItemContextState implementation
	@Override
	public void setDisplayItem(net.minecraft.world.item.Item itemStack, net.minecraft.resources.ResourceLocation modelId) {
		this.iris_displayStack = itemStack;
		this.iris_displayModelId = modelId;
	}

	@Override
	public net.minecraft.world.item.Item getDisplayItem() {
		return iris_displayStack;
	}
	
	public net.minecraft.resources.ResourceLocation getDisplayItemModel() {
		return iris_displayModelId;
	}
}
