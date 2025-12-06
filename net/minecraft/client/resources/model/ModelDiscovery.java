package net.minecraft.client.resources.model;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectFunction;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.block.model.TextureSlots;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class ModelDiscovery {
	private static final Logger LOGGER = LogUtils.getLogger();
	private final Object2ObjectMap<ResourceLocation, ModelDiscovery.ModelWrapper> modelWrappers = new Object2ObjectOpenHashMap<>();
	private final ModelDiscovery.ModelWrapper missingModel;
	private final Object2ObjectFunction<ResourceLocation, ModelDiscovery.ModelWrapper> uncachedResolver;
	private final ResolvableModel.Resolver resolver;
	private final Queue<ModelDiscovery.ModelWrapper> parentDiscoveryQueue = new ArrayDeque();

	public ModelDiscovery(Map<ResourceLocation, UnbakedModel> map, UnbakedModel unbakedModel) {
		this.missingModel = new ModelDiscovery.ModelWrapper(MissingBlockModel.LOCATION, unbakedModel, true);
		this.modelWrappers.put(MissingBlockModel.LOCATION, this.missingModel);
		this.uncachedResolver = object -> {
			ResourceLocation resourceLocation = (ResourceLocation)object;
			UnbakedModel unbakedModelx = (UnbakedModel)map.get(resourceLocation);
			if (unbakedModelx == null) {
				LOGGER.warn("Missing block model: {}", resourceLocation);
				return this.missingModel;
			} else {
				return this.createAndQueueWrapper(resourceLocation, unbakedModelx);
			}
		};
		this.resolver = this::getOrCreateModel;
	}

	private static boolean isRoot(UnbakedModel unbakedModel) {
		return unbakedModel.parent() == null;
	}

	private ModelDiscovery.ModelWrapper getOrCreateModel(ResourceLocation resourceLocation) {
		return this.modelWrappers.computeIfAbsent(resourceLocation, this.uncachedResolver);
	}

	private ModelDiscovery.ModelWrapper createAndQueueWrapper(ResourceLocation resourceLocation, UnbakedModel unbakedModel) {
		boolean bl = isRoot(unbakedModel);
		ModelDiscovery.ModelWrapper modelWrapper = new ModelDiscovery.ModelWrapper(resourceLocation, unbakedModel, bl);
		if (!bl) {
			this.parentDiscoveryQueue.add(modelWrapper);
		}

		return modelWrapper;
	}

	public void addRoot(ResolvableModel resolvableModel) {
		resolvableModel.resolveDependencies(this.resolver);
	}

	public void addSpecialModel(ResourceLocation resourceLocation, UnbakedModel unbakedModel) {
		if (!isRoot(unbakedModel)) {
			LOGGER.warn("Trying to add non-root special model {}, ignoring", resourceLocation);
		} else {
			ModelDiscovery.ModelWrapper modelWrapper = this.modelWrappers.put(resourceLocation, this.createAndQueueWrapper(resourceLocation, unbakedModel));
			if (modelWrapper != null) {
				LOGGER.warn("Duplicate special model {}", resourceLocation);
			}
		}
	}

	public ResolvedModel missingModel() {
		return this.missingModel;
	}

	public Map<ResourceLocation, ResolvedModel> resolve() {
		List<ModelDiscovery.ModelWrapper> list = new ArrayList();
		this.discoverDependencies(list);
		propagateValidity(list);
		Builder<ResourceLocation, ResolvedModel> builder = ImmutableMap.builder();
		this.modelWrappers.forEach((resourceLocation, modelWrapper) -> {
			if (modelWrapper.valid) {
				builder.put(resourceLocation, modelWrapper);
			} else {
				LOGGER.warn("Model {} ignored due to cyclic dependency", resourceLocation);
			}
		});
		return builder.build();
	}

	private void discoverDependencies(List<ModelDiscovery.ModelWrapper> list) {
		ModelDiscovery.ModelWrapper modelWrapper;
		while ((modelWrapper = (ModelDiscovery.ModelWrapper)this.parentDiscoveryQueue.poll()) != null) {
			ResourceLocation resourceLocation = (ResourceLocation)Objects.requireNonNull(modelWrapper.wrapped.parent());
			ModelDiscovery.ModelWrapper modelWrapper2 = this.getOrCreateModel(resourceLocation);
			modelWrapper.parent = modelWrapper2;
			if (modelWrapper2.valid) {
				modelWrapper.valid = true;
			} else {
				list.add(modelWrapper);
			}
		}
	}

	private static void propagateValidity(List<ModelDiscovery.ModelWrapper> list) {
		boolean bl = true;

		while (bl) {
			bl = false;
			Iterator<ModelDiscovery.ModelWrapper> iterator = list.iterator();

			while (iterator.hasNext()) {
				ModelDiscovery.ModelWrapper modelWrapper = (ModelDiscovery.ModelWrapper)iterator.next();
				if (((ModelDiscovery.ModelWrapper)Objects.requireNonNull(modelWrapper.parent)).valid) {
					modelWrapper.valid = true;
					iterator.remove();
					bl = true;
				}
			}
		}
	}

	@Environment(EnvType.CLIENT)
	static class ModelWrapper implements ResolvedModel {
		private static final ModelDiscovery.Slot<Boolean> KEY_AMBIENT_OCCLUSION = slot(0);
		private static final ModelDiscovery.Slot<UnbakedModel.GuiLight> KEY_GUI_LIGHT = slot(1);
		private static final ModelDiscovery.Slot<UnbakedGeometry> KEY_GEOMETRY = slot(2);
		private static final ModelDiscovery.Slot<ItemTransforms> KEY_TRANSFORMS = slot(3);
		private static final ModelDiscovery.Slot<TextureSlots> KEY_TEXTURE_SLOTS = slot(4);
		private static final ModelDiscovery.Slot<TextureAtlasSprite> KEY_PARTICLE_SPRITE = slot(5);
		private static final ModelDiscovery.Slot<QuadCollection> KEY_DEFAULT_GEOMETRY = slot(6);
		private static final int SLOT_COUNT = 7;
		private final ResourceLocation id;
		boolean valid;
		@Nullable
		ModelDiscovery.ModelWrapper parent;
		final UnbakedModel wrapped;
		private final AtomicReferenceArray<Object> fixedSlots = new AtomicReferenceArray(7);
		private final Map<ModelState, QuadCollection> modelBakeCache = new ConcurrentHashMap();

		private static <T> ModelDiscovery.Slot<T> slot(int i) {
			Objects.checkIndex(i, 7);
			return new ModelDiscovery.Slot<>(i);
		}

		ModelWrapper(ResourceLocation resourceLocation, UnbakedModel unbakedModel, boolean bl) {
			this.id = resourceLocation;
			this.wrapped = unbakedModel;
			this.valid = bl;
		}

		@Override
		public UnbakedModel wrapped() {
			return this.wrapped;
		}

		@Nullable
		@Override
		public ResolvedModel parent() {
			return this.parent;
		}

		@Override
		public String debugName() {
			return this.id.toString();
		}

		@Nullable
		private <T> T getSlot(ModelDiscovery.Slot<T> slot) {
			return (T)this.fixedSlots.get(slot.index);
		}

		private <T> T updateSlot(ModelDiscovery.Slot<T> slot, T object) {
			T object2 = (T)this.fixedSlots.compareAndExchange(slot.index, null, object);
			return object2 == null ? object : object2;
		}

		private <T> T getSimpleProperty(ModelDiscovery.Slot<T> slot, Function<ResolvedModel, T> function) {
			T object = this.getSlot(slot);
			return object != null ? object : this.updateSlot(slot, (T)function.apply(this));
		}

		@Override
		public boolean getTopAmbientOcclusion() {
			return this.getSimpleProperty(KEY_AMBIENT_OCCLUSION, ResolvedModel::findTopAmbientOcclusion);
		}

		@Override
		public UnbakedModel.GuiLight getTopGuiLight() {
			return this.getSimpleProperty(KEY_GUI_LIGHT, ResolvedModel::findTopGuiLight);
		}

		@Override
		public ItemTransforms getTopTransforms() {
			return this.getSimpleProperty(KEY_TRANSFORMS, ResolvedModel::findTopTransforms);
		}

		@Override
		public UnbakedGeometry getTopGeometry() {
			return this.getSimpleProperty(KEY_GEOMETRY, ResolvedModel::findTopGeometry);
		}

		@Override
		public TextureSlots getTopTextureSlots() {
			return this.getSimpleProperty(KEY_TEXTURE_SLOTS, ResolvedModel::findTopTextureSlots);
		}

		@Override
		public TextureAtlasSprite resolveParticleSprite(TextureSlots textureSlots, ModelBaker modelBaker) {
			TextureAtlasSprite textureAtlasSprite = this.getSlot(KEY_PARTICLE_SPRITE);
			return textureAtlasSprite != null
				? textureAtlasSprite
				: this.updateSlot(KEY_PARTICLE_SPRITE, ResolvedModel.resolveParticleSprite(textureSlots, modelBaker, this));
		}

		private QuadCollection bakeDefaultState(TextureSlots textureSlots, ModelBaker modelBaker, ModelState modelState) {
			QuadCollection quadCollection = this.getSlot(KEY_DEFAULT_GEOMETRY);
			return quadCollection != null
				? quadCollection
				: this.updateSlot(KEY_DEFAULT_GEOMETRY, this.getTopGeometry().bake(textureSlots, modelBaker, modelState, this));
		}

		@Override
		public QuadCollection bakeTopGeometry(TextureSlots textureSlots, ModelBaker modelBaker, ModelState modelState) {
			return modelState == BlockModelRotation.X0_Y0
				? this.bakeDefaultState(textureSlots, modelBaker, modelState)
				: (QuadCollection)this.modelBakeCache.computeIfAbsent(modelState, modelStatex -> {
					UnbakedGeometry unbakedGeometry = this.getTopGeometry();
					return unbakedGeometry.bake(textureSlots, modelBaker, modelStatex, this);
				});
		}
	}

	@Environment(EnvType.CLIENT)
	record Slot<T>(int index) {
	}
}
