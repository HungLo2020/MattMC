package net.minecraft.client.tacz;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TaczGunBallistics;
import net.minecraft.world.item.TaczMvpGunItem;

public final class TaczCameraRecoil {
	private static TaczGunBallistics.RecoilSpline pitchSpline = TaczGunBallistics.RecoilSpline.EMPTY;
	private static TaczGunBallistics.RecoilSpline yawSpline = TaczGunBallistics.RecoilSpline.EMPTY;
	private static long shootTimeStamp = -1L;
	private static double previousPitch;
	private static double previousYaw;

	private TaczCameraRecoil() {
	}

	public static void trigger(ItemStack itemStack) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || !(itemStack.getItem() instanceof TaczMvpGunItem gunItem)) {
			return;
		}

		float partialTicks = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		float aimingProgress = TaczGlock17AnimationController.aimProgress(partialTicks);
		boolean crawling = !player.isSwimming() && player.getPose() == Pose.SWIMMING;
		TaczGunBallistics.RecoilInstance recoil = TaczGunBallistics.recoilInstance(
			gunItem.gunId(),
			itemStack,
			aimingProgress,
			crawling,
			TaczScopeData.zoom(itemStack)
		);
		pitchSpline = recoil.pitch();
		yawSpline = recoil.yaw();
		shootTimeStamp = System.currentTimeMillis();
		previousPitch = 0.0;
		previousYaw = 0.0;
	}

	public static void apply(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || shootTimeStamp < 0L) {
			return;
		}

		long elapsedMillis = System.currentTimeMillis() - shootTimeStamp;
		if (pitchSpline.isValidPoint(elapsedMillis)) {
			double value = pitchSpline.value(elapsedMillis);
			player.setXRot(player.getXRot() - (float)(value - previousPitch));
			previousPitch = value;
		}
		if (yawSpline.isValidPoint(elapsedMillis)) {
			double value = yawSpline.value(elapsedMillis);
			player.setYRot(player.getYRot() - (float)(value - previousYaw));
			previousYaw = value;
		}
		if (!pitchSpline.isValidPoint(elapsedMillis) && !yawSpline.isValidPoint(elapsedMillis)) {
			shootTimeStamp = -1L;
			previousPitch = 0.0;
			previousYaw = 0.0;
		}
	}
}
