package net.vulkanic.world;

import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.data.AtlasIds;
import net.sodium.client.model.quad.BakedQuadView;
import com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper;
import com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;

import java.util.EnumMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Converts an already selected Minecraft model into a small, copied face
 * material table for the Rust-owned Distant Horizons texture pass.
 *
 * <p>The output contains no {@link TextureAtlasSprite}, model, renderer, or
 * backend object. It is deliberately strict: a reduced LOD quad may use a
	 * sprite only when its source state resolves to one or more co-planar,
	 * copied atlas regions for that face. Animated regions are refreshed through
	 * the atlas semantic generation before Rust submission. A reduced DH face can reproduce
	 * ordinary baked-model overlays such as grass sides only by retaining their
	 * ordered layers; callers retain the color-only path for geometry that cannot
	 * be represented by the reduced face contract.</p>
 */
final class DistantHorizonsFaceMaterialResolver {
	private DistantHorizonsFaceMaterialResolver() {
	}

	private static final int MAX_FACE_LAYERS = 4;
	/**
	 * Reusing a resolution is safe because it retains only copied semantic atlas
	 * values, never a Minecraft model, sprite, or renderer object.  Bound it so
	 * a modded world's state vocabulary cannot turn the exact-atlas path into an
	 * unbounded Java heap cache.
	 */
	private static final int MAX_CACHED_STATE_RESOLUTIONS = 2_048;
	private static final Map<String, Resolution> CACHED_STATE_RESOLUTIONS =
		new LinkedHashMap<>(128, 0.75F, true);
	/** Position-specific weighted-model results are also copied semantic data,
	 * but they must not share the state-only cache because the packed source
	 * position is part of vanilla's model-selection seed. Keep a separate
	 * bounded LRU so large DH rebuilds cannot grow this cache with the world. */
	private static final int MAX_CACHED_VARIANT_RESOLUTIONS = 4_096;
	private static final Map<VariantResolutionKey, Resolution> CACHED_VARIANT_RESOLUTIONS =
		new LinkedHashMap<>(256, 0.75F, true);
	private static final LinkedHashSet<Integer> AUDIT_WATER_TINTS = new LinkedHashSet<>();

	enum Status {
		COMPLETE,
		PARTIAL_FACE_MAPPING,
		UNCULLED_QUAD,
		MULTIPLE_FACE_QUADS,
		MIXED_FACE_SPRITES,
		ANIMATED_SPRITE,
		UNSUPPORTED_FACE_MAPPING,
		VARIANT_DEPENDENT,
		SOURCE_STATE_UNAVAILABLE
	}

	/**
	 * Pure semantic atlas data. `uvCornerOrder` maps the canonical DH face
	 * corners `[00, 01, 11, 10]` to the source sprite corners encoded as
	 * `uBit | (vBit << 1)`. It preserves UV rotation/mirroring without keeping
	 * a model, sprite, or renderer object alive.
	 */
	record FaceMaterial(
		String atlasIdentity,
		String spriteIdentity,
		float u0,
		float v0,
		float u1,
		float v1,
		int uvCornerOrder,
		int layer,
		boolean tinted,
		int tintArgb
	) {
		static final int CANONICAL_UV_CORNER_ORDER = 0x78;

		FaceMaterial(String atlasIdentity, String spriteIdentity, float u0, float v0, float u1, float v1) {
			this(atlasIdentity, spriteIdentity, u0, v0, u1, v1, CANONICAL_UV_CORNER_ORDER, 0, false, 0xffffffff);
		}

		FaceMaterial(String atlasIdentity, String spriteIdentity, float u0, float v0, float u1, float v1, int uvCornerOrder) {
			this(atlasIdentity, spriteIdentity, u0, v0, u1, v1, uvCornerOrder, 0, false, 0xffffffff);
		}

		FaceMaterial(String atlasIdentity, String spriteIdentity, float u0, float v0, float u1, float v1, int uvCornerOrder, int layer) {
			this(atlasIdentity, spriteIdentity, u0, v0, u1, v1, uvCornerOrder, layer, false, 0xffffffff);
		}

		FaceMaterial {
			if (atlasIdentity == null || atlasIdentity.isBlank()
				|| spriteIdentity == null || spriteIdentity.isBlank()) {
				throw new IllegalArgumentException("DH face material identities must be non-blank");
			}
			if (!Float.isFinite(u0) || !Float.isFinite(v0) || !Float.isFinite(u1) || !Float.isFinite(v1)
				|| u0 < 0.0F || v0 < 0.0F || u1 > 1.0F || v1 > 1.0F || u0 >= u1 || v0 >= v1) {
				throw new IllegalArgumentException("DH face material atlas UVs must be finite normalized regions");
			}
			if (!validUvCornerOrder(uvCornerOrder)) {
				throw new IllegalArgumentException("DH face material UV corner order must be a permutation");
			}
			if (layer < 0 || layer >= MAX_FACE_LAYERS) {
				throw new IllegalArgumentException("DH face material layer exceeds the bounded reduced-face contract");
			}
		}
	}

	/** One copied candidate used by the pure validation and test path. */
	record FaceCandidate(Direction face, FaceMaterial material, boolean animated, boolean unculled) {
		FaceCandidate {
			Objects.requireNonNull(face, "face");
			Objects.requireNonNull(material, "material");
		}
	}

