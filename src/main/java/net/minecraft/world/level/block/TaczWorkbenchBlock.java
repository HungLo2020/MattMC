package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.TaczWorkbenchMenu;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public abstract class TaczWorkbenchBlock extends Block {
	public static final MapCodec<GunSmithTable> GUN_SMITH_TABLE_CODEC = simpleCodec(GunSmithTable::new);
	public static final MapCodec<AmmoWorkbench> AMMO_WORKBENCH_CODEC = simpleCodec(AmmoWorkbench::new);
	public static final MapCodec<AttachmentWorkbench> ATTACHMENT_WORKBENCH_CODEC = simpleCodec(AttachmentWorkbench::new);
	public static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 14.0, 16.0);
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
	private final Component title;

	protected TaczWorkbenchBlock(Component title, BlockBehaviour.Properties properties) {
		super(properties);
		this.title = title;
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (!level.isClientSide()) {
			player.openMenu(state.getMenuProvider(level, pos));
		}

		return InteractionResult.SUCCESS;
	}

	@Nullable
	@Override
	protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
		return new SimpleMenuProvider((containerId, inventory, player) -> this.createMenu(containerId, inventory, ContainerLevelAccess.create(level, pos)), this.title);
	}

	protected abstract TaczWorkbenchMenu createMenu(int containerId, net.minecraft.world.entity.player.Inventory inventory, ContainerLevelAccess access);

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter getter, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean useShapeForLightOcclusion(BlockState state) {
		return false;
	}

	@Override
	protected VoxelShape getOcclusionShape(BlockState state) {
		return Shapes.empty();
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	public static final class GunSmithTable extends TaczWorkbenchBlock {
		public GunSmithTable(BlockBehaviour.Properties properties) {
			super(Component.translatable("container.tacz.gun_smith_table"), properties);
		}

		@Override
		public MapCodec<GunSmithTable> codec() {
			return GUN_SMITH_TABLE_CODEC;
		}

		@Override
		protected TaczWorkbenchMenu createMenu(int containerId, net.minecraft.world.entity.player.Inventory inventory, ContainerLevelAccess access) {
			return TaczWorkbenchMenu.gunSmithTable(containerId, inventory, access);
		}
	}

	public static final class AmmoWorkbench extends TaczWorkbenchBlock {
		public AmmoWorkbench(BlockBehaviour.Properties properties) {
			super(Component.translatable("container.tacz.ammo_workbench"), properties);
		}

		@Override
		public MapCodec<AmmoWorkbench> codec() {
			return AMMO_WORKBENCH_CODEC;
		}

		@Override
		protected TaczWorkbenchMenu createMenu(int containerId, net.minecraft.world.entity.player.Inventory inventory, ContainerLevelAccess access) {
			return TaczWorkbenchMenu.ammoWorkbench(containerId, inventory, access);
		}
	}

	public static final class AttachmentWorkbench extends TaczWorkbenchBlock {
		public AttachmentWorkbench(BlockBehaviour.Properties properties) {
			super(Component.translatable("container.tacz.attachment_workbench"), properties);
		}

		@Override
		public MapCodec<AttachmentWorkbench> codec() {
			return ATTACHMENT_WORKBENCH_CODEC;
		}

		@Override
		protected TaczWorkbenchMenu createMenu(int containerId, net.minecraft.world.entity.player.Inventory inventory, ContainerLevelAccess access) {
			return TaczWorkbenchMenu.attachmentWorkbench(containerId, inventory, access);
		}
	}
}
