package net.minecraft.world.level.material;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMapper;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public abstract class Fluid {
	public static final IdMapper<FluidState> FLUID_STATE_REGISTRY = new IdMapper<>();
	protected final StateDefinition<Fluid, FluidState> stateDefinition;
	final NativeFluidDefinitions.Definition nativeDefinition;
	private FluidState defaultFluidState;
	private final Holder.Reference<Fluid> builtInRegistryHolder = BuiltInRegistries.FLUID.createIntrusiveHolder(this);

	protected Fluid(NativeFluidDefinitions.Definition definition) {
		this.nativeDefinition = definition;
		StateDefinition.Builder<Fluid, FluidState> builder = new StateDefinition.Builder<>(this);
		builder.add(definition.properties().toArray(net.minecraft.world.level.block.state.properties.Property<?>[]::new));
		int[] nextState = {0};
		this.stateDefinition = builder.create(Fluid::defaultFluidState,
			(owner, values, codec) -> new FluidState(owner, values, codec, definition.states().get(nextState[0]++)));
		if (nextState[0] != definition.states().size()) throw new IllegalStateException("Fluid state projection changed");
		this.registerDefaultState(this.stateDefinition.getPossibleStates().get(definition.defaultLocalState()));
	}

	public StateDefinition<Fluid, FluidState> getStateDefinition() {
		return this.stateDefinition;
	}

	protected final void registerDefaultState(FluidState fluidState) {
		this.defaultFluidState = fluidState;
	}

	public final FluidState defaultFluidState() {
		return this.defaultFluidState;
	}

	public abstract Item getBucket();

	protected void animateTick(Level level, BlockPos blockPos, FluidState fluidState, RandomSource randomSource) {
	}

	protected void tick(ServerLevel serverLevel, BlockPos blockPos, BlockState blockState, FluidState fluidState) {
	}

	protected void randomTick(ServerLevel serverLevel, BlockPos blockPos, FluidState fluidState, RandomSource randomSource) {
	}

	protected void entityInside(Level level, BlockPos blockPos, Entity entity, InsideBlockEffectApplier insideBlockEffectApplier) {
	}

	@Nullable
	protected ParticleOptions getDripParticle() {
		return null;
	}

	protected abstract boolean canBeReplacedWith(FluidState fluidState, BlockGetter blockGetter, BlockPos blockPos, Fluid fluid, Direction direction);

	protected abstract Vec3 getFlow(BlockGetter blockGetter, BlockPos blockPos, FluidState fluidState);

	public abstract int getTickDelay(LevelReader levelReader);

	protected boolean isRandomlyTicking() {
		return false;
	}

	protected final boolean isEmpty() {
		return this.nativeDefinition.family() == 0;
	}

	protected final float getExplosionResistance() {
		return this.nativeDefinition.explosionResistance();
	}

	public abstract float getHeight(FluidState fluidState, BlockGetter blockGetter, BlockPos blockPos);

	public final float getOwnHeight(FluidState fluidState) {
		return fluidState.getOwnHeight();
	}

	protected abstract BlockState createLegacyBlock(FluidState fluidState);

	public final boolean isSource(FluidState fluidState) {
		return fluidState.isSource();
	}

	public final int getAmount(FluidState fluidState) {
		return fluidState.getAmount();
	}

	public boolean isSame(Fluid fluid) {
		return fluid == this;
	}

	@Deprecated
	public boolean is(TagKey<Fluid> tagKey) {
		return this.builtInRegistryHolder.is(tagKey);
	}

	public abstract VoxelShape getShape(FluidState fluidState, BlockGetter blockGetter, BlockPos blockPos);

	@Nullable
	public AABB getAABB(FluidState fluidState, BlockGetter blockGetter, BlockPos blockPos) {
		if (this.isEmpty()) {
			return null;
		} else {
			float f = fluidState.getHeight(blockGetter, blockPos);
			return new AABB(blockPos.getX(), blockPos.getY(), blockPos.getZ(), blockPos.getX() + 1.0, blockPos.getY() + f, blockPos.getZ() + 1.0);
		}
	}

	public Optional<SoundEvent> getPickupSound() {
		return Optional.empty();
	}

	@Deprecated
	public Holder.Reference<Fluid> builtInRegistryHolder() {
		return this.builtInRegistryHolder;
	}
}