	record Resolution(Status status, EnumMap<Direction, List<FaceMaterial>> faceLayers) {
		Resolution {
			Objects.requireNonNull(status, "status");
			EnumMap<Direction, List<FaceMaterial>> copied = new EnumMap<>(Direction.class);
			for (var entry : Objects.requireNonNull(faceLayers, "faceLayers").entrySet()) {
				copied.put(entry.getKey(), List.copyOf(entry.getValue()));
			}
			faceLayers = copied;
		}

		/** The first layer remains available for legacy diagnostics. */
		EnumMap<Direction, FaceMaterial> faces() {
			EnumMap<Direction, FaceMaterial> first = new EnumMap<>(Direction.class);
			for (var entry : faceLayers.entrySet()) {
				if (!entry.getValue().isEmpty()) {
					first.put(entry.getKey(), entry.getValue().getFirst());
				}
			}
			return first;
		}

		boolean isComplete() {
			return this.status == Status.COMPLETE && !this.faceLayers.isEmpty();
		}

		/**
		 * A partial result is still safe for the faces it names. The Rust LOD
		 * planner pairs every reduced quad with its normal, so it can retain an
		 * unresolved face in the color-only stream without inventing a sprite for
		 * it. This is deliberately distinct from declaring the whole block state
		 * textureless because one decorative face is ambiguous.
		 */
		boolean hasResolvedFaces() {
			return !this.faceLayers.isEmpty();
		}

		/** Exact-atlas provenance may only advertise a complete model-face map. */
		boolean isExactAtlasAdmissible() {
			return this.status == Status.COMPLETE && hasResolvedFaces();
		}

		/**
	 * Some baked models (notably crossed vegetation) contain the same copied
	 * material on several planar faces, while DH's reduced geometry may classify
	 * one of those quads under a different cardinal normal. Publishing that
	 * material for omitted cardinal faces is safe only when at least two resolved
	 * faces prove one copied atlas/sprite/UV-region/tint identity and one layer;
	 * omitted faces use the canonical DH UV orientation. Mixed sprites, UV
	 * regions, tint contracts, partial/multi-layer mappings, and unsupported
	 * geometry stay unresolved rather than receiving a guessed texture.
		 */
		Resolution withUniformFaceCoverageIfSafe() {
			if (status != Status.COMPLETE || faceLayers.size() < 2
				|| faceLayers.size() == Direction.values().length) {
				return this;
			}
			List<FaceMaterial> reference = faceLayers.values().iterator().next();
			if (reference.isEmpty()) {
				return this;
			}
			EnumMap<Direction, List<FaceMaterial>> covered = new EnumMap<>(faceLayers);
			if (faceLayers.values().stream().allMatch(layers -> layers.equals(reference))) {
				for (Direction direction : Direction.values()) {
					covered.putIfAbsent(direction, reference);
				}
				return new Resolution(status, covered);
			}
			FaceMaterial canonical = canonicalUniformFaceMaterial(faceLayers.values());
			if (canonical == null) return this;
			for (Direction direction : Direction.values()) {
				covered.putIfAbsent(direction, List.of(canonical));
			}
			return new Resolution(status, covered);
		}

		Resolution withUniformFaceCoverageIfSafe(java.util.Set<MaterialIdentity> observedMaterials) {
			if (status != Status.COMPLETE
				|| faceLayers.size() < 2 || faceLayers.size() == Direction.values().length
				|| observedMaterials == null || observedMaterials.size() != 1) {
				return this;
			}
			List<FaceMaterial> reference = faceLayers.values().iterator().next();
			if (reference.isEmpty()) {
				return this;
			}
			EnumMap<Direction, List<FaceMaterial>> covered = new EnumMap<>(faceLayers);
			if (faceLayers.values().stream().allMatch(layers -> layers.equals(reference))) {
				for (Direction direction : Direction.values()) {
					covered.putIfAbsent(direction, reference);
				}
				return new Resolution(status, covered);
			}
			FaceMaterial canonical = canonicalUniformFaceMaterial(faceLayers.values());
			if (canonical == null) return this;
			for (Direction direction : Direction.values()) {
				covered.putIfAbsent(direction, List.of(canonical));
			}
			return new Resolution(status, covered);
		}

		private static FaceMaterial canonicalUniformFaceMaterial(Collection<List<FaceMaterial>> layersByFace) {
			FaceMaterial reference = null;
			for (List<FaceMaterial> layers : layersByFace) {
				if (layers.size() != 1 || layers.getFirst().layer() != 0) return null;
				FaceMaterial material = layers.getFirst();
				if (reference == null) {
					reference = material;
				} else if (!sameMaterialIdentity(reference, material)) {
					return null;
				}
			}
			if (reference == null) return null;
			return new FaceMaterial(
				reference.atlasIdentity(), reference.spriteIdentity(), reference.u0(), reference.v0(),
				reference.u1(), reference.v1(), FaceMaterial.CANONICAL_UV_CORNER_ORDER, 0,
				reference.tinted(), reference.tintArgb()
			);
		}

		private static boolean sameMaterialIdentity(FaceMaterial left, FaceMaterial right) {
			return left.atlasIdentity().equals(right.atlasIdentity())
				&& left.spriteIdentity().equals(right.spriteIdentity())
				&& Float.floatToIntBits(left.u0()) == Float.floatToIntBits(right.u0())
				&& Float.floatToIntBits(left.v0()) == Float.floatToIntBits(right.v0())
				&& Float.floatToIntBits(left.u1()) == Float.floatToIntBits(right.u1())
				&& Float.floatToIntBits(left.v1()) == Float.floatToIntBits(right.v1())
				&& left.tinted() == right.tinted()
				&& left.tintArgb() == right.tintArgb();
		}
	}

