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
import net.sodium.client.model.quad.BakedQuadView;
import com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper;
import com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;

import java.util.EnumMap;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Converts an already selected Minecraft model into a small, copied face
 * material table for a future Rust-owned Distant Horizons texture pass.
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
		for (BlockModelPart part : parts) {
			for (Direction direction : Direction.values()) {
				quadsByFace.get(direction).addAll(part.getQuads(direction));
			}
			for (BakedQuad quad : part.getQuads(null)) {
				// Unculled Minecraft quads still have a semantic outward normal.
				// They are safe for the matching reduced DH face when their material
				// is unambiguous; they must not poison unrelated faces.
				quadsByFace.get(quad.direction()).add(quad);
			}
		}
		EnumMap<Direction, List<FaceMaterial>> faces = new EnumMap<>(Direction.class);
		Status firstUnavailable = null;
		for (Direction direction : Direction.values()) {
			FaceResolution faceResolution = resolveFace(direction, quadsByFace.get(direction), state, null);
			if (!faceResolution.materials().isEmpty()) {
				faces.put(direction, faceResolution.materials());
			} else if (faceResolution.status() != null && firstUnavailable == null) {
				firstUnavailable = faceResolution.status();
			}
		}
		if (faces.isEmpty()) {
			return new Resolution(firstUnavailable == null ? Status.UNSUPPORTED_FACE_MAPPING : firstUnavailable, faces);
		}
		return new Resolution(firstUnavailable == null ? Status.COMPLETE : Status.PARTIAL_FACE_MAPPING, faces);
	}

	private static FaceResolution resolveFace(Direction direction, List<BakedQuad> quads, BlockState state, BlockPos tintPosition) {
		if (quads == null || quads.isEmpty()) {
			return new FaceResolution(List.of(), null);
		}
		LinkedHashSet<FaceMaterial> materials = new LinkedHashSet<>();
		for (BakedQuad quad : quads) {
			TextureAtlasSprite sprite = quad.sprite();
			int uvCornerOrder = canonicalUvCornerOrder((BakedQuadView)(Object)quad, direction, sprite);
			if (uvCornerOrder < 0) {
				return new FaceResolution(List.of(), Status.UNSUPPORTED_FACE_MAPPING);
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
		return new FaceResolution(numberedLayers(materials), null);
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

	/**
	 * Resolves a copied DH block-state identity on the client render thread.
	 * Several deterministic seeds must agree before a reduced quad is allowed
	 * to inherit a model face sprite; weighted variants are otherwise unsafe.
	 */
	static Resolution resolveCurrentClientState(String blockStateIdentity) {
		Objects.requireNonNull(blockStateIdentity, "blockStateIdentity");
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

	/**
	 * Resolves one weighted model using the exact source block position that
	 * survived DH reduction. `BlockState#getSeed` carries vanilla's model
	 * selection semantics, including state-specific overrides; using the raw
	 * packed position as a random seed would not.
	 */
	static Resolution resolveCurrentClientState(String blockStateIdentity, long packedBlockPosition) {
		Objects.requireNonNull(blockStateIdentity, "blockStateIdentity");
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
		return resolveAtPosition(minecraft.getBlockRenderer(), state, state.getSeed(position), position);
	}

	private static Resolution resolveAtPosition(BlockRenderDispatcher dispatcher, BlockState state, long modelSeed, BlockPos tintPosition) {
		List<BlockModelPart> parts = dispatcher.getBlockModel(state).collectParts(RandomSource.create(modelSeed));
		EnumMap<Direction, List<BakedQuad>> quadsByFace = new EnumMap<>(Direction.class);
		for (Direction direction : Direction.values()) quadsByFace.put(direction, new ArrayList<>());
		for (BlockModelPart part : parts) {
			for (Direction direction : Direction.values()) quadsByFace.get(direction).addAll(part.getQuads(direction));
			for (BakedQuad quad : part.getQuads(null)) quadsByFace.get(quad.direction()).add(quad);
		}
		EnumMap<Direction, List<FaceMaterial>> faces = new EnumMap<>(Direction.class);
		Status unavailable = null;
		for (Direction direction : Direction.values()) {
			FaceResolution resolution = resolveFace(direction, quadsByFace.get(direction), state, tintPosition);
			if (!resolution.materials().isEmpty()) faces.put(direction, resolution.materials());
			else if (resolution.status() != null && unavailable == null) unavailable = resolution.status();
		}
		if (faces.isEmpty()) {
			return new Resolution(unavailable == null ? Status.UNSUPPORTED_FACE_MAPPING : unavailable, faces);
		}
		return new Resolution(unavailable == null ? Status.COMPLETE : Status.PARTIAL_FACE_MAPPING, faces);
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
		for (int index = 0; index < 4; index++) {
			float x = quad.getX(index), y = quad.getY(index), z = quad.getZ(index);
			if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) return -1;
			minX = Math.min(minX, x); maxX = Math.max(maxX, x);
			minY = Math.min(minY, y); maxY = Math.max(maxY, y);
			minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
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
			int spriteCorner = spriteCorner(sprite, quad.getTexU(sourceIndex), quad.getTexV(sourceIndex));
			if (spriteCorner < 0) return -1;
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
