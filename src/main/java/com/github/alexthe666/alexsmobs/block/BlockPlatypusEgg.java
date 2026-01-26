package com.github.alexthe666.alexsmobs.block;

import com.github.alexthe666.alexsmobs.entity.EntityPlatypus;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Supplier;

public class BlockPlatypusEgg extends Block {
    public static final IntegerProperty HATCH = BlockStateProperties.HATCH;
    public static final IntegerProperty EGGS = BlockStateProperties.EGGS;
    private static final VoxelShape ONE_EGG_SHAPE = Block.box(3.0D, 0.0D, 3.0D, 12.0D, 7.0D, 12.0D);
    private static final VoxelShape MULTI_EGG_SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 7.0D, 15.0D);
    
    public BlockPlatypusEgg(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(HATCH, Integer.valueOf(0)).setValue(EGGS, Integer.valueOf(1)));
    }
    
    // Use EntityType.PLATYPUS directly instead of a supplier
    private static Supplier<EntityType<?>> getBirths() {
        return () -> EntityType.PLATYPUS;
    }
    
    public static boolean hasProperHabitat(BlockGetter reader, BlockPos blockReader) {
        return isProperHabitat(reader, blockReader.below());
    }

    public static boolean isProperHabitat(BlockGetter reader, BlockPos pos) {
        return reader.getBlockState(pos).is(BlockTags.SAND) || reader.getBlockState(pos).is(BlockTags.DIRT);
    }

    public void stepOn(Level worldIn, BlockPos pos, BlockState state, Entity entityIn) {
        this.tryTrample(worldIn, pos, entityIn, 100);
        super.stepOn(worldIn, pos, state, entityIn);
    }

    public void fallOn(Level worldIn, BlockState state, BlockPos pos, Entity entityIn, float fallDistance) {
        if (!(entityIn instanceof Zombie)) {
            this.tryTrample(worldIn, pos, entityIn, 3);
        }
        super.fallOn(worldIn, state, pos, entityIn, fallDistance);
    }

    private void tryTrample(Level worldIn, BlockPos pos, Entity trampler, int chances) {
        if (this.canTrample(worldIn, trampler)) {
            if (!worldIn.isClientSide() && worldIn.random.nextInt(chances) == 0) {
                AABB bb = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1).inflate(25, 25, 25);
                if (trampler instanceof LivingEntity) {
                    List<Mob> list = worldIn.getEntitiesOfClass(Mob.class, bb, living -> living.isAlive() && living.getType() == EntityType.PLATYPUS);
                    for (Mob living : list) {
                        if (!(living instanceof TamableAnimal) || !((TamableAnimal)living).isTame() || !((TamableAnimal)living).isOwnedBy((LivingEntity) trampler)) {
                            living.setTarget((LivingEntity) trampler);
                        }
                    }
                }
                BlockState blockstate = worldIn.getBlockState(pos);
                this.removeOneEgg(worldIn, pos, blockstate);
            }
        }
    }

    private void removeOneEgg(Level worldIn, BlockPos pos, BlockState state) {
        worldIn.playSound(null, pos, SoundEvents.TURTLE_EGG_BREAK, SoundSource.BLOCKS, 0.7F, 0.9F + worldIn.random.nextFloat() * 0.2F);
        int i = state.getValue(EGGS);
        if (i <= 1) {
            worldIn.destroyBlock(pos, false);
        } else {
            worldIn.setBlock(pos, state.setValue(EGGS, Integer.valueOf(i - 1)), 2);
            worldIn.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(state));
            worldIn.levelEvent(2001, pos, Block.getId(state));
        }
    }

    private boolean canTrample(Level world, Entity trampler) {
        if (!(trampler instanceof EntityPlatypus)) {
            return true;
        } else {
            return false;
        }
    }

    public void randomTick(BlockState state, ServerLevel worldIn, BlockPos pos, RandomSource random) {
        if (!hasProperHabitat(worldIn, pos)) {
            worldIn.destroyBlock(pos, false);
        } else {
            int i = state.getValue(HATCH);
            if (i < 2) {
                worldIn.playSound(null, pos, SoundEvents.TURTLE_EGG_CRACK, SoundSource.BLOCKS, 0.7F, 0.9F + random.nextFloat() * 0.2F);
                worldIn.setBlock(pos, state.setValue(HATCH, Integer.valueOf(i + 1)), 2);
            } else {
                worldIn.playSound(null, pos, SoundEvents.TURTLE_EGG_HATCH, SoundSource.BLOCKS, 0.7F, 0.9F + random.nextFloat() * 0.2F);
                worldIn.removeBlock(pos, false);

                for (int j = 0; j < state.getValue(EGGS); ++j) {
                    worldIn.levelEvent(2001, pos, Block.getId(state));
                    EntityPlatypus platypus = EntityType.PLATYPUS.create(worldIn, EntitySpawnReason.BREEDING);
                    if (platypus != null) {
                        platypus.setAge(-24000);
                        platypus.setPos((double)pos.getX() + 0.3D + (double)j * 0.2D, (double)pos.getY(), (double)pos.getZ() + 0.3D);
                        worldIn.addFreshEntity(platypus);
                    }
                }
            }
        }
    }

    public void playerDestroy(Level worldIn, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity te, ItemStack stack) {
        super.playerDestroy(worldIn, player, pos, state, te, stack);
        this.removeOneEgg(worldIn, pos, state);
    }

    public boolean canBeReplaced(BlockState state, BlockPlaceContext useContext) {
        return useContext.getItemInHand().getItem() == this.asItem() && state.getValue(EGGS) < 4 ? true : super.canBeReplaced(state, useContext);
    }

    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState blockstate = context.getLevel().getBlockState(context.getClickedPos());
        return blockstate.is(this) ? blockstate.setValue(EGGS, Integer.valueOf(Math.min(4, blockstate.getValue(EGGS) + 1))) : super.getStateForPlacement(context);
    }

    public VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
        return state.getValue(EGGS) > 1 ? MULTI_EGG_SHAPE : ONE_EGG_SHAPE;
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HATCH, EGGS);
    }
}