	/**
	 * Resolves copied candidates with no Minecraft object retained in the
	 * result. The first ambiguity rejects the entire identity; choosing an
	 * arbitrary face sprite would create the texture swaps this contract exists
	 * to prevent.
	 */
	static Resolution resolveCandidates(List<FaceCandidate> candidates) {
		Objects.requireNonNull(candidates, "candidates");
		EnumMap<Direction, List<FaceMaterial>> faces = new EnumMap<>(Direction.class);
		Status firstUnavailable = null;
		for (Direction face : Direction.values()) {
			LinkedHashSet<FaceMaterial> materials = new LinkedHashSet<>();
			Status unavailable = null;
			for (FaceCandidate candidate : candidates) {
				if (candidate.face() != face) {
					continue;
				}
				if (candidate.unculled()) {
					unavailable = Status.UNCULLED_QUAD;
					break;
				}
				materials.add(candidate.material());
				if (materials.size() > MAX_FACE_LAYERS) {
					unavailable = Status.MULTIPLE_FACE_QUADS;
					break;
				}
			}
			if (unavailable != null) {
				if (firstUnavailable == null) {
					firstUnavailable = unavailable;
				}
			} else if (!materials.isEmpty()) {
				faces.put(face, numberedLayers(materials));
			}
		}
		if (faces.isEmpty()) {
			return new Resolution(firstUnavailable == null ? Status.UNSUPPORTED_FACE_MAPPING : firstUnavailable, faces);
		}
		return new Resolution(firstUnavailable == null ? Status.COMPLETE : Status.PARTIAL_FACE_MAPPING, faces);
	}

	/** Stable semantic face IDs shared with the Rust world-material ABI. */
	static int faceId(Direction direction) {
		return switch (Objects.requireNonNull(direction, "direction")) {
			case DOWN -> 0;
			case UP -> 1;
			case NORTH -> 2;
			case SOUTH -> 3;
			case WEST -> 4;
			case EAST -> 5;
		};
	}

	/**
	 * Render-thread extraction boundary. The caller supplies a deterministic
	 * model seed; texture atlas objects are converted immediately into copied
	 * semantic values and never leave this method.
	 */
	static Resolution resolve(
		BlockRenderDispatcher dispatcher,
		BlockState state,
		long modelSeed
	) {
		Objects.requireNonNull(dispatcher, "dispatcher");
		Objects.requireNonNull(state, "state");
		BlockStateModel model = dispatcher.getBlockModel(state);
		List<BlockModelPart> parts = model.collectParts(RandomSource.create(modelSeed));
		EnumMap<Direction, List<BakedQuad>> quadsByFace = new EnumMap<>(Direction.class);
		for (Direction direction : Direction.values()) {
			quadsByFace.put(direction, new java.util.ArrayList<>());
		}
		Set<BakedQuad> unculledQuads = Collections.newSetFromMap(new IdentityHashMap<>());
		for (BlockModelPart part : parts) {
			for (Direction direction : Direction.values()) {
				quadsByFace.get(direction).addAll(part.getQuads(direction));
			}
			for (BakedQuad quad : part.getQuads(null)) {
				// Keep unculled provenance beside the face bucket. A crossed model's
				// reported cardinal direction is not proof that its rotated plane is
				// the matching reduced DH face; it may participate only in the strict
				// uniform planar fallback below.
				unculledQuads.add(quad);
				quadsByFace.get(quad.direction()).add(quad);
			}
		}
		LinkedHashSet<MaterialIdentity> observedMaterials = observedMaterialIdentities(quadsByFace, state, null);
		EnumMap<Direction, List<FaceMaterial>> faces = new EnumMap<>(Direction.class);
		Status firstUnavailable = null;
		for (Direction direction : Direction.values()) {
			FaceResolution faceResolution = resolveFace(direction, quadsByFace.get(direction), unculledQuads, state, null);
			if (!faceResolution.materials().isEmpty()) {
				faces.put(direction, faceResolution.materials());
			} else if (faceResolution.status() != null && firstUnavailable == null) {
				firstUnavailable = faceResolution.status();
			}
		}
		if (faces.isEmpty()) {
			FaceMaterial fallback = resolveUniformPlanarFallback(quadsByFace, state, null, observedMaterials);
			if (fallback != null) {
				return uniformFaceResolution(fallback);
			}
			return new Resolution(firstUnavailable == null ? Status.UNSUPPORTED_FACE_MAPPING : firstUnavailable, faces);
		}
		return new Resolution(firstUnavailable == null ? Status.COMPLETE : Status.PARTIAL_FACE_MAPPING, faces)
			.withUniformFaceCoverageIfSafe(observedMaterials);
	}

