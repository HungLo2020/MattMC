package net.minecraft.world.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.TaczGunBallistics.DamagePoint;
import net.minecraft.world.item.TaczGunBallistics.SpreadOffset;
import net.minecraft.world.item.TaczGunBallistics.Vec3Like;
import org.junit.jupiter.api.Test;

class TaczGunBallisticsTest {
	private static final Path PROJECT_ROOT = Paths.get(System.getProperty("user.dir"));

	private static String readSource(String relativePath) throws IOException {
		return Files.readString(PROJECT_ROOT.resolve(relativePath)).replace("\r\n", "\n").replace('\r', '\n');
	}

	@Test
	void stg44UsesGunpackAimInaccuracyAndDamageCurve() {
		TaczGunBallistics.GunBallistics stg44 = TaczGunBallistics.dataForTest("stg44");

		assertEquals(4.5F, stg44.inaccuracy().get(TaczGunBallistics.InaccuracyType.STAND), 0.0001F);
		assertEquals(0.15F, stg44.inaccuracy().get(TaczGunBallistics.InaccuracyType.AIM), 0.0001F);
		assertEquals(0.5F, stg44.crawlRecoilMultiplier(), 0.0001F);
		assertEquals(4, stg44.recoil().pitch().keyFrames().size());
		assertEquals(2, stg44.recoil().yaw().keyFrames().size());

		List<DamagePoint> damageCurve = TaczGunBallistics.damageCurve("stg44", TaczFireMode.AUTO, 1, 12.0F);
		assertEquals(3, damageCurve.size());
		assertDamagePoint(damageCurve.get(0), 30.0F, 12.0F);
		assertDamagePoint(damageCurve.get(1), 60.0F, 11.0F);
		assertDamagePoint(damageCurve.get(2), Float.MAX_VALUE, 9.0F);
	}

	@Test
	void shotgunDamageCurveIsSplitPerPelletLikeUpstream() {
		List<DamagePoint> damageCurve = TaczGunBallistics.damageCurve("db_short", TaczFireMode.SEMI, 16, 24.0F);

		assertEquals(3, damageCurve.size());
		assertDamagePoint(damageCurve.get(0), 5.0F, 1.5F);
		assertDamagePoint(damageCurve.get(1), 9.0F, 1.125F);
		assertDamagePoint(damageCurve.get(2), Float.MAX_VALUE, 0.75F);
	}

	@Test
	void fireModeAdjustmentsApplyToAllServerBulletProperties() {
		TaczGunDefinitions.Gun type81 = TaczGunDefinitions.GUN_BY_ID.get("type_81");
		assertEquals(type81.bulletSpeed() + 1.0F, TaczGunBallistics.bulletSpeed("type_81", TaczFireMode.SEMI, type81.bulletSpeed()), 0.0001F);
		assertEquals(type81.headshotMultiplier() + 0.25F, TaczGunBallistics.headshotMultiplier("type_81", TaczFireMode.SEMI, type81.headshotMultiplier()), 0.0001F);
		assertEquals(11.0F, TaczGunBallistics.damageCurve("type_81", TaczFireMode.SEMI, 1, type81.damage()).get(0).damage(), 0.0001F);

		TaczGunDefinitions.Gun hkG3 = TaczGunDefinitions.GUN_BY_ID.get("hk_g3");
		assertEquals(hkG3.bulletSpeed() - 0.5F, TaczGunBallistics.bulletSpeed("hk_g3", TaczFireMode.AUTO, hkG3.bulletSpeed()), 0.0001F);
	}

	@Test
	void scriptedSpreadVectorMatchesUpstreamScreenPlaneMath() {
		Vec3Like vector = TaczGunBallistics.directionFromScriptedSpread(0.0F, 0.0F, 1.0F, new SpreadOffset(1.0, 0.0));
		double expectedScale = 1.0 / Math.sqrt(65.0);

		assertEquals(expectedScale, vector.x(), 0.0000001);
		assertEquals(0.0, vector.y(), 0.0000001);
		assertEquals(8.0 * expectedScale, vector.z(), 0.0000001);
	}

	@Test
	void recoilSplineUsesUpstreamThirtyMillisecondOffset() {
		TaczGunBallistics.GunBallistics stg44 = TaczGunBallistics.dataForTest("stg44");
		TaczGunBallistics.RecoilSpline pitch = stg44.recoil().pitch().sample(1.0F);

		assertEquals(0.0, pitch.value(0.0), 0.0000001);
		assertEquals(0.66, pitch.value(30.0), 0.0000001);
		assertTrue(pitch.isValidPoint(630.0));
		assertTrue(!pitch.isValidPoint(631.0));
	}

	@Test
	void recoilAttachmentModifiersUseUpstreamParameterizedRules() {
		TaczGunBallistics.AttachmentRecoilModifiers grip = TaczGunBallistics.attachmentRecoilModifiers("grip_vertical_ranger");

		assertEquals(0.8F, grip.pitch().eval(1.0F), 0.0001F);
		assertEquals(0.7F, grip.yaw().eval(1.0F), 0.0001F);
	}

