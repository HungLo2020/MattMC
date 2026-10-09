package net.vulkanic.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.render.state.GuiItemRenderState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import net.sodium.client.model.quad.BakedQuadView;
import net.vulkanic.bridge.VulkanicGalBridge.*;
import static net.vulkanic.bridge.VulkanicGalBridge.GUI_MESH_MATERIAL_MODEL_OVERLAY;

/** Flat item model snapshots; no Java raster target, projection or foil matrix. */
final class GuiFlatItemMeshCollector {
	private static final int MAX_COPIED_VERTICES = 65_536;
	private static final int MAX_CACHED_TOPOLOGIES = 256;
	private static final Map<FlatTopologyKey, FlatTopology> TOPOLOGY_CACHE = new LinkedHashMap<>(64, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<FlatTopologyKey, FlatTopology> eldest) {
			return this.size() > MAX_CACHED_TOPOLOGIES;
		}
	};
	private record Faces(long asset, List<GuiMeshVertexRecord> vertices) {}
	private record FlatTopologyKey(Object modelIdentity, int guiScale, boolean foil) {}
	/** Raster identity of a cached flat item; foil and plain variants never share pixels. */
	private record FlatRasterIdentity(Object modelIdentity, boolean foil) {}
	/** One cached layer; {@code foil} layers take each frame's foil clock, {@code specialFoil} its decal layout. */
	private record FlatLayer(int material, long asset, float[] transform, List<GuiMeshVertexRecord> vertices,
			List<Integer> indices, boolean foil, boolean specialFoil, net.vulkanic.world.NativeItemLayerTransform.Capture nativeTransform) {
		private FlatLayer {
			if (nativeTransform == null) transform = transform.clone();
			else if (transform != null) throw new IllegalArgumentException("native flat item pose conflicts with matrix");
			vertices = List.copyOf(vertices);
		}
	}
	private record FlatTopology(List<FlatLayer> layers, List<GuiItemTextureSource> sources) {
		private FlatTopology {
			layers = List.copyOf(layers);
			sources = List.copyOf(sources);
		}
	}
	record Snapshot(List<GuiMeshBatchRecord> batches, List<GuiItemTextureSource> sources) {
		Snapshot { batches=List.copyOf(batches); sources=List.copyOf(sources); }
	}

	static synchronized void invalidateCache() {
		TOPOLOGY_CACHE.clear();
	}

    static Snapshot collect(GuiItemRenderState item, int guiWidth, int guiHeight,
                            int guiScale, int stratum, StandardItemFoilRecord foil) {
        if (item.itemStackRenderState().displayContext()!=ItemDisplayContext.GUI
            || item.itemStackRenderState().usesBlockLight() || guiScale<=0) {
            throw new IllegalArgumentException("flat mesh requires GUI flat-lighting semantics");
        }
        List<GuiItemTextureSource> sources=new ArrayList<>();
        Object modelIdentity = item.itemStackRenderState().getModelIdentity();
        FlatTopologyKey cacheKey = !item.itemStackRenderState().isAnimated() && modelIdentity != null
            ? new FlatTopologyKey(modelIdentity, guiScale, foil != null) : null;
        FlatTopology cached = cacheKey == null ? null : cachedTopology(cacheKey);
        if (cached != null) {
            net.minecraft.client.dev.GraphicsFrameBenchmark.recordCounterSample("gui.item-flat-topology-cache.hit", 1L);
            return instantiate(item, guiWidth, guiHeight, guiScale, stratum, cached, foil, itemCache(cacheKey));
        }
        List<FlatLayer> layers = new ArrayList<>();
        int[] copiedVertexCount = {0};
        boolean[] animatedSource = {false};
        item.itemStackRenderState().forEachSemanticLayer(layer -> {
            boolean specialFoil=layer.foilType()==ItemStackRenderState.FoilType.SPECIAL;
            if (layer.hasSpecialRenderer() || layer.usesBlockLight() || layer.quads().isEmpty()
                || !RustGalGuiItemRenderer.supportedGuiRenderType(layer.renderType())) {
                throw new IllegalArgumentException("unsupported flat mesh layer");
            }
            int material=RustGalGuiItemRenderer.flatItemMaterial(layer.renderType().pipeline())==2 ? 2 : 3;
            int copies = layer.foilType()!=ItemStackRenderState.FoilType.NONE ? 2 : 1;
            if (layer.quads().size() > (MAX_COPIED_VERTICES-copiedVertexCount[0])/(4*copies))
                throw new IllegalArgumentException("flat mesh vertex bound exceeded");
            copiedVertexCount[0] += layer.quads().size()*4*copies;
            List<Faces> copied=new ArrayList<>();
            for (BakedQuad baked:layer.quads()) {
                // Preserve back and edge faces too. Rust owns normal
                // transformation, winding, depth and raster-cell validation.
                if (!(baked instanceof BakedQuadView quad))
                    throw new IllegalArgumentException("flat mesh face has no semantic quad view");
                var sprite=quad.getSprite();
                if (sprite==null) throw new IllegalArgumentException("flat mesh sprite missing");
                if (sprite.contents().isAnimated()) animatedSource[0] = true;
                var region=net.vulkanic.world.RustGalTerrainRenderer.requireGuiAtlasSpritePayload(sprite);
                long asset=RustGalGuiRawImageAssets.assetId("gui-atlas-region:"+sprite.atlasLocation()+":"+sprite.contents().name());
                var source=new GuiItemTextureSource.Atlas(new GuiAtlasRegion(asset,region.texture(),
                    region.atlasWidth(),region.atlasHeight(),region.x(),region.y(),region.width(),region.height()));
                boolean newResource = copied.isEmpty() || copied.getLast().asset()!=asset;
                if (newResource) {
                    sources.add(source);
                    copied.add(new Faces(asset,new ArrayList<>()));
                }
                int[] tints=layer.tintLayers();
                int tint=baked.isTinted() && baked.tintIndex()>=0 && baked.tintIndex()<tints.length ? tints[baked.tintIndex()] : -1;
                List<GuiMeshVertexRecord> vertices=new ArrayList<>(4);
                for(int vertex=0;vertex<4;vertex++) {
                    float u=quad.getTexU(vertex), v=quad.getTexV(vertex);
                    float du=sprite.getU1()-sprite.getU0(), dv=sprite.getV1()-sprite.getV0();
                    if (!(du>0) || !(dv>0)) throw new IllegalArgumentException("flat mesh sprite UV extent missing");
                    // Original model position, normal, tint and texture coordinates.
                    // Do not apply the GUI/model matrix or animation here.
                    vertices.add(new GuiMeshVertexRecord(new float[]{quad.getX(vertex),quad.getY(vertex),quad.getZ(vertex)},
                        new float[]{u,v},new float[]{RustGalGuiItemRenderer.itemLocalUv(u,sprite.getU0(),sprite.getU1()),
                            RustGalGuiItemRenderer.itemLocalUv(v,sprite.getV0(),sprite.getV1())},
                        GuiItemMeshSemanticCollector.standard3dVertexColor(quad.getColor(vertex),tint,
                            net.sodium.client.render.immediate.model.BakedModelEncoder.shouldMultiplyAlpha()),
                        quad.getAccurateNormal(vertex)));
                }
                copied.getLast().vertices().addAll(vertices);
            }
            for (var faces : copied) {
                layers.add(new FlatLayer(material, faces.asset(), layer.nativeTransform() == null ? layer.modelTransform() : null, faces.vertices(),
                    quadIndices(faces.vertices().size()), false, false, layer.nativeTransform()));
            }
            if(layer.foilType()!=ItemStackRenderState.FoilType.NONE) {
                var glint=RustGalGuiRawImageAssets.resolve(ItemRenderer.ENCHANTED_GLINT_ITEM);
                if(glint==null) throw new IllegalArgumentException("flat mesh foil image missing");
                sources.add(new GuiItemTextureSource.Raw(glint));
                List<GuiMeshVertexRecord> foilVertices = new ArrayList<>();
                for (var faces : copied) foilVertices.addAll(faces.vertices());
                if (foil == null) throw new IllegalArgumentException("flat mesh foil layer without foil semantics");
                layers.add(new FlatLayer(4, glint.assetId(), layer.nativeTransform() == null ? layer.modelTransform() : null, foilVertices,
                    quadIndices(foilVertices.size()), true, specialFoil, layer.nativeTransform()));
            }
            if(layers.size()>64) throw new IllegalArgumentException("flat mesh layer bound exceeded");
        });
        if(layers.isEmpty()) throw new IllegalArgumentException("flat mesh empty");
        FlatTopology topology = new FlatTopology(layers, sources);
        if (cacheKey != null && !animatedSource[0]) {
            cacheTopology(cacheKey, topology);
            net.minecraft.client.dev.GraphicsFrameBenchmark.recordCounterSample("gui.item-flat-topology-cache.miss", 1L);
            return instantiate(item, guiWidth, guiHeight, guiScale, stratum, topology, foil, itemCache(cacheKey));
        }
        if (cacheKey == null) {
            net.minecraft.client.dev.GraphicsFrameBenchmark.recordCounterSample("gui.item-flat-topology-cache.bypass", 1L);
        }
        return instantiate(item, guiWidth, guiHeight, guiScale, stratum, topology, foil, null);
    }

	private static synchronized FlatTopology cachedTopology(FlatTopologyKey key) {
		return TOPOLOGY_CACHE.get(key);
	}

	private static synchronized void cacheTopology(FlatTopologyKey key, FlatTopology topology) {
		TOPOLOGY_CACHE.putIfAbsent(key, topology);
	}

	/**
	 * A cached flat item reuses its Rust raster while static; with foil the
	 * raster is redrawn each frame but the geometry stays resident.
	 */
	private static GuiItemCacheRecord itemCache(FlatTopologyKey key) {
		long identity = GuiItemSemanticIdentities.identityOrZero(new FlatRasterIdentity(key.modelIdentity(), key.foil()));
		return identity == 0 ? null : new GuiItemCacheRecord(identity, key.foil(), true);
	}

	private static List<Integer> quadIndices(int vertexCount) {
		int[] indices = new int[vertexCount / 4 * 6];
		for (int first = 0, cursor = 0; first < vertexCount; first += 4) {
			indices[cursor++] = first; indices[cursor++] = first + 1; indices[cursor++] = first + 2;
			indices[cursor++] = first + 2; indices[cursor++] = first + 3; indices[cursor++] = first;
		}
		return net.vulkanic.bridge.VulkanicGalBridge.packedGuiMeshIndices(indices, indices.length);
	}

	private static Snapshot instantiate(GuiItemRenderState item, int guiWidth, int guiHeight,
		int guiScale, int stratum, FlatTopology topology, StandardItemFoilRecord foil, GuiItemCacheRecord itemCache) {
		List<GuiMeshBatchRecord> batches = new ArrayList<>(topology.layers().size());
		for (FlatLayer layer : topology.layers()) {
			if (layer.foil() && foil == null) throw new IllegalArgumentException("flat mesh foil layer without foil semantics");
			var batch = batch(item, guiWidth, guiHeight, guiScale, stratum, batches.size(), layer.material(),
				layer.asset(), layer.transform(), layer.vertices(), layer.indices(), layer.foil() ? foil : null, 1, layer.nativeTransform());
			if (layer.specialFoil()) batch = batch.withDecalFoil(GuiDecalFoilRecord.forNativeItemLayout());
			batches.add(itemCache == null ? batch : batch.withItemCache(itemCache));
		}
		return new Snapshot(batches, topology.sources());
	}

    static GuiMeshBatchRecord batch(GuiItemRenderState item,int width,int height,int scale,int stratum,int layer,
                                           int material,long asset,float[] transform,List<GuiMeshVertexRecord> vertices,
                                           StandardItemFoilRecord foil) {
        return batch(item,width,height,scale,stratum,layer,material,asset,transform,vertices,foil,1);
    }

    static GuiMeshBatchRecord batch(GuiItemRenderState item,int width,int height,int scale,int stratum,int layer,
                                           int material,long asset,float[] transform,List<GuiMeshVertexRecord> vertices,
                                           StandardItemFoilRecord foil,int lighting) {
        if (vertices.isEmpty() || vertices.size()%4!=0 || vertices.size()>MAX_COPIED_VERTICES)
            throw new IllegalArgumentException("flat mesh requires bounded complete quads");
        return batch(item,width,height,scale,stratum,layer,material,asset,transform,vertices,
            quadIndices(vertices.size()),foil,lighting);
    }

    private static GuiMeshBatchRecord batch(GuiItemRenderState item,int width,int height,int scale,int stratum,int layer,
                                           int material,long asset,float[] transform,List<GuiMeshVertexRecord> vertices,
                                           List<Integer> indices,StandardItemFoilRecord foil,int lighting) {
        return batch(item,width,height,scale,stratum,layer,material,asset,transform,vertices,indices,foil,lighting,null);
    }
    private static GuiMeshBatchRecord batch(GuiItemRenderState item,int width,int height,int scale,int stratum,int layer,
                                           int material,long asset,float[] transform,List<GuiMeshVertexRecord> vertices,
                                           List<Integer> indices,StandardItemFoilRecord foil,int lighting,net.vulkanic.world.NativeItemLayerTransform.Capture nativeTransform) {
        var clip=item.scissorArea();
        var pose=item.pose();
        if (vertices.isEmpty() || vertices.size()%4!=0 || vertices.size()>MAX_COPIED_VERTICES)
            throw new IllegalArgumentException("flat mesh requires bounded complete quads");
        return new GuiMeshBatchRecord(stratum,layer,material,lighting,asset,0L,material == 1 || material == GUI_MESH_MATERIAL_MODEL_OVERLAY ? 0.0F : 0.1F,transform,
            new float[]{pose.m00(),pose.m01(),pose.m10(),pose.m11(),pose.m20(),pose.m21()},
            item.x(),item.y(),item.x()+16,item.y()+16,width,height,0,0,0,
            clip==null?0:1,clip==null?0:clip.left(),clip==null?0:clip.top(),
            clip==null?0:clip.width(),clip==null?0:clip.height(),vertices,indices,foil,scale,null,null,null,nativeTransform);
    }
}