	private static FaceResolution resolveFace(
		Direction direction,
		List<BakedQuad> quads,
		Set<BakedQuad> unculledQuads,
		BlockState state,
		BlockPos tintPosition
	) {
		if (quads == null || quads.isEmpty()) {
			return new FaceResolution(List.of(), null);
		}
		LinkedHashSet<FaceMaterial> materials = new LinkedHashSet<>();
		boolean skippedInvalidQuad = false;
		for (BakedQuad quad : quads) {
			if (unculledQuads.contains(quad)) {
				skippedInvalidQuad = true;
				continue;
			}
			TextureAtlasSprite sprite = quad.sprite();
			int uvCornerOrder = canonicalUvCornerOrder((BakedQuadView)(Object)quad, direction, sprite);
			if (uvCornerOrder < 0) {
				// A model face may contain several geometric quads (stairs are a
				// common example). Keep independently valid records instead of
				// discarding the entire face because one inset/diagonal quad cannot
				// be represented by the bounded reduced-face contract. The caller
				// still receives a partial status and the invalid geometry remains
				// explicitly unavailable in Rust.
				skippedInvalidQuad = true;
				continue;
			}
			int tintArgb = 0xffffffff;
			if (quad.isTinted() && tintPosition != null) {
				tintArgb = 0xff000000 | Minecraft.getInstance().getBlockColors().getColor(
					state, Minecraft.getInstance().level, tintPosition, quad.tintIndex()
				);
			}
			FaceMaterial candidate = new FaceMaterial(
				sprite.atlasLocation().toString(),
				sprite.contents().name().toString(),
				sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1(), uvCornerOrder, 0, quad.isTinted(), tintArgb
			);
			materials.add(candidate);
			if (materials.size() > MAX_FACE_LAYERS) {
				return new FaceResolution(List.of(), Status.MULTIPLE_FACE_QUADS);
			}
		}
		return new FaceResolution(numberedLayers(materials),
			skippedInvalidQuad ? Status.UNSUPPORTED_FACE_MAPPING : null);
	}

	private static List<FaceMaterial> numberedLayers(LinkedHashSet<FaceMaterial> materials) {
		List<FaceMaterial> layers = new ArrayList<>(materials.size());
		int layer = 0;
		for (FaceMaterial material : materials) {
			layers.add(new FaceMaterial(
				material.atlasIdentity(), material.spriteIdentity(), material.u0(), material.v0(), material.u1(), material.v1(),
				material.uvCornerOrder(), layer++, material.tinted(), material.tintArgb()
			));
		}
		return List.copyOf(layers);
	}

	private record FaceResolution(List<FaceMaterial> materials, Status status) {
		private FaceResolution {
			materials = List.copyOf(materials == null ? List.of() : materials);
		}
	}

	private static Resolution uniformFaceResolution(FaceMaterial material) {
		EnumMap<Direction, List<FaceMaterial>> faces = new EnumMap<>(Direction.class);
		for (Direction direction : Direction.values()) {
			faces.put(direction, List.of(material));
		}
		return new Resolution(Status.COMPLETE, faces);
	}

	/**
	 * Crossed vegetation and crystal models store rotated quads in the unculled
	 * list and some Minecraft versions report a cardinal direction unrelated to
	 * the quad's actual plane. Recovering a uniform material is safe only when all
	 * source quads share one copied identity, use the complete sprite region, and
	 * are non-degenerate planar quads. This keeps sloped models (dripstone),
	 * subregion/multi-material models, and block-entity models on the explicit
	 * reduced-color fallback.
	 */
	private static FaceMaterial resolveUniformPlanarFallback(
		EnumMap<Direction, List<BakedQuad>> quadsByFace,
		BlockState state,
		BlockPos tintPosition,
		java.util.Set<MaterialIdentity> observedMaterials
	) {
		if (observedMaterials == null || observedMaterials.size() != 1) return null;
		MaterialIdentity identity = observedMaterials.iterator().next();
		TextureAtlasSprite referenceSprite = null;
		boolean sawQuad = false;
		for (List<BakedQuad> quads : quadsByFace.values()) {
			for (BakedQuad quad : quads) {
				sawQuad = true;
				TextureAtlasSprite sprite = quad.sprite();
				int tintArgb = 0xffffffff;
				if (quad.isTinted() && tintPosition != null) {
					Minecraft minecraft = Minecraft.getInstance();
					tintArgb = 0xff000000 | minecraft.getBlockColors().getColor(
						state, minecraft.level, tintPosition, quad.tintIndex()
					);
				}
				MaterialIdentity candidate = new MaterialIdentity(
					sprite.atlasLocation().toString(), sprite.contents().name().toString(),
					sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1(), quad.isTinted(), tintArgb
				);
				if (!identity.equals(candidate) || !isPlanarQuad((BakedQuadView)(Object)quad)
					|| !usesCompleteSpriteRegion((BakedQuadView)(Object)quad, sprite)) {
					return null;
				}
				if (referenceSprite == null) referenceSprite = sprite;
			}
		}
		if (!sawQuad || referenceSprite == null) return null;
		return new FaceMaterial(
			identity.atlasIdentity(), identity.spriteIdentity(), identity.u0(), identity.v0(),
			identity.u1(), identity.v1(), FaceMaterial.CANONICAL_UV_CORNER_ORDER, 0,
			identity.tinted(), identity.tintArgb()
		);
	}

	private static boolean isPlanarQuad(BakedQuadView quad) {
		float[] x = new float[4], y = new float[4], z = new float[4];
		for (int index = 0; index < 4; index++) {
			x[index] = quad.getX(index);
			y[index] = quad.getY(index);
			z[index] = quad.getZ(index);
			if (!Float.isFinite(x[index]) || !Float.isFinite(y[index]) || !Float.isFinite(z[index])) return false;
		}
		float ax = x[1] - x[0], ay = y[1] - y[0], az = z[1] - z[0];
		float bx = x[2] - x[0], by = y[2] - y[0], bz = z[2] - z[0];
		float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
		float normalLengthSquared = nx * nx + ny * ny + nz * nz;
		if (!(normalLengthSquared > 1.0e-10F) || !Float.isFinite(normalLengthSquared)) return false;
		float coplanar = nx * (x[3] - x[0]) + ny * (y[3] - y[0]) + nz * (z[3] - z[0]);
		return Float.isFinite(coplanar) && Math.abs(coplanar) <= 0.0001F * (float)Math.sqrt(normalLengthSquared);
	}

