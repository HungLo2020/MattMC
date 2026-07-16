package net.minecraft.world.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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
	void nativeModifierEvaluatorMatchesLegacyJavaExpressionSemantics() {
		String[] expressions = {
			"y = x + r * 2",
			"y = (x + r) * 2",
			"y = -x - +r",
			"if x >= r then y = 1 else y = 0 end",
			"if x <= r then y = 1 else y = 0 end",
			"if x == r then y = 1 else y = 0 end",
			"if x ~= r then y = 1 else y = 0 end",
			"if x > r then y = x / r else y = r / x end",
			"if x < r then y = .5 else y = 1. end",
			"if r - 3 then y = x else y = r end",
			" z = x + r ",
			"if x then y = 1"
		};
		double[][] inputs = {
			{2.0, 3.0},
			{3.0, 2.0},
			{0.0, 3.0},
			{Double.POSITIVE_INFINITY, 1.0}
		};

		for (String expression : expressions) {
			for (double[] input : inputs) {
				assertNativeMatchesLegacy(input[0], input[1], expression);
			}
		}
	}

	@Test
	void nativeModifierEvaluatorFallsBackLikeLegacyJavaOnMalformedExpressions() {
		String[] expressions = {
			"y =",
			"y = x + nope",
			"y = (x + r",
			"y = 1e3",
			"if x > then y = 1 else y = 0 end"
		};

		for (String expression : expressions) {
			assertNativeMatchesLegacy(5.0, 2.0, expression);
			assertEquals(5.0, NativeTaczFunctionModifierEvaluator.eval(5.0, 2.0, expression), 0.0000001);
		}
	}

	@Test
	void nativeModifierEvaluatorMatchesRealAttachmentFunctionExpressions() throws IOException {
		List<String> expressions = loadRealAttachmentFunctionExpressions();
		assertTrue(expressions.contains("if (x > 0.5) then y = x*1.5 else y = x*1.75 end"));
		assertTrue(expressions.contains("if (x > 2) then y = x + 2 else y = x end"));
		assertTrue(expressions.contains("y = 1"));

		double[][] inputs = {
			{0.4, 0.4},
			{0.8, 0.8},
			{2.5, 1.0},
			{22.0, 12.0}
		};
		for (String expression : expressions) {
			for (double[] input : inputs) {
				assertNativeMatchesLegacy(input[0], input[1], expression);
			}
		}
	}

	@Test
	void productionParameterizedModifiersUseNativeEvaluatorFallback() {
		TaczGunBallistics.ParameterizedModifiers malformed = new TaczGunBallistics.ParameterizedModifiers(List.of(
			new TaczGunBallistics.Modifier(2.0, 0.0, 1.0, "y =")
		));
		TaczGunBallistics.ParameterizedModifiers wrongAssignmentTarget = new TaczGunBallistics.ParameterizedModifiers(List.of(
			new TaczGunBallistics.Modifier(2.0, 0.0, 1.0, "z = x + 1")
		));

		assertEquals(6.0F, malformed.eval(4.0), 0.0001F);
		assertEquals(6.0F, wrongAssignmentTarget.eval(4.0), 0.0001F);
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
	void reloadAudioComesFromDisplaySoundsLikeUpstream() throws IOException {
		String item = readSource("src/main/java/net/minecraft/world/item/TaczMvpGunItem.java");
		String animationSounds = readSource("src/main/java/net/minecraft/client/tacz/TaczAnimationSoundEffects.java");
		String controller = readSource("src/main/java/net/minecraft/client/tacz/TaczGlock17AnimationController.java");

		assertTrue(controller.contains("boolean playedDisplayReload = TaczAnimationSoundEffects.playReload(itemStack, TaczMvpGunItem.getAmmo(itemStack) <= 0)"),
			"Reload should play one display-level reload sound at reload start like upstream SoundPlayManager.playReloadSound");
		assertTrue(controller.contains("triggerMain(itemStack, TaczGunAnimationTimings.reloadSequence(itemStack), !playedDisplayReload)"),
			"Reload animation keyframe sounds should be suppressed only when a display reload sound was actually found");
		assertTrue(animationSounds.contains("\"display/guns/\" + gunId + \"_display.json\""),
			"Reload sounds should come from the imported gun display sounds table");
		assertTrue(animationSounds.contains("reload_empty") && animationSounds.contains("reload_tactical"),
			"Reload sound selection must preserve upstream empty versus tactical variants");
		assertTrue(animationSounds.contains("importedSoundEventForDisplayTarget"),
			"Display sound events imported under legacy names must be resolved through their actual sound targets");
		assertFalse(item.contains("this.sound(\"reload_start\")"), "Item-level reload_start audio duplicates animation keyframe audio");
		assertFalse(item.contains("this.sound(\"reload_end\")"), "Item-level reload_end audio duplicates animation keyframe audio");
	}

	@Test
	void mk14AndM1GarandReloadAudioUseDisplayEntriesForDifferentReasons() throws IOException {
		JsonObject mk14Display = JsonParser.parseString(readSource("src/main/resources/assets/minecraft/display/guns/mk14_display.json")).getAsJsonObject();
		JsonObject mk14Reload = JsonParser.parseString(readSource("src/main/resources/assets/minecraft/animations/mk14.animation.json"))
			.getAsJsonObject()
			.getAsJsonObject("animations")
			.getAsJsonObject("reload_tactical");
		JsonObject garandDisplay = JsonParser.parseString(readSource("src/main/resources/assets/minecraft/display/guns/m1_garand_display.json")).getAsJsonObject();
		JsonObject garandReload = JsonParser.parseString(readSource("src/main/resources/assets/minecraft/animations/m1_garand.animation.json"))
			.getAsJsonObject()
			.getAsJsonObject("animations")
			.getAsJsonObject("reload_tactical");
		JsonObject sounds = JsonParser.parseString(readSource("src/main/resources/assets/minecraft/sounds.json")).getAsJsonObject();

		assertEquals("minecraft:mk14/mk14_reload_empty", mk14Display.getAsJsonObject("sounds").get("reload_empty").getAsString());
		assertEquals("minecraft:mk14/mk14_reload_tactical", mk14Display.getAsJsonObject("sounds").get("reload_tactical").getAsString());
		assertTrue(mk14Reload.getAsJsonObject("sound_effects").size() > 1,
			"MK14 has multiple reload animation sound keyframes, so reload audio must not also schedule that sequence");

		assertEquals("minecraft:rifles/m1_garand/m1_garand_reload_empty", garandDisplay.getAsJsonObject("sounds").get("reload_empty").getAsString());
		assertEquals("minecraft:rifles/m1_garand/m1_garand_reload_normal", garandDisplay.getAsJsonObject("sounds").get("reload_tactical").getAsString());
		assertFalse(garandReload.has("sound_effects"),
			"M1 Garand has no reload animation sound keyframes, so display reload audio is required");

		assertEquals("minecraft:tacz_sounds/mk14/mk14_reload_empty",
			sounds.getAsJsonObject("mk14.reload_start").getAsJsonArray("sounds").get(0).getAsString());
		assertEquals("minecraft:tacz_sounds/rifles/m1_garand/m1_garand_reload_empty",
			sounds.getAsJsonObject("m1_garand.reload_end").getAsJsonArray("sounds").get(0).getAsString());
	}

	@Test
	void vectorReloadFallsBackToAnimationKeyframesBecauseUpstreamDisplayHasNoReloadSounds() throws IOException {
		JsonObject vectorDisplay = JsonParser.parseString(readSource("src/main/resources/assets/minecraft/display/guns/vector45_display.json")).getAsJsonObject();
		JsonObject vectorReload = JsonParser.parseString(readSource("src/main/resources/assets/minecraft/animations/vector45.animation.json"))
			.getAsJsonObject()
			.getAsJsonObject("animations")
			.getAsJsonObject("reload_tactical");
		JsonObject sounds = JsonParser.parseString(readSource("src/main/resources/assets/minecraft/sounds.json")).getAsJsonObject();

		assertFalse(vectorDisplay.getAsJsonObject("sounds").has("reload_empty"),
			"Vector's upstream display does not define display-level reload sounds");
		assertFalse(vectorDisplay.getAsJsonObject("sounds").has("reload_tactical"),
			"Vector's upstream display does not define display-level reload sounds");
		assertTrue(vectorReload.getAsJsonObject("sound_effects").size() > 1,
			"Vector reload audio is carried by animation keyframes and must remain scheduled");
		assertEquals("minecraft:tacz_sounds/victor45/victor_reload_raise",
			sounds.getAsJsonObject("victor45/victor_reload_raise").getAsJsonArray("sounds").get(0).getAsString());
	}

	@Test
	void reloadInputDoesNotRescheduleTheSameAnimationSoundSequence() throws IOException {
		String item = readSource("src/main/java/net/minecraft/world/item/TaczMvpGunItem.java");
		String controller = readSource("src/main/java/net/minecraft/client/tacz/TaczGlock17AnimationController.java");

		assertTrue(item.contains("if (player.isUsingItem())"),
			"Server reload handling must not restart use duration or reload sounds while a reload is already active");
		assertTrue(controller.contains("if (isReloading(itemStack))"),
			"Client reload animation scheduling must ignore repeated reload input while the same gun is already reloading");
	}

	@Test
	void shootAnimationAudioDoesNotDuplicateTheGunReportSound() throws IOException {
		String clientInput = readSource("src/main/java/net/minecraft/client/tacz/TaczClientInputHandler.java");
		String item = readSource("src/main/java/net/minecraft/world/item/TaczMvpGunItem.java");
		String controller = readSource("src/main/java/net/minecraft/client/tacz/TaczGlock17AnimationController.java");
		String animationSounds = readSource("src/main/java/net/minecraft/client/tacz/TaczAnimationSoundEffects.java");
		JsonObject sounds = JsonParser.parseString(readSource("src/main/resources/assets/minecraft/sounds.json")).getAsJsonObject();
		JsonObject m1Animation = JsonParser.parseString(readSource("src/main/resources/assets/minecraft/animations/m1.animation.json")).getAsJsonObject();

		assertTrue(controller.contains("TaczAnimationSoundEffects.scheduleShoot(itemStack, animationName)"),
			"Shoot animations need duplicate-aware sound scheduling");
		assertTrue(animationSounds.contains("duplicatesGunShootSound") && animationSounds.contains("ResourceLocation.withDefaultNamespace(gunId + \".shoot\")"),
			"Animation sound scheduling must compare shoot keyframes against the runtime gunId.shoot event");
		assertTrue(animationSounds.contains("soundEventTargets"),
			"Duplicate detection must resolve sounds.json aliases instead of relying only on event-name equality");
		assertTrue(clientInput.contains("SoundSource.PLAYERS,\n\t\t\t\t0.8F,"),
			"Local gun report volume should match upstream TACZ's 0.8 first-person shoot volume");
		assertTrue(item.contains("SHOOT_SOUND_VOLUME = 0.8F"),
			"Server gun report volume should match the local TACZ shoot volume");

		String m1ShootEffect = m1Animation.getAsJsonObject("animations")
			.getAsJsonObject("shoot")
			.getAsJsonObject("sound_effects")
			.getAsJsonObject("0.0")
			.get("effect")
			.getAsString();
		String m1ShootTarget = sounds.getAsJsonObject("m1.shoot").getAsJsonArray("sounds").get(0).getAsString();
		String m1AnimationTarget = sounds.getAsJsonObject(m1ShootEffect.substring("minecraft:".length())).getAsJsonArray("sounds").get(0).getAsString();

		assertEquals(m1ShootTarget, m1AnimationTarget,
			"The M1 Carbine shoot animation keyframe resolves to the same sound as the item gun report and must be suppressed");
	}

	private static void assertDamagePoint(DamagePoint actual, float distance, float damage) {
		assertEquals(distance, actual.distance(), 0.0001F);
		assertEquals(damage, actual.damage(), 0.0001F);
	}

	private static void assertNativeMatchesLegacy(double value, double input, String expression) {
		double expected = LegacyFunctionModifierEvaluator.eval(value, input, expression);
		double actual = NativeTaczFunctionModifierEvaluator.eval(value, input, expression);
		if (Double.isNaN(expected)) {
			assertTrue(Double.isNaN(actual), "Expected NaN for " + expression);
		} else {
			assertEquals(expected, actual, 0.0000001, expression);
		}
	}

	private static List<String> loadRealAttachmentFunctionExpressions() throws IOException {
		List<String> expressions = new ArrayList<>();
		Path attachments = PROJECT_ROOT.resolve("src/main/resources/data/minecraft/data/attachments");
		try (var paths = Files.list(attachments)) {
			for (Path path : paths.filter(path -> path.getFileName().toString().endsWith("_data.json")).toList()) {
				try (JsonReader reader = new JsonReader(Files.newBufferedReader(path, StandardCharsets.UTF_8))) {
					reader.setStrictness(Strictness.LENIENT);
					collectFunctionExpressions(JsonParser.parseReader(reader), expressions);
				}
			}
		}
		return expressions;
	}

	private static void collectFunctionExpressions(JsonElement element, List<String> expressions) {
		if (element == null || element.isJsonNull()) {
			return;
		}
		if (element.isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
				if ("function".equals(entry.getKey()) && entry.getValue().isJsonPrimitive()) {
					expressions.add(entry.getValue().getAsString());
				} else {
					collectFunctionExpressions(entry.getValue(), expressions);
				}
			}
		} else if (element.isJsonArray()) {
			for (JsonElement child : element.getAsJsonArray()) {
				collectFunctionExpressions(child, expressions);
			}
		}
	}

	private static final class LegacyFunctionModifierEvaluator {
		private LegacyFunctionModifierEvaluator() {
		}

		static double eval(double value, double input, String function) {
			try {
				return evalUnchecked(value, input, function);
			} catch (RuntimeException exception) {
				return value;
			}
		}

		private static double evalUnchecked(double value, double input, String function) {
			String script = function.trim().toLowerCase(java.util.Locale.ENGLISH);
			if (script.startsWith("if")) {
				int thenIndex = script.indexOf("then");
				int elseIndex = script.indexOf("else", thenIndex + 4);
				int endIndex = script.lastIndexOf("end");
				if (thenIndex < 0 || elseIndex < 0 || endIndex < 0) {
					return value;
				}
				String condition = script.substring(2, thenIndex).trim();
				if (condition.startsWith("(") && condition.endsWith(")")) {
					condition = condition.substring(1, condition.length() - 1);
				}
				String branch = evalCondition(condition, value, input)
					? script.substring(thenIndex + 4, elseIndex)
					: script.substring(elseIndex + 4, endIndex);
				return evalAssignment(branch, value, input);
			}
			return evalAssignment(script, value, input);
		}

		private static boolean evalCondition(String condition, double value, double input) {
			for (String operator : new String[]{">=", "<=", "==", "~=", ">", "<"}) {
				int index = condition.indexOf(operator);
				if (index < 0) {
					continue;
				}
				double left = new ExpressionParser(condition.substring(0, index), value, input).parse();
				double right = new ExpressionParser(condition.substring(index + operator.length()), value, input).parse();
				return switch (operator) {
					case ">=" -> left >= right;
					case "<=" -> left <= right;
					case "==" -> left == right;
					case "~=" -> left != right;
					case ">" -> left > right;
					case "<" -> left < right;
					default -> false;
				};
			}
			return new ExpressionParser(condition, value, input).parse() != 0.0;
		}

		private static double evalAssignment(String assignment, double value, double input) {
			String trimmed = assignment.trim();
			int equals = trimmed.indexOf('=');
			if (equals >= 0) {
				String target = trimmed.substring(0, equals).trim();
				if (!target.equals("y")) {
					return value;
				}
				trimmed = trimmed.substring(equals + 1);
			}
			return new ExpressionParser(trimmed, value, input).parse();
		}

		private static final class ExpressionParser {
			private final String expression;
			private final double x;
			private final double r;
			private int cursor;

			ExpressionParser(String expression, double x, double r) {
				this.expression = expression;
				this.x = x;
				this.r = r;
			}

			double parse() {
				double result = this.parseAddSubtract();
				this.skipWhitespace();
				if (this.cursor != this.expression.length()) {
					throw new IllegalArgumentException("Unexpected expression tail");
				}
				return result;
			}

			private double parseAddSubtract() {
				double value = this.parseMultiplyDivide();
				while (true) {
					this.skipWhitespace();
					if (this.consume('+')) {
						value += this.parseMultiplyDivide();
					} else if (this.consume('-')) {
						value -= this.parseMultiplyDivide();
					} else {
						return value;
					}
				}
			}

			private double parseMultiplyDivide() {
				double value = this.parseUnary();
				while (true) {
					this.skipWhitespace();
					if (this.consume('*')) {
						value *= this.parseUnary();
					} else if (this.consume('/')) {
						value /= this.parseUnary();
					} else {
						return value;
					}
				}
			}

			private double parseUnary() {
				this.skipWhitespace();
				if (this.consume('+')) {
					return this.parseUnary();
				}
				if (this.consume('-')) {
					return -this.parseUnary();
				}
				return this.parsePrimary();
			}

			private double parsePrimary() {
				this.skipWhitespace();
				if (this.consume('(')) {
					double value = this.parseAddSubtract();
					if (!this.consume(')')) {
						throw new IllegalArgumentException("Unclosed parenthesis");
					}
					return value;
				}
				if (this.consume('x')) {
					return this.x;
				}
				if (this.consume('r')) {
					return this.r;
				}
				int start = this.cursor;
				while (this.cursor < this.expression.length()) {
					char c = this.expression.charAt(this.cursor);
					if ((c >= '0' && c <= '9') || c == '.') {
						this.cursor++;
					} else {
						break;
					}
				}
				if (start == this.cursor) {
					throw new IllegalArgumentException("Expected expression value");
				}
				return Double.parseDouble(this.expression.substring(start, this.cursor));
			}

			private boolean consume(char expected) {
				this.skipWhitespace();
				if (this.cursor < this.expression.length() && this.expression.charAt(this.cursor) == expected) {
					this.cursor++;
					return true;
				}
				return false;
			}

			private void skipWhitespace() {
				while (this.cursor < this.expression.length() && Character.isWhitespace(this.expression.charAt(this.cursor))) {
					this.cursor++;
				}
			}
		}
	}
}