	@Test
	void attachmentModifierFunctionsSupportTaczLuaStyleArithmeticSnippets() {
		TaczGunBallistics.ParameterizedModifiers conditional = new TaczGunBallistics.ParameterizedModifiers(List.of(
			new TaczGunBallistics.Modifier(0.0, 0.0, 1.0, "if (x > 0.5) then y = x*1.5 else y = x*1.75 end")
		));
		TaczGunBallistics.ParameterizedModifiers defaultValueExpressionHigh = new TaczGunBallistics.ParameterizedModifiers(List.of(
			new TaczGunBallistics.Modifier(10.0, 0.0, 1.0, "if (x > 20) then y = r + 5 else y = x * 3 end")
		));
		TaczGunBallistics.ParameterizedModifiers defaultValueExpressionLow = new TaczGunBallistics.ParameterizedModifiers(List.of(
			new TaczGunBallistics.Modifier(0.0, 0.0, 1.0, "if (x > 20) then y = r + 5 else y = x * 3 end")
		));

		assertEquals(1.2F, conditional.eval(0.8), 0.0001F);
		assertEquals(0.7F, conditional.eval(0.4), 0.0001F);
		assertEquals(17.0F, defaultValueExpressionHigh.eval(12.0), 0.0001F);
		assertEquals(12.0F, defaultValueExpressionLow.eval(4.0), 0.0001F);
	}

	@Test
	void firingPathCarriesAimStateAndDataDrivenBallistics() throws IOException {
		String item = readSource("src/main/java/net/minecraft/world/item/TaczMvpGunItem.java");
		String clientInput = readSource("src/main/java/net/minecraft/client/tacz/TaczClientInputHandler.java");
		String cameraRecoil = readSource("src/main/java/net/minecraft/client/tacz/TaczCameraRecoil.java");
		String gameRenderer = readSource("src/main/java/net/minecraft/client/renderer/GameRenderer.java");
		String serverInput = readSource("src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java");
		String bullet = readSource("src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java");

		assertTrue(item.contains("TaczGunBallistics.inaccuracy("), "Gun firing must use gunpack inaccuracy states");
		assertTrue(item.contains("TaczGunBallistics.damageCurve("), "Gun firing must install the gunpack damage curve on bullets");
		assertTrue(item.contains("TaczGunBallistics.bulletSpeed("), "Gun firing must use fire-mode bullet speed adjustments");
		assertTrue(item.contains("TaczGunBallistics.headshotMultiplier("), "Gun firing must use fire-mode headshot adjustments");
		assertTrue(item.contains("TaczGunBallistics.knockback("), "Gun firing must use fire-mode knockback adjustments");
		assertTrue(clientInput.contains("new TaczGunInputC2SPayload(TaczGunInputC2SPayload.Action.SHOOT, precisionAiming)"),
			"Client shoot packets must tell the server when ADS is fully settled");
		assertTrue(clientInput.contains("TaczCameraRecoil.trigger(itemStack)"),
			"Client shot feedback must start camera recoil for every local shot");
		assertTrue(cameraRecoil.contains("TaczGunBallistics.recoilInstance("),
			"Camera recoil must be driven by gunpack recoil data and installed attachments");
		assertTrue(gameRenderer.indexOf("TaczCameraRecoil.apply(this.minecraft)") < gameRenderer.indexOf(".setup(this.minecraft.level, entity"),
			"Camera recoil must update player rotation before the render camera is set up");
		assertTrue(serverInput.contains("payload.precisionAiming()"),
			"Server shoot handling must pass the synced ADS state into gun firing");
		assertTrue(bullet.contains("for (TaczGunBallistics.DamagePoint point : this.damageCurve)"),
			"Bullet damage must be selected from the gunpack curve");
		assertTrue(!bullet.contains("distance <= 24.0") && !bullet.contains("distance >= 48.0"),
			"The old hard-coded 24-48 block falloff must not return");
	}

	@Test
	void displayShootAliasesTargetImportedTaczSounds() throws IOException {
		JsonObject sounds = JsonParser.parseString(readSource("src/main/resources/assets/minecraft/sounds.json")).getAsJsonObject();
		Map<String, String> aliases = Map.of(
			"deagle_golden.shoot", "minecraft:tacz_sounds/deagle/deagle_shoot",
			"hk_mk23.shoot", "minecraft:tacz_sounds/mk23/mk23_shoot",
			"sks_tactical.shoot", "minecraft:tacz_sounds/sks/sks_shoot",
			"vector45.shoot", "minecraft:tacz_sounds/victor45/vector_shoot"
		);

		for (Map.Entry<String, String> alias : aliases.entrySet()) {
			JsonObject sound = sounds.getAsJsonObject(alias.getKey());
			assertTrue(sound != null, alias.getKey() + " must exist because item shooting resolves gunId.shoot");
			assertEquals(alias.getValue(), sound.getAsJsonArray("sounds").get(0).getAsString());
		}
	}

	@Test
	void reloadAudioComesFromTaczAnimationKeyframesOnly() throws IOException {
		String item = readSource("src/main/java/net/minecraft/world/item/TaczMvpGunItem.java");
		String animationSounds = readSource("src/main/java/net/minecraft/client/tacz/TaczAnimationSoundEffects.java");

		assertTrue(animationSounds.contains("sound_effects"), "TACZ reload audio should be scheduled from animation sound_effects keyframes");
		assertFalse(item.contains("this.sound(\"reload_start\")"), "Item-level reload_start audio duplicates animation keyframe audio");
		assertFalse(item.contains("this.sound(\"reload_end\")"), "Item-level reload_end audio duplicates animation keyframe audio");
	}

	private static void assertDamagePoint(DamagePoint actual, float distance, float damage) {
		assertEquals(distance, actual.distance(), 0.0001F);
		assertEquals(damage, actual.damage(), 0.0001F);
	}
}