	private static boolean usesCompleteSpriteRegion(BakedQuadView quad, TextureAtlasSprite sprite) {
		int corners = 0;
		for (int index = 0; index < 4; index++) {
			int corner = spriteCorner(sprite, quad.getTexU(index), quad.getTexV(index));
			if (corner < 0) return false;
			corners |= 1 << corner;
		}
		return corners == 0xf;
	}

	/** Copied material identity used to prove that skipped geometry did not hide a different sprite. */
	private record MaterialIdentity(
		String atlasIdentity,
		String spriteIdentity,
		float u0,
		float v0,
		float u1,
		float v1,
		boolean tinted,
		int tintArgb
	) {
	}

	private static LinkedHashSet<MaterialIdentity> observedMaterialIdentities(
		EnumMap<Direction, List<BakedQuad>> quadsByFace,
		BlockState state,
		BlockPos tintPosition
	) {
		LinkedHashSet<MaterialIdentity> observed = new LinkedHashSet<>();
		for (List<BakedQuad> quads : quadsByFace.values()) {
			for (BakedQuad quad : quads) {
				TextureAtlasSprite sprite = quad.sprite();
				int tintArgb = 0xffffffff;
				if (quad.isTinted() && tintPosition != null) {
					Minecraft minecraft = Minecraft.getInstance();
					tintArgb = 0xff000000 | minecraft.getBlockColors().getColor(
						state, minecraft.level, tintPosition, quad.tintIndex()
					);
				}
				observed.add(new MaterialIdentity(
					sprite.atlasLocation().toString(), sprite.contents().name().toString(),
					sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1(), quad.isTinted(), tintArgb
				));
			}
		}
		return observed;
	}

	/**
	 * Resolves a copied DH block-state identity on the client render thread.
	 * Several deterministic seeds must agree before a reduced quad is allowed
	 * to inherit a model face sprite; weighted variants are otherwise unsafe.
	 */
	static Resolution resolveCurrentClientState(String blockStateIdentity) {
		Objects.requireNonNull(blockStateIdentity, "blockStateIdentity");
		synchronized (CACHED_STATE_RESOLUTIONS) {
			Resolution cached = CACHED_STATE_RESOLUTIONS.get(blockStateIdentity);
			if (cached != null) {
				return cached;
			}
		}
		Resolution resolved = resolveCurrentClientStateUncached(blockStateIdentity);
		synchronized (CACHED_STATE_RESOLUTIONS) {
			Resolution cached = CACHED_STATE_RESOLUTIONS.get(blockStateIdentity);
			if (cached != null) {
				return cached;
			}
			CACHED_STATE_RESOLUTIONS.put(blockStateIdentity, resolved);
			if (CACHED_STATE_RESOLUTIONS.size() > MAX_CACHED_STATE_RESOLUTIONS) {
				CACHED_STATE_RESOLUTIONS.remove(CACHED_STATE_RESOLUTIONS.entrySet().iterator().next().getKey());
			}
		}
		return resolved;
	}

