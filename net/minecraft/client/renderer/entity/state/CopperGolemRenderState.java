package net.minecraft.client.renderer.entity.state;

import java.util.Optional;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.animal.coppergolem.CopperGolemState;
import net.minecraft.world.level.block.WeatheringCopper.WeatherState;
import net.minecraft.world.level.block.state.BlockState;

@Environment(EnvType.CLIENT)
public class CopperGolemRenderState extends ArmedEntityRenderState {
	public WeatherState weathering = WeatherState.UNAFFECTED;
	public CopperGolemState copperGolemState = CopperGolemState.IDLE;
	public final AnimationState idleAnimationState = new AnimationState();
	public final AnimationState interactionGetItem = new AnimationState();
	public final AnimationState interactionGetNoItem = new AnimationState();
	public final AnimationState interactionDropItem = new AnimationState();
	public final AnimationState interactionDropNoItem = new AnimationState();
	public Optional<BlockState> blockOnAntenna = Optional.empty();
}
