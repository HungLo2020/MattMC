package net.minecraft.world.level.block;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.Optional;
import net.minecraft.BlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class PrimordialCavesPortalBlock extends Block implements Portal {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final MapCodec<PrimordialCavesPortalBlock> CODEC = simpleCodec(PrimordialCavesPortalBlock::new);
	public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
	private static final Map<Direction.Axis, VoxelShape> SHAPES = Shapes.rotateHorizontalAxis(Block.column(4.0, 16.0, 0.0, 16.0));

	@Override
	public MapCodec<PrimordialCavesPortalBlock> codec() {
		return CODEC;
	}

	public PrimordialCavesPortalBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(AXIS, Direction.Axis.X));
	}

	@Override
	protected VoxelShape getShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext) {
		return (VoxelShape)SHAPES.get(blockState.getValue(AXIS));
	}

	@Override
	protected BlockState updateShape(
		BlockState blockState,
		LevelReader levelReader,
		ScheduledTickAccess scheduledTickAccess,
		BlockPos blockPos,
		Direction direction,
		BlockPos blockPos2,
		BlockState blockState2,
		RandomSource randomSource
	) {
		Direction.Axis axis = direction.getAxis();
		Direction.Axis axis2 = blockState.getValue(AXIS);
		boolean bl = axis2 != axis && axis.isHorizontal();
		// Use the same logic as NetherPortalBlock - check if portal shape is still complete
		return !bl && !blockState2.is(this) && !PortalShape.findAnyShape(levelReader, blockPos, axis2).isComplete()
			? Blocks.AIR.defaultBlockState()
			: super.updateShape(blockState, levelReader, scheduledTickAccess, blockPos, direction, blockPos2, blockState2, randomSource);
	}

	@Override
	protected void entityInside(
		BlockState blockState, Level level, BlockPos blockPos, Entity entity, InsideBlockEffectApplier insideBlockEffectApplier, boolean bl
	) {
		if (entity.canUsePortal(false)) {
			entity.setAsInsidePortal(this, blockPos);
		}
	}

	@Override
	public int getPortalTransitionTime(ServerLevel serverLevel, Entity entity) {
		return entity instanceof Player player
			? Math.max(
				0,
				serverLevel.getGameRules()
					.getInt(player.getAbilities().invulnerable ? GameRules.RULE_PLAYERS_NETHER_PORTAL_CREATIVE_DELAY : GameRules.RULE_PLAYERS_NETHER_PORTAL_DEFAULT_DELAY)
			)
			: 0;
	}

	@Nullable
	@Override
	public TeleportTransition getPortalDestination(ServerLevel serverLevel, Entity entity, BlockPos blockPos) {
		// Primordial Caves portal always goes to Primordial Caves or back to Overworld
		ResourceKey<Level> resourceKey = serverLevel.dimension() == Level.PRIMORDIAL_CAVES ? Level.OVERWORLD : Level.PRIMORDIAL_CAVES;
		ServerLevel serverLevel2 = serverLevel.getServer().getLevel(resourceKey);
		if (serverLevel2 == null) {
			LOGGER.error("Failed to get dimension {} for portal teleportation. Dimension may not be loaded.", resourceKey.location());
			return null;
		} else {
			WorldBorder worldBorder = serverLevel2.getWorldBorder();
			double d = DimensionType.getTeleportationScale(serverLevel.dimensionType(), serverLevel2.dimensionType());
			BlockPos blockPos2 = worldBorder.clampToBounds(entity.getX() * d, entity.getY(), entity.getZ() * d);
			return this.getExitPortal(serverLevel2, entity, blockPos, blockPos2, worldBorder);
		}
	}

	@Nullable
	private TeleportTransition getExitPortal(ServerLevel serverLevel, Entity entity, BlockPos blockPos, BlockPos blockPos2, WorldBorder worldBorder) {
		// Look for existing primordial caves portal
		Optional<BlockPos> optional = this.findPrimordialCavesPortal(serverLevel, blockPos2, worldBorder);
		BlockUtil.FoundRectangle foundRectangle;
		TeleportTransition.PostTeleportTransition postTeleportTransition;
		if (optional.isPresent()) {
			BlockPos blockPos3 = (BlockPos)optional.get();
			BlockState blockState = serverLevel.getBlockState(blockPos3);
			foundRectangle = BlockUtil.getLargestRectangleAround(
				blockPos3,
				blockState.getValue(BlockStateProperties.HORIZONTAL_AXIS),
				21,
				Direction.Axis.Y,
				21,
				blockPosx -> serverLevel.getBlockState(blockPosx).is(this)
			);
			postTeleportTransition = TeleportTransition.PLAY_PORTAL_SOUND.then(entityx -> entityx.placePortalTicket(blockPos3));
		} else {
			// Create new portal at destination
			Direction.Axis axis = (Direction.Axis)entity.level().getBlockState(blockPos).getOptionalValue(AXIS).orElse(Direction.Axis.X);
			Optional<BlockUtil.FoundRectangle> optional2 = this.createPrimordialCavesPortal(serverLevel, blockPos2, axis);
			if (optional2.isEmpty()) {
				LOGGER.error("Unable to create a portal, likely target out of worldborder");
				return null;
			}

			foundRectangle = (BlockUtil.FoundRectangle)optional2.get();
			postTeleportTransition = TeleportTransition.PLAY_PORTAL_SOUND.then(TeleportTransition.PLACE_PORTAL_TICKET);
		}

		return getDimensionTransitionFromExit(entity, blockPos, foundRectangle, serverLevel, postTeleportTransition);
	}
	
	private Optional<BlockPos> findPrimordialCavesPortal(ServerLevel serverLevel, BlockPos blockPos, WorldBorder worldBorder) {
		// Search for existing primordial caves portal POI
		PoiManager poiManager = serverLevel.getPoiManager();
		int searchRadius = 128; // Same as overworld portal search radius
		poiManager.ensureLoadedAndValid(serverLevel, blockPos, searchRadius);
		return poiManager.getInSquare(holder -> holder.is(PoiTypes.PRIMORDIAL_CAVES_PORTAL), blockPos, searchRadius, PoiManager.Occupancy.ANY)
			.map(PoiRecord::getPos)
			.filter(worldBorder::isWithinBounds)
			.filter(blockPosx -> serverLevel.getBlockState(blockPosx).hasProperty(BlockStateProperties.HORIZONTAL_AXIS))
			.min(java.util.Comparator.comparingDouble((BlockPos bp2) -> bp2.distSqr(blockPos)).thenComparingInt(bp2 -> bp2.getY()));
	}
	
	private Optional<BlockUtil.FoundRectangle> createPrimordialCavesPortal(ServerLevel serverLevel, BlockPos blockPos, Direction.Axis axis) {
		// Use the standard portal creation logic but with our portal block
		Direction direction = Direction.get(Direction.AxisDirection.POSITIVE, axis);
		double d = -1.0;
		BlockPos blockPos2 = null;
		double e = -1.0;
		BlockPos blockPos3 = null;
		WorldBorder worldBorder = serverLevel.getWorldBorder();
		int i = Math.min(serverLevel.getMaxY(), serverLevel.getMinY() + serverLevel.getLogicalHeight() - 1);
		BlockPos.MutableBlockPos mutableBlockPos = blockPos.mutable();

		for (BlockPos.MutableBlockPos mutableBlockPos2 : BlockPos.spiralAround(blockPos, 16, Direction.EAST, Direction.SOUTH)) {
			int k = Math.min(i, serverLevel.getHeight(Heightmap.Types.MOTION_BLOCKING, mutableBlockPos2.getX(), mutableBlockPos2.getZ()));
			if (worldBorder.isWithinBounds(mutableBlockPos2) && worldBorder.isWithinBounds(mutableBlockPos2.move(direction, 1))) {
				mutableBlockPos2.move(direction.getOpposite(), 1);

				for (int l = k; l >= serverLevel.getMinY(); l--) {
					mutableBlockPos2.setY(l);
					if (this.canPortalReplaceBlock(mutableBlockPos2, serverLevel)) {
						int m = l;

						while (l > serverLevel.getMinY() && this.canPortalReplaceBlock(mutableBlockPos2.move(Direction.DOWN), serverLevel)) {
							l--;
						}

						if (l + 4 <= i) {
							int n = m - l;
							if (n <= 0 || n >= 3) {
								mutableBlockPos2.setY(l);
								if (this.canCreatePortalAt(serverLevel, mutableBlockPos2, mutableBlockPos, direction, n)) {
									double f = blockPos.distSqr(mutableBlockPos2);
									if (this.canCreatePortalAt(serverLevel, mutableBlockPos2, mutableBlockPos, direction, n)
										&& (d == -1.0 || d > f)) {
										d = f;
										blockPos2 = mutableBlockPos2.immutable();
									}

									if (d == -1.0 && (e == -1.0 || e > f)) {
										e = f;
										blockPos3 = mutableBlockPos2.immutable();
									}
								}
							}
						}
					}
				}
			}
		}

		if (d == -1.0 && e != -1.0) {
			blockPos2 = blockPos3;
			d = e;
		}

		if (d == -1.0) {
			blockPos2 = (new BlockPos(blockPos.getX(), Mth.clamp(blockPos.getY(), 70, serverLevel.getMaxY() - 10), blockPos.getZ())).immutable();
			Direction direction2 = direction.getClockWise();
			if (!worldBorder.isWithinBounds(blockPos2)) {
				return Optional.empty();
			}

			for (int o = -1; o < 2; o++) {
				for (int p = 0; p < 2; p++) {
					for (int q = -1; q < 3; q++) {
						BlockState blockState = q < 0 ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.AIR.defaultBlockState();
						mutableBlockPos.setWithOffset(blockPos2, p * direction.getStepX() + o * direction2.getStepX(), q, p * direction.getStepZ() + o * direction2.getStepZ());
						serverLevel.setBlockAndUpdate(mutableBlockPos, blockState);
					}
				}
			}
		}

		for (int o = -1; o < 3; o++) {
			for (int p = -1; p < 4; p++) {
				if (o == -1 || o == 2 || p == -1 || p == 3) {
					mutableBlockPos.setWithOffset(blockPos2, o * direction.getStepX(), p, o * direction.getStepZ());
					serverLevel.setBlock(mutableBlockPos, Blocks.OBSIDIAN.defaultBlockState(), 3);
				}
			}
		}

		BlockState blockState = this.defaultBlockState().setValue(AXIS, axis);

		for (int o = 0; o < 2; o++) {
			for (int p = 0; p < 3; p++) {
				mutableBlockPos.setWithOffset(blockPos2, o * direction.getStepX(), p, o * direction.getStepZ());
				serverLevel.setBlock(mutableBlockPos, blockState, 18);
			}
		}

		return Optional.of(new BlockUtil.FoundRectangle(blockPos2.immutable(), 2, 3));
	}
	
	private boolean canPortalReplaceBlock(BlockPos blockPos, ServerLevel serverLevel) {
		BlockState blockState = serverLevel.getBlockState(blockPos);
		return blockState.canBeReplaced() || blockState.is(Blocks.AIR) || blockState.is(Blocks.WATER) || blockState.is(Blocks.LAVA);
	}
	
	private boolean canCreatePortalAt(ServerLevel serverLevel, BlockPos blockPos, BlockPos.MutableBlockPos mutableBlockPos, Direction direction, int i) {
		Direction direction2 = direction.getClockWise();

		for (int j = -1; j < 3; j++) {
			for (int k = -1; k < 4; k++) {
				mutableBlockPos.setWithOffset(blockPos, j * direction.getStepX() + i * direction2.getStepX(), k, j * direction.getStepZ() + i * direction2.getStepZ());
				if (k < 0 && !serverLevel.getBlockState(mutableBlockPos).isSolid()) {
					return false;
				}

				if (k >= 0 && !this.canPortalReplaceBlock(mutableBlockPos, serverLevel)) {
					return false;
				}
			}
		}

		return true;
	}

	private static TeleportTransition getDimensionTransitionFromExit(
		Entity entity,
		BlockPos blockPos,
		BlockUtil.FoundRectangle foundRectangle,
		ServerLevel serverLevel,
		TeleportTransition.PostTeleportTransition postTeleportTransition
	) {
		BlockState blockState = entity.level().getBlockState(blockPos);
		Direction.Axis axis;
		Vec3 vec3;
		if (blockState.hasProperty(BlockStateProperties.HORIZONTAL_AXIS)) {
			axis = blockState.getValue(BlockStateProperties.HORIZONTAL_AXIS);
			BlockUtil.FoundRectangle foundRectangle2 = BlockUtil.getLargestRectangleAround(
				blockPos, axis, 21, Direction.Axis.Y, 21, blockPosx -> entity.level().getBlockState(blockPosx) == blockState
			);
			vec3 = entity.getRelativePortalPosition(axis, foundRectangle2);
		} else {
			axis = Direction.Axis.X;
			vec3 = new Vec3(0.5, 0.0, 0.0);
		}

		return createDimensionTransition(serverLevel, foundRectangle, axis, vec3, entity, postTeleportTransition);
	}

	private static TeleportTransition createDimensionTransition(
		ServerLevel serverLevel,
		BlockUtil.FoundRectangle foundRectangle,
		Direction.Axis axis,
		Vec3 vec3,
		Entity entity,
		TeleportTransition.PostTeleportTransition postTeleportTransition
	) {
		BlockPos blockPos = foundRectangle.minCorner;
		BlockState blockState = serverLevel.getBlockState(blockPos);
		Direction.Axis axis2 = (Direction.Axis)blockState.getOptionalValue(BlockStateProperties.HORIZONTAL_AXIS).orElse(Direction.Axis.X);
		double d = foundRectangle.axis1Size;
		double e = foundRectangle.axis2Size;
		EntityDimensions entityDimensions = entity.getDimensions(entity.getPose());
		int i = axis == Direction.Axis.X ? Mth.floor(d - (double)entityDimensions.width()) / 2 : Mth.floor(d - (double)entityDimensions.width() / 2.0) / 2;
		int j = Mth.floor(e - (double)entityDimensions.height()) / 2;
		BlockPos blockPos2 = blockPos.relative(Direction.get(Direction.AxisDirection.POSITIVE, axis2), i);
		BlockPos blockPos3 = blockPos2.relative(Direction.UP, j);
		Vec3 vec32 = new Vec3((double)blockPos3.getX() + vec3.x, (double)blockPos3.getY() + vec3.y, (double)blockPos3.getZ() + vec3.z);
		Vec3 vec33 = PortalShape.findCollisionFreePosition(vec32, serverLevel, entity, entityDimensions);
		return new TeleportTransition(serverLevel, vec33, Vec3.ZERO, 0.0F, 0.0F, Relative.union(Relative.DELTA, Relative.ROTATION), postTeleportTransition);
	}

	public void animateTick(BlockState blockState, Level level, BlockPos blockPos, RandomSource randomSource) {
		if (randomSource.nextInt(100) == 0) {
			level.playLocalSound(
				(double)blockPos.getX() + 0.5,
				(double)blockPos.getY() + 0.5,
				(double)blockPos.getZ() + 0.5,
				SoundEvents.PORTAL_AMBIENT,
				SoundSource.BLOCKS,
				0.5F,
				randomSource.nextFloat() * 0.4F + 0.8F,
				false
			);
		}

		// Different particle color for Primordial Caves portal - green/earthy tone
		for (int i = 0; i < 4; i++) {
			double d = (double)blockPos.getX() + randomSource.nextDouble();
			double e = (double)blockPos.getY() + randomSource.nextDouble();
			double f = (double)blockPos.getZ() + randomSource.nextDouble();
			double g = ((double)randomSource.nextFloat() - 0.5) * 0.5;
			double h = ((double)randomSource.nextFloat() - 0.5) * 0.5;
			double j = ((double)randomSource.nextFloat() - 0.5) * 0.5;
			int k = randomSource.nextInt(2) * 2 - 1;
			if (!level.getBlockState(blockPos.west()).is(this) && !level.getBlockState(blockPos.east()).is(this)) {
				d = (double)blockPos.getX() + 0.5 + 0.25 * (double)k;
				g = randomSource.nextFloat() * 2.0F * (float)k;
			} else {
				f = (double)blockPos.getZ() + 0.5 + 0.25 * (double)k;
				j = randomSource.nextFloat() * 2.0F * (float)k;
			}

			// Use a different particle type or color for visual distinction
			level.addParticle(ParticleTypes.PORTAL, d, e, f, g, h, j);
		}
	}

	public ItemStack getCloneItemStack(LevelReader levelReader, BlockPos blockPos, BlockState blockState) {
		return ItemStack.EMPTY;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AXIS);
	}
}