	private static Resolution resolveCurrentClientStateUncached(String blockStateIdentity) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null) {
			return unavailable();
		}
		ClientLevel level = minecraft.level;
		if (level == null) {
			return unavailable();
		}
		final BlockState state;
		try {
			IBlockStateWrapper wrapper = BlockStateWrapper.deserialize(
				blockStateIdentity, ClientLevelWrapper.getWrapper(level)
			);
			if (!(wrapper instanceof BlockStateWrapper stateWrapper) || stateWrapper.blockState == null) {
				return unavailable();
			}
			state = stateWrapper.blockState;
		} catch (Exception ignored) {
			return unavailable();
		}
		if (state.is(Blocks.WATER)) {
			return resolveWaterMaterial(null);
		}
		if (state.is(Blocks.LAVA)) {
			return resolveLavaMaterial(null);
		}
		Resolution first = resolve(minecraft.getBlockRenderer(), state, 0L);
		if (!first.hasResolvedFaces()) {
			return first;
		}
		for (long seed : new long[] {1L, 0x5eedL}) {
			Resolution candidate = resolve(minecraft.getBlockRenderer(), state, seed);
			if (!candidate.hasResolvedFaces() || !candidate.faceLayers().equals(first.faceLayers())) {
				return new Resolution(Status.VARIANT_DEPENDENT, new EnumMap<>(Direction.class));
			}
		}
		return first;
	}

	static void clearCachedStateResolutions() {
		synchronized (CACHED_STATE_RESOLUTIONS) {
			CACHED_STATE_RESOLUTIONS.clear();
		}
		synchronized (CACHED_VARIANT_RESOLUTIONS) {
			CACHED_VARIANT_RESOLUTIONS.clear();
		}
	}

	static int cachedStateResolutionCountForTest() {
		synchronized (CACHED_STATE_RESOLUTIONS) {
			return CACHED_STATE_RESOLUTIONS.size();
		}
	}

	static void cacheStateResolutionForTest(String blockStateIdentity, Resolution resolution) {
		Objects.requireNonNull(blockStateIdentity, "blockStateIdentity");
		Objects.requireNonNull(resolution, "resolution");
		synchronized (CACHED_STATE_RESOLUTIONS) {
			CACHED_STATE_RESOLUTIONS.put(blockStateIdentity, resolution);
			if (CACHED_STATE_RESOLUTIONS.size() > MAX_CACHED_STATE_RESOLUTIONS) {
				CACHED_STATE_RESOLUTIONS.remove(CACHED_STATE_RESOLUTIONS.entrySet().iterator().next().getKey());
			}
		}
	}

	/**
	 * Resolves one weighted model using the exact source block position that
	 * survived DH reduction. `BlockState#getSeed` carries vanilla's model
	 * selection semantics, including state-specific overrides; using the raw
	 * packed position as a random seed would not.
	 */
	static Resolution resolveCurrentClientState(String blockStateIdentity, long packedBlockPosition) {
		Objects.requireNonNull(blockStateIdentity, "blockStateIdentity");
		VariantResolutionKey cacheKey = new VariantResolutionKey(blockStateIdentity, packedBlockPosition);
		synchronized (CACHED_VARIANT_RESOLUTIONS) {
			Resolution cached = CACHED_VARIANT_RESOLUTIONS.get(cacheKey);
			if (cached != null) {
				return cached;
			}
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.level == null) {
			return unavailable();
		}
		final BlockState state;
		try {
			IBlockStateWrapper wrapper = BlockStateWrapper.deserialize(
				blockStateIdentity, ClientLevelWrapper.getWrapper(minecraft.level)
			);
			if (!(wrapper instanceof BlockStateWrapper stateWrapper) || stateWrapper.blockState == null) {
				return unavailable();
			}
			state = stateWrapper.blockState;
		} catch (Exception ignored) {
			return unavailable();
		}
		BlockPos position = BlockPos.of(packedBlockPosition);
		Resolution resolved = resolveAtPosition(minecraft.getBlockRenderer(), state, state.getSeed(position), position);
		synchronized (CACHED_VARIANT_RESOLUTIONS) {
			Resolution cached = CACHED_VARIANT_RESOLUTIONS.get(cacheKey);
			if (cached != null) {
				return cached;
			}
			CACHED_VARIANT_RESOLUTIONS.put(cacheKey, resolved);
			if (CACHED_VARIANT_RESOLUTIONS.size() > MAX_CACHED_VARIANT_RESOLUTIONS) {
				CACHED_VARIANT_RESOLUTIONS.remove(CACHED_VARIANT_RESOLUTIONS.entrySet().iterator().next().getKey());
			}
		}
		return resolved;
	}

	private record VariantResolutionKey(String blockStateIdentity, long packedBlockPosition) {
		private VariantResolutionKey {
			Objects.requireNonNull(blockStateIdentity, "blockStateIdentity");
		}
	}

	private static Resolution resolveAtPosition(BlockRenderDispatcher dispatcher, BlockState state, long modelSeed, BlockPos tintPosition) {
		if (state.is(Blocks.WATER)) {
			return resolveWaterMaterial(tintPosition);
		}
		if (state.is(Blocks.LAVA)) {
			return resolveLavaMaterial(tintPosition);
		}
		List<BlockModelPart> parts = dispatcher.getBlockModel(state).collectParts(RandomSource.create(modelSeed));
		EnumMap<Direction, List<BakedQuad>> quadsByFace = new EnumMap<>(Direction.class);
		for (Direction direction : Direction.values()) quadsByFace.put(direction, new ArrayList<>());
		Set<BakedQuad> unculledQuads = Collections.newSetFromMap(new IdentityHashMap<>());
		for (BlockModelPart part : parts) {
			for (Direction direction : Direction.values()) quadsByFace.get(direction).addAll(part.getQuads(direction));
			for (BakedQuad quad : part.getQuads(null)) {
				unculledQuads.add(quad);
				quadsByFace.get(quad.direction()).add(quad);
			}
		}
		LinkedHashSet<MaterialIdentity> observedMaterials = observedMaterialIdentities(quadsByFace, state, tintPosition);
		EnumMap<Direction, List<FaceMaterial>> faces = new EnumMap<>(Direction.class);
		Status unavailable = null;
		for (Direction direction : Direction.values()) {
			FaceResolution resolution = resolveFace(direction, quadsByFace.get(direction), unculledQuads, state, tintPosition);
			if (!resolution.materials().isEmpty()) faces.put(direction, resolution.materials());
			else if (resolution.status() != null && unavailable == null) unavailable = resolution.status();
		}
		if (faces.isEmpty()) {
			FaceMaterial fallback = resolveUniformPlanarFallback(quadsByFace, state, tintPosition, observedMaterials);
			if (fallback != null) {
				return uniformFaceResolution(fallback);
			}
			return new Resolution(unavailable == null ? Status.UNSUPPORTED_FACE_MAPPING : unavailable, faces);
		}
		return new Resolution(unavailable == null ? Status.COMPLETE : Status.PARTIAL_FACE_MAPPING, faces)
			.withUniformFaceCoverageIfSafe(observedMaterials);
	}

	/**
	 * Fluid blocks do not expose a normal baked block-model quad set. DH still
	 * emits their reduced water faces, so resolve the copied block-atlas water
	 * sprite explicitly instead of admitting a textureless water stream.
	 */
	private static Resolution resolveWaterMaterial(BlockPos tintPosition) {
		return resolveFluidMaterial("block/water_still", true, tintPosition);
	}

	/**
	 * Built-in lava has no block-model quad set, just like water. Keep it on an
	 * explicit copied atlas path so source DH lava quads do not become
	 * permanently textureless or borrow the fluid renderer. Lava's texture is
	 * authored with its own color, so it intentionally carries no biome tint.
	 */
	private static Resolution resolveLavaMaterial(BlockPos tintPosition) {
		return resolveFluidMaterial("block/lava_still", false, tintPosition);
	}

	private static Resolution resolveFluidMaterial(String spritePath, boolean tinted, BlockPos tintPosition) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null) return unavailable();
		TextureAtlasSprite sprite;
		try {
			sprite = minecraft.getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).getSprite(
					 net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft", spritePath));
		} catch (RuntimeException ignored) {
			return unavailable();
		}
		if (sprite == null || sprite.contents() == null) return unavailable();
		int tintArgb = 0xffffffff;
		if (tinted) {
			// The vanilla water sprite is grayscale. Its visible color comes from the
			// block-color resolver at the source position, so the exact-atlas path must
			// retain the same position-specific tint contract as baked grass/leaves.
			// When a DH opaque quad has no position key, use vanilla's documented
			// default water color rather than emitting a white grayscale sprite.
			tintArgb = 0xff3f76e4;
			if (tintPosition != null && minecraft.level != null) {
				tintArgb = 0xff000000 | minecraft.getBlockColors().getColor(
					Blocks.WATER.defaultBlockState(), minecraft.level, tintPosition, 0
				);
			}
		}
		if (isGraphicsAudit()) {
			synchronized (AUDIT_WATER_TINTS) {
				if (tinted && AUDIT_WATER_TINTS.size() < 16 && AUDIT_WATER_TINTS.add(tintArgb)) {
					System.out.println("[MattMC graphics audit] DH water tint "
						+ String.format("0x%08x", tintArgb)
						+ " position=" + (tintPosition == null ? "state" : tintPosition));
				}
			}
		}
		return copiedFluidResolution(
			sprite.atlasLocation().toString(), sprite.contents().name().toString(),
			sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1(), tinted, tintArgb
		);
	}

	/** Builds the bounded copied-fluid record after atlas objects have been read. */
	static Resolution copiedFluidResolution(
		String atlasIdentity, String spriteIdentity,
		float u0, float v0, float u1, float v1,
		boolean tinted, int tintArgb
	) {
		EnumMap<Direction, List<FaceMaterial>> faces = new EnumMap<>(Direction.class);
		FaceMaterial material = new FaceMaterial(
			atlasIdentity, spriteIdentity, u0, v0, u1, v1,
			FaceMaterial.CANONICAL_UV_CORNER_ORDER, 0, tinted, tintArgb
		);
		for (Direction direction : Direction.values()) faces.put(direction, List.of(material));
		return new Resolution(Status.COMPLETE, faces);
	}

	private static boolean isGraphicsAudit() {
		String value = System.getenv("MATTMC_GRAPHICS_AUDIT");
		return "1".equals(value) || "true".equalsIgnoreCase(value);
	}

	private static Resolution unavailable() {
		return new Resolution(Status.SOURCE_STATE_UNAVAILABLE, new EnumMap<>(Direction.class));
	}

	private static boolean validUvCornerOrder(int order) {
		if (order < 0 || order > 0xff) {
			return false;
		}
		int mask = 0;
		for (int index = 0; index < 4; index++) {
			int corner = order >>> (index * 2) & 0x3;
			int bit = 1 << corner;
			if ((mask & bit) != 0) {
				return false;
			}
			mask |= bit;
		}
		return mask == 0xf;
	}

	private static int canonicalUvCornerOrder(BakedQuadView quad, Direction face, TextureAtlasSprite sprite) {
		// Most vanilla cube faces use the unit-cube coordinates handled by the
		// fast path below. Slabs, stairs, and other baked models may occupy an
		// inset/expanded axis-aligned rectangle instead. Normalize those bounds
		// only when all four vertices are coplanar on the requested face and map
		// to four distinct rectangle corners; crossed or diagonal quads remain
		// deliberately unavailable rather than receiving guessed texture data.
		int order = 0;
		int seenCorners = 0;
		boolean fastPathValid = true;
		for (int sourceIndex = 0; sourceIndex < 4; sourceIndex++) {
			int canonicalIndex = canonicalFaceCorner(face, quad.getX(sourceIndex), quad.getY(sourceIndex), quad.getZ(sourceIndex));
			if (canonicalIndex < 0 || (seenCorners & 1 << canonicalIndex) != 0) {
				fastPathValid = false;
				break;
			}
			int spriteCorner = spriteCorner(sprite, quad.getTexU(sourceIndex), quad.getTexV(sourceIndex));
			if (spriteCorner < 0) {
				fastPathValid = false;
				break;
			}
			order |= spriteCorner << (canonicalIndex * 2);
			seenCorners |= 1 << canonicalIndex;
		}
		if (fastPathValid && seenCorners == 0xf && validUvCornerOrder(order)) return order;

		return boundedPlanarUvCornerOrder(quad, face, sprite);
	}

	private static int boundedPlanarUvCornerOrder(BakedQuadView quad, Direction face, TextureAtlasSprite sprite) {
		float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
		float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
		float minU = Float.POSITIVE_INFINITY, minV = Float.POSITIVE_INFINITY;
		float maxU = Float.NEGATIVE_INFINITY, maxV = Float.NEGATIVE_INFINITY;
		for (int index = 0; index < 4; index++) {
			float x = quad.getX(index), y = quad.getY(index), z = quad.getZ(index);
			float u = quad.getTexU(index), v = quad.getTexV(index);
			if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
				|| !Float.isFinite(u) || !Float.isFinite(v)) return -1;
			float uTolerance = Math.max(0.0001F, Math.abs(sprite.getU1() - sprite.getU0()) * 0.001F);
			float vTolerance = Math.max(0.0001F, Math.abs(sprite.getV1() - sprite.getV0()) * 0.001F);
			// A source model may address a bounded portion of a sprite (stairs and
			// doors do this) while retaining an unambiguous sprite identity. Keep the
			// identity, but never accept UVs that leave that sprite's atlas rectangle.
			if (u < sprite.getU0() - uTolerance || u > sprite.getU1() + uTolerance
				|| v < sprite.getV0() - vTolerance || v > sprite.getV1() + vTolerance) return -1;
			minX = Math.min(minX, x); maxX = Math.max(maxX, x);
			minY = Math.min(minY, y); maxY = Math.max(maxY, y);
			minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
			minU = Math.min(minU, u); maxU = Math.max(maxU, u);
			minV = Math.min(minV, v); maxV = Math.max(maxV, v);
		}
		float normalMin = switch (face) {
			case DOWN, UP -> minY;
			case NORTH, SOUTH -> minZ;
			case WEST, EAST -> minX;
		};
		float normalMax = switch (face) {
			case DOWN, UP -> maxY;
			case NORTH, SOUTH -> maxZ;
			case WEST, EAST -> maxX;
		};
		// A planar face has no extent along its normal. Do not infer one from a
		// slanted or non-planar quad.
		if (Math.abs(normalMax - normalMin) > 0.0001F) return -1;
		int order = 0;
		int seenCorners = 0;
		float uvWidth = maxU - minU;
		float uvHeight = maxV - minV;
		if (!(uvWidth > 0.0001F) || !(uvHeight > 0.0001F)) return -1;
		for (int sourceIndex = 0; sourceIndex < 4; sourceIndex++) {
			float x = quad.getX(sourceIndex), y = quad.getY(sourceIndex), z = quad.getZ(sourceIndex);
			int xBit = boundedBit(x, minX, maxX), yBit = boundedBit(y, minY, maxY), zBit = boundedBit(z, minZ, maxZ);
			if (xBit < 0 || yBit < 0 || zBit < 0) return -1;
			int canonicalIndex = switch (face) {
				case DOWN -> cornerIndex(xBit, zBit);
				case UP -> cornerIndex(1 - xBit, zBit);
				case NORTH -> cornerIndex(xBit, yBit);
				case SOUTH -> cornerIndex(1 - xBit, yBit);
				case WEST -> cornerIndex(zBit, yBit);
				case EAST -> cornerIndex(zBit, 1 - yBit);
			};
			if ((seenCorners & 1 << canonicalIndex) != 0) return -1;
			// Prefer the atlas-edge classification for ordinary cube quads. If the
			// source uses an inset UV rectangle, classify its own low/high bounds;
			// this preserves rotation/mirroring without stretching a neighboring atlas
			// sprite or inventing a material identity.
			int spriteCorner = spriteCorner(sprite, quad.getTexU(sourceIndex), quad.getTexV(sourceIndex));
			if (spriteCorner < 0) {
				int uBit = boundedBit(quad.getTexU(sourceIndex), minU, maxU);
				int vBit = boundedBit(quad.getTexV(sourceIndex), minV, maxV);
				if (uBit < 0 || vBit < 0) return -1;
				spriteCorner = uBit | vBit << 1;
			}
			order |= spriteCorner << (canonicalIndex * 2);
			seenCorners |= 1 << canonicalIndex;
		}
		return seenCorners == 0xf && validUvCornerOrder(order) ? order : -1;
	}

	private static int boundedBit(float value, float minimum, float maximum) {
		float tolerance = Math.max(0.0001F, Math.abs(maximum - minimum) * 0.001F);
		if (Math.abs(value - minimum) <= tolerance) return 0;
		if (Math.abs(value - maximum) <= tolerance) return 1;
		return -1;
	}

	private static int canonicalFaceCorner(Direction face, float x, float y, float z) {
		int xBit = unitBit(x);
		int yBit = unitBit(y);
		int zBit = unitBit(z);
		if (xBit < 0 || yBit < 0 || zBit < 0) {
			return -1;
		}
		return switch (face) {
			case DOWN -> yBit == 0 ? cornerIndex(xBit, zBit) : -1;
			case UP -> yBit == 1 ? cornerIndex(1 - xBit, zBit) : -1;
			case NORTH -> zBit == 0 ? cornerIndex(xBit, yBit) : -1;
			case SOUTH -> zBit == 1 ? cornerIndex(1 - xBit, yBit) : -1;
			case WEST -> xBit == 0 ? cornerIndex(zBit, yBit) : -1;
			case EAST -> xBit == 1 ? cornerIndex(zBit, 1 - yBit) : -1;
		};
	}

	private static int unitBit(float value) {
		if (Math.abs(value) <= 0.0001F) return 0;
		if (Math.abs(value - 1.0F) <= 0.0001F) return 1;
		return -1;
	}

	private static int cornerIndex(int uBit, int vBit) {
		return switch ((uBit << 1) | vBit) {
			case 0 -> 0;
			case 1 -> 1;
			case 3 -> 2;
			case 2 -> 3;
			default -> throw new IllegalArgumentException("invalid canonical face corner");
		};
	}

	private static int spriteCorner(TextureAtlasSprite sprite, float u, float v) {
		int uBit = unitIntervalBit(u, sprite.getU0(), sprite.getU1());
		int vBit = unitIntervalBit(v, sprite.getV0(), sprite.getV1());
		return uBit < 0 || vBit < 0 ? -1 : uBit | vBit << 1;
	}

	private static int unitIntervalBit(float value, float minimum, float maximum) {
		float tolerance = Math.max(0.0001F, Math.abs(maximum - minimum) * 0.001F);
		if (Math.abs(value - minimum) <= tolerance) return 0;
		if (Math.abs(value - maximum) <= tolerance) return 1;
		return -1;
	}
}
