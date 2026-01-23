package com.github.alexthe666.alexsmobs.block;

import com.github.alexthe666.alexsmobs.entity.AMEntityRegistry;
import com.github.alexthe666.alexsmobs.entity.EntityCrocodile;
import com.github.alexthe666.alexsmobs.entity.EntityCaiman;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.entity.EntityType;

import java.util.function.Supplier;

public class BlockCaimanEgg extends Block {
    public static final IntegerProperty HATCH = net.minecraft.world.level.block.state.properties.BlockStateProperties.HATCH;
    public static final IntegerProperty EGGS = net.minecraft.world.level.block.state.properties.BlockStateProperties.EGGS;
    private static final net.minecraft.world.phys.shapes.VoxelShape ONE_EGG_SHAPE = Block.box(3.0D, 0.0D, 3.0D, 12.0D, 7.0D, 12.0D);
    private static final net.minecraft.world.phys.shapes.VoxelShape MULTI_EGG_SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 7.0D, 15.0D);
    
    public BlockCaimanEgg(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(HATCH, Integer.valueOf(0)).setValue(EGGS, Integer.valueOf(1)));
    }
    
    // Delegate to BlockReptileEgg methods at runtime, not during static init
    private static Supplier<EntityType<?>> getBirths() {
        return () -> AMEntityRegistry.CAIMAN.get();
    }
    
    // Copy all methods from BlockReptileEgg but use our deferred entity type lookup
    public static boolean hasProperHabitat(net.minecraft.world.level.BlockGetter reader, net.minecraft.core.BlockPos blockReader) {
        return BlockReptileEgg.isProperHabitat(reader, blockReader.below());
    }

    public static boolean isProperHabitat(net.minecraft.world.level.BlockGetter reader, net.minecraft.core.BlockPos pos) {
        return reader.getBlockState(pos).is(net.minecraft.tags.BlockTags.SAND) || reader.getBlockState(pos).is(com.github.alexthe666.alexsmobs.misc.AMTagRegistry.CROCODILE_SPAWNS);
    }

    public void stepOn(net.minecraft.world.level.Level worldIn, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.entity.Entity entityIn) {
        this.tryTrample(worldIn, pos, entityIn, 100);
        super.stepOn(worldIn, pos, state, entityIn);
    }

    public void fallOn(net.minecraft.world.level.Level worldIn, net.minecraft.world.level.block.state.BlockState state, net.minecraft.core.BlockPos pos, net.minecraft.world.entity.Entity entityIn, float fallDistance) {
        if (!(entityIn instanceof net.minecraft.world.entity.monster.Zombie)) {
            this.tryTrample(worldIn, pos, entityIn, 3);
        }
        super.fallOn(worldIn, state, pos, entityIn, fallDistance);
    }

    private void tryTrample(net.minecraft.world.level.Level worldIn, net.minecraft.core.BlockPos pos, net.minecraft.world.entity.Entity trampler, int chances) {
        if (this.canTrample(worldIn, trampler)) {
            if (worldIn.isClientSide() == false && worldIn.random.nextInt(chances) == 0) {
                net.minecraft.world.phys.AABB bb = new net.minecraft.world.phys.AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1).inflate(25, 25, 25);
                if (trampler instanceof net.minecraft.world.entity.LivingEntity) {
                    java.util.List<net.minecraft.world.entity.Mob> list = worldIn.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, bb, living -> living.isAlive() && living.getType() == getBirths().get());
                    for (net.minecraft.world.entity.Mob living : list) {
                        if (!(living instanceof net.minecraft.world.entity.TamableAnimal) || !((net.minecraft.world.entity.TamableAnimal)living).isTame() || !((net.minecraft.world.entity.TamableAnimal)living).isOwnedBy((net.minecraft.world.entity.LivingEntity) trampler)) {
                            living.setTarget((net.minecraft.world.entity.LivingEntity) trampler);
                        }
                    }
                }
                net.minecraft.world.level.block.state.BlockState blockstate = worldIn.getBlockState(pos);
                this.removeOneEgg(worldIn, pos, blockstate);
            }
        }
    }

    private void removeOneEgg(net.minecraft.world.level.Level worldIn, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        worldIn.playSound(null, pos, net.minecraft.sounds.SoundEvents.TURTLE_EGG_BREAK, net.minecraft.sounds.SoundSource.BLOCKS, 0.7F, 0.9F + worldIn.random.nextFloat() * 0.2F);
        int i = state.getValue(EGGS);
        if (i <= 1) {
            worldIn.destroyBlock(pos, false);
        } else {
            worldIn.setBlock(pos, state.setValue(EGGS, Integer.valueOf(i - 1)), 2);
            worldIn.gameEvent(net.minecraft.world.level.gameevent.GameEvent.BLOCK_DESTROY, pos, net.minecraft.world.level.gameevent.GameEvent.Context.of(state));
            worldIn.levelEvent(2001, pos, Block.getId(state));
        }
    }

    public void randomTick(net.minecraft.world.level.block.state.BlockState state, net.minecraft.server.level.ServerLevel worldIn, net.minecraft.core.BlockPos pos, net.minecraft.util.RandomSource random) {
        if (this.canGrow(worldIn) && hasProperHabitat(worldIn, pos)) {
            int i = state.getValue(HATCH);
            if (i < 2) {
                worldIn.playSound(null, pos, net.minecraft.sounds.SoundEvents.TURTLE_EGG_CRACK, net.minecraft.sounds.SoundSource.BLOCKS, 0.7F, 0.9F + random.nextFloat() * 0.2F);
                worldIn.gameEvent(net.minecraft.world.level.gameevent.GameEvent.BLOCK_DESTROY, pos, net.minecraft.world.level.gameevent.GameEvent.Context.of(state));
                worldIn.setBlock(pos, state.setValue(HATCH, Integer.valueOf(i + 1)), 2);
            } else {
                worldIn.playSound(null, pos, net.minecraft.sounds.SoundEvents.TURTLE_EGG_HATCH, net.minecraft.sounds.SoundSource.BLOCKS, 0.7F, 0.9F + random.nextFloat() * 0.2F);
                worldIn.gameEvent(net.minecraft.world.level.gameevent.GameEvent.BLOCK_DESTROY, pos, net.minecraft.world.level.gameevent.GameEvent.Context.of(state));
                worldIn.removeBlock(pos, false);
                for (int j = 0; j < state.getValue(EGGS); ++j) {
                    worldIn.levelEvent(2001, pos, Block.getId(state));
                    net.minecraft.world.entity.Entity fromType = getBirths().get().create(worldIn, net.minecraft.world.entity.EntitySpawnReason.BREEDING);
                    if(fromType instanceof net.minecraft.world.entity.animal.Animal animal){
                        animal.setAge(-24000);
                        // Skip restrictTo - not available in 1.21
                    }
                    net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome = worldIn.getBiome(pos);
                    fromType.setPos((double) pos.getX() + 0.3D + (double) j * 0.2D, pos.getY(), (double) pos.getZ() + 0.3D);
                    if (!worldIn.isClientSide()) {
                        net.minecraft.world.entity.player.Player closest = worldIn.getNearestPlayer(pos.getX() + 0.5F, pos.getY() + 0.5F, pos.getZ() + 0.5F, 20, net.minecraft.world.entity.EntitySelector.NO_SPECTATORS);
                        if (closest != null) {
                            if(fromType instanceof net.minecraft.world.entity.TamableAnimal tamableAnimal){
                                tamableAnimal.setTame(true, true);
                                tamableAnimal.setOrderedToSit(true);
                                tamableAnimal.tame(closest);
                            }
                            if(fromType instanceof com.github.alexthe666.alexsmobs.entity.EntityCrocodile crocodile){
                                // Skip desert crocodile setting since we don't have the tag
                            }
                        }
                        worldIn.addFreshEntity(fromType);
                    }
                }
            }
        }
    }

    public void onPlace(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.Level worldIn, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState oldState, boolean isMoving) {
        if (hasProperHabitat(worldIn, pos) && !worldIn.isClientSide()) {
            worldIn.levelEvent(2005, pos, 0);
        }
    }

    private boolean canGrow(net.minecraft.world.level.Level worldIn) {
        float f = worldIn.getTimeOfDay(1.0F);
        if ((double) f < 0.8D && (double) f > 0.65D) {
            return true;
        } else {
            return worldIn.random.nextInt(15) == 0;
        }
    }

    public void playerDestroy(net.minecraft.world.level.Level worldIn, net.minecraft.world.entity.player.Player player, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state, @javax.annotation.Nullable net.minecraft.world.level.block.entity.BlockEntity te, net.minecraft.world.item.ItemStack stack) {
        super.playerDestroy(worldIn, player, pos, state, te, stack);
        this.removeOneEgg(worldIn, pos, state);
    }

    public boolean canBeReplaced(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.item.context.BlockPlaceContext useContext) {
        return useContext.getItemInHand().getItem() == this.asItem() && state.getValue(EGGS) < 4 || super.canBeReplaced(state, useContext);
    }

    @javax.annotation.Nullable
    public net.minecraft.world.level.block.state.BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        net.minecraft.world.level.block.state.BlockState blockstate = context.getLevel().getBlockState(context.getClickedPos());
        return blockstate.getBlock() == this ? blockstate.setValue(EGGS, Integer.valueOf(Math.min(4, blockstate.getValue(EGGS) + 1))) : super.getStateForPlacement(context);
    }

    public net.minecraft.world.phys.shapes.VoxelShape getShape(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.BlockGetter worldIn, net.minecraft.core.BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        return state.getValue(EGGS) > 1 ? MULTI_EGG_SHAPE : ONE_EGG_SHAPE;
    }

    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, net.minecraft.world.level.block.state.BlockState> builder) {
        builder.add(HATCH, EGGS);
    }

    private boolean canTrample(net.minecraft.world.level.Level worldIn, net.minecraft.world.entity.Entity trampler) {
        if (!(trampler instanceof com.github.alexthe666.alexsmobs.entity.EntityCrocodile || trampler instanceof com.github.alexthe666.alexsmobs.entity.EntityCaiman) && !(trampler instanceof net.minecraft.world.entity.ambient.Bat)) {
            if (!(trampler instanceof net.minecraft.world.entity.LivingEntity)) {
                return false;
            } else {
                return trampler instanceof net.minecraft.world.entity.player.Player;  // Simplified - only allow player trampling
            }
        } else {
            return false;
        }
    }
}
